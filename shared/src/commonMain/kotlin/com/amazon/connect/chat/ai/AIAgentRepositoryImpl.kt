package com.amazon.connect.chat.ai

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Implementation of AIAgentRepository with streaming support
 * and automatic Claude -> OpenAI fallback.
 */
class AIAgentRepositoryImpl(
    private val config: AIAgentConfig,
    private val httpClient: HttpClient
) : AIAgentRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _currentProvider = MutableStateFlow(config.primaryProvider)
    override val currentProvider: StateFlow<AIProvider> = _currentProvider.asStateFlow()

    private val _isAvailable = MutableStateFlow(true)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    /**
     * Stream AI response with automatic fallback.
     */
    override fun processMessageStream(
        userMessage: String,
        context: ConversationContext
    ): Flow<AIStreamChunk> = flow {
        val messagesWithNew = context.messages + ConversationMessage(
            role = ConversationMessage.ROLE_USER,
            content = userMessage,
            timestamp = currentTimeMillis()
        )

        try {
            // Try primary provider (Claude)
            _currentProvider.value = config.primaryProvider
            emitAll(streamFromProvider(config.primaryProvider, messagesWithNew, context.sessionId))
        } catch (e: Exception) {
            // Switch to fallback provider before emitting error
            _currentProvider.value = config.fallbackProvider

            // Emit error notification with correct (fallback) provider
            emit(AIStreamChunk(
                error = "Primary provider unavailable, switching to ${config.fallbackProvider.name.lowercase()}...",
                provider = config.fallbackProvider.name.lowercase()
            ))

            // Try fallback provider (OpenAI)
            try {
                emitAll(streamFromProvider(config.fallbackProvider, messagesWithNew, context.sessionId))
            } catch (fallbackError: Exception) {
                _isAvailable.value = false
                emit(AIStreamChunk(
                    error = "AI service unavailable. Please try again later or speak with an agent.",
                    done = true,
                    provider = config.fallbackProvider.name.lowercase()
                ))
            }
        }
    }

    private fun streamFromProvider(
        provider: AIProvider,
        messages: List<ConversationMessage>,
        sessionId: String
    ): Flow<AIStreamChunk> = flow {
        val requestBody = AIChatRequest(
            messages = messages,
            provider = provider.name.lowercase(),
            stream = true,
            maxTokens = config.maxTokens,
            temperature = config.temperature,
            systemPrompt = config.systemPrompt,
            sessionId = sessionId
        )

        val response = httpClient.preparePost("${config.proxyBaseUrl}/api/v1/chat/stream") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(requestBody))
            header("X-Provider", provider.name.lowercase())
        }.execute { httpResponse ->
            if (!httpResponse.status.isSuccess()) {
                throw Exception("API returned ${httpResponse.status}")
            }

            val channel: ByteReadChannel = httpResponse.bodyAsChannel()

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: continue

                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()

                    if (data == "[DONE]") {
                        break
                    }

                    if (data.isNotEmpty()) {
                        try {
                            val chunk = json.decodeFromString<AIStreamChunk>(data)
                            emit(chunk.copy(provider = provider.name.lowercase()))
                        } catch (e: Exception) {
                            // Skip malformed chunks - don't emit garbage as text
                            // Log for debugging (in real impl, use proper logging)
                            println("Skipping unparseable SSE chunk: ${data.take(50)}...")
                        }
                    }
                }
            }
        }
    }

    /**
     * Non-streaming message processing (collects stream into response).
     */
    override suspend fun processMessage(
        userMessage: String,
        context: ConversationContext
    ): AIAgentResponse {
        val buffer = StringBuilder()
        var finalChunk: AIStreamChunk? = null
        var lastError: String? = null

        processMessageStream(userMessage, context).collect { chunk ->
            chunk.delta?.let { buffer.append(it) }
            chunk.error?.let { lastError = it }
            if (chunk.done) finalChunk = chunk
        }

        if (buffer.isEmpty() && lastError != null) {
            throw Exception(lastError)
        }

        return AIAgentResponse(
            responseText = cleanResponse(buffer.toString()),
            shouldEscalate = finalChunk?.shouldEscalate ?: false,
            escalationReason = finalChunk?.escalationReason,
            suggestedReplies = finalChunk?.suggestedReplies ?: emptyList(),
            confidence = 0.85f,
            provider = _currentProvider.value
        )
    }

    /**
     * Check health of AI providers.
     */
    override suspend fun healthCheck(): HealthCheckResult {
        val primaryOk = checkProviderHealth(config.primaryProvider)
        val fallbackOk = checkProviderHealth(config.fallbackProvider)

        val active = when {
            primaryOk -> config.primaryProvider
            fallbackOk -> config.fallbackProvider
            else -> config.primaryProvider
        }

        _isAvailable.value = primaryOk || fallbackOk
        _currentProvider.value = active

        return HealthCheckResult(
            primaryAvailable = primaryOk,
            fallbackAvailable = fallbackOk,
            activeProvider = active
        )
    }

    /**
     * Generate conversation summary for agent handover.
     */
    override suspend fun generateSummary(context: ConversationContext): String {
        return try {
            val requestBody = SummaryRequest(
                messages = context.messages,
                provider = _currentProvider.value.name.lowercase()
            )

            val response = httpClient.post("${config.proxyBaseUrl}/api/v1/summarize") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(requestBody))
            }

            if (response.status.isSuccess()) {
                response.bodyAsText()
            } else {
                generateLocalSummary(context)
            }
        } catch (e: Exception) {
            generateLocalSummary(context)
        }
    }

    /**
     * Analyze customer sentiment.
     */
    override suspend fun analyzeSentiment(context: ConversationContext): SentimentResult {
        return try {
            val requestBody = SentimentRequest(messages = context.messages)

            val response = httpClient.post("${config.proxyBaseUrl}/api/v1/sentiment") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(requestBody))
            }

            if (response.status.isSuccess()) {
                json.decodeFromString<SentimentResult>(response.bodyAsText())
            } else {
                analyzeLocalSentiment(context)
            }
        } catch (e: Exception) {
            analyzeLocalSentiment(context)
        }
    }

    // --- Private helper methods ---

    private suspend fun checkProviderHealth(provider: AIProvider): Boolean {
        return try {
            val response = httpClient.get("${config.proxyBaseUrl}/api/v1/health") {
                header("X-Provider", provider.name.lowercase())
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clean AI response by removing internal markers.
     */
    private fun cleanResponse(text: String): String {
        return text
            .replace(Regex("\\[ESCALATE:[^\\]]*\\]"), "")
            .replace(Regex("\\[QUICK_REPLIES:[^\\]]*\\]"), "")
            .trim()
    }

    /**
     * Generate a basic summary locally if API fails.
     */
    private fun generateLocalSummary(context: ConversationContext): String {
        val userMessages = context.messages.filter { it.role == ConversationMessage.ROLE_USER }
        val messageCount = userMessages.size

        return if (messageCount > 0) {
            val topics = userMessages.takeLast(3).joinToString("; ") {
                it.content.take(50) + if (it.content.length > 50) "..." else ""
            }
            "Customer had $messageCount messages. Recent topics: $topics"
        } else {
            "New conversation, no messages exchanged yet."
        }
    }

    /**
     * Basic local sentiment analysis based on keywords.
     */
    private fun analyzeLocalSentiment(context: ConversationContext): SentimentResult {
        val userText = context.messages
            .filter { it.role == ConversationMessage.ROLE_USER }
            .joinToString(" ") { it.content.lowercase() }

        val negativeIndicators = listOf(
            "frustrated", "angry", "upset", "terrible", "awful",
            "hate", "horrible", "worst", "unacceptable", "ridiculous"
        )
        val positiveIndicators = listOf(
            "thanks", "thank you", "great", "awesome", "perfect",
            "excellent", "wonderful", "appreciate", "helpful", "good"
        )

        val negativeCount = negativeIndicators.count { userText.contains(it) }
        val positiveCount = positiveIndicators.count { userText.contains(it) }

        val (sentiment, confidence) = when {
            negativeCount > 2 -> SentimentResult.FRUSTRATED to 0.8f
            negativeCount > positiveCount -> SentimentResult.NEGATIVE to 0.6f
            positiveCount > negativeCount -> SentimentResult.POSITIVE to 0.6f
            else -> SentimentResult.NEUTRAL to 0.5f
        }

        return SentimentResult(
            sentiment = sentiment,
            confidence = confidence,
            indicators = (negativeIndicators + positiveIndicators).filter { userText.contains(it) }
        )
    }

    companion object {
        /**
         * Get current time in milliseconds (platform-specific).
         */
        private fun currentTimeMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    }
}

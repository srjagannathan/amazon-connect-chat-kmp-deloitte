package com.amazon.connect.chat.ai

import kotlinx.serialization.Serializable

/**
 * AI providers supported by the system.
 * Claude is the primary provider, OpenAI is the fallback.
 */
enum class AIProvider {
    CLAUDE,
    OPENAI
}

/**
 * Configuration for AI agent behavior.
 */
@Serializable
data class AIAgentConfig(
    val primaryProvider: AIProvider = AIProvider.CLAUDE,
    val fallbackProvider: AIProvider = AIProvider.OPENAI,
    val proxyBaseUrl: String,
    val streamingEnabled: Boolean = true,
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val escalationConfig: EscalationConfig = EscalationConfig()
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = """You are a helpful customer service assistant. Be friendly, concise, and helpful.
If you cannot help with something, politely offer to connect the customer with a human agent."""
    }
}

/**
 * Escalation behavior configuration.
 * User confirmation is always required before escalating.
 */
@Serializable
data class EscalationConfig(
    val confirmWithUser: Boolean = true,
    val keywords: List<String> = listOf(
        "human", "agent", "representative", "manager",
        "supervisor", "real person", "speak to someone",
        "talk to a person", "live agent", "customer service"
    ),
    val confidenceThreshold: Float = 0.6f,
    val maxRetryAttempts: Int = 3
)

/**
 * Streaming chunk from AI response.
 * Used for real-time display of AI responses.
 */
@Serializable
data class AIStreamChunk(
    val delta: String? = null,
    val done: Boolean = false,
    val shouldEscalate: Boolean = false,
    val escalationReason: String? = null,
    val suggestedReplies: List<String>? = null,
    val error: String? = null,
    val provider: String? = null
)

/**
 * Complete AI response assembled from stream or non-streaming call.
 */
@Serializable
data class AIAgentResponse(
    val responseText: String,
    val shouldEscalate: Boolean,
    val escalationReason: String?,
    val suggestedReplies: List<String>,
    val confidence: Float,
    val provider: AIProvider,
    val usage: TokenUsage? = null
)

/**
 * Token usage statistics for cost tracking.
 */
@Serializable
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * Conversation context sent to AI for processing.
 */
@Serializable
data class ConversationContext(
    val messages: List<ConversationMessage>,
    val sessionId: String,
    val customerId: String? = null,
    val customerName: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Individual message in conversation history.
 */
@Serializable
data class ConversationMessage(
    val role: String,
    val content: String,
    val timestamp: Long
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
    }
}

/**
 * Health check result for AI providers.
 */
data class HealthCheckResult(
    val primaryAvailable: Boolean,
    val fallbackAvailable: Boolean,
    val activeProvider: AIProvider
)

/**
 * Sentiment analysis result.
 */
@Serializable
data class SentimentResult(
    val sentiment: String,
    val confidence: Float,
    val indicators: List<String> = emptyList()
) {
    companion object {
        const val POSITIVE = "positive"
        const val NEUTRAL = "neutral"
        const val NEGATIVE = "negative"
        const val FRUSTRATED = "frustrated"
    }
}

/**
 * Request body for AI chat endpoint.
 */
@Serializable
data class AIChatRequest(
    val messages: List<ConversationMessage>,
    val provider: String = "claude",
    val stream: Boolean = true,
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val systemPrompt: String? = null,
    val sessionId: String? = null
)

/**
 * Request body for summary generation.
 */
@Serializable
data class SummaryRequest(
    val messages: List<ConversationMessage>,
    val provider: String = "claude"
)

/**
 * Request body for sentiment analysis.
 */
@Serializable
data class SentimentRequest(
    val messages: List<ConversationMessage>
)

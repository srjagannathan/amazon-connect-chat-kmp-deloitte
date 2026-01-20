package com.amazon.connect.chat.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.amazon.connect.chat.ai.AIAgentConfig
import com.amazon.connect.chat.ai.AIAgentRepository
import com.amazon.connect.chat.ai.AIAgentRepositoryImpl
import com.amazon.connect.chat.ai.SentimentResult
import com.amazon.connect.chat.connect.*
import com.amazon.connect.chat.data.Message
import com.amazon.connect.chat.data.ParticipantRole
import com.amazon.connect.chat.data.User
import com.amazon.connect.chat.store.*
import com.amazon.connect.chat.ui.ChatAppWithScaffold
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {

    // Configuration - replace with your deployed backend URL
    companion object {
        // AI Virtual Agent backend URL (deployed Lambda Function URL)
        private const val AI_PROXY_URL = "https://your-lambda-url.lambda-url.us-east-1.on.aws"

        // Amazon Connect auth API URL
        private const val CONNECT_AUTH_API_URL = "https://8c78if046b.execute-api.us-east-1.amazonaws.com/Prod"

        // System prompt for the AI Virtual Agent
        private const val SYSTEM_PROMPT = """You are a helpful and friendly customer service virtual assistant.
Your goal is to assist customers with their questions and issues.
Be concise, empathetic, and professional.
If you cannot help with something or the customer requests a human agent, recommend escalation."""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val scope = rememberCoroutineScope()
            val store = remember { scope.createStore() }

            // Initialize Connect repository
            val connectRepository = remember {
                ConnectChatRepositoryImpl(
                    config = ConnectChatConfig(
                        region = "us-east-1",
                        enableLogging = true
                    )
                )
            }

            // Initialize AI Agent repository
            val aiRepository = remember {
                createAIRepository()
            }

            // Initialize current user
            LaunchedEffect(Unit) {
                val currentUser = User(
                    name = "Customer",
                    role = ParticipantRole.CUSTOMER
                )
                store.send(Action.SetCurrentUser(currentUser))

                // Add welcome message from VA
                val welcomeMessage = Message(
                    user = store.state.virtualAgentUser,
                    text = "Hello! I'm your virtual assistant. How can I help you today?"
                )
                store.send(Action.ReceiveMessage(welcomeMessage))
            }

            ChatAppWithScaffold(
                store = store,
                connectRepository = connectRepository,
                aiRepository = aiRepository,
                onRequestHandover = {
                    scope.launch {
                        initiateHandover(store, connectRepository, aiRepository)
                    }
                }
            )
        }
    }

    /**
     * Create AI Agent repository with configuration
     */
    private fun createAIRepository(): AIAgentRepository {
        val httpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        return AIAgentRepositoryImpl(
            config = AIAgentConfig(
                proxyBaseUrl = AI_PROXY_URL,
                systemPrompt = SYSTEM_PROMPT,
                streamingEnabled = true,
                maxTokens = 1024,
                temperature = 0.7f
            ),
            httpClient = httpClient
        )
    }

    /**
     * Initiate handover from AI Virtual Agent to human agent.
     * Includes AI-generated summary and sentiment analysis.
     */
    private suspend fun initiateHandover(
        store: Store,
        repository: ConnectChatRepository,
        aiRepository: AIAgentRepository
    ) {
        store.send(Action.InitiateHandover)

        try {
            // Build conversation context for AI analysis
            val conversationContext = com.amazon.connect.chat.ai.ConversationContext(
                messages = store.state.messages.map { msg ->
                    com.amazon.connect.chat.ai.ConversationMessage(
                        role = if (msg.user.role == ParticipantRole.CUSTOMER) "user" else "assistant",
                        content = msg.text,
                        timestamp = msg.timeMs
                    )
                },
                sessionId = store.state.chatSession?.contactId ?: "local-session",
                customerName = store.state.currentUser?.name
            )

            // Generate AI summary for agent context
            val summary = try {
                aiRepository.generateSummary(conversationContext)
            } catch (e: Exception) {
                "Customer conversation summary unavailable"
            }

            // Analyze sentiment
            val sentiment = try {
                aiRepository.analyzeSentiment(conversationContext)
            } catch (e: Exception) {
                SentimentResult("neutral", 0.5f, emptyList())
            }

            // Build enhanced handover context
            val context = HandoverContext(
                customerId = "customer-123", // Would come from auth
                customerName = store.state.currentUser?.name ?: "Customer",
                intent = store.state.escalationReason ?: "General Inquiry",
                summary = summary,
                transcript = store.state.messages.map { msg ->
                    TranscriptEntry(
                        role = if (msg.user.role == ParticipantRole.CUSTOMER) "customer" else "virtual_agent",
                        content = msg.text,
                        timestamp = msg.timeMs
                    )
                },
                metadata = mapOf(
                    "aiProvider" to store.state.currentAIProvider,
                    "customerSentiment" to sentiment.sentiment,
                    "sentimentConfidence" to sentiment.confidence.toString(),
                    "escalationReason" to (store.state.escalationReason ?: "User requested")
                )
            )

            // Start handover
            repository.startHandover(
                authApiUrl = CONNECT_AUTH_API_URL,
                context = context
            )

        } catch (e: Exception) {
            store.send(Action.SetError("Failed to connect to agent: ${e.message}"))
            store.send(Action.SetConnectionState(ConnectionState.ERROR))
        }
    }
}

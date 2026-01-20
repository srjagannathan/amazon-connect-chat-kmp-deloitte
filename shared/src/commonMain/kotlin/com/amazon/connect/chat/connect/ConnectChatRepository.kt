package com.amazon.connect.chat.connect

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for Amazon Connect Chat operations.
 *
 * This is the main integration point for connecting to Amazon Connect Chat.
 * Implementations handle:
 * - HTTP communication with Connect Participant APIs
 * - WebSocket connection management
 * - Event parsing and distribution
 */
interface ConnectChatRepository {
    /**
     * Current connection state
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Flow of events from Amazon Connect
     */
    val events: Flow<ConnectEvent>

    /**
     * Current chat session information
     */
    val chatSession: StateFlow<ChatSession?>

    /**
     * Start handover to human agent.
     *
     * Flow:
     * 1. Call your auth API with handover context
     * 2. Auth API calls StartChatContact, returns ParticipantToken
     * 3. Call CreateParticipantConnection with ParticipantToken
     * 4. Connect to WebSocket
     * 5. Inject transcript as first message
     *
     * @param authApiUrl Your backend auth API URL
     * @param context The virtual agent conversation context
     * @return ChatSession on success
     * @throws ConnectChatException on failure
     */
    suspend fun startHandover(
        authApiUrl: String,
        context: HandoverContext
    ): ChatSession

    /**
     * Connect directly with a participant token (when auth is handled externally)
     *
     * @param participantToken Token from StartChatContact API
     * @param region AWS region (default: us-east-1)
     */
    suspend fun connectWithToken(
        participantToken: String,
        region: String = "us-east-1"
    ): ChatSession

    /**
     * Send a text message to the chat
     *
     * @param content Message content
     * @param contentType MIME type (default: text/plain)
     */
    suspend fun sendMessage(
        content: String,
        contentType: String = "text/plain"
    ): SendMessageResponse

    /**
     * Send typing indicator to show customer is typing
     */
    suspend fun sendTypingIndicator()

    /**
     * Get chat transcript (useful for reconnection)
     *
     * @param maxResults Maximum messages to retrieve
     * @return List of transcript items
     */
    suspend fun getTranscript(maxResults: Int = 100): List<TranscriptItem>

    /**
     * Disconnect from the chat session
     */
    suspend fun disconnect()

    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean
}

/**
 * Configuration for the Connect Chat client
 */
data class ConnectChatConfig(
    val region: String = "us-east-1",
    val heartbeatIntervalMs: Long = 30_000,
    val connectionTimeoutMs: Long = 10_000,
    val enableLogging: Boolean = false
)

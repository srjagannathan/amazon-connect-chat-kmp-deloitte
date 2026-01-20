package com.amazon.connect.chat.connect

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Handover context from virtual agent conversation
 */
@Serializable
data class HandoverContext(
    val customerId: String,
    val customerName: String,
    val intent: String,
    val summary: String,
    val transcript: List<TranscriptEntry>,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class TranscriptEntry(
    val role: String,  // "customer" or "virtual_agent"
    val content: String,
    val timestamp: Long = 0
)

/**
 * Chat session information from Amazon Connect
 */
@Serializable
data class ChatSession(
    val contactId: String,
    val participantToken: String,
    val connectionToken: String? = null,
    val websocketUrl: String? = null
)

/**
 * Connection state for the Connect chat
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    WAITING_FOR_AGENT,
    AGENT_CONNECTED,
    ERROR
}

// --- Request/Response DTOs for Connect APIs ---

@Serializable
data class StartChatRequest(
    @SerialName("ParticipantDetails") val participantDetails: ParticipantDetails,
    @SerialName("Attributes") val attributes: Map<String, String> = emptyMap()
)

@Serializable
data class ParticipantDetails(
    @SerialName("DisplayName") val displayName: String
)

@Serializable
data class StartChatResponse(
    @SerialName("data") val data: StartChatData
)

@Serializable
data class StartChatData(
    @SerialName("startChatResult") val startChatResult: StartChatResult
)

@Serializable
data class StartChatResult(
    @SerialName("ContactId") val contactId: String,
    @SerialName("ParticipantId") val participantId: String,
    @SerialName("ParticipantToken") val participantToken: String
)

@Serializable
data class CreateConnectionRequest(
    @SerialName("Type") val type: List<String>
)

@Serializable
data class CreateConnectionResponse(
    @SerialName("ConnectionCredentials") val connectionCredentials: ConnectionCredentials? = null,
    @SerialName("Websocket") val websocket: WebsocketInfo? = null
)

@Serializable
data class ConnectionCredentials(
    @SerialName("ConnectionToken") val connectionToken: String,
    @SerialName("Expiry") val expiry: String? = null
)

@Serializable
data class WebsocketInfo(
    @SerialName("Url") val url: String,
    @SerialName("ConnectionExpiry") val connectionExpiry: String? = null
)

@Serializable
data class SendMessageRequest(
    @SerialName("ConnectionToken") val connectionToken: String,
    @SerialName("Content") val content: String,
    @SerialName("ContentType") val contentType: String = "text/plain"
)

@Serializable
data class SendMessageResponse(
    @SerialName("Id") val id: String,
    @SerialName("AbsoluteTime") val absoluteTime: String
)

@Serializable
data class SendEventRequest(
    @SerialName("ContentType") val contentType: String
)

@Serializable
data class GetTranscriptRequest(
    @SerialName("MaxResults") val maxResults: Int = 100,
    @SerialName("SortOrder") val sortOrder: String = "ASCENDING",
    @SerialName("NextToken") val nextToken: String? = null
)

@Serializable
data class GetTranscriptResponse(
    @SerialName("Transcript") val transcript: List<TranscriptItem>,
    @SerialName("NextToken") val nextToken: String? = null
)

@Serializable
data class TranscriptItem(
    @SerialName("Id") val id: String,
    @SerialName("Content") val content: String? = null,
    @SerialName("ContentType") val contentType: String,
    @SerialName("ParticipantId") val participantId: String? = null,
    @SerialName("ParticipantRole") val participantRole: String? = null,
    @SerialName("DisplayName") val displayName: String? = null,
    @SerialName("AbsoluteTime") val absoluteTime: String
)

// --- WebSocket Event Models ---

// Wrapper message from WebSocket protocol (lowercase fields)
// Note: content can be either a JSON string (for aws/chat) or an object (for aws/subscribe)
@Serializable
data class WebSocketWrapper(
    val topic: String? = null,
    val contentType: String? = null
    // content field is handled manually due to polymorphic type
)

// Inner message from Amazon Connect (uppercase fields)
@Serializable
data class WebSocketMessage(
    @SerialName("Type") val type: String? = null,
    @SerialName("ContentType") val contentType: String? = null,
    @SerialName("Id") val id: String? = null,
    @SerialName("Content") val content: String? = null,
    @SerialName("ParticipantId") val participantId: String? = null,
    @SerialName("ParticipantRole") val participantRole: String? = null,
    @SerialName("DisplayName") val displayName: String? = null,
    @SerialName("AbsoluteTime") val absoluteTime: String? = null
)

/**
 * Sealed class representing events from Amazon Connect WebSocket
 */
sealed class ConnectEvent {
    data class MessageReceived(
        val id: String,
        val content: String,
        val contentType: String,
        val participantRole: String,
        val displayName: String,
        val timestamp: String
    ) : ConnectEvent()

    data class ParticipantJoined(
        val participantId: String,
        val participantRole: String,
        val displayName: String
    ) : ConnectEvent()

    data class ParticipantLeft(
        val participantId: String,
        val participantRole: String,
        val displayName: String
    ) : ConnectEvent()

    data class TypingIndicator(
        val participantRole: String,
        val displayName: String
    ) : ConnectEvent()

    object ChatEnded : ConnectEvent()

    data class Error(val message: String, val cause: Throwable? = null) : ConnectEvent()

    object Connected : ConnectEvent()
    object Disconnected : ConnectEvent()
}

/**
 * Exception for Connect chat operations
 */
class ConnectChatException(message: String, cause: Throwable? = null) : Exception(message, cause)

package com.amazon.connect.chat.connect

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Implementation of ConnectChatRepository using Ktor client.
 *
 * This implementation handles:
 * - HTTP calls to Connect Participant APIs
 * - WebSocket connection and message handling
 * - Event parsing and state management
 */
class ConnectChatRepositoryImpl(
    private val config: ConnectChatConfig = ConnectChatConfig(),
    httpClient: HttpClient? = null
) : ConnectChatRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client: HttpClient = httpClient ?: HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(WebSockets)
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<ConnectEvent>(replay = 0, extraBufferCapacity = 64)
    override val events: Flow<ConnectEvent> = _events.asSharedFlow()

    private val _chatSession = MutableStateFlow<ChatSession?>(null)
    override val chatSession: StateFlow<ChatSession?> = _chatSession.asStateFlow()

    private var webSocketSession: DefaultClientWebSocketSession? = null
    private var heartbeatJob: Job? = null
    private var messageListenerJob: Job? = null
    private var currentRegion: String = config.region

    override suspend fun startHandover(
        authApiUrl: String,
        context: HandoverContext
    ): ChatSession {
        try {
            _connectionState.value = ConnectionState.CONNECTING
            log("Starting handover for customer: ${context.customerName}")

            // Step 1: Call auth API to get participant token
            val authResponse = client.post(authApiUrl) {
                contentType(ContentType.Application.Json)
                setBody(StartChatRequest(
                    participantDetails = ParticipantDetails(displayName = context.customerName),
                    attributes = mapOf(
                        "customerId" to context.customerId,
                        "intent" to context.intent,
                        "summary" to context.summary,
                        "transcriptLength" to context.transcript.size.toString()
                    ) + context.metadata
                ))
            }

            if (!authResponse.status.isSuccess()) {
                throw ConnectChatException("Auth API failed: ${authResponse.status}")
            }

            val startResponse: StartChatResponse = authResponse.body()
            val chatResult = startResponse.data.startChatResult
            log("Got participant token for contact: ${chatResult.contactId}")

            // Step 2: Connect with the token
            val session = connectWithToken(chatResult.participantToken, currentRegion)

            // Step 3: Inject transcript
            if (context.transcript.isNotEmpty()) {
                injectTranscript(context)
            }

            return session

        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            _events.emit(ConnectEvent.Error("Handover failed: ${e.message}", e))
            throw ConnectChatException("Handover failed", e)
        }
    }

    override suspend fun connectWithToken(
        participantToken: String,
        region: String
    ): ChatSession {
        currentRegion = region
        val endpoint = "https://participant.connect.$region.amazonaws.com"

        try {
            _connectionState.value = ConnectionState.CONNECTING
            log("Creating participant connection...")

            // Create participant connection
            val connectionResponse = client.post("$endpoint/participant/connection") {
                contentType(ContentType.Application.Json)
                header("X-Amz-Bearer", participantToken)
                setBody(CreateConnectionRequest(
                    type = listOf("WEBSOCKET", "CONNECTION_CREDENTIALS")
                ))
            }

            if (!connectionResponse.status.isSuccess()) {
                throw ConnectChatException("CreateParticipantConnection failed: ${connectionResponse.status}")
            }

            val connResult: CreateConnectionResponse = connectionResponse.body()
            log("Got WebSocket URL and connection token")

            val connectionToken = connResult.connectionCredentials?.connectionToken
                ?: throw ConnectChatException("No connection token in response")
            val websocketUrl = connResult.websocket?.url
                ?: throw ConnectChatException("No WebSocket URL in response")

            val session = ChatSession(
                contactId = "",
                participantToken = participantToken,
                connectionToken = connectionToken,
                websocketUrl = websocketUrl
            )
            _chatSession.value = session

            // Connect to WebSocket
            connectWebSocket(websocketUrl)

            _connectionState.value = ConnectionState.WAITING_FOR_AGENT
            _events.emit(ConnectEvent.Connected)

            return session

        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            _events.emit(ConnectEvent.Error("Connection failed: ${e.message}", e))
            throw ConnectChatException("Connection failed", e)
        }
    }

    private suspend fun connectWebSocket(url: String) {
        log("Connecting to WebSocket: ${url.take(80)}...")

        try {
            webSocketSession = client.webSocketSession(url)
            val session = webSocketSession
            log("WebSocket connected - session isActive: ${session?.isActive}")

            // Subscribe to aws/chat topic (required by Amazon Connect protocol)
            val subscribeMessage = """{"topic":"aws/subscribe","content":{"topics":["aws/chat"]}}"""
            log("Sending subscribe: $subscribeMessage")
            session?.send(Frame.Text(subscribeMessage))

            // Start listening for messages
            startMessageListener()

            // Start heartbeat
            startHeartbeat()

        } catch (e: Exception) {
            log("WebSocket connection failed: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(config.heartbeatIntervalMs)
                try {
                    // Use Amazon Connect heartbeat protocol instead of WebSocket ping
                    val heartbeatMessage = """{"topic":"aws/heartbeat","content":{"eventType":"heartbeat"}}"""
                    webSocketSession?.send(Frame.Text(heartbeatMessage))
                } catch (e: Exception) {
                    log("Heartbeat failed: ${e.message}")
                }
            }
        }
    }

    private fun startMessageListener() {
        messageListenerJob?.cancel()
        messageListenerJob = CoroutineScope(Dispatchers.Default).launch {
            log("Message listener started")
            try {
                val session = webSocketSession
                if (session == null) {
                    log("No WebSocket session available")
                    return@launch
                }

                for (frame in session.incoming) {
                    log("Frame received: ${frame.frameType}")
                    when (frame) {
                        is Frame.Text -> handleWebSocketMessage(frame.readText())
                        is Frame.Close -> {
                            log("WebSocket Close frame received: ${frame.readReason()}")
                            _connectionState.value = ConnectionState.DISCONNECTED
                            _events.emit(ConnectEvent.Disconnected)
                        }
                        is Frame.Binary -> log("Binary frame: ${frame.data.size} bytes")
                        is Frame.Ping -> log("Ping frame received")
                        is Frame.Pong -> log("Pong frame received")
                        else -> {}
                    }
                }

                // Channel closed - check why
                val closeReason = session.closeReason.await()
                log("WebSocket channel closed. Reason: $closeReason, isActive: ${session.isActive}")

                if (_connectionState.value != ConnectionState.DISCONNECTED) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _events.emit(ConnectEvent.Disconnected)
                }

            } catch (e: CancellationException) {
                log("Message listener cancelled")
            } catch (e: Exception) {
                log("Message listener error: ${e.message}")
                _events.emit(ConnectEvent.Error("WebSocket error: ${e.message}", e))
            }
        }
    }

    private suspend fun handleWebSocketMessage(text: String) {
        log("RAW: $text")

        try {
            // Parse as generic JSON to handle polymorphic content field
            val jsonElement = json.parseToJsonElement(text)
            val jsonObject = jsonElement.jsonObject

            val topic = jsonObject["topic"]?.jsonPrimitive?.contentOrNull

            when (topic) {
                "aws/chat" -> {
                    // content is a JSON-encoded string containing the actual message
                    val contentString = jsonObject["content"]?.jsonPrimitive?.contentOrNull
                    if (contentString != null) {
                        val innerMessage = json.decodeFromString<WebSocketMessage>(contentString)
                        processConnectMessage(innerMessage)
                    }
                }
                "aws/subscribe" -> {
                    log("Subscribe response received")
                }
                "aws/heartbeat" -> {
                    // Heartbeat response - connection is alive
                }
                else -> {
                    log("Unknown topic: $topic")
                }
            }
        } catch (e: Exception) {
            log("Error parsing message: ${e.message}")
        }
    }

    private suspend fun processConnectMessage(message: WebSocketMessage) {
        val contentType = message.contentType ?: ""
        val type = message.type ?: ""

        log("Processing: type=$type contentType=$contentType role=${message.participantRole} content=${message.content?.take(50)}")

        when {
            contentType.contains("participant.joined") -> {
                val event = ConnectEvent.ParticipantJoined(
                    participantId = message.participantId ?: "",
                    participantRole = message.participantRole ?: "",
                    displayName = message.displayName ?: ""
                )
                _events.emit(event)

                if (message.participantRole == "AGENT") {
                    _connectionState.value = ConnectionState.AGENT_CONNECTED
                }
            }

            contentType.contains("participant.left") -> {
                _events.emit(ConnectEvent.ParticipantLeft(
                    participantId = message.participantId ?: "",
                    participantRole = message.participantRole ?: "",
                    displayName = message.displayName ?: ""
                ))
            }

            contentType.contains("typing") -> {
                _events.emit(ConnectEvent.TypingIndicator(
                    participantRole = message.participantRole ?: "",
                    displayName = message.displayName ?: ""
                ))
            }

            contentType.contains("chat.ended") -> {
                _events.emit(ConnectEvent.ChatEnded)
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            type == "MESSAGE" || contentType == "text/plain" || contentType == "text/markdown" -> {
                _events.emit(ConnectEvent.MessageReceived(
                    id = message.id ?: "",
                    content = message.content ?: "",
                    contentType = contentType.ifEmpty { "text/plain" },
                    participantRole = message.participantRole ?: "",
                    displayName = message.displayName ?: "",
                    timestamp = message.absoluteTime ?: ""
                ))
            }

            else -> {
                log("UNHANDLED type=$type contentType=$contentType")
            }
        }
    }

    private suspend fun injectTranscript(context: HandoverContext) {
        val transcriptText = buildString {
            appendLine("--- Prior conversation with Virtual Agent ---")
            appendLine("Customer: ${context.customerName}")
            appendLine("Intent: ${context.intent}")
            if (context.summary.isNotBlank()) {
                appendLine("Summary: ${context.summary}")
            }
            appendLine()

            context.transcript.forEach { entry ->
                val role = if (entry.role == "customer") "Customer" else "Virtual Agent"
                appendLine("[$role]: ${entry.content}")
            }

            appendLine()
            appendLine("--- Live agent conversation begins ---")
        }

        sendMessage(transcriptText)
        log("Transcript injected")
    }

    override suspend fun sendMessage(content: String, contentType: String): SendMessageResponse {
        val session = _chatSession.value ?: throw ConnectChatException("Not connected")
        val connectionToken = session.connectionToken ?: throw ConnectChatException("No connection token")
        val endpoint = "https://participant.connect.$currentRegion.amazonaws.com"

        log("Sending message: ${content.take(50)}...")

        val response = client.post("$endpoint/participant/message") {
            contentType(ContentType.Application.Json)
            header("X-Amz-Bearer", connectionToken)
            setBody(SendMessageRequest(
                connectionToken = connectionToken,
                content = content,
                contentType = contentType
            ))
        }

        if (!response.status.isSuccess()) {
            log("SendMessage failed: ${response.status}")
            throw ConnectChatException("SendMessage failed: ${response.status}")
        }

        log("Message sent successfully")
        return response.body()
    }

    override suspend fun sendTypingIndicator() {
        val session = _chatSession.value ?: throw ConnectChatException("Not connected")
        val connectionToken = session.connectionToken ?: throw ConnectChatException("No connection token")
        val endpoint = "https://participant.connect.$currentRegion.amazonaws.com"

        client.post("$endpoint/participant/event") {
            contentType(ContentType.Application.Json)
            header("X-Amz-Bearer", connectionToken)
            setBody(SendEventRequest(
                contentType = "application/vnd.amazonaws.connect.event.typing"
            ))
        }
    }

    override suspend fun getTranscript(maxResults: Int): List<TranscriptItem> {
        val session = _chatSession.value ?: throw ConnectChatException("Not connected")
        val connectionToken = session.connectionToken ?: throw ConnectChatException("No connection token")
        val endpoint = "https://participant.connect.$currentRegion.amazonaws.com"

        val response = client.post("$endpoint/participant/transcript") {
            contentType(ContentType.Application.Json)
            header("X-Amz-Bearer", connectionToken)
            setBody(GetTranscriptRequest(maxResults = maxResults))
        }

        if (!response.status.isSuccess()) {
            throw ConnectChatException("GetTranscript failed: ${response.status}")
        }

        val result: GetTranscriptResponse = response.body()
        return result.transcript
    }

    override suspend fun disconnect() {
        log("Disconnecting...")

        heartbeatJob?.cancel()
        heartbeatJob = null

        messageListenerJob?.cancel()
        messageListenerJob = null

        val session = _chatSession.value
        if (session?.connectionToken != null) {
            try {
                val endpoint = "https://participant.connect.$currentRegion.amazonaws.com"
                client.post("$endpoint/participant/disconnect") {
                    contentType(ContentType.Application.Json)
                    header("X-Amz-Bearer", session.connectionToken)
                }
            } catch (e: Exception) {
                log("Disconnect API call failed: ${e.message}")
            }
        }

        try {
            webSocketSession?.close(CloseReason(CloseReason.Codes.NORMAL, "User disconnected"))
        } catch (e: Exception) {
            log("WebSocket close failed: ${e.message}")
        }

        webSocketSession = null
        _chatSession.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _events.emit(ConnectEvent.Disconnected)
    }

    override fun isConnected(): Boolean {
        return _connectionState.value in listOf(
            ConnectionState.CONNECTED,
            ConnectionState.WAITING_FOR_AGENT,
            ConnectionState.AGENT_CONNECTED
        )
    }

    private fun log(message: String) {
        if (config.enableLogging) {
            println("[ConnectChat] $message")
        }
    }
}

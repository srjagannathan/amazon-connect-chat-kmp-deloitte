package com.amazon.connect.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.amazon.connect.chat.ai.AIAgentRepository
import com.amazon.connect.chat.ai.ConversationContext
import com.amazon.connect.chat.ai.ConversationMessage
import com.amazon.connect.chat.connect.ConnectionState
import com.amazon.connect.chat.connect.ConnectChatRepository
import com.amazon.connect.chat.connect.ConnectEvent
import com.amazon.connect.chat.data.*
import com.amazon.connect.chat.store.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main Chat Application composable with scaffold.
 * Includes AI Virtual Agent with streaming responses and escalation to human agents.
 */
@Composable
fun ChatAppWithScaffold(
    store: Store,
    connectRepository: ConnectChatRepository,
    aiRepository: AIAgentRepository? = null,
    onRequestHandover: () -> Unit = {},
    showSendMessage: Boolean = true
) {
    ChatTheme {
        Scaffold(
            topBar = {
                ChatTopBar(
                    state = store.stateFlow.collectAsState().value,
                    onRequestHandover = onRequestHandover
                )
            }
        ) { paddingValues ->
            ChatApp(
                store = store,
                connectRepository = connectRepository,
                aiRepository = aiRepository,
                modifier = Modifier.padding(paddingValues),
                showSendMessage = showSendMessage,
                onRequestHandover = onRequestHandover
            )
        }
    }
}

/**
 * Top app bar showing chat status and handover button
 */
@Composable
private fun ChatTopBar(
    state: ChatState,
    onRequestHandover: () -> Unit
) {
    val gradientColors = ChatColors.TOP_GRADIENT
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(gradientColors))
    ) {
        // Status bar spacer
        Spacer(modifier = Modifier.height(statusBarHeight))

        TopAppBar(
            backgroundColor = Color.Transparent,
            elevation = 0.dp,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status indicator
                    StatusDot(state.connectionState)

                Column {
                    Text(
                        text = when (state.chatMode) {
                            ChatMode.VIRTUAL_AGENT -> "Virtual Assistant"
                            ChatMode.CONNECTING_TO_AGENT -> "Connecting..."
                            ChatMode.HUMAN_AGENT -> state.agentUser?.name ?: "Live Agent"
                            ChatMode.ENDED -> "Chat Ended"
                        },
                        style = MaterialTheme.typography.h6,
                        color = Color.White
                    )

                    if (state.isAgentTyping) {
                        Text(
                            text = "typing...",
                            style = MaterialTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        },
        actions = {
            // Show "Talk to Agent" button when in VA mode
            if (state.chatMode == ChatMode.VIRTUAL_AGENT) {
                IconButton(onClick = onRequestHandover) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Talk to Agent",
                        tint = Color.White
                    )
                }
            }
        }
        )
    }
}

/**
 * Status indicator dot
 */
@Composable
private fun StatusDot(connectionState: ConnectionState) {
    val color = when (connectionState) {
        ConnectionState.AGENT_CONNECTED -> ChatColors.ONLINE
        ConnectionState.CONNECTED,
        ConnectionState.WAITING_FOR_AGENT -> ChatColors.CONNECTING
        ConnectionState.CONNECTING -> ChatColors.CONNECTING
        else -> ChatColors.OFFLINE
    }

    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, shape = androidx.compose.foundation.shape.CircleShape)
    )
}

/**
 * Core chat UI component with AI Virtual Agent support.
 * Handles streaming AI responses and escalation to human agents.
 */
@Composable
fun ChatApp(
    store: Store,
    connectRepository: ConnectChatRepository,
    aiRepository: AIAgentRepository? = null,
    modifier: Modifier = Modifier,
    showSendMessage: Boolean = true,
    onRequestHandover: () -> Unit = {}
) {
    val state by store.stateFlow.collectAsState()
    val scope = rememberCoroutineScope()

    // Observe Connect events and update store
    LaunchedEffect(connectRepository) {
        connectRepository.events.collect { event ->
            handleConnectEvent(event, store, scope)
        }
    }

    // Observe connection state changes
    LaunchedEffect(connectRepository) {
        connectRepository.connectionState.collect { connState ->
            store.send(Action.SetConnectionState(connState))
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = ChatColors.SURFACE
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Messages area
            Messages(
                messages = state.messages,
                currentUser = state.currentUser,
                modifier = Modifier.weight(1f)
            )

            // Streaming AI response bubble
            if (state.isAIProcessing && state.aiStreamBuffer.isNotEmpty()) {
                StreamingMessageBubble(
                    text = state.aiStreamBuffer,
                    isComplete = false
                )
            }

            // Typing indicator (AI or human agent)
            if (state.isAIProcessing && state.aiStreamBuffer.isEmpty()) {
                AITypingIndicator()
            } else if (state.isAgentTyping) {
                TypingIndicator(
                    userName = state.agentUser?.name ?: "Agent"
                )
            }

            // Quick reply chips
            if (state.suggestedReplies.isNotEmpty() && !state.isAIProcessing) {
                QuickReplies(
                    replies = state.suggestedReplies,
                    onReplyClick = { reply ->
                        val user = state.currentUser ?: return@QuickReplies
                        val message = Message(user, reply)
                        store.send(Action.SendMessage(message))
                        store.send(Action.SetQuickReplies(emptyList()))

                        // Process with AI if in virtual agent mode
                        if (state.chatMode == ChatMode.VIRTUAL_AGENT && aiRepository != null) {
                            processWithAI(store, aiRepository, reply, state, scope)
                        }
                    }
                )
            }

            // Connection status banner
            ConnectionBanner(state)

            // Send message input
            if (showSendMessage && state.chatMode != ChatMode.ENDED) {
                SendMessage(
                    onSendMessage = { text ->
                        val user = state.currentUser ?: return@SendMessage
                        val message = Message(user, text)
                        store.send(Action.SendMessage(message))

                        // Process with AI if in virtual agent mode
                        if (state.chatMode == ChatMode.VIRTUAL_AGENT && aiRepository != null) {
                            processWithAI(store, aiRepository, text, state, scope)
                        } else if (state.chatMode == ChatMode.HUMAN_AGENT) {
                            // Send to Connect if connected to human agent
                            scope.launch {
                                try {
                                    connectRepository.sendMessage(text)
                                } catch (e: Exception) {
                                    store.send(Action.SetError("Failed to send message: ${e.message}"))
                                }
                            }
                        }
                    },
                    onTyping = {
                        if (state.chatMode == ChatMode.HUMAN_AGENT) {
                            scope.launch {
                                try {
                                    connectRepository.sendTypingIndicator()
                                } catch (e: Exception) {
                                    // Ignore typing indicator failures
                                }
                            }
                        }
                    },
                    enabled = state.chatMode != ChatMode.CONNECTING_TO_AGENT && !state.isAIProcessing
                )
            }
        }
    }

    // Escalation confirmation dialog
    if (state.showEscalationDialog) {
        EscalationConfirmationDialog(
            reason = state.escalationReason ?: "I think a human agent could better assist you with this request.",
            onConfirm = {
                store.send(Action.EscalationResponse(confirmed = true))
                onRequestHandover()
            },
            onDismiss = {
                store.send(Action.EscalationResponse(confirmed = false))
            }
        )
    }
}

/**
 * Process user message with AI (streaming response).
 */
private fun processWithAI(
    store: Store,
    aiRepository: AIAgentRepository,
    userMessage: String,
    state: ChatState,
    scope: CoroutineScope
) {
    scope.launch {
        store.send(Action.AIProcessingStarted)

        // Build conversation context from message history
        // Map roles properly for AI context
        val context = ConversationContext(
            messages = state.messages.mapNotNull { msg ->
                // Map ParticipantRole to AI conversation roles
                val role = when (msg.user.role) {
                    ParticipantRole.CUSTOMER -> "user"
                    ParticipantRole.VIRTUAL_AGENT -> "assistant"
                    ParticipantRole.HUMAN_AGENT -> "assistant"  // Human agent responses also as assistant
                    ParticipantRole.SYSTEM -> null  // Skip system messages in AI context
                }
                role?.let {
                    ConversationMessage(
                        role = it,
                        content = msg.text,
                        timestamp = msg.timeMs
                    )
                }
            },
            sessionId = state.chatSession?.contactId ?: "local-session",
            customerName = state.currentUser?.name
        )

        try {
            aiRepository.processMessageStream(userMessage, context)
                .collect { chunk ->
                    // Handle text delta
                    chunk.delta?.let {
                        store.send(Action.AIStreamChunk(it))
                    }

                    // Handle errors (can occur with or without done flag)
                    chunk.error?.let { error ->
                        store.send(Action.AIError(error))
                    }

                    // Handle stream completion (even if there was an error)
                    if (chunk.done) {
                        // Only send completion if there was actual content
                        if (chunk.error == null) {
                            store.send(Action.AIResponseComplete(
                                suggestedReplies = chunk.suggestedReplies ?: emptyList(),
                                shouldEscalate = chunk.shouldEscalate,
                                escalationReason = chunk.escalationReason
                            ))
                        }
                    }

                    // Track provider changes (fallback)
                    chunk.provider?.let {
                        store.send(Action.AIProviderChanged(it))
                    }
                }
        } catch (e: Exception) {
            store.send(Action.AIError("Failed to get AI response: ${e.message}"))
        }
    }
}

/**
 * Handle events from Amazon Connect
 */
private fun handleConnectEvent(
    event: ConnectEvent,
    store: Store,
    scope: CoroutineScope
) {
    when (event) {
        is ConnectEvent.MessageReceived -> {
            if (event.participantRole != "CUSTOMER") {
                val user = User(
                    name = event.displayName,
                    role = if (event.participantRole == "AGENT") ParticipantRole.HUMAN_AGENT else ParticipantRole.SYSTEM
                )
                val message = Message(user, event.content)
                store.send(Action.ReceiveMessage(message))
            }
        }

        is ConnectEvent.ParticipantJoined -> {
            if (event.participantRole == "AGENT") {
                val agentUser = User(
                    name = event.displayName,
                    role = ParticipantRole.HUMAN_AGENT
                )
                store.send(Action.SetAgentUser(agentUser))
                store.send(Action.HandoverComplete)

                // Add system message
                val systemMessage = Message(
                    user = User("System", ParticipantRole.SYSTEM),
                    text = "${event.displayName} has joined the chat"
                )
                store.send(Action.ReceiveMessage(systemMessage))
            }
        }

        is ConnectEvent.ParticipantLeft -> {
            if (event.participantRole == "AGENT") {
                val systemMessage = Message(
                    user = User("System", ParticipantRole.SYSTEM),
                    text = "${event.displayName} has left the chat"
                )
                store.send(Action.ReceiveMessage(systemMessage))
            }
        }

        is ConnectEvent.TypingIndicator -> {
            if (event.participantRole == "AGENT") {
                store.send(Action.SetAgentTyping(true))
                // Auto-clear typing after 3 seconds
                scope.launch {
                    delay(3000)
                    store.send(Action.SetAgentTyping(false))
                }
            }
        }

        is ConnectEvent.ChatEnded -> {
            store.send(Action.EndChat)
            val systemMessage = Message(
                user = User("System", ParticipantRole.SYSTEM),
                text = "Chat has ended"
            )
            store.send(Action.ReceiveMessage(systemMessage))
        }

        is ConnectEvent.Error -> {
            store.send(Action.SetError(event.message))
        }

        is ConnectEvent.Connected -> {
            store.send(Action.SetConnectionState(ConnectionState.CONNECTED))
        }

        is ConnectEvent.Disconnected -> {
            store.send(Action.SetConnectionState(ConnectionState.DISCONNECTED))
        }
    }
}

/**
 * Connection status banner
 */
@Composable
private fun ConnectionBanner(state: ChatState) {
    when (state.chatMode) {
        ChatMode.CONNECTING_TO_AGENT -> {
            Surface(
                color = ChatColors.CONNECTING.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ChatColors.CONNECTING
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Connecting to a live agent...",
                        style = MaterialTheme.typography.body2,
                        color = ChatColors.TEXT_SECONDARY
                    )
                }
            }
        }
        ChatMode.ENDED -> {
            Surface(
                color = ChatColors.OFFLINE.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "This chat has ended",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.body2,
                    color = ChatColors.TEXT_SECONDARY
                )
            }
        }
        else -> {}
    }

    // Error banner
    state.error?.let { error ->
        Surface(
            color = Color.Red.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                error,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.body2,
                color = Color.Red
            )
        }
    }
}

/**
 * Typing indicator component
 */
@Composable
private fun TypingIndicator(userName: String) {
    Surface(
        color = ChatColors.SURFACE,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated dots would go here
            Text(
                "$userName is typing...",
                style = MaterialTheme.typography.caption,
                color = ChatColors.TEXT_SECONDARY
            )
        }
    }
}

/**
 * Chat theme wrapper
 */
@Composable
fun ChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = MaterialTheme.colors.copy(
            primary = ChatColors.PRIMARY,
            primaryVariant = ChatColors.PRIMARY_DARK,
            surface = ChatColors.SURFACE,
            background = ChatColors.BACKGROUND,
            onPrimary = Color.White,
            onSurface = ChatColors.TEXT_PRIMARY
        ),
        content = content
    )
}

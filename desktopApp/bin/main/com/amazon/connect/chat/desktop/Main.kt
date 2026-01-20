package com.amazon.connect.chat.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.amazon.connect.chat.connect.*
import com.amazon.connect.chat.data.Message
import com.amazon.connect.chat.data.ParticipantRole
import com.amazon.connect.chat.data.User
import com.amazon.connect.chat.store.*
import com.amazon.connect.chat.ui.ChatAppWithScaffold
import kotlinx.coroutines.launch

fun main() = application {
    val windowState = rememberWindowState(width = 400.dp, height = 700.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Amazon Connect Chat",
        state = windowState
    ) {
        val scope = rememberCoroutineScope()
        val store = remember { scope.createStore() }
        val connectRepository = remember {
            ConnectChatRepositoryImpl(
                config = ConnectChatConfig(
                    region = "us-east-1",
                    enableLogging = true
                )
            )
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
            onRequestHandover = {
                scope.launch {
                    initiateHandover(store, connectRepository)
                }
            }
        )
    }
}

private suspend fun initiateHandover(
    store: Store,
    repository: ConnectChatRepository
) {
    store.send(Action.InitiateHandover)

    try {
        // Build handover context from conversation history
        val context = HandoverContext(
            customerId = "customer-123",
            customerName = store.state.currentUser?.name ?: "Customer",
            intent = "General Inquiry",
            summary = "Customer needs assistance with their account",
            transcript = store.state.messages.map { msg ->
                TranscriptEntry(
                    role = if (msg.user.role == ParticipantRole.CUSTOMER) "customer" else "virtual_agent",
                    content = msg.text,
                    timestamp = msg.timeMs
                )
            }
        )

        // Start handover
        repository.startHandover(
            authApiUrl = "https://8c78if046b.execute-api.us-east-1.amazonaws.com/Prod",
            context = context
        )

    } catch (e: Exception) {
        store.send(Action.SetError("Failed to connect to agent: ${e.message}"))
        store.send(Action.SetConnectionState(ConnectionState.ERROR))
    }
}

package com.amazon.connect.chat.store

import com.amazon.connect.chat.connect.ChatSession
import com.amazon.connect.chat.connect.ConnectionState
import com.amazon.connect.chat.data.ChatMode
import com.amazon.connect.chat.data.Message
import com.amazon.connect.chat.data.ParticipantRole
import com.amazon.connect.chat.data.User

/**
 * Complete chat state including AI agent features
 */
data class ChatState(
    // Core chat state
    val messages: List<Message> = emptyList(),
    val chatMode: ChatMode = ChatMode.VIRTUAL_AGENT,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val currentUser: User? = null,
    val agentUser: User? = null,
    val virtualAgentUser: User = User(
        name = "Virtual Assistant",
        role = ParticipantRole.VIRTUAL_AGENT
    ),
    val isAgentTyping: Boolean = false,
    val error: String? = null,
    val chatSession: ChatSession? = null,

    // AI Agent state
    /** AI is currently processing/streaming a response */
    val isAIProcessing: Boolean = false,
    /** Buffer for streaming AI response text */
    val aiStreamBuffer: String = "",
    /** Suggested quick replies from AI */
    val suggestedReplies: List<String> = emptyList(),
    /** Show escalation confirmation dialog */
    val showEscalationDialog: Boolean = false,
    /** Reason for escalation (shown in dialog) */
    val escalationReason: String? = null,
    /** Current AI provider being used (claude/openai) */
    val currentAIProvider: String = "claude"
)

/**
 * Actions that can modify chat state
 */
sealed interface Action {
    // Message actions
    data class SendMessage(val message: Message) : Action
    data class ReceiveMessage(val message: Message) : Action
    data class ClearMessages(val keepCount: Int = 0) : Action

    // User actions
    data class SetCurrentUser(val user: User) : Action
    data class SetAgentUser(val user: User) : Action

    // Connection state actions
    data class SetConnectionState(val state: ConnectionState) : Action
    data class SetChatMode(val mode: ChatMode) : Action
    data class SetChatSession(val session: ChatSession) : Action

    // Typing indicators
    data class SetAgentTyping(val isTyping: Boolean) : Action

    // Error handling
    data class SetError(val error: String?) : Action
    object ClearError : Action

    // Handover
    object InitiateHandover : Action
    object HandoverComplete : Action
    object EndChat : Action

    // === AI AGENT ACTIONS ===

    /** Start AI processing (shows typing indicator) */
    object AIProcessingStarted : Action

    /** Streaming chunk received from AI */
    data class AIStreamChunk(val chunk: String) : Action

    /** AI response complete with metadata */
    data class AIResponseComplete(
        val suggestedReplies: List<String>,
        val shouldEscalate: Boolean,
        val escalationReason: String?
    ) : Action

    /** AI processing error */
    data class AIError(val error: String) : Action

    /** Show escalation confirmation dialog */
    data class ShowEscalationDialog(val reason: String) : Action

    /** User responded to escalation prompt */
    data class EscalationResponse(val confirmed: Boolean) : Action

    /** Clear AI stream buffer */
    object ClearAIBuffer : Action

    /** Update suggested quick replies */
    data class SetQuickReplies(val replies: List<String>) : Action

    /** AI provider changed (e.g., fallback triggered) */
    data class AIProviderChanged(val provider: String) : Action
}

/**
 * Pure reducer function that computes new state from current state and action
 */
fun chatReducer(state: ChatState, action: Action): ChatState = when (action) {
    is Action.SendMessage -> {
        state.copy(
            messages = (state.messages + action.message).takeLast(200)
        )
    }

    is Action.ReceiveMessage -> {
        state.copy(
            messages = (state.messages + action.message).takeLast(200),
            isAgentTyping = false
        )
    }

    is Action.ClearMessages -> {
        if (action.keepCount > 0) {
            state.copy(messages = state.messages.takeLast(action.keepCount))
        } else {
            state.copy(messages = emptyList())
        }
    }

    is Action.SetCurrentUser -> {
        state.copy(currentUser = action.user)
    }

    is Action.SetAgentUser -> {
        state.copy(agentUser = action.user)
    }

    is Action.SetConnectionState -> {
        state.copy(connectionState = action.state)
    }

    is Action.SetChatMode -> {
        state.copy(chatMode = action.mode)
    }

    is Action.SetChatSession -> {
        state.copy(chatSession = action.session)
    }

    is Action.SetAgentTyping -> {
        state.copy(isAgentTyping = action.isTyping)
    }

    is Action.SetError -> {
        state.copy(error = action.error)
    }

    is Action.ClearError -> {
        state.copy(error = null)
    }

    is Action.InitiateHandover -> {
        state.copy(
            chatMode = ChatMode.CONNECTING_TO_AGENT,
            connectionState = ConnectionState.CONNECTING
        )
    }

    is Action.HandoverComplete -> {
        state.copy(
            chatMode = ChatMode.HUMAN_AGENT,
            connectionState = ConnectionState.AGENT_CONNECTED
        )
    }

    is Action.EndChat -> {
        state.copy(
            chatMode = ChatMode.ENDED,
            connectionState = ConnectionState.DISCONNECTED
        )
    }

    // === AI AGENT REDUCERS ===

    is Action.AIProcessingStarted -> {
        state.copy(
            isAIProcessing = true,
            aiStreamBuffer = "",
            suggestedReplies = emptyList(),
            error = null
        )
    }

    is Action.AIStreamChunk -> {
        state.copy(
            aiStreamBuffer = state.aiStreamBuffer + action.chunk
        )
    }

    is Action.AIResponseComplete -> {
        // Finalize the AI message by adding it to messages list
        val cleanedResponse = cleanAIResponse(state.aiStreamBuffer)
        val aiMessage = Message(
            user = state.virtualAgentUser,
            text = cleanedResponse
        )

        state.copy(
            messages = (state.messages + aiMessage).takeLast(200),
            isAIProcessing = false,
            aiStreamBuffer = "",
            suggestedReplies = action.suggestedReplies,
            showEscalationDialog = action.shouldEscalate,
            escalationReason = action.escalationReason
        )
    }

    is Action.AIError -> {
        state.copy(
            isAIProcessing = false,
            error = action.error,
            aiStreamBuffer = ""
        )
    }

    is Action.ShowEscalationDialog -> {
        state.copy(
            showEscalationDialog = true,
            escalationReason = action.reason
        )
    }

    is Action.EscalationResponse -> {
        if (action.confirmed) {
            state.copy(
                showEscalationDialog = false,
                chatMode = ChatMode.CONNECTING_TO_AGENT,
                connectionState = ConnectionState.CONNECTING
            )
        } else {
            state.copy(
                showEscalationDialog = false,
                escalationReason = null
            )
        }
    }

    is Action.ClearAIBuffer -> {
        state.copy(aiStreamBuffer = "")
    }

    is Action.SetQuickReplies -> {
        state.copy(suggestedReplies = action.replies)
    }

    is Action.AIProviderChanged -> {
        state.copy(currentAIProvider = action.provider)
    }
}

/**
 * Remove escalation markers and quick reply markers from AI response text.
 * These markers are used internally and should not be shown to users.
 */
private fun cleanAIResponse(text: String): String {
    return text
        .replace(Regex("\\[ESCALATE:[^\\]]*\\]"), "")
        .replace(Regex("\\[QUICK_REPLIES:[^\\]]*\\]"), "")
        .trim()
}

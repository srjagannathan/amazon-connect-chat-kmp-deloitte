package com.amazon.connect.chat.store

import com.amazon.connect.chat.connect.ConnectionState
import com.amazon.connect.chat.data.ChatMode
import com.amazon.connect.chat.data.Message
import com.amazon.connect.chat.data.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

/**
 * Redux-like store for managing chat state
 */
interface Store {
    fun send(action: Action)
    val stateFlow: StateFlow<ChatState>
    val state: ChatState get() = stateFlow.value
}

/**
 * Creates a new store instance within the given coroutine scope
 */
fun CoroutineScope.createStore(): Store {
    val mutableStateFlow = MutableStateFlow(ChatState())
    val channel: Channel<Action> = Channel(Channel.UNLIMITED)

    return object : Store {
        init {
            launch {
                channel.consumeAsFlow().collect { action ->
                    mutableStateFlow.value = chatReducer(mutableStateFlow.value, action)
                }
            }
        }

        override fun send(action: Action) {
            launch {
                channel.send(action)
            }
        }

        override val stateFlow: StateFlow<ChatState> = mutableStateFlow
    }
}

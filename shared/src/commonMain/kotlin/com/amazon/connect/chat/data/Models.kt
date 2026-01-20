package com.amazon.connect.chat.data

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Represents a chat message in the conversation
 */
data class Message(
    val user: User,
    val text: String,
    val timeMs: Long,
    val id: Long
) {
    constructor(user: User, text: String) : this(
        user = user,
        text = text,
        timeMs = Clock.System.now().toEpochMilliseconds(),
        id = Random.nextLong()
    )
}

/**
 * Represents a participant in the chat (customer, agent, or virtual agent)
 */
data class User(
    val name: String,
    val role: ParticipantRole,
    val color: Color = ColorProvider.getColor(),
    val picture: String? = null
)

/**
 * Participant roles in the conversation
 */
enum class ParticipantRole {
    CUSTOMER,
    VIRTUAL_AGENT,
    HUMAN_AGENT,
    SYSTEM
}

/**
 * Chat mode - determines who the customer is talking to
 */
enum class ChatMode {
    VIRTUAL_AGENT,
    CONNECTING_TO_AGENT,
    HUMAN_AGENT,
    ENDED
}

/**
 * Color provider for user avatars
 */
object ColorProvider {
    private val colors = mutableListOf(
        0xFFEA3468,
        0xFFB634EA,
        0xFF349BEA,
        0xFF34EA68,
        0xFFEA9834
    )
    private val allColors = colors.toList()

    fun getColor(): Color {
        if (colors.isEmpty()) {
            colors.addAll(allColors)
        }
        val idx = Random.nextInt(colors.size)
        val color = colors[idx]
        colors.removeAt(idx)
        return Color(color)
    }
}

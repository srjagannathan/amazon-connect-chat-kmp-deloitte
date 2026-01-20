package com.amazon.connect.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import com.amazon.connect.chat.data.ChatColors
import com.amazon.connect.chat.data.Message
import com.amazon.connect.chat.data.ParticipantRole
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Individual chat message bubble
 */
@Composable
fun ChatMessage(
    message: Message,
    isFromCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    val isSystemMessage = message.user.role == ParticipantRole.SYSTEM

    if (isSystemMessage) {
        SystemMessage(message, modifier)
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isFromCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isFromCurrentUser) {
            UserPic(
                user = message.user,
                size = 36,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        Column(
            horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // Show name for non-customer messages
            if (!isFromCurrentUser) {
                Text(
                    text = message.user.name,
                    style = MaterialTheme.typography.caption,
                    color = ChatColors.TEXT_SECONDARY,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                )
            }

            // Message bubble
            MessageBubble(
                message = message,
                isFromCurrentUser = isFromCurrentUser
            )

            // Timestamp
            Text(
                text = formatTime(message.timeMs),
                style = MaterialTheme.typography.caption,
                color = ChatColors.TIME_TEXT,
                modifier = Modifier.padding(
                    start = if (isFromCurrentUser) 0.dp else 12.dp,
                    end = if (isFromCurrentUser) 12.dp else 0.dp,
                    top = 2.dp
                )
            )
        }

        if (isFromCurrentUser) {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

/**
 * Message bubble with styling based on sender
 */
@Composable
private fun MessageBubble(
    message: Message,
    isFromCurrentUser: Boolean
) {
    val backgroundColor = when {
        isFromCurrentUser -> ChatColors.CUSTOMER_MESSAGE
        message.user.role == ParticipantRole.HUMAN_AGENT -> ChatColors.AGENT_MESSAGE
        message.user.role == ParticipantRole.VIRTUAL_AGENT -> ChatColors.VIRTUAL_AGENT_MESSAGE
        else -> ChatColors.AGENT_MESSAGE
    }

    val bubbleShape = if (isFromCurrentUser) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 4.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp
        )
    }

    Surface(
        shape = bubbleShape,
        color = backgroundColor,
        elevation = 1.dp
    ) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.body1,
            color = ChatColors.TEXT_PRIMARY,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/**
 * System message (centered, no bubble)
 */
@Composable
private fun SystemMessage(
    message: Message,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = ChatColors.SYSTEM_MESSAGE
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.caption,
                color = ChatColors.TEXT_SECONDARY,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Format timestamp for display
 */
private fun formatTime(timeMs: Long): String {
    return try {
        val instant = Instant.fromEpochMilliseconds(timeMs)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = localDateTime.hour
        val minute = localDateTime.minute
        val amPm = if (hour < 12) "AM" else "PM"
        val hour12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        "$hour12:${minute.toString().padStart(2, '0')} $amPm"
    } catch (e: Exception) {
        ""
    }
}

/**
 * Triangle shape for message bubble pointer (can be used for enhanced styling)
 */
class TriangleEdgeShape(private val offset: Int, private val isLeft: Boolean) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val trianglePath = Path().apply {
            if (isLeft) {
                moveTo(x = 0f, y = size.height - offset)
                lineTo(x = 0f, y = size.height)
                lineTo(x = offset.toFloat(), y = size.height)
            } else {
                moveTo(x = size.width, y = size.height - offset)
                lineTo(x = size.width, y = size.height)
                lineTo(x = size.width - offset, y = size.height)
            }
        }
        return Outline.Generic(trianglePath)
    }
}

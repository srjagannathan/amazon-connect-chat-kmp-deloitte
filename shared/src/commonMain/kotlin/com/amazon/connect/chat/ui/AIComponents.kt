package com.amazon.connect.chat.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amazon.connect.chat.data.ChatColors

/**
 * Streaming message bubble with animated cursor.
 * Shows AI response as it streams in real-time.
 */
@Composable
fun StreamingMessageBubble(
    text: String,
    isComplete: Boolean,
    modifier: Modifier = Modifier
) {
    // Blinking cursor animation
    val infiniteTransition = rememberInfiniteTransition()
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = ChatColors.VA_BUBBLE,
            elevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.body1,
                    color = ChatColors.TEXT_PRIMARY
                )
                if (!isComplete && text.isNotEmpty()) {
                    Text(
                        text = "▋",
                        modifier = Modifier.alpha(cursorAlpha),
                        style = MaterialTheme.typography.body1,
                        color = ChatColors.PRIMARY
                    )
                }
            }
        }
    }
}

/**
 * Quick reply chips displayed after AI response.
 * Tapping a chip sends that message.
 */
@Composable
fun QuickReplies(
    replies: List<String>,
    onReplyClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (replies.isEmpty()) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(replies) { reply ->
            QuickReplyChip(
                text = reply,
                onClick = { onReplyClick(reply) }
            )
        }
    }
}

/**
 * Individual quick reply chip.
 */
@Composable
private fun QuickReplyChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = ChatColors.PRIMARY.copy(alpha = 0.1f)
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.body2,
                color = ChatColors.PRIMARY
            )
        }
    }
}

/**
 * Escalation confirmation dialog.
 * Always requires user confirmation before connecting to human agent.
 */
@Composable
fun EscalationConfirmationDialog(
    reason: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Connect to Live Agent?",
                style = MaterialTheme.typography.h6,
                color = ChatColors.TEXT_PRIMARY
            )
        },
        text = {
            Column {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.body1,
                    color = ChatColors.TEXT_PRIMARY
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Would you like to speak with a live agent?",
                    style = MaterialTheme.typography.body2,
                    color = ChatColors.TEXT_SECONDARY
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ChatColors.PRIMARY
                )
            ) {
                Text("Yes, connect me", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "No, continue with assistant",
                    color = ChatColors.TEXT_SECONDARY
                )
            }
        },
        backgroundColor = ChatColors.SURFACE,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * AI typing indicator with animated dots.
 */
@Composable
fun AITypingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()

    // Staggered dot animations
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        )
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 150),
            repeatMode = RepeatMode.Reverse
        )
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 300),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ChatColors.VA_BUBBLE,
            elevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Virtual Assistant is typing",
                    style = MaterialTheme.typography.caption,
                    color = ChatColors.TEXT_SECONDARY
                )
                Spacer(modifier = Modifier.width(4.dp))
                TypingDot(alpha = dot1Alpha)
                TypingDot(alpha = dot2Alpha)
                TypingDot(alpha = dot3Alpha)
            }
        }
    }
}

@Composable
private fun TypingDot(alpha: Float) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .alpha(alpha)
            .background(
                color = ChatColors.PRIMARY,
                shape = RoundedCornerShape(3.dp)
            )
    )
}

/**
 * AI provider badge (shown when fallback provider is used).
 */
@Composable
fun AIProviderBadge(
    provider: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = ChatColors.TEXT_SECONDARY.copy(alpha = 0.1f)
    ) {
        Text(
            text = "Powered by ${provider.replaceFirstChar { it.uppercase() }}",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.caption,
            color = ChatColors.TEXT_SECONDARY
        )
    }
}

/**
 * Error banner for AI processing errors.
 */
@Composable
fun AIErrorBanner(
    error: String,
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFFF3E0)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = error,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.body2,
                color = Color(0xFFE65100)
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text("Retry", color = ChatColors.PRIMARY)
                }
            }
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = ChatColors.TEXT_SECONDARY)
            }
        }
    }
}

package com.amazon.connect.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.amazon.connect.chat.data.ChatColors
import kotlinx.datetime.Clock

/**
 * Message input component with send button
 */
@Composable
fun SendMessage(
    onSendMessage: (String) -> Unit,
    onTyping: () -> Unit = {},
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var lastTypingTime by remember { mutableStateOf(0L) }

    val sendMessage = {
        if (inputText.isNotBlank()) {
            onSendMessage(inputText.trim())
            inputText = ""
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        elevation = 8.dp,
        color = ChatColors.BACKGROUND
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = { newValue ->
                    inputText = newValue
                    // Send typing indicator (throttled)
                    val now = Clock.System.now().toEpochMilliseconds()
                    if (now - lastTypingTime > 2000) {
                        lastTypingTime = now
                        onTyping()
                    }
                },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .onPreviewKeyEvent { event ->
                        // Handle Enter key (without Shift) to send message
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.Enter &&
                            !event.isShiftPressed) {
                            sendMessage()
                            true  // Consume the event to prevent newline
                        } else {
                            false
                        }
                    },
                placeholder = {
                    Text(
                        text = if (enabled) "Type a message..." else "Connecting...",
                        color = ChatColors.TEXT_SECONDARY
                    )
                },
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = ChatColors.SURFACE,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = ChatColors.PRIMARY
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                shape = RoundedCornerShape(24.dp),
                singleLine = false,
                maxLines = 4
            )

            // Send button
            SendButton(
                onClick = { sendMessage() },
                enabled = enabled && inputText.isNotBlank()
            )
        }
    }
}

/**
 * Circular send button
 */
@Composable
private fun SendButton(
    onClick: () -> Unit,
    enabled: Boolean
) {
    val backgroundColor = if (enabled) ChatColors.PRIMARY else ChatColors.OFFLINE
    val contentColor = Color.White

    Surface(
        modifier = Modifier
            .size(48.dp)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(enabled = enabled, onClick = onClick),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = backgroundColor,
        elevation = if (enabled) 4.dp else 0.dp
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send message",
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

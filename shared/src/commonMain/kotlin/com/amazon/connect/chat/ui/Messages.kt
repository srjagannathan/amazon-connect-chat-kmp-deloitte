package com.amazon.connect.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amazon.connect.chat.data.Message
import com.amazon.connect.chat.data.User

/**
 * Scrollable list of chat messages
 */
@Composable
fun Messages(
    messages: List<Message>,
    currentUser: User?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(
            items = messages,
            key = { it.id }
        ) { message ->
            ChatMessage(
                message = message,
                isFromCurrentUser = message.user == currentUser
            )
        }
    }
}

/**
 * User avatar/profile picture
 */
@Composable
fun UserPic(
    user: User,
    modifier: Modifier = Modifier,
    size: Int = 40
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(user.color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = user.name.take(1).uppercase(),
            style = MaterialTheme.typography.body1,
            color = Color.White
        )
    }
}

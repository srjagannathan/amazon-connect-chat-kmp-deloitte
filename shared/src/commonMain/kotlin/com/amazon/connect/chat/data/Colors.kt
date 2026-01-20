package com.amazon.connect.chat.data

import androidx.compose.ui.graphics.Color

/**
 * Color scheme for the chat UI
 */
object ChatColors {
    // Gradient colors for header/background
    val GRADIENT_PRIMARY = listOf(0xFF232F3E, 0xFF37475A)
    val GRADIENT_ACCENT = listOf(0xFFFF9900, 0xFFFFB84D)

    // Main UI colors (Amazon-inspired)
    val PRIMARY = Color(0xFFFF9900)  // Amazon Orange
    val PRIMARY_DARK = Color(0xFF232F3E)  // Amazon Dark Blue
    val SURFACE = Color(0xFFF5F5F5)
    val BACKGROUND = Color(0xFFFFFFFF)

    // Message bubble colors
    val CUSTOMER_MESSAGE = Color(0xFFFF9900).copy(alpha = 0.1f)
    val AGENT_MESSAGE = Color(0xFFFFFFFF)
    val VIRTUAL_AGENT_MESSAGE = Color(0xFFE3F2FD)
    val SYSTEM_MESSAGE = Color(0xFFFFF3E0)
    val VA_BUBBLE = Color(0xFFE3F2FD)  // Virtual Agent bubble (alias for readability)

    // Text colors
    val TEXT_PRIMARY = Color(0xFF111111)
    val TEXT_SECONDARY = Color(0xFF565656)
    val TIME_TEXT = Color(0xFF979797)

    // Status colors
    val ONLINE = Color(0xFF4CAF50)
    val CONNECTING = Color(0xFFFF9800)
    val OFFLINE = Color(0xFF9E9E9E)

    // Top bar gradient
    val TOP_GRADIENT = listOf(Color(0xFF232F3E), Color(0xFF37475A))
}

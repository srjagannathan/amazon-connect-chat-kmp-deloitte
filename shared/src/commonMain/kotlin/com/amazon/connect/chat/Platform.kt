package com.amazon.connect.chat

/**
 * Platform identification interface for KMP
 */
interface Platform {
    val name: String
}

/**
 * Get current platform - implemented in each platform source set
 */
expect fun getPlatform(): Platform

/**
 * Get current time in milliseconds - common implementation
 */
expect fun currentTimeMillis(): Long

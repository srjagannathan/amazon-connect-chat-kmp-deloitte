package com.amazon.connect.chat

import kotlin.js.Date

class JsPlatform : Platform {
    override val name: String = "Web Browser"
}

actual fun getPlatform(): Platform = JsPlatform()

actual fun currentTimeMillis(): Long = Date.now().toLong()

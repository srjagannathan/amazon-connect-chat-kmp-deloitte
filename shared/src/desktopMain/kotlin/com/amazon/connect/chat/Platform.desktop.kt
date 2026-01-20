package com.amazon.connect.chat

class DesktopPlatform : Platform {
    override val name: String = "Desktop JVM ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = DesktopPlatform()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

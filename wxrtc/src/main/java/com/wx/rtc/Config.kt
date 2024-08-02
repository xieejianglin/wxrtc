package com.wx.rtc

internal object Config {
    const val WS_URL: String = "ws://127.0.0.1:50000/ws/chat/"
    const val RECONNECT_MAX_NUM: Int = 60
    const val RECONNECT_MILLIS: Long = 1000L
}

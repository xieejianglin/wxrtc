package com.wx.rtc.rtc

import java.io.File

internal interface RTCListener {
    fun onConnected()
    fun onClose()
    fun onSnapshot(userId: String, file: File)
}

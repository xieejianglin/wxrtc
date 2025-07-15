package com.wx.rtc.rtc

internal interface RTCListener {
    fun onConnected()
    fun onClose()
    fun onSnapshot(userId: String, file: String)
}

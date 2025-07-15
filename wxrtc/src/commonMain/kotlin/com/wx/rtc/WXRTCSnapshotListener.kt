package com.wx.rtc

interface WXRTCSnapshotListener {
    fun onSnapshot(userId: String, filePath: String)
}
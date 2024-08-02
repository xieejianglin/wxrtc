package com.wx.rtc

import java.io.File

interface WXRTCSnapshotListener {
    fun onSnapshot(userId: String, file: File)
}
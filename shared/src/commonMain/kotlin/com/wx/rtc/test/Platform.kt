package com.wx.rtc.test

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
package com.wx.rtc.test.app

import android.app.Application
import com.wx.rtc.WXRTC

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        WXRTC.getInstance("")
    }
}
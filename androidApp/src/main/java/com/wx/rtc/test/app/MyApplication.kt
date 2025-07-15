package com.wx.rtc.test.app

import android.app.Application
import com.wx.rtc.WXRTC

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        WXRTC.getInstance("ws://36.138.203.153:52000/ws/chat/")
//        WXRTC.getInstance("ws://122.228.133.178:52210/ws/chat/")
//        WXRTC.getInstance("ws://60.190.89.77:52230/ws/chat/", 1)

    }
}
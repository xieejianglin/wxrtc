package com.wx.rtc.test.app;

import android.app.Application;

import com.wx.rtc.WXRTC;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        WXRTC.getInstance("");
    }
}

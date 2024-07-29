package com.wx.rtc.rtc;

import java.io.File;

public interface RTCListener {
    void onConnected();
    void onClose();
    void onScreenShot(File file);
}

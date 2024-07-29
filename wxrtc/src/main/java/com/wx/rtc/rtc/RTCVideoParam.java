package com.wx.rtc.rtc;

public class RTCVideoParam {
    public int videoWidth = 1280;
    public int videoHeight = 720;
    public int videoFps = 25;
    public int videoMinBitrate = 2000;
    public int videoMaxBitrate = 2500;

    public RTCVideoParam(){
    }

    public RTCVideoParam(int videoWidth, int videoHeight, int videoFps, int videoMinBitrate, int videoMaxBitrate){
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.videoFps = videoFps;
        this.videoMinBitrate = videoMinBitrate;
        this.videoMaxBitrate = videoMaxBitrate;
    }
}

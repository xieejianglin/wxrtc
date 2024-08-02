package com.wx.rtc.rtc

class RTCVideoParam {
    var videoWidth: Int = 1280
    var videoHeight: Int = 720
    var videoFps: Int = 25
    var videoMinBitrate: Int = 2000
    var videoMaxBitrate: Int = 2500

    constructor()

    constructor(
        videoWidth: Int,
        videoHeight: Int,
        videoFps: Int,
        videoMinBitrate: Int,
        videoMaxBitrate: Int
    ) {
        this.videoWidth = videoWidth
        this.videoHeight = videoHeight
        this.videoFps = videoFps
        this.videoMinBitrate = videoMinBitrate
        this.videoMaxBitrate = videoMaxBitrate
    }
}

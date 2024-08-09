package com.wx.rtc.rtc

import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

internal class ProxyVideoSink : VideoSink {
    var streamId: String? = null

    @JvmField
    @set:Synchronized
    var target: VideoSink? = null

    var frame: VideoFrame? = null
        private set

    @Synchronized
    override fun onFrame(frame: VideoFrame) {
        this.frame = frame
        if (target == null) {
            return
        }
        target!!.onFrame(frame)
    }

    @Synchronized
    fun setTarget(streamId: String?, target: VideoSink?) {
        this.streamId = streamId
        this.target = target
    }

    @Synchronized
    fun release() {
        if (target != null && target is SurfaceViewRenderer) {
            if (!(target as SurfaceViewRenderer).released()) {
                (target as SurfaceViewRenderer).release()
            }
        }
        target = null
    }
}
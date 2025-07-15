package com.wx.rtc.rtc

import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

internal class ProxyVideoSink : VideoSink {
    var target: VideoSink? = null
        private set

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
    fun setTarget(target: VideoSink?) {
        this.target = target
    }

    @Synchronized
    fun release() {
        if (target != null){
            when (target) {
                is SurfaceViewRenderer -> {
                    val renderer = target as SurfaceViewRenderer
                    if (!renderer.isReleased) {
                        renderer.release()
                    } else {  }
                }
                else -> {}
            }
        }
        target = null
    }
}

package com.wx.rtc

import com.wx.rtc.WXRTCDef.WXRTCRenderParams

expect class RTCVideoContainerView {
    var params: WXRTCRenderParams
    var isScreenCapture: Boolean
    var isLocalRenderer: Boolean
    var useFrontCamera: Boolean

    fun setVisible(visible: Boolean)

    fun getVideoView(): RTCVideoView?

    fun addVideoView(videoView: RTCVideoView?)

    fun removeVideoView()

    fun setRendererRenderParams(params: WXRTCRenderParams, isScreenCapture: Boolean, isLocalRenderer: Boolean, useFrontCamera: Boolean)
}
package com.wx.rtc

import com.wx.rtc.WXRTCDef.WXRTCRenderParams

expect class RTCVideoView

expect fun RTCVideoView.setRendererRenderParams(params: WXRTCRenderParams, isScreenCapture: Boolean, isLocalRenderer: Boolean, useFrontCamera: Boolean)

expect fun RTCVideoView.snapshotVideo(context: PlatformContext, completion: (String) -> Unit)
package com.wx.rtc.rtc

import com.wx.rtc.RTCVideoContainerView
import com.wx.rtc.WXRTCDef.WXRTCRenderParams
import kotlin.jvm.JvmField

internal data class PeerConnectionManager(
    @JvmField
    var userId: String? = null,
    @JvmField
    var sendSdpUrl: String? = null,
    @JvmField
    var needReconnect: Boolean = true,
    @JvmField
    var videoRecvEnabled: Boolean = false,
    @JvmField
    var videoRecvMute: Boolean = false,
    @JvmField
    var audioRecvMute: Boolean = false,
    @JvmField
    var audioVolume: Float = 0f,
    @JvmField
    var videoContainerView: RTCVideoContainerView? = null,
    @JvmField
    var client: PeerConnectionClient? = null,
    @JvmField
    var renderParams: WXRTCRenderParams? = null,
)

package com.wx.rtc.rtc

import com.wx.rtc.WXRTCDef.WXRTCRenderParams

internal class PeerConnectionManager {
    @JvmField
    var userId: String? = null
    @JvmField
    var sendSdpUrl: String? = null
    @JvmField
    var needReconnect: Boolean = false
    @JvmField
    var videoRecvEnabled: Boolean = false
    @JvmField
    var videoRecvMute: Boolean = false
    @JvmField
    var audioRecvMute: Boolean = false
    @JvmField
    var audioVolume: Float = 0f
    @JvmField
    var videoSink: ProxyVideoSink? = null
    @JvmField
    var client: PeerConnectionClient? = null
    @JvmField
    var renderParams: WXRTCRenderParams? = null
}

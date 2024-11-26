package com.wx.rtc.bean

import com.wx.rtc.WXRTCDef

internal class RecvCommandMessage {
    @JvmField
    var code: Int = 0

    @JvmField
    var message: String? = null

    @JvmField
    var signal: String? = null

    @JvmField
    var publishUrl: String? = null

    @JvmField
    var unpublishUrl: String? = null

    @JvmField
    var userId: String? = null

    @JvmField
    var pullUrl: String? = null

    @JvmField
    var available: Boolean? = false

    @JvmField
    var recordFileName: String? = null

    @JvmField
    var p2pMsg: P2PMsg? = null

    @JvmField
    var roomMsg: RoomMsg? = null

    @JvmField
    var callMsg: CallMsg? = null

    @JvmField
    var result: WXRTCDef.ProcessData? = null
}

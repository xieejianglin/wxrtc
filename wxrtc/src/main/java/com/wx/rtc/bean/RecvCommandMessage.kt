package com.wx.rtc.bean

import com.google.gson.annotations.SerializedName
import com.wx.rtc.WXRTCDef

internal class RecvCommandMessage {
    @JvmField
    @SerializedName("code")
    var code: Int = 0

    @JvmField
    @SerializedName("message")
    var message: String? = null

    @JvmField
    @SerializedName("signal")
    var signal: String? = null

    @JvmField
    @SerializedName("publish_url")
    var publishUrl: String? = null

    @JvmField
    @SerializedName("unpublish_url")
    var unpublishUrl: String? = null

    @JvmField
    @SerializedName("user_id")
    var userId: String? = null

    @JvmField
    @SerializedName("pull_url")
    var pullUrl: String? = null

    @JvmField
    @SerializedName("available")
    var available: Boolean? = false

    @JvmField
    @SerializedName("record_file_name")
    var recordFileName: String? = null

    @JvmField
    @SerializedName("p2p_msg")
    var p2pMsg: P2PMsg? = null

    @JvmField
    @SerializedName("room_msg")
    var roomMsg: RoomMsg? = null

    @JvmField
    @SerializedName("call_msg")
    var callMsg: CallMsg? = null

    @JvmField
    @SerializedName("result")
    var result: WXRTCDef.ProcessData? = null
}

package com.wx.rtc.bean

import com.wx.rtc.WXRTCDef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField

@Serializable
internal data class RecvCommandMessage(
    @JvmField
    @SerialName("code")
    var code: Int = 0,

    @JvmField
    @SerialName("message")
    var message: String? = null,

    @JvmField
    @SerialName("signal")
    var signal: String? = null,

    @JvmField
    @SerialName("publish_url")
    var publishUrl: String? = null,

    @JvmField
    @SerialName("unpublish_url")
    var unpublishUrl: String? = null,

    @JvmField
    @SerialName("user_id")
    var userId: String? = null,

    @JvmField
    @SerialName("pull_url")
    var pullUrl: String? = null,

    @JvmField
    var available: Boolean? = false,

    @JvmField
    @SerialName("record_file_name")
    var recordFileName: String? = null,

    @JvmField
    @SerialName("p2p_msg")
    var p2pMsg: P2PMsg? = null,

    @JvmField
    @SerialName("room_msg")
    var roomMsg: RoomMsg? = null,

    @JvmField
    @SerialName("call_msg")
    var callMsg: CallMsg? = null,

    @JvmField
    @SerialName("result")
    var result: WXRTCDef.ProcessData? = null,
)

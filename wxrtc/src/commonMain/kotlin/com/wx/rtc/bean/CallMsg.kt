package com.wx.rtc.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField

@Serializable
internal data class CallMsg(
    @JvmField
    @SerialName("cmd")
    var cmd: String? = null,

    @JvmField
    @SerialName("user_id")
    var userId: String? = null,

    @JvmField
    @SerialName("room_id")
    var roomId: String? = null,
)

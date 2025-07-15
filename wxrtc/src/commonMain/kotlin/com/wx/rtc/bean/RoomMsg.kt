package com.wx.rtc.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField

@Serializable
internal data class RoomMsg(
    @JvmField
    @SerialName("cmd")
    var cmd: String? = null,

    @JvmField
    @SerialName("message")
    var message: String? = null,
)

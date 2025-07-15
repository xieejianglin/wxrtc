package com.wx.rtc.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField

@Serializable
internal data class P2PMsg(
    @JvmField
    @SerialName("from")
    var from: String? = null,

    @JvmField
    @SerialName("to")
    var to: String? = null,

    @JvmField
    @SerialName("message")
    var message: String? = null,
)

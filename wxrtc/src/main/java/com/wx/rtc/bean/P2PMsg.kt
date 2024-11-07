package com.wx.rtc.bean

import com.google.gson.annotations.SerializedName

internal class P2PMsg {
    @JvmField
    @SerializedName("from")
    var from: String? = null

    @JvmField
    @SerializedName("to")
    var to: String? = null

    @JvmField
    @SerializedName("message")
    var message: String? = null
}

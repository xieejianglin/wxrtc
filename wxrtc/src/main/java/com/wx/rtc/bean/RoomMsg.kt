package com.wx.rtc.bean

import com.google.gson.annotations.SerializedName

internal class RoomMsg {
    @JvmField
    @SerializedName("cmd")
    var cmd: String? = null

    @JvmField
    @SerializedName("message")
    var message: String? = null
}

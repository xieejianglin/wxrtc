package com.wx.rtc.bean

import com.google.gson.annotations.SerializedName

internal class CallMsg {
    @JvmField
    @SerializedName("cmd")
    var cmd: String? = null

    @JvmField
    @SerializedName("user_id")
    var userId: String? = null

    @JvmField
    @SerializedName("room_id")
    var roomId: String? = null
}

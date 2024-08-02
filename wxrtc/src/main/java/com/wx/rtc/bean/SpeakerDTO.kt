package com.wx.rtc.bean

import com.google.gson.annotations.SerializedName

class SpeakerDTO {
    @JvmField
    @SerializedName("spk_id")
    var spkId: Long? = null

    @JvmField
    @SerializedName("spk_name")
    var spkName: String? = null
}
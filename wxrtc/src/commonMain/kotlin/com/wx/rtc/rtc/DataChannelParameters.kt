package com.wx.rtc.rtc

data class DataChannelParameters(
    val ordered: Boolean = true, val maxRetransmitTimeMs: Int = -1, val maxRetransmits: Int = -1,
    val protocol: String = "", val negotiated: Boolean = false, val id: Int = -1
)

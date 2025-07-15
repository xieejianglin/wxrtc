package com.wx.rtc.rtc

import com.wx.rtc.utils.AUDIO_CODEC_OPUS
import com.wx.rtc.utils.VIDEO_CODEC_H264_BASELINE

data class PeerConnectionParameters(
    val loopback: Boolean = false,
    val tracing: Boolean = false,
    val videoCodec: String = VIDEO_CODEC_H264_BASELINE,
    val videoCodecHwAcceleration: Boolean = true,
    val videoFlexfecEnabled: Boolean = true,
    val audioStartBitrate: Int = 0,
    val audioCodec: String = AUDIO_CODEC_OPUS,
    val noAudioProcessing: Boolean = false,
    val aecDump: Boolean = false,
    val saveInputAudioToFile: Boolean = false,
    val useOpenSLES: Boolean = false,
    val disableBuiltInAEC: Boolean = false,
    val disableBuiltInAGC: Boolean = false,
    val disableBuiltInNS: Boolean = false,
    val disableWebRtcAGCAndHPF: Boolean = false,
    val enableRtcEventLog: Boolean = false,
    val dataChannelParameters: DataChannelParameters? = null
)

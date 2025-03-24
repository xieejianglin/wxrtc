package com.wx.rtc.utils

import android.util.Size
import com.wx.rtc.WXRTCDef
import com.wx.rtc.WXRTCDef.Companion.WXRTC_VIDEO_RESOLUTION_MODE_LANDSCAPE
import com.wx.rtc.WXRTCDef.Companion.WXRTC_VIDEO_RESOLUTION_MODE_PORTRAIT

internal object RTCUtils {
    @JvmStatic
    fun getVideoResolution(videoResolutionMode: Int, videoResolution: Int): Size {
        var videoWidth = 0
        var videoHeight = 0
        when (videoResolution) {
            WXRTCDef.WXRTC_VIDEO_RESOLUTION_120_120 -> {
                videoWidth = 120
                videoHeight = 120
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_160_160 -> {
                videoWidth = 160
                videoHeight = 160
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_270_270 -> {
                videoWidth = 270
                videoHeight = 270
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_480_480 -> {
                videoWidth = 480
                videoHeight = 480
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_160_120 -> {
                videoWidth = 160
                videoHeight = 120
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_240_180 -> {
                videoWidth = 240
                videoHeight = 180
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_280_210 -> {
                videoWidth = 280
                videoHeight = 210
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_320_240 -> {
                videoWidth = 320
                videoHeight = 240
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_400_300 -> {
                videoWidth = 400
                videoHeight = 300
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_480_360 -> {
                videoWidth = 480
                videoHeight = 360
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_640_480 -> {
                videoWidth = 640
                videoHeight = 480
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_960_720 -> {
                videoWidth = 960
                videoHeight = 720
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_160_90 -> {
                videoWidth = 160
                videoHeight = 90
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_256_144 -> {
                videoWidth = 256
                videoHeight = 144
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_320_180 -> {
                videoWidth = 320
                videoHeight = 180
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_480_270 -> {
                videoWidth = 480
                videoHeight = 270
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_640_360 -> {
                videoWidth = 640
                videoHeight = 360
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_960_540 -> {
                videoWidth = 960
                videoHeight = 540
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_1280_720 -> {
                videoWidth = 1280
                videoHeight = 720
            }

            WXRTCDef.WXRTC_VIDEO_RESOLUTION_1920_1080 -> {
                videoWidth = 1920
                videoHeight = 1080
            }

            else -> {}
        }
        if (videoResolutionMode == WXRTC_VIDEO_RESOLUTION_MODE_PORTRAIT) {
            val tempHeight = videoHeight
            videoHeight = videoWidth
            videoWidth = tempHeight
        }
        return Size(videoWidth, videoHeight)
    }
}

package com.wx.rtc

/**
 * @author Administrator
 */
object WXRTCDef {
    const val WXRTC_VIDEO_RESOLUTION_120_120: Int = 1

    const val WXRTC_VIDEO_RESOLUTION_160_160: Int = 3

    const val WXRTC_VIDEO_RESOLUTION_270_270: Int = 5

    const val WXRTC_VIDEO_RESOLUTION_480_480: Int = 7

    const val WXRTC_VIDEO_RESOLUTION_160_120: Int = 50

    const val WXRTC_VIDEO_RESOLUTION_240_180: Int = 52

    const val WXRTC_VIDEO_RESOLUTION_280_210: Int = 54

    const val WXRTC_VIDEO_RESOLUTION_320_240: Int = 56

    const val WXRTC_VIDEO_RESOLUTION_400_300: Int = 58

    const val WXRTC_VIDEO_RESOLUTION_480_360: Int = 60

    const val WXRTC_VIDEO_RESOLUTION_640_480: Int = 62

    const val WXRTC_VIDEO_RESOLUTION_960_720: Int = 64

    const val WXRTC_VIDEO_RESOLUTION_160_90: Int = 100

    const val WXRTC_VIDEO_RESOLUTION_256_144: Int = 102

    const val WXRTC_VIDEO_RESOLUTION_320_180: Int = 104

    const val WXRTC_VIDEO_RESOLUTION_480_270: Int = 106

    const val WXRTC_VIDEO_RESOLUTION_640_360: Int = 108

    const val WXRTC_VIDEO_RESOLUTION_960_540: Int = 110

    const val WXRTC_VIDEO_RESOLUTION_1280_720: Int = 112

    const val WXRTC_VIDEO_RESOLUTION_1920_1080: Int = 114

    const val WXRTC_VIDEO_RESOLUTION_MODE_LANDSCAPE: Int = 0

    const val WXRTC_VIDEO_RESOLUTION_MODE_PORTRAIT: Int = 1

    const val WXRTC_VIDEO_RENDER_MODE_FILL: Int = 0

    const val WXRTC_VIDEO_RENDER_MODE_FIT: Int = 1

    const val WXRTC_VIDEO_ROTATION_0: Int = 0

    const val WXRTC_VIDEO_ROTATION_90: Int = 1

    const val WXRTC_VIDEO_ROTATION_180: Int = 2

    const val WXRTC_VIDEO_ROTATION_270: Int = 3

    const val WXRTC_VIDEO_MIRROR_TYPE_AUTO: Int = 0

    const val WXRTC_VIDEO_MIRROR_TYPE_ENABLE: Int = 1

    const val WXRTC_VIDEO_MIRROR_TYPE_DISABLE: Int = 2

    /**
     * 通话角色
     */
    enum class Role {
        /**
         * 未知类型
         */
        None,

        /**
         * 主叫（邀请方）
         */
        Caller,

        /**
         * 被叫（被邀请方）
         */
        Callee,
    }

    /**
     * 通话角色
     */
    enum class Status {
        /**
         * 未定义
         */
        None,

        /**
         * 通话等待中
         */
        Calling,

        /**
         * 通话已接听（通话中）
         */
        Connected,
    }

    class WXRTCVideoEncParam (
        @JvmField
        var videoResolution: Int = WXRTC_VIDEO_RESOLUTION_1280_720,
        @JvmField
        var videoResolutionMode: Int = WXRTC_VIDEO_RESOLUTION_MODE_LANDSCAPE,
        @JvmField
        var videoFps: Int = 25,
        @JvmField
        var videoMinBitrate: Int = 2000,
        @JvmField
        var videoMaxBitrate: Int = 2500,
    )

    class WXRTCRenderParams (
        @JvmField
        var rotation: Int = WXRTC_VIDEO_ROTATION_0,

        @JvmField
        var fillMode: Int = WXRTC_VIDEO_RENDER_MODE_FILL,

        @JvmField
        var mirrorType: Int = WXRTC_VIDEO_MIRROR_TYPE_AUTO,
    )
}

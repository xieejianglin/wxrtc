package com.wx.rtc

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField

/**
 * @author Administrator
 */
object WXRTCDef {
    const val WXRTC_NETWORK_TYPE_PUBLIC: Int = 1

    const val WXRTC_NETWORK_TYPE_PROTECTED: Int = 2

    const val WXRTC_NETWORK_TYPE_PRIVATE: Int = 3

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

    const val WXRTC_PROCESS_DATA_RST_NO_RESULT: Int = 0

    /**
     * 气囊水滴
     */
    const val WXRTC_PROCESS_DATA_RST_DROP: Int = 1

    /**
     * 温度计
     */
    const val WXRTC_PROCESS_DATA_RST_THERMOMETER: Int = 2

    /**
     * 条形码
     */
    const val WXRTC_PROCESS_DATA_RST_BARCODE: Int = 3

    /**
     * 二维码
     */
    const val WXRTC_PROCESS_DATA_RST_QRCODE: Int = 4

    /**
     * 血压仪
     */
    const val WXRTC_PROCESS_DATA_RST_SPHYGMOMANOMETER: Int = 5

    /**
     * 腰椎穿刺
     */
    const val WXRTC_PROCESS_DATA_RST_SPINAL_PUNCTURE: Int = 6

    /**
     * 眼科
     */
    const val WXRTC_PROCESS_DATA_RST_AROPTP: Int = 7

    /**
     * 人脸
     */
    const val WXRTC_PROCESS_DATA_RST_FACE: Int = 8

    /**
     * 语音识别
     */
    const val WXRTC_PROCESS_DATA_RST_SPEECH_RECOGNITION: Int = 9

    /**
     * 手势识别
     */
    const val WXRTC_PROCESS_DATA_RST_GESTURE_RECOGNITION: Int = 10

    /**
     * 血氧仪
     */
    const val WXRTC_PROCESS_DATA_RST_OXIMETER: Int = 11

    /**
     * 体重秤
     */
    const val WXRTC_PROCESS_DATA_RST_WEIGHT_SCALE: Int = 12

    /**
     * 心电监护仪
     */
    const val WXRTC_PROCESS_DATA_RST_ECG_MONITOR: Int = 13

    /**
     * 血糖仪
     */
    const val WXRTC_PROCESS_DATA_RST_GLUCOSE_METER: Int = 14

    /**
     * 床头屏
     */
    const val WXRTC_PROCESS_DATA_RST_BEDHEAD_SCREEN: Int = 15

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

    class WXRTCRenderParams(
        @JvmField
        var rotation: Int = WXRTC_VIDEO_ROTATION_0,

        @JvmField
        var fillMode: Int = WXRTC_VIDEO_RENDER_MODE_FILL,

        @JvmField
        var mirrorType: Int = WXRTC_VIDEO_MIRROR_TYPE_AUTO,
    )

    @Serializable
    class Speaker {
        @JvmField
        var spkId: Long? = null

        @JvmField
        var spkName: String? = null
    }

    @Serializable
    class ProcessData {
        var rst: Int? = null //WXRTC_PROCESS_DATA_RST_
        var need_focus: Int? = null //1表示需要聚焦，0表示不需要聚焦
        var focus_point: List<Float>? = null //聚焦位置
        var drop_speed: String? = null //滴速,单位 滴/秒
        var scale: String? = null //体温度数
        var need_magnify: Int? = null // 0表示不需要放大,1表示需要放大
        var barcodeDate: String? = null // 二维码 条形码 内容
        var high_pressure: String? = null // 血压高压
        var low_pressure: String? = null // 血压低压
        var pulse: String? = null // 脉搏
        var has_csf: Int? = null //1表示有液体流出，0表示没有液体流出
        var right_eye: EyeMark? = null //
        var left_eye: EyeMark? = null //
        var pid: String? = null // 人脸患者id
        var asr_result: String? = null //语音识别结果
        var gesture: Int? = null //手势识别结果
        var oxygen_saturation: String? = null //血氧含量
        var weight_scale: String? = null //体重
        var respiratory_rate: String? = null //呼吸率
        var capture_image_url: String? = null //识别出来的截图
        var blood_sugar: String? = null //血糖
        var bed_number: String? = null  //床号
    }

    @Serializable
    class EyeMark {
        /**
         * normal : 0
         * femtosecond : 0
         * astigmatism : 0
         */
        //普通：0为无，1为有
        var normal: Int = 0

        //飞秒：0为无，1为有
        var femtosecond: Int = 0

        //散光：0为无，1为有
        var astigmatism: Int = 0
    }

    class RoomMemberEntity<T> {
        var userId: String? = null
        var userName: String? = null
        var userAvatar: String? = null
        var audioVolume: Int = 0

        // 用户是否打开了视频
        var isVideoAvailable: Boolean = false

        // 用户是否打开音频
        var isAudioAvailable: Boolean = false

        // 是否对用户静画
        var isMuteVideo: Boolean = false

        // 是否对用户静音
        var isMuteAudio: Boolean = false

        var videoView: RTCVideoContainerView? = null

        var customData: T? = null
    }
}

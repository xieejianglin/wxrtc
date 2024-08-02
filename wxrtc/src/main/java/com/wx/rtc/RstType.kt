package com.wx.rtc

annotation class RstType {
    companion object {
        const val NO_RESULT: Int = 0

        /**
         * 气囊水滴
         */
        const val DROP: Int = 1

        /**
         * 温度计
         */
        const val THERMOMETER: Int = 2

        /**
         * 条形码
         */
        const val BARCODE: Int = 3

        /**
         * 二维码
         */
        const val QRCODE: Int = 4

        /**
         * 血压仪
         */
        const val SPHYGMOMANOMETER: Int = 5

        /**
         * 腰椎穿刺
         */
        const val SPINAL_PUNCTURE: Int = 6

        /**
         * 眼科
         */
        const val AROPTP: Int = 7

        /**
         * 人脸
         */
        const val FACE: Int = 8

        /**
         * 语音识别
         */
        const val SPEECH_RECOGNITION: Int = 9

        /**
         * 手势识别
         */
        const val GESTURE_RECOGNITION: Int = 10

        /**
         * 血氧仪
         */
        const val OXIMETER: Int = 11

        /**
         * 体重秤
         */
        const val WEIGHT_SCALE: Int = 12

        /**
         * 心电监护仪
         */
        const val ECG_MONITOR: Int = 13
    }
}

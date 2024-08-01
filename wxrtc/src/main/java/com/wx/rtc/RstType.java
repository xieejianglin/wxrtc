package com.wx.rtc;

public @interface RstType {
    int NO_RESULT = 0;

    /**
     * 气囊水滴
     */
    int DROP = 1;

    /**
     * 温度计
     */
    int THERMOMETER = 2;

    /**
     * 条形码
     */
    int BARCODE = 3;

    /**
     * 二维码
     */
    int QRCODE = 4;

    /**
     * 血压仪
     */
    int SPHYGMOMANOMETER = 5;

    /**
     * 腰椎穿刺
     */
    int SPINAL_PUNCTURE = 6;

    /**
     * 眼科
     */
    int AROPTP = 7;

    /**
     * 人脸
     */
    int FACE = 8;

    /**
     * 语音识别
     */
    int SPEECH_RECOGNITION = 9;

    /**
     * 手势识别
     */
    int GESTURE_RECOGNITION = 10;

    /**
     * 血氧仪
     */
    int OXIMETER = 11;

    /**
     * 体重秤
     */
    int WEIGHT_SCALE = 12;

    /**
     * 心电监护仪
     */
    int ECG_MONITOR = 13;
}

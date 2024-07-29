package com.wx.rtc.bean;

/**
 * @author wjf
 */
public @interface ProcessCommand {
    String START_ASR = "start_asr";
    String END_ASR = "end_asr";
    String END_AND_START_ASR = "end_and_start_asr";
    String START_QRCODE = "start_qrcode";
    String END_QRCODE = "end_qrcode";
    String START_BARCODE = "start_barcode";
    String END_BARCODE = "end_barcode";
    String START_THERMOMETER = "start_thermometer";
    String END_THERMOMETER = "end_thermometer";
    String START_DIGITAL_THERMOMETER = "start_digital_thermometer";
    String END_DIGITAL_THERMOMETER = "end_digital_thermometer";
    String START_INFRARED_THERMOMETER = "start_infrared_thermometer";
    String END_INFRARED_THERMOMETER = "end_infrared_thermometer";
    String START_SPHYGMOMANOMETER = "start_sphygmomanometer";
    String END_SPHYGMOMANOMETER = "end_sphygmomanometer";
    String START_DROP = "start_drop";
    String END_DROP = "end_drop";
}

package com.wx.rtc.bean

/**
 * @author wjf
 */
internal object ProcessCommand {
    const val START_ASR: String = "start_asr"
    const val END_ASR: String = "end_asr"
    const val END_AND_START_ASR: String = "end_and_start_asr"
    const val START_QRCODE: String = "start_qrcode"
    const val END_QRCODE: String = "end_qrcode"
    const val START_BARCODE: String = "start_barcode"
    const val END_BARCODE: String = "end_barcode"
    const val START_THERMOMETER: String = "start_thermometer"
    const val END_THERMOMETER: String = "end_thermometer"
    const val START_DIGITAL_THERMOMETER: String = "start_digital_thermometer"
    const val END_DIGITAL_THERMOMETER: String = "end_digital_thermometer"
    const val START_INFRARED_THERMOMETER: String = "start_infrared_thermometer"
    const val END_INFRARED_THERMOMETER: String = "end_infrared_thermometer"
    const val START_SPHYGMOMANOMETER: String = "start_sphygmomanometer"
    const val END_SPHYGMOMANOMETER: String = "end_sphygmomanometer"
    const val START_DROP: String = "start_drop"
    const val END_DROP: String = "end_drop"
}

package com.wx.rtc.bean


class ResultData {
    var rst: Int? = null //0 没有关心的物体  1 气囊 2 温度计 3条形码 4二维码 5血压仪 6腰椎穿刺 7眼科标记 8人脸 9语音识别
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
}

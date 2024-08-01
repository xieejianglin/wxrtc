package com.wx.rtc.bean;


import java.util.List;

public class ResultData {
    private int rst;                    //0 没有关心的物体  1 气囊 2 温度计 3条形码 4二维码 5血压仪 6腰椎穿刺 7眼科标记 8人脸 9语音识别
    private int need_focus;                    //1表示需要聚焦，0表示不需要聚焦
    private List<Float> focus_point;                    //聚焦位置
    private String drop_speed;                    //滴速,单位 滴/秒
    private String scale;                    //体温度数
    private int need_magnify;                    // 0表示不需要放大,1表示需要放大
    private String barcodeDate;                    // 二维码 条形码 内容
    private String high_pressure;                    // 血压高压
    private String low_pressure;                    // 血压低压
    private String pulse;                    // 脉搏
    private int has_csf;                //1表示有液体流出，0表示没有液体流出
    private EyeMark right_eye;              //
    private EyeMark left_eye;              //
    private String pid;                    // 人脸患者id
    private String asr_result;                    //语音识别结果
    private int gesture;                    //手势识别结果
    private String oxygen_saturation;                    //血氧含量
    private String weight_scale;                    //体重
    private String respiratory_rate;                    //呼吸率
    private String capture_image_url;                   //识别出来的截图

    public int getRst() {
        return rst;
    }

    public void setRst(int rst) {
        this.rst = rst;
    }

    public int getNeed_focus() {
        return need_focus;
    }

    public void setNeed_focus(int need_focus) {
        this.need_focus = need_focus;
    }

    public List<Float> getFocus_point() {
        return focus_point;
    }

    public void setFocus_point(List<Float> focus_point) {
        this.focus_point = focus_point;
    }

    public String getDrop_speed() {
        return drop_speed;
    }

    public void setDrop_speed(String drop_speed) {
        this.drop_speed = drop_speed;
    }

    public String getScale() {
        return scale;
    }

    public void setScale(String scale) {
        this.scale = scale;
    }

    public int getNeed_magnify() {
        return need_magnify;
    }

    public void setNeed_magnify(int need_magnify) {
        this.need_magnify = need_magnify;
    }

    public String getBarcodeDate() {
        return barcodeDate;
    }

    public void setBarcodeDate(String barcodeDate) {
        this.barcodeDate = barcodeDate;
    }

    public String getHigh_pressure() {
        return high_pressure;
    }

    public void setHigh_pressure(String high_pressure) {
        this.high_pressure = high_pressure;
    }

    public String getLow_pressure() {
        return low_pressure;
    }

    public void setLow_pressure(String low_pressure) {
        this.low_pressure = low_pressure;
    }

    public String getPulse() {
        return pulse;
    }

    public void setPulse(String pulse) {
        this.pulse = pulse;
    }

    public int getHas_csf() {
        return has_csf;
    }

    public void setHas_csf(int has_csf) {
        this.has_csf = has_csf;
    }

    public EyeMark getRight_eye() {
        return right_eye;
    }

    public void setRight_eye(EyeMark right_eye) {
        this.right_eye = right_eye;
    }

    public EyeMark getLeft_eye() {
        return left_eye;
    }

    public void setLeft_eye(EyeMark left_eye) {
        this.left_eye = left_eye;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getAsr_result() {
        return asr_result;
    }

    public void setAsr_result(String asr_result) {
        this.asr_result = asr_result;
    }

    public int getGesture() {
        return gesture;
    }

    public void setGesture(int gesture) {
        this.gesture = gesture;
    }

    public String getOxygen_saturation() {
        return oxygen_saturation;
    }

    public void setOxygen_saturation(String oxygen_saturation) {
        this.oxygen_saturation = oxygen_saturation;
    }

    public String getWeight_scale() {
        return weight_scale;
    }

    public void setWeight_scale(String weight_scale) {
        this.weight_scale = weight_scale;
    }

    public String getRespiratory_rate() {
        return respiratory_rate;
    }

    public void setRespiratory_rate(String respiratory_rate) {
        this.respiratory_rate = respiratory_rate;
    }

    public String getCapture_image_url() {
        return capture_image_url;
    }

    public void setCapture_image_url(String capture_image_url) {
        this.capture_image_url = capture_image_url;
    }
}

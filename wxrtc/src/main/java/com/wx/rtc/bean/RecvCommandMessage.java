package com.wx.rtc.bean;

import com.google.gson.annotations.SerializedName;

public class RecvCommandMessage {
    @SerializedName("code")
    private int code;
    @SerializedName("message")
    private String message;
    @SerializedName("signal")
    private String signal;
    @SerializedName("publish_url")
    private String publishUrl;
    @SerializedName("unpublish_url")
    private String unpublishUrl;
    @SerializedName("user_id")
    private String userId;
    @SerializedName("pull_url")
    private String pullUrl;
    @SerializedName("record_file_name")
    private String recordFileName;
    @SerializedName("room_msg")
    private RoomMsg roomMsg;
    @SerializedName("call_msg")
    private CallMsg callMsg;
    @SerializedName("result")
    private ResultData result;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSignal() {
        return signal;
    }

    public void setSignal(String signal) {
        this.signal = signal;
    }

    public String getPublishUrl() {
        return publishUrl;
    }

    public void setPublishUrl(String publishUrl) {
        this.publishUrl = publishUrl;
    }

    public String getUnpublishUrl() {
        return unpublishUrl;
    }

    public void setUnpublishUrl(String unpublishUrl) {
        this.unpublishUrl = unpublishUrl;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPullUrl() {
        return pullUrl;
    }

    public void setPullUrl(String pullUrl) {
        this.pullUrl = pullUrl;
    }

    public String getRecordFileName() {
        return recordFileName;
    }

    public void setRecordFileName(String recordFileName) {
        this.recordFileName = recordFileName;
    }

    public RoomMsg getRoomMsg() {
        return roomMsg;
    }

    public void setRoomMsg(RoomMsg roomMsg) {
        this.roomMsg = roomMsg;
    }

    public CallMsg getCallMsg() {
        return callMsg;
    }

    public void setCallMsg(CallMsg callMsg) {
        this.callMsg = callMsg;
    }

    public ResultData getResult() {
        return result;
    }

    public void setResult(ResultData result) {
        this.result = result;
    }
}

package com.wx.rtc.bean;

import com.google.gson.annotations.SerializedName;

public class RoomMsg {
    @SerializedName("cmd")
    private String cmd;
    @SerializedName("message")
    private String message;

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

package com.wx.rtc.bean;

import com.google.gson.annotations.SerializedName;

public class CallMsg {
    @SerializedName("cmd")
    private String cmd;
    @SerializedName("user_id")
    private String userId;
    @SerializedName("room_id")
    private String roomId;

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
}

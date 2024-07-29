package com.wx.rtc;

public interface WXCallListener {
    void onError(int errCode, String errMsg);
    void onCallReceived(String callerId);
    void onCallCancelled(String streamId);
    void onCallBegin(String roomId, WXRTCDef.Role callRole);
    void onCallEnd(String roomId, WXRTCDef.Role callRole);
    void onUserReject(String streamId);
    void onUserNoResponse(String streamId);
    void onUserLineBusy(String streamId);
    void onUserJoin(String streamId);
    void onUserLeave(String streamId);
}

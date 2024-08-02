package com.wx.rtc

interface WXCallListener {
    fun onError(errCode: Int, errMsg: String)
    fun onCallReceived(callerId: String)
    fun onCallCancelled(streamId: String)
    fun onCallBegin(roomId: String, callRole: WXRTCDef.Role)
    fun onCallEnd(roomId: String, callRole: WXRTCDef.Role)
    fun onUserReject(streamId: String)
    fun onUserNoResponse(streamId: String)
    fun onUserLineBusy(streamId: String)
    fun onUserJoin(streamId: String)
    fun onUserLeave(streamId: String)
}

package com.wx.rtc

interface WXCallListener {
    fun onError(errCode: Int, errMsg: String){}
    fun onCallReceived(callerId: String){}
    fun onCallCancelled(streamId: String){}
    fun onCallBegin(roomId: String, callRole: WXRTCDef.Role){}
    fun onCallEnd(roomId: String, callRole: WXRTCDef.Role){}
    fun onUserReject(streamId: String){}
    fun onUserNoResponse(streamId: String){}
    fun onUserLineBusy(streamId: String){}
    fun onUserJoin(streamId: String){}
    fun onUserLeave(streamId: String){}
    fun onUserVideoAvailable(userId: String, available: Boolean) {}
    fun onUserAudioAvailable(userId: String, available: Boolean) {}
    fun onRecvP2PMsg(fromUserId: String, message: String?) {}
    fun onRecvRoomMsg(userId: String, cmd: String, message: String?) {}
}

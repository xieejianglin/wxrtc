package com.wx.rtc

import com.wx.rtc.WXRTCDef.ProcessData

interface WXRTCListener {
    fun onError(errCode: Int, errMsg: String) {}
    fun onLogin() {}
    fun onLogout(reason: Int) {}
    fun onEnterRoom() {}
    fun onExitRoom(reason: Int) {}
    fun onRemoteUserEnterRoom(userId: String) {}
    fun onRemoteUserLeaveRoom(userId: String, reason: Int) {}
    fun onUserVideoAvailable(userId: String, available: Boolean) {}
    fun onUserAudioAvailable(userId: String, available: Boolean) {}
    fun onRecvP2PMsg(fromUserId: String, message: String?) {}
    fun onRecvRoomMsg(userId: String, cmd: String, message: String?) {}
    fun onProcessResult(processData: ProcessData) {}
    fun onRecordStart(fileName: String) {}
    fun onRecordEnd(fileName: String) {}
}

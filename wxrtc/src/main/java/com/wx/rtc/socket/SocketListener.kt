package com.wx.rtc.socket

import com.wx.rtc.WXRTCDef

internal interface SocketListener {
    fun onError(errCode: Int, errMsg: String)
    fun onSocketOpen()
    fun onLogin()
    fun onLogout(reason: Int)
    fun onEnterRoom(publishUrl: String)
    fun onExitRoom(reason: Int)
    fun onGetUnpublishUrl(unpublishUrl: String)
    fun onRemoteUserEnterRoom(pullUrl: String, userId: String)
    fun onRemoteUserLeaveRoom(userId: String, reason: Int)
    fun onUserVideoAvailable(userId: String, available: Boolean)
    fun onUserAudioAvailable(userId: String, available: Boolean)
    fun onRecvRoomMsg(userId: String, cmd: String, message: String)
    fun onRecvCallMsg(userId: String?, cmd: String, roomId: String?)
    fun onResult(processData: WXRTCDef.ProcessData)

    fun onRecordStart(fileName: String)
    fun onRecordEnd(fileName: String)
}

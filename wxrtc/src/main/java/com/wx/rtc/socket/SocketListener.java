package com.wx.rtc.socket;

import com.wx.rtc.bean.ResultData;

public interface SocketListener{
    void onError(int errCode, String errMsg);
    void onSocketOpen();
    void onLogin();
    void onLogout(int reason);
    void onEnterRoom(String publishUrl);
    void onExitRoom(int reason);
    void onGetUnpublishUrl(String unpublishUrl);
    void onRemoteUserEnterRoom(String pullUrl, String userId);
    void onRemoteUserLeaveRoom(String userId, int reason);
    void onRecvRoomMsg(String streamId, String cmd, String message);
    void onRecvCallMsg(String streamId, String cmd, String roomId);
    void onResult(ResultData resultData);

    void onRecordStart(String fileName);
    void onRecordEnd(String fileName);
}

package com.wx.rtc;

import com.wx.rtc.bean.ResultData;

import java.io.File;

public interface WXRTCListener {
    default void onError(int errCode, String errMsg){};
    default void onLogin(){};
    default void onLogout(int reason){};
    default void onEnterRoom(){};
    default void onExitRoom(int reason){};
    default void onRemoteUserEnterRoom(String userId){};
    default void onRemoteUserLeaveRoom(String userId, int reason){};
    default void onRecvRoomMsg(String userId, String cmd, String message){};
    default void onResult(ResultData resultData){};

    default void onRecordStart(String fileName){};
    default void onRecordEnd(String fileName){};
    default void onScreenShot(File file){};
}

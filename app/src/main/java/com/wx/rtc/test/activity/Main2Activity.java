package com.wx.rtc.test.activity;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.wx.rtc.WXRTC;
import com.wx.rtc.WXRTCDef;
import com.wx.rtc.WXRTCListener;
import com.wx.rtc.bean.ResultData;
import com.wx.rtc.test.R;

import org.webrtc.SurfaceViewRenderer;

public class Main2Activity extends AppCompatActivity implements WXRTCListener {

    private WXRTC mWXRTC;
    private String mUserId = "123456789";

    private SurfaceViewRenderer localVideo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        localVideo = findViewById(R.id.localVideo);

        mWXRTC = WXRTC.Companion.getInstance();
        mWXRTC.init(this);

        WXRTCDef.WXRTCVideoEncParam param = new WXRTCDef.WXRTCVideoEncParam();
        param.videoMinBitrate = 3000;
        param.videoMaxBitrate = 3000;
        param.videoFps = 30;
        param.videoResolution = WXRTCDef.WXRTC_VIDEO_RESOLUTION_1920_1080;
        param.videoResolutionMode = WXRTCDef.WXRTC_VIDEO_RESOLUTION_MODE_PORTRAIT;
        mWXRTC.setRTCVideoParam(param);
        mWXRTC.setRTCListener(this);

        mWXRTC.login("100000", mUserId);
    }

    @Override
    public void onLogin() {
        mWXRTC.enterRoom("123456");
    }

    @Override
    public void onEnterRoom() {
        mWXRTC.startLocalVideo(false, localVideo);
        mWXRTC.startLocalAudio();
    }

    @Override
    public void onError(int errCode, @NonNull String errMsg) {

    }

    @Override
    public void onLogout(int reason) {

    }

    @Override
    public void onExitRoom(int reason) {

    }

    @Override
    public void onRemoteUserEnterRoom(@NonNull String userId) {

    }

    @Override
    public void onRemoteUserLeaveRoom(@NonNull String userId, int reason) {

    }

    @Override
    public void onRecvRoomMsg(@NonNull String userId, @NonNull String cmd, @NonNull String message) {

    }

    @Override
    public void onResult(@NonNull ResultData resultData) {

    }

    @Override
    public void onRecordStart(@NonNull String fileName) {

    }

    @Override
    public void onRecordEnd(@NonNull String fileName) {

    }
}

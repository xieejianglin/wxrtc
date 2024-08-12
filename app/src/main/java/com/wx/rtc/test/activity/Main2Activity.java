package com.wx.rtc.test.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wx.rtc.WXRTC;
import com.wx.rtc.WXRTCDef;
import com.wx.rtc.WXRTCListener;
import com.wx.rtc.test.R;

import org.webrtc.SurfaceViewRenderer;

public class Main2Activity extends AppCompatActivity implements WXRTCListener {

    private Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private WXRTC mWXRTC;
    private String mUserId = "123456789";

    private SurfaceViewRenderer localVideo;

    private SurfaceViewRenderer remoteVideo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        localVideo = findViewById(R.id.localVideo);
        remoteVideo = findViewById(R.id.remoteVideo);

        mWXRTC = WXRTC.getInstance();
        mWXRTC.init(this);

        WXRTCDef.WXRTCVideoEncParam param = new WXRTCDef.WXRTCVideoEncParam();
        param.videoMinBitrate = 3000;
        param.videoMaxBitrate = 3000;
        param.videoFps = 30;
        param.videoResolution = WXRTCDef.WXRTC_VIDEO_RESOLUTION_1920_1080;
        param.videoResolutionMode = WXRTCDef.WXRTC_VIDEO_RESOLUTION_MODE_PORTRAIT;
        mWXRTC.setRTCVideoParam(param);
        mWXRTC.setRTCListener(this);

        WXRTCDef.WXRTCRenderParams params = new WXRTCDef.WXRTCRenderParams();
        params.fillMode = WXRTCDef.WXRTC_VIDEO_RENDER_MODE_FILL;
        params.rotation = WXRTCDef.WXRTC_VIDEO_ROTATION_0;
        params.mirrorType = WXRTCDef.WXRTC_VIDEO_MIRROR_TYPE_AUTO;
        mWXRTC.setLocalRenderParams(params);

        mWXRTC.login("100000", mUserId);
    }

    @Override
    public void onBackPressed() {
        mWXRTC.stopLocalVideo();
        mWXRTC.stopLocalAudio();
        mWXRTC.endProcess();
        mWXRTC.exitRoom();
        mWXRTC.logout();
        super.onBackPressed();
    }

    @Override
    public void onLogin() {
        mWXRTC.enterRoom("123456");
    }

    @Override
    public void onEnterRoom() {
        mWXRTC.startLocalVideo(false, remoteVideo);
        mWXRTC.startLocalAudio();

        mWXRTC.startProcess();

        WXRTCDef.Speaker speaker = WXRTC.getSpeaker(10000L, "测试");
        speaker.spkId = 1000L;
        speaker.spkName = "测试2";
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
        WXRTCDef.RoomMemberEntity<String> member = new WXRTCDef.RoomMemberEntity<>();
        member.setUserId(userId);
    }

    @Override
    public void onRemoteUserLeaveRoom(@NonNull String userId, int reason) {

    }

    @Override
    public void onRecvRoomMsg(@NonNull String userId, @NonNull String cmd, @NonNull String message) {

    }

    @Override
    public void onProcessResult(@NonNull WXRTCDef.ProcessData processData) {
        Toast.makeText(this, "收到处理消息:"+gson.toJson(processData), Toast.LENGTH_SHORT).show();
        if (processData.rst != null) {
            switch (processData.rst) {
                case WXRTCDef.WXRTC_PROCESS_DATA_RST_NO_RESULT:
                {
                    Log.d("", "收到处理消息:没有关心的物体");
                    break;
                }
                case WXRTCDef.WXRTC_PROCESS_DATA_RST_DROP:
                {
                    Log.d("", "收到处理消息:水滴滴速:"+processData.getDrop_speed());
                    break;
                }
                case WXRTCDef.WXRTC_PROCESS_DATA_RST_THERMOMETER:
                {
                    Log.d("", "收到处理消息:温度计:"+processData.getScale());
                    break;
                }
                default:
                {

                }
            }
        }
    }

    @Override
    public void onRecordStart(@NonNull String fileName) {

    }

    @Override
    public void onRecordEnd(@NonNull String fileName) {

    }
}

package com.wx.rtc.test.activity;

import static com.wx.rtc.WXRTCDef.WXRTC_VIDEO_RESOLUTION_1920_1080;
import static com.wx.rtc.WXRTCDef.WXRTC_VIDEO_RESOLUTION_MODE_PORTRAIT;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.wx.rtc.WXRTC;
import com.wx.rtc.WXRTCDef;
import com.wx.rtc.WXRTCListener;
import com.wx.rtc.bean.ResultData;
import com.wx.rtc.test.R;

import org.webrtc.SurfaceViewRenderer;

import java.io.File;

public class MainActivity extends AppCompatActivity implements WXRTCListener {
    private static final int REQUEST_CODE = 1;

    private static final String[] PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE};

    private WXRTC mWXRTC;
    private SurfaceViewRenderer localVideoView;
    private SurfaceViewRenderer remoteVideoView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        localVideoView = findViewById(R.id.localVideo);
        remoteVideoView = findViewById(R.id.remoteVideo);

        init();

        checkPermission();
    }

    private void init() {
        mWXRTC = WXRTC.getInstance();
        mWXRTC.init(this);
        mWXRTC.login("100000", "123456789");

        WXRTCDef.WXRTCVideoEncParam param = new WXRTCDef.WXRTCVideoEncParam();
        param.videoMinBitrate = 3000;
        param.videoMaxBitrate = 3000;
        param.videoFps = 30;
        param.videoResolution = WXRTC_VIDEO_RESOLUTION_1920_1080;
        param.videoResolutionMode = WXRTC_VIDEO_RESOLUTION_MODE_PORTRAIT;
        mWXRTC.setRTCVideoParam(param);
        mWXRTC.setRTCListener(this);
    }

    private void checkPermission(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE);
        }else{
            enterRoom();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int granted : grantResults) {
            if (granted != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        enterRoom();
    }

    private void enterRoom(){
        mWXRTC.enterRoom("123456");
    }

    @Override
    public void onError(int errCode, String errMsg) {
        Toast.makeText(this, errCode +":"+ errMsg, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onLogin() {
        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
        enterRoom();
    }

    @Override
    public void onLogout(int reason) {
        Toast.makeText(this, "登出成功", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEnterRoom() {
        Toast.makeText(this, "进入房间", Toast.LENGTH_SHORT).show();
        mWXRTC.startLocalVideo(false, localVideoView);
        mWXRTC.startLocalAudio();
    }

    @Override
    public void onExitRoom(int reason) {
        Toast.makeText(this, "退出房间", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRemoteUserEnterRoom(String userId) {
        Toast.makeText(this, userId+"进入房间", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRemoteUserLeaveRoom(String userId, int reason) {
        Toast.makeText(this, userId+"退出房间", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecvRoomMsg(String userId, String cmd, String message) {
        Toast.makeText(this, userId+"发送了房间消息："+cmd+","+message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onResult(ResultData resultData) {
        Toast.makeText(this, "有处理消息", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordStart(String fileName) {
        Toast.makeText(this, "开始录像成"+fileName, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordEnd(String fileName) {
        Toast.makeText(this, "结束录像"+fileName, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onScreenShot(File file) {
        Toast.makeText(this, "截图成功，文件路径"+file.getAbsolutePath(), Toast.LENGTH_LONG).show();
    }
}

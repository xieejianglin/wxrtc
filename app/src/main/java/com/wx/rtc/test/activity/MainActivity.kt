package com.wx.rtc.test.activity

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.wx.rtc.WXRTC
import com.wx.rtc.WXRTCDef
import com.wx.rtc.WXRTCDef.WXRTCVideoEncParam
import com.wx.rtc.WXRTCListener
import com.wx.rtc.WXRTCSnapshotListener
import com.wx.rtc.bean.ResultData
import com.wx.rtc.test.R
import org.webrtc.SurfaceViewRenderer
import java.io.File

class MainActivity : AppCompatActivity(), WXRTCListener {
    companion object {
        private val REQUEST_CODE: Int = 1

        private val PERMISSIONS: Array<String> = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE
        )
    }

    private var mWXRTC: WXRTC = WXRTC.getInstance()
    private val mUserId = "123456789"

    private val localVideoView: SurfaceViewRenderer?  by lazy { findViewById(R.id.localVideo) }
    private val remoteVideoView: SurfaceViewRenderer?  by lazy { findViewById(R.id.remoteVideo) }
    private val snapshotButton: Button?  by lazy { findViewById(R.id.btn_snapshot) }
    private val snapshotView: View?  by lazy { findViewById(R.id.cl_snapshot) }
    private val snapshotImage: ImageView?  by lazy { findViewById(R.id.iv_snapshot) }
    private val closeSnapshotButton: ImageButton?  by lazy { findViewById(R.id.btn_close_snapshot) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        init()

        checkPermission()
    }

    private fun init() {
        mWXRTC.init(this)

        mWXRTC.login("100000", mUserId)

        val param = WXRTCVideoEncParam()
        param.videoMinBitrate = 3000
        param.videoMaxBitrate = 3000
        param.videoFps = 30
        param.videoResolution = WXRTCDef.WXRTC_VIDEO_RESOLUTION_1920_1080
        param.videoResolutionMode = WXRTCDef.WXRTC_VIDEO_RESOLUTION_MODE_PORTRAIT
        mWXRTC.setRTCVideoParam(param)
        mWXRTC.setRTCListener(this)

        val params = WXRTCDef.WXRTCRenderParams()
        params.fillMode = WXRTCDef.WXRTC_VIDEO_RENDER_MODE_FILL
        params.rotation = WXRTCDef.WXRTC_VIDEO_ROTATION_0
        params.mirrorType = WXRTCDef.WXRTC_VIDEO_MIRROR_TYPE_AUTO
        mWXRTC.setLocalRenderParams(params)

        snapshotButton?.setOnClickListener {
            snapshotVideo(mUserId);
        }
        closeSnapshotButton?.setOnClickListener {
            snapshotView?.visibility = GONE
        }
    }

    private fun checkPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED || (
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED) || (
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_PHONE_STATE
                    ) != PackageManager.PERMISSION_GRANTED)
        ) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE)
        } else {
            if (!mWXRTC.isEnterRoom) {
                enterRoom()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (granted in grantResults) {
            if (granted != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        if (!mWXRTC.isEnterRoom) {
            enterRoom()
        }
    }

    private fun enterRoom() {
        mWXRTC.enterRoom("123456")
    }

    private fun snapshotVideo(userId: String) {
        mWXRTC.snapshotVideo(userId, object : WXRTCSnapshotListener {
            override fun onSnapshot(userId: String, file: File) {
                Toast.makeText(this@MainActivity, "${userId}截图成功，文件路径:${file.absolutePath}", Toast.LENGTH_LONG).show()
                snapshotView?.visibility = VISIBLE
                snapshotImage?.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            }
        })
    }

    override fun onError(errCode: Int, errMsg: String) {
        Toast.makeText(this, "$errCode:$errMsg", Toast.LENGTH_LONG).show()
    }

    override fun onLogin() {
        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
        if (!mWXRTC.isEnterRoom) {
            enterRoom()
        }
    }

    override fun onLogout(reason: Int) {
        Toast.makeText(this, "登出成功", Toast.LENGTH_SHORT).show()
    }

    override fun onEnterRoom() {
        Toast.makeText(this, "进入房间", Toast.LENGTH_SHORT).show()
        mWXRTC.startLocalVideo(false, localVideoView)
        mWXRTC.startLocalAudio()
    }

    override fun onExitRoom(reason: Int) {
        Toast.makeText(this, "退出房间", Toast.LENGTH_SHORT).show()
    }

    override fun onRemoteUserEnterRoom(userId: String) {
        Toast.makeText(this, userId + "进入房间", Toast.LENGTH_SHORT).show()
        mWXRTC.startRemoteVideo(userId, remoteVideoView)
    }

    override fun onRemoteUserLeaveRoom(userId: String, reason: Int) {
        Toast.makeText(this, userId + "退出房间", Toast.LENGTH_SHORT).show()
        mWXRTC.stopRemoteVideo(userId)
    }

    override fun onRecvRoomMsg(userId: String, cmd: String, message: String) {
        Toast.makeText(this, "${userId}发送了房间消息：$cmd, $message", Toast.LENGTH_LONG)
            .show()
    }

    override fun onResult(resultData: ResultData) {
        Toast.makeText(this, "收到处理消息$resultData", Toast.LENGTH_SHORT).show()
    }

    override fun onRecordStart(fileName: String) {
        Toast.makeText(this, "开始录像成$fileName", Toast.LENGTH_SHORT).show()
    }

    override fun onRecordEnd(fileName: String) {
        Toast.makeText(this, "结束录像$fileName", Toast.LENGTH_SHORT).show()
    }
}
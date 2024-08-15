package com.wx.rtc.test.activity

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.blankj.utilcode.util.ConvertUtils
import com.google.gson.GsonBuilder
import com.wx.rtc.WXRTC
import com.wx.rtc.WXRTCDef
import com.wx.rtc.WXRTCDef.WXRTCVideoEncParam
import com.wx.rtc.WXRTCListener
import com.wx.rtc.WXRTCSnapshotListener
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

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private var mWXRTC: WXRTC = WXRTC.getInstance()
    private val mUserId = "123456789"
    private var localVideoInLocalView = true
    private var isFrontCamera = false
    private var remoteUserId: String? = null

    private val localVideoView: SurfaceViewRenderer?  by lazy { findViewById(R.id.localVideo) }
    private val remoteVideoView: SurfaceViewRenderer?  by lazy { findViewById(R.id.remoteVideo) }
    private val snapshotButton: Button?  by lazy { findViewById(R.id.btn_snapshot) }
    private val snapshotView: View?  by lazy { findViewById(R.id.cl_snapshot) }
    private val snapshotImage: ImageView?  by lazy { findViewById(R.id.iv_snapshot) }
    private val closeSnapshotButton: ImageButton?  by lazy { findViewById(R.id.btn_close_snapshot) }
    private val changeVideoButton: Button?  by lazy { findViewById(R.id.btn_change_video) }
    private val changeCameraButton: Button?  by lazy { findViewById(R.id.btn_change_camera) }
    private val zoomInCamreaButton: Button?  by lazy { findViewById(R.id.btn_zoomin_camera) }
    private val zoomOutCamreaButton: Button?  by lazy { findViewById(R.id.btn_zoomout_camera) }
    private val changeOrientationButton: Button?  by lazy { findViewById(R.id.btn_change_orientation) }

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
        changeVideoButton?.setOnClickListener {
            if (mWXRTC.isEnterRoom) {
                localVideoInLocalView = !localVideoInLocalView
                if (localVideoInLocalView) {
                    mWXRTC.updateLocalVideo(localVideoView)
                    remoteUserId?.let { userId ->
                        mWXRTC.startRemoteVideo(userId, remoteVideoView)
                    }
                } else {
                    mWXRTC.updateLocalVideo(remoteVideoView)
                    localVideoView?.visibility = View.GONE
                    remoteUserId?.let { userId ->
                        mWXRTC.startRemoteVideo(userId, localVideoView)
                    }
                }
            }
        }
        changeCameraButton?.setOnClickListener {
            if (mWXRTC.isEnterRoom) {
                isFrontCamera = !isFrontCamera
                mWXRTC.switchCamera(isFrontCamera)
            }
        }
        zoomInCamreaButton?.setOnClickListener {
            mWXRTC.cameraZoom += 1
        }
        zoomOutCamreaButton?.setOnClickListener {
            mWXRTC.cameraZoom -= 1
        }
        changeOrientationButton?.setOnClickListener {
            if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                changeOrientationButton?.text = "竖屏"
                localVideoView?.apply {
                    layoutParams.width = ConvertUtils.dp2px(320f)
                    layoutParams.height = ConvertUtils.dp2px(180f)
                }
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                changeOrientationButton?.text = "横屏"
                localVideoView?.apply {
                    layoutParams.width = ConvertUtils.dp2px(180f)
                    layoutParams.height = ConvertUtils.dp2px(320f)
                }
            }
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
            if (mWXRTC.isLogin && !mWXRTC.isEnterRoom) {
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
        if (mWXRTC.isLogin && !mWXRTC.isEnterRoom) {
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

    override fun onBackPressed() {
        mWXRTC.stopLocalVideo()
        mWXRTC.stopLocalAudio()
        mWXRTC.endProcess()
        mWXRTC.exitRoom()
        mWXRTC.logout()
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onError(errCode: Int, errMsg: String) {
        Toast.makeText(this, "$errCode:$errMsg", Toast.LENGTH_LONG).show()
    }

    override fun onLogin() {
        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
        if (mWXRTC.isLogin && !mWXRTC.isEnterRoom) {
            enterRoom()
        }
    }

    override fun onLogout(reason: Int) {
        Toast.makeText(this, "登出成功", Toast.LENGTH_SHORT).show()
    }

    override fun onEnterRoom() {
        Toast.makeText(this, "进入房间", Toast.LENGTH_SHORT).show()
        localVideoInLocalView = false
        mWXRTC.startLocalVideo(isFrontCamera, remoteVideoView)
        mWXRTC.startLocalAudio()

        mWXRTC.startProcess()


        val speaker = WXRTC.getSpeaker(10000L, "测试")
        speaker.spkId = 1000L
        speaker.spkName = "测试2"
    }

    override fun onExitRoom(reason: Int) {
        Toast.makeText(this, "退出房间", Toast.LENGTH_SHORT).show()
    }

    override fun onRemoteUserEnterRoom(userId: String) {
        Toast.makeText(this, userId + "进入房间", Toast.LENGTH_SHORT).show()
        remoteUserId = userId
        mWXRTC.startRemoteVideo(userId, remoteVideoView)

        localVideoInLocalView = true
        mWXRTC.updateLocalVideo(localVideoView)

        val member = WXRTCDef.RoomMemberEntity<String>()
        member.userId = userId
    }

    override fun onRemoteUserLeaveRoom(userId: String, reason: Int) {
        Toast.makeText(this, userId + "退出房间", Toast.LENGTH_SHORT).show()
        remoteUserId = null
        mWXRTC.stopRemoteVideo(userId)
    }

    override fun onRecvRoomMsg(userId: String, cmd: String, message: String) {
        Toast.makeText(this, "${userId}发送了房间消息：$cmd, $message", Toast.LENGTH_LONG)
            .show()
    }

    override fun onProcessResult(processData: WXRTCDef.ProcessData) {
        Toast.makeText(this, "收到处理消息:${gson.toJson(processData)}", Toast.LENGTH_SHORT).show()
        when (processData.rst) {
            WXRTCDef.WXRTC_PROCESS_DATA_RST_NO_RESULT -> {
                Log.d("", "收到处理消息:没有关心的物体")
            }
            WXRTCDef.WXRTC_PROCESS_DATA_RST_DROP -> {
                Log.d("", "收到处理消息:水滴滴速:${processData.drop_speed}")
            }
            WXRTCDef.WXRTC_PROCESS_DATA_RST_THERMOMETER -> {
                Log.d("", "收到处理消息:温度计:${processData.scale}")
            }
            WXRTCDef.WXRTC_PROCESS_DATA_RST_BARCODE -> {
                Log.d("", "收到处理消息:条码:${processData.barcodeDate}")
            }
            WXRTCDef.WXRTC_PROCESS_DATA_RST_QRCODE -> {
                Log.d("", "收到处理消息:二维码:${processData.barcodeDate}")
            }
            WXRTCDef.WXRTC_PROCESS_DATA_RST_SPHYGMOMANOMETER -> {
                Log.d("", "收到处理消息:血压仪:${processData.low_pressure}/${processData.high_pressure}")
            }
            WXRTCDef.WXRTC_PROCESS_DATA_RST_SPINAL_PUNCTURE -> {}
            WXRTCDef.WXRTC_PROCESS_DATA_RST_AROPTP -> {}
            WXRTCDef.WXRTC_PROCESS_DATA_RST_FACE -> {}
            WXRTCDef.WXRTC_PROCESS_DATA_RST_SPEECH_RECOGNITION -> {}
            WXRTCDef.WXRTC_PROCESS_DATA_RST_GESTURE_RECOGNITION -> {}
            WXRTCDef.WXRTC_PROCESS_DATA_RST_OXIMETER -> {}
            WXRTCDef.WXRTC_PROCESS_DATA_RST_WEIGHT_SCALE -> {}
            WXRTCDef.WXRTC_PROCESS_DATA_RST_ECG_MONITOR -> {}
            else -> {}
        }
    }

    override fun onRecordStart(fileName: String) {
        Toast.makeText(this, "开始录像成$fileName", Toast.LENGTH_SHORT).show()
    }

    override fun onRecordEnd(fileName: String) {
        Toast.makeText(this, "结束录像$fileName", Toast.LENGTH_SHORT).show()
    }
}
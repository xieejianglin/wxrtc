package com.wx.rtc.test

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.wx.rtc.RTCVideoContainerView
import com.wx.rtc.WXRTC
import com.wx.rtc.WXRTCDef
import com.wx.rtc.WXRTCDef.WXRTCVideoEncParam
import com.wx.rtc.WXRTCListener

class MainActivity : ComponentActivity(), WXRTCListener {

    companion object {
        private val REQUEST_CODE: Int = 1

        private val PERMISSIONS: Array<String> = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE
        )
    }

    private val mUserId = "12345"
    private val mAppId = "2"
    private val mRoomId = "998839"
    private val mWXRTC = WXRTC.getInstance()

    private var localVideoInLocalView = true
    private var isFrontCamera = true
    private var remoteUserId: String? = null
    private var isScreenCapture = false

    private var localVideoView: RTCVideoContainerView? = null
    private val remoteVideoView: RTCVideoContainerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        mWXRTC.init(this)

        mWXRTC.login(mAppId, mUserId)

        val param = WXRTCVideoEncParam()
        param.videoMinBitrate = 2800
        param.videoMaxBitrate = 3000
        param.videoFps = 30
        param.videoResolution = WXRTCDef.WXRTC_VIDEO_RESOLUTION_1920_1080
        param.videoResolutionMode = WXRTCDef.WXRTC_VIDEO_RESOLUTION_MODE_LANDSCAPE
        mWXRTC.setRTCVideoParam(param)
        mWXRTC.setRTCListener(this)

//        mWXRTC.startLocalPreview(isFrontCamera, if (localVideoInLocalView) localVideoView else remoteVideoView)
//        mWXRTC.startLocalAudio()

        val params = WXRTCDef.WXRTCRenderParams()
        params.fillMode = WXRTCDef.WXRTC_VIDEO_RENDER_MODE_FIT
        params.rotation = WXRTCDef.WXRTC_VIDEO_ROTATION_0
        params.mirrorType = WXRTCDef.WXRTC_VIDEO_MIRROR_TYPE_AUTO
        mWXRTC.setLocalRenderParams(params)

        checkPermission()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GreetingView(Greeting().greet())
                    AndroidView(
                        factory = {
                            localVideoView =  RTCVideoContainerView(it)
                            localVideoView as View
                        },
                        update = {

                        },
                        modifier = Modifier.fillMaxSize()
                    )
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
        requestCode: Int, permissions: Array<out String?>, grantResults: IntArray, deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
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
        mWXRTC.enterRoom(mRoomId)
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
        finish()
    }

    override fun onEnterRoom() {
        Toast.makeText(this, "进入房间", Toast.LENGTH_SHORT).show()
        //        localVideoInLocalView = false
        mWXRTC.startLocalPreview(isFrontCamera, if (localVideoInLocalView) localVideoView else remoteVideoView)
        mWXRTC.startLocalAudio()

        //        mWXRTC.startProcess()
        //        mWXRTC.startAsr(mAppId, null)
        //        mWXRTC.startRecord("222222", "{\"order_id\":\"222222\"}")
        //        mWXRTC.startRecord()

        val speaker = WXRTC.getSpeaker(10000L, "测试")
        speaker.spkId = 1000L
        speaker.spkName = "测试2"

//        lifecycleScope.launch {
//            while (mWXRTC.cameraZoom < mWXRTC.cameraMaxZoom) {
//                delay(3.seconds)
//                mWXRTC.cameraZoom += 1
//            }
//            delay(3.seconds)
//            mWXRTC.cameraZoom = 0
//        }
    }

    override fun onExitRoom(reason: Int) {
        Toast.makeText(this, "退出房间", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun GreetingView(text: String) {
    Text(text = text)
}

@Preview
@Composable
fun DefaultPreview() {
    MyApplicationTheme {
        GreetingView("Hello, Android!")
    }
}

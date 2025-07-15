package com.wx.rtc.rtc

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log


internal class WXScreenCaptureAssistantActivity : Activity() {
    companion object {
        private const val TAG: String = "WXScreenCaptureAssistantActivity"
        private const val REQUEST_CODE: Int = 100
    }
    private var mMediaProjectionManager: MediaProjectionManager? = null

    @SuppressLint("LongLogTag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate $this")
        this.requestWindowFeature(1)
        this.mMediaProjectionManager = this.applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mMediaProjectionManager!!.createScreenCaptureIntent()

        try {
            this.startActivityForResult(intent, REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Start permission activity failed. $e")
            this.finish()
        }
    }

    @SuppressLint("LongLogTag")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG, "onActivityResult $this")

        if (data != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(this, WXScreenCaptureAssistantService::class.java)
            intent.putExtra("resultCode", resultCode)
            intent.putExtra("data", data)
            startForegroundService(intent)
        }

        PeerConnectionClient.mediaProjectionPermissionRequest = true
        PeerConnectionClient.mediaProjectionPermissionResultData = data

        this.finish()
    }

    @SuppressLint("LongLogTag")
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy $this")
    }
}
package com.wx.rtc.rtc

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log

internal class WXScreenCaptureAssistantActivity : Activity() {
    companion object {
        private val TAG: String = "WXScreenCaptureAssistantActivity"
        private val REQUEST_CODE: Int = 100
    }
    private var mMediaProjectionManager: MediaProjectionManager? = null

    @SuppressLint("LongLogTag")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate $this")
        this.requestWindowFeature(1)
        this.mMediaProjectionManager = this.applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mMediaProjectionManager!!.createScreenCaptureIntent()

        try {
            this.startActivityForResult(intent, REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Start permission activity failed. $e")
//                    bg.a(this).a(null as MediaProjection?)
            this.finish()
        }
    }

    @SuppressLint("LongLogTag")
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG, "onActivityResult $this")

//        var mediaProjection: MediaProjection? = null
//
//        try {
//            mediaProjection = mMediaProjectionManager!!.getMediaProjection(resultCode, data!!)
//        } catch (e: Exception) {
//            Log.e(TAG, "onActivityResult mMediaProjectionManager.getMediaProjection fail.", e)
//        }

//                bg.a(this).a(mediaProjection)
        PeerConnectionClient.mediaProjectionPermissionResultData = data
        this.finish()
    }

    @SuppressLint("LongLogTag")
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy $this")
    }
}
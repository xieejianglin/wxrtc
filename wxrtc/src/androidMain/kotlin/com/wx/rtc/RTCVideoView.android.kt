package com.wx.rtc

import android.graphics.Bitmap
import com.wx.rtc.WXRTCDef.WXRTCRenderParams
import com.wx.rtc.utils.luban.Luban
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.EglRenderer
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

actual typealias RTCVideoView = SurfaceViewRenderer

actual fun RTCVideoView.setRendererRenderParams(params: WXRTCRenderParams, isScreenCapture: Boolean, isLocalRenderer: Boolean, useFrontCamera: Boolean) {
    if (params.fillMode == WXRTCDef.WXRTC_VIDEO_RENDER_MODE_FILL) {
        this.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
    } else if (params.fillMode == WXRTCDef.WXRTC_VIDEO_RENDER_MODE_FIT) {
        this.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
    }
    if (params.mirrorType == WXRTCDef.WXRTC_VIDEO_MIRROR_TYPE_AUTO) {
        if (!isScreenCapture && isLocalRenderer) {
            this.setMirror(useFrontCamera)
        } else {
            this.setMirror(false)
        }
    } else if (params.mirrorType == WXRTCDef.WXRTC_VIDEO_MIRROR_TYPE_ENABLE) {
        this.setMirror(true)
    } else if (params.mirrorType == WXRTCDef.WXRTC_VIDEO_MIRROR_TYPE_DISABLE) {
        this.setMirror(false)
    }
}

actual fun RTCVideoView.snapshotVideo(context: PlatformContext, completion: (String) -> Unit) {

    this.addFrameListener(object : EglRenderer.FrameListener {
        override fun onFrame(bitmap: Bitmap) {
            this@snapshotVideo.post(Runnable {
                this@snapshotVideo.removeFrameListener(this)

                CoroutineScope(Dispatchers.IO).launch {
                    flowOf(bitmap).map {
                        saveImage(context, it)
                    }.map {
                        Luban.with(context).ignoreBy(100).get(it)
                    }.collect() {
                        withContext(Dispatchers.Main) {
                            completion(it.absolutePath)
                        }
                    }
                }
            })
        }
    }, 1f)
}

private fun saveImage(context: PlatformContext, bitmap: Bitmap): String {
    val filePath = context.applicationContext.externalCacheDir!!.parentFile.absolutePath + File.separator + "shot.jpg"

    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)

    try {
        val fos = FileOutputStream(filePath)
        fos.write(stream.toByteArray())
        fos.flush()
        fos.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }

    return filePath
}
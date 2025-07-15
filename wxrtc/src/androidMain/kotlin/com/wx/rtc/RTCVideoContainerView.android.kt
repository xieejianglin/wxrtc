package com.wx.rtc

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.wx.rtc.WXRTCDef.WXRTCRenderParams
import com.wx.rtc.rtc.PeerConnectionClient.Companion.eglBase
import org.webrtc.SurfaceViewRenderer

actual class RTCVideoContainerView: LinearLayout {
    actual var params: WXRTCRenderParams = WXRTCRenderParams()
    actual var isScreenCapture: Boolean = false
    actual var isLocalRenderer: Boolean = true
    actual var useFrontCamera: Boolean = true

    private var videoView: RTCVideoView? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        val videoView = SurfaceViewRenderer(context).apply {
            init(eglBase.eglBaseContext, null)
            setEnableHardwareScaler(true /* enabled */)
        }
        addVideoView(videoView)
    }

    actual fun setVisible(visible: Boolean) {
        if (visible) {
            this.visibility = VISIBLE
        } else {
            this.visibility = GONE
        }
    }

    actual fun getVideoView(): RTCVideoView? {
        return this.videoView
    }

    actual fun addVideoView(videoView: RTCVideoView?) {
        this.videoView = videoView
        videoView?.let {
            addView(videoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            videoView.setRendererRenderParams(params, isScreenCapture, isLocalRenderer, useFrontCamera)
        }
    }

    actual fun removeVideoView() {
        videoView?.let {
            it.clearImage()
            removeView(it)
        }
    }

    actual fun setRendererRenderParams(params: WXRTCRenderParams, isScreenCapture: Boolean, isLocalRenderer: Boolean, useFrontCamera: Boolean) {
        this.params = params
        this.isScreenCapture = isScreenCapture
        this.isLocalRenderer = isLocalRenderer
        this.useFrontCamera = useFrontCamera
        this.videoView?.setRendererRenderParams(params, isScreenCapture, isLocalRenderer, useFrontCamera)
    }
}
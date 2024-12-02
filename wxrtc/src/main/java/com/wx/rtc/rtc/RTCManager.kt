package com.wx.rtc.rtc

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.text.TextUtils
import android.util.Log
import com.wx.rtc.Config
import com.wx.rtc.Config.RECONNECT_MAX_NUM
import com.wx.rtc.Config.RECONNECT_MILLIS
import com.wx.rtc.WXRTCDef
import com.wx.rtc.WXRTCDef.WXRTCRenderParams
import com.wx.rtc.WXRTCDef.WXRTCVideoEncParam
import com.wx.rtc.rtc.PeerConnectionClient.PeerConnectionEvents
import com.wx.rtc.rtc.PeerConnectionClient.PeerConnectionParameters
import com.wx.rtc.utils.RTCUtils.getVideoResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.IceCandidate
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.SessionDescription
import org.webrtc.StatsReport
import org.webrtc.SurfaceViewRenderer
import top.zibin.luban.Luban
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class RTCManager : PeerConnectionEvents {
    private var publishPCClient: PeerConnectionClient? = null
    private val callStartedTimeMs: Long = 0
    private var webRTCReconnectNum = 0
    private var publishUserId: String? = null
    private var publishUrl: String? = null
    private var unpublishUrl: String? = null
    private var useFrontCamera = false
    private var publishAudioSendEnabled = false
    private var publishVideoSendEnabled = false
    private var publishVideoMute = false
    private var publishAudioMute = false
    private var isScreenCapture = false
    private var publishRenderParams: WXRTCRenderParams = WXRTCRenderParams()
    private var remoteVideoAllMute = false
    private var remoteAudioAllMute = false
    private var remoteAudioAllVolume = 0
    private val eglBase: EglBase = EglBase.create()
    private lateinit var mContext: Context
    private var mVideoEncParam: WXRTCVideoEncParam = WXRTCVideoEncParam()

    private val localProxyVideoSink = ProxyVideoSink()

    private val pcManagers: MutableList<PeerConnectionManager> = ArrayList()
    private var localRenderer: SurfaceViewRenderer? = null
    private var mRTCListener: RTCListener? = null
    private var mStartPublish = false


    fun init(context: Context) {
        this.mContext = context
    }

    fun setRTCVideoParam(param: WXRTCVideoEncParam) {
        this.mVideoEncParam = param
        publishPCClient?.let { client ->
            client.setVideoEncParam(param)
        }
    }

    fun setRTCListener(listener: RTCListener?) {
        this.mRTCListener = listener
    }

    fun startPublish(publishUrl: String, userId: String) {
        if (mStartPublish && publishPCClient != null) {
            return
        }
        this.publishUrl = publishUrl
        this.publishUserId = userId
        mStartPublish = true

        publishPCClient = PeerConnectionClient(
            mContext!!.applicationContext,
            eglBase, userId, publishUrl, true,
            this
        ).apply {
            val options = PeerConnectionFactory.Options()

            options.networkIgnoreMask = 0
            //        options.disableEncryption = true;
//        options.disableNetworkMonitor = true;
//            this.setLocalVideoTrackEnabled(publishVideoSendEnabled)
//            this.setLocalAudioTrackEnabled(publishAudioSendEnabled)
            this.setRemoteVideoTrackEnabled(false)
            this.createPeerConnectionFactory(options)

//            localProxyVideoSink.setTarget(userId, localRenderer)
            setLocalRenderer(localRenderer)

            setRTCVideoParam(mVideoEncParam)

            setLocalRenderParams(publishRenderParams)

            muteLocalVideo(publishVideoMute)
            muteLocalAudio(publishAudioMute)

            this.startCall(localProxyVideoSink, null)
        }


    }

    fun setUnpublishUrl(unpublishUrl: String) {
        this.unpublishUrl = unpublishUrl
        publishPCClient?.unpublishUrl = unpublishUrl
    }

    fun stopAllPC() {
        publishPCClient?.isNeedReconnect = false

        stopPublish()

        stopAllPull()
    }

    fun startOnePull(pullUrl: String, userId: String) {
        var pcm = getPeerConnectionManagerByUserId(userId)

        val pc = startPull(userId, pullUrl)

        if (pcm == null) {
            pcm = PeerConnectionManager()
            pcm.userId = userId
            pcManagers.add(pcm)

            if (remoteVideoAllMute) {
                pc.setRemoteAudioTrackEnabled(remoteVideoAllMute)
            }
            if (remoteAudioAllMute) {
                pc.setRemoteAudioTrackEnabled(remoteAudioAllMute)
            }
            //            pc.setRemoteAudioTrackVolume(remoteAudioAllVolume);
        } else {
            pcm.client?.close()

            if (pcm.videoRecvEnabled) {
                pcm.videoSink?.let { videoSink ->
                    videoSink.target?.let { target ->
                        startRemoteVideo(userId, target as SurfaceViewRenderer)
                    }
                }
            }
            if (pcm.videoRecvMute) {
                pc.setRemoteVideoTrackEnabled(pcm.videoRecvMute)
            }
            if (pcm.audioRecvMute) {
                pc.setRemoteAudioTrackEnabled(pcm.audioRecvMute)
            }

            //            pc.setRemoteAudioTrackVolume(pcm.audioVolume);
        }
        pcm.sendSdpUrl = pullUrl
        pcm.client = pc

        if (pcm.videoSink == null) {
            pcm.videoSink = ProxyVideoSink()
        }

        pc.isNeedReconnect = pcm.needReconnect
        pc.startCall(null, pcm.videoSink)
    }

    private fun setLocalRenderer(renderer: SurfaceViewRenderer?) {
        localProxyVideoSink.setTarget(publishUserId, renderer)

        if (renderer != null) {
            if (this.localRenderer != null && this.localRenderer === renderer) {
                return
            }

            for (pcm in this.pcManagers) {
                if (pcm.videoSink != null && pcm.videoSink!!.target != null && pcm.videoSink!!.target === renderer) {
                    pcm.videoSink!!.setTarget(pcm.userId, null)
                }
            }
        }

        renderer?.apply {
            if (!isInited || isReleased) {
                init(eglBase.eglBaseContext, null)
                setEnableHardwareScaler(true /* enabled */)
            }
        }

        this.localRenderer = renderer

        setLocalRenderParams(publishRenderParams)
    }

    fun startLocalVideo(frontCamera: Boolean, renderer: SurfaceViewRenderer?) {
        useFrontCamera = frontCamera
        publishVideoSendEnabled = true
        publishPCClient?.startVideoSource(frontCamera)
        setLocalRenderer(renderer)
    }

    fun updateLocalVideo(renderer: SurfaceViewRenderer?) {
        setLocalRenderer(renderer)
    }

    fun stopLocalVideo() {
        publishVideoSendEnabled = false
        publishPCClient?.stopVideoSource()
        setLocalRenderer(null)
    }

    fun muteLocalVideo(mute: Boolean) {
        publishVideoMute = mute
        publishPCClient?.setLocalVideoTrackEnabled(!mute)
    }

    fun startRemoteVideo(userId: String, renderer: SurfaceViewRenderer?) {
        if (localRenderer != null && localRenderer === renderer) {
            setLocalRenderer(null)
        }

        if (renderer != null) {
            for (pcm in this.pcManagers) {
                if (pcm.userId != userId && pcm.videoSink != null && pcm.videoSink!!.target != null && pcm.videoSink!!.target === renderer) {
                    pcm.videoSink!!.setTarget(pcm.userId, null)
                }
            }
        }

        var pcm = getPeerConnectionManagerByUserId(userId)

        if (pcm != null) {
            if (pcm.videoSink != null && pcm.videoSink!!.target != null && renderer != null && pcm.videoSink!!.target === renderer) {
                return
            }

            renderer?.apply {
                if (!isInited || isReleased) {
                    init(eglBase.eglBaseContext, null)
                    setEnableHardwareScaler(true /* enabled */)
                }
            }

            if (pcm.videoSink == null) {
                pcm.videoSink = ProxyVideoSink()
            }
            pcm.videoSink!!.setTarget(userId, renderer)
        } else {
            pcm = PeerConnectionManager()
            pcm.userId = userId

            renderer?.apply {
                if (!isInited || isReleased) {
                    init(eglBase.eglBaseContext, null)
                    setEnableHardwareScaler(true /* enabled */)
                }
            }

            val remoteVideoSink = ProxyVideoSink()
            remoteVideoSink.setTarget(userId, renderer)

            pcm.videoSink = remoteVideoSink

            pcManagers.add(pcm)
        }

        pcm.videoRecvEnabled = true

        if (pcm.renderParams == null) {
            pcm.renderParams = WXRTCRenderParams()
        }

        if (!pcm.videoRecvMute) {
            pcm.client?.setRemoteVideoTrackEnabled(true)
        }

        renderer?.let {
            setRendererRenderParams(false, it, pcm.renderParams!!)
        }
    }

    fun updateRemoteVideo(userId: String, renderer: SurfaceViewRenderer?) {
        if (localRenderer != null && localRenderer === renderer) {
            setLocalRenderer(null)
        }

        if (renderer != null) {
            for (pcm in this.pcManagers) {
                if (pcm.userId != userId && pcm.videoSink != null && pcm.videoSink!!.target != null && pcm.videoSink!!.target === renderer) {
                    pcm.videoSink!!.setTarget(pcm.userId, null)
                }
            }
        }

        var pcm = getPeerConnectionManagerByUserId(userId)

        if (pcm != null) {
            if (pcm.videoSink != null && pcm.videoSink!!.target != null && renderer != null && pcm.videoSink!!.target === renderer) {
                return
            }

            renderer?.apply {
                if (!isInited || isReleased) {
                    init(eglBase.eglBaseContext, null)
                    setEnableHardwareScaler(true /* enabled */)
                }
            }

            if (pcm.videoSink == null) {
                pcm.videoSink = ProxyVideoSink()
            }
            pcm.videoSink!!.setTarget(userId, renderer)

            if (pcm.renderParams == null) {
                pcm.renderParams = WXRTCRenderParams()
            }

            renderer?.let {
                setRendererRenderParams(false, it, pcm.renderParams!!)
            }
        }
    }

    fun stopRemoteVideo(userId: String) {
        getPeerConnectionManagerByUserId(userId)?.let { pcm ->
            pcm.videoRecvEnabled = false
            pcm.client?.setRemoteVideoTrackEnabled(false)
            pcm.videoSink?.release()
        }
    }

    fun stopAllRemoteVideo() {
        for (pcm in pcManagers) {
            pcm.videoRecvEnabled = false
            pcm.client?.setRemoteVideoTrackEnabled(false)
            pcm.videoSink?.release()
        }
    }

    fun muteRemoteVideo(userId: String, mute: Boolean) {
        var pcm = getPeerConnectionManagerByUserId(userId)
        if (pcm != null) {
            pcm.client?.setRemoteVideoTrackEnabled(!mute)
        } else {
            pcm = PeerConnectionManager()
            pcm.userId = userId
            pcManagers.add(pcm)
        }
        pcm.videoRecvMute = mute
    }

    fun muteAllRemoteVideo(mute: Boolean) {
        remoteVideoAllMute = mute
        for (pcm in pcManagers) {
            pcm.videoRecvMute = mute
            pcm.client?.setRemoteVideoTrackEnabled(!mute)
        }
    }

    private fun setRendererRenderParams(
        isLocalrenderer: Boolean,
        renderer: SurfaceViewRenderer,
        params: WXRTCRenderParams
    ) {
        if (params.fillMode == WXRTCDef.WXRTC_VIDEO_RENDER_MODE_FILL) {
            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        } else if (params.fillMode == WXRTCDef.WXRTC_VIDEO_RENDER_MODE_FIT) {
            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        }
        if (params.mirrorType == WXRTCDef.WXRTC_VIDEO_MIRROR_TYPE_AUTO) {
            if (!isScreenCapture && isLocalrenderer) {
                renderer.setMirror(useFrontCamera)
            } else {
                renderer.setMirror(false)
            }
        } else if (params.mirrorType == WXRTCDef.WXRTC_VIDEO_MIRROR_TYPE_ENABLE) {
            renderer.setMirror(true)
        } else if (params.mirrorType == WXRTCDef.WXRTC_VIDEO_MIRROR_TYPE_DISABLE) {
            renderer.setMirror(false)
        }
    }

    fun setLocalRenderParams(params: WXRTCRenderParams) {
        publishRenderParams = params
        localRenderer?.let {
            setRendererRenderParams(true, it, params)
        }
    }

    fun setRemoteRenderParams(userId: String, params: WXRTCRenderParams) {
        val pcm = getPeerConnectionManagerByUserId(userId)
        if (pcm != null) {
            pcm.renderParams = params
            pcm.videoSink?.let { videoSink ->
                videoSink.target
            }?.let { renderer ->
                setRendererRenderParams(false, renderer as SurfaceViewRenderer, params)
            }
        } else {
            val manager = PeerConnectionManager()
            manager.userId = userId
            manager.renderParams = params

            pcManagers.add(manager)
        }
    }

    fun startLocalAudio() {
        publishAudioSendEnabled = true
        publishPCClient?.startAudioCapture()
    }

    fun stopLocalAudio() {
        publishAudioSendEnabled = false
        publishPCClient?.stopAudioCapture()
    }

    fun muteLocalAudio(mute: Boolean) {
        publishAudioMute = mute
        publishPCClient?.setLocalAudioTrackEnabled(!mute)
    }

    fun muteRemoteAudio(userId: String, mute: Boolean) {
        var pcm = getPeerConnectionManagerByUserId(userId)

        if (pcm != null) {
            pcm.client?.setRemoteAudioTrackEnabled(!mute)
        } else {
            pcm = PeerConnectionManager()
            pcm.userId = userId
            pcManagers.add(pcm)
        }
        pcm.audioRecvMute = mute
    }

    fun muteAllRemoteAudio(mute: Boolean) {
        remoteAudioAllMute = mute
        for (pcm in pcManagers) {
            pcm.audioRecvMute = mute
            pcm.client?.setRemoteAudioTrackEnabled(!mute)
        }
    }

    fun setRemoteAudioVolume(userId: String, volume: Int) {
        var pcm = getPeerConnectionManagerByUserId(userId)

        if (pcm != null) {
            pcm.client?.setRemoteAudioTrackVolume(volume)
        } else {
            pcm = PeerConnectionManager()
            pcm.userId = userId
            pcManagers.add(pcm)
        }
        pcm.audioVolume = volume.toFloat()
    }

    fun setAllRemoteAudioVolume(volume: Int) {
        remoteAudioAllVolume = volume
        for (pcm in pcManagers) {
            pcm.audioVolume = volume.toFloat()
            pcm.client?.setRemoteAudioTrackVolume(volume)
        }
    }

    fun startScreenCapture(encParam: WXRTCVideoEncParam?, renderer: SurfaceViewRenderer?) {
        publishVideoSendEnabled = true
        isScreenCapture = true
        publishPCClient?.startScreenCapture()
        setLocalRenderer(renderer)
        encParam?.let {
            publishPCClient?.let { client ->
                client.setScreenEncParamCapture(it)
            }
        } ?: {
            publishPCClient?.let { client ->
                client.setScreenEncParamCapture(mVideoEncParam)
            }
        }
    }

    fun stopScreenCapture() {
        publishVideoSendEnabled = false
        isScreenCapture = false
        publishPCClient?.stopScreenCapture()
        setLocalRenderer(null)
    }

    fun pauseScreenCapture() {
        publishPCClient?.pauseScreenCapture()
    }

    fun resumeScreenCapture() {
        publishPCClient?.resumeScreenCapture()
    }

    val isFrontCamera: Boolean
        get() {
            if (publishPCClient != null) {
                return publishPCClient!!.isFrontCamera
            }
            return false
        }

    val isCameraZoomSupported: Boolean
        get() {
            if (publishPCClient != null) {
                return publishPCClient!!.isCameraZoomSupported
            }
            return false
        }

    val cameraMaxZoom: Int
        get() {
            if (publishPCClient != null) {
                return publishPCClient!!.cameraMaxZoom
            }
            return 0
        }

    var cameraZoom: Int
        get() {
            if (publishPCClient != null) {
                return publishPCClient!!.cameraZoom
            }
            return 0
        }
        set(value) {
            if (publishPCClient != null) {
                publishPCClient!!.cameraZoom = value
            }
        }

    fun snapshotLocalVideo(userId: String): Boolean {
        return snapshotVideo(userId, localRenderer)
    }

    fun snapshotRemoteVideo(userId: String): Boolean {
        val pcm = getPeerConnectionManagerByUserId(userId)
        if (pcm?.videoSink == null || pcm.videoSink!!.target == null || pcm.client == null) {
            return false
        }
        return snapshotVideo(userId, pcm.videoSink!!.target as SurfaceViewRenderer?)
    }

    private fun snapshotVideo(userId: String, renderer: SurfaceViewRenderer?): Boolean {
        if (renderer == null) {
            return false
        }
        renderer.addFrameListener(object : EglRenderer.FrameListener {
            override fun onFrame(bitmap: Bitmap) {
                renderer.post(Runnable {
                    renderer.removeFrameListener(this)

                    CoroutineScope(Dispatchers.IO).launch {
                        flowOf(bitmap).map {
                            saveImage(it)
                        }.map {
                            Luban.with(mContext).ignoreBy(100).get(it)
                        }.collect() {
                            withContext(Dispatchers.Main) {
                                mRTCListener?.onSnapshot(userId, it)
                            }
                        }
                    }
                })
            }
        }, 1f)
        return true
    }

    fun destory() {
        publishPCClient?.isNeedReconnect = false
        stopPublish()
        setLocalRenderer(null)
        stopAllPull()

        publishPCClient = null

        eglBase.release()
    }

    private fun saveImage(bitmap: Bitmap): String {
        val filePath = mContext!!.applicationContext.externalCacheDir!!.parentFile.absolutePath + File.separator + "shot.jpg"

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

    private fun startPull(userId: String, streamUrl: String): PeerConnectionClient {
        return PeerConnectionClient(
            mContext!!.applicationContext,
            eglBase, userId, streamUrl, false,
            this
        ).apply {
            val options = PeerConnectionFactory.Options()

            options.networkIgnoreMask = 0
            //        options.disableEncryption = true;
//        options.disableNetworkMonitor = true;
//            this.setLocalVideoTrackEnabled(false)
//            this.setLocalAudioTrackEnabled(false)
            this.createPeerConnectionFactory(options)
        }
    }

    private fun stopPublish() {
        Log.e(TAG, "unpublish onResponse")
        stopLocalVideo()
        stopScreenCapture()
        stopLocalAudio()

        localProxyVideoSink.release()

        mStartPublish = false
        publishPCClient?.let { client ->
            client.isNeedReconnect = false
            client.close()

            publishPCClient = null
        }
    }

    fun stopPull(userId: String) {
        getPeerConnectionManagerByUserId(userId)?.let { pcm ->
            pcm.needReconnect = false
            pcm.videoSink?.release()
            pcm.client?.let { client ->
                stopPull(client)
                pcm.client = null
            }
        }
    }

    private fun stopPull(pc: PeerConnectionClient) {
        pc.isNeedReconnect = false
        pc.close()
    }

    private fun stopAllPull() {
        stopAllRemoteVideo()

        for (pcm in pcManagers) {
            pcm.videoSink?.release()
            pcm.needReconnect = false
            pcm.client?.let { client ->
                client.isNeedReconnect = false
                client.close()
                pcm.client = null
            }
        }
        pcManagers.clear()
    }

    override fun onLocalDescription(pc: PeerConnectionClient, sdp: SessionDescription) {
    }

    override fun onIceCandidate(pc: PeerConnectionClient, candidate: IceCandidate) {

    }

    override fun onIceCandidatesRemoved(
        pc: PeerConnectionClient,
        candidates: Array<IceCandidate>
    ) {
    }

    override fun onIceGatheringComplete(pc: PeerConnectionClient, sdp: SessionDescription) {
    }

    override fun onIceConnected(pc: PeerConnectionClient) {
        val delta = System.currentTimeMillis() - callStartedTimeMs
    }

    override fun onIceDisconnected(pc: PeerConnectionClient) {
    }

    override fun onConnected(pc: PeerConnectionClient) {
        val delta = System.currentTimeMillis() - callStartedTimeMs
        CoroutineScope(Dispatchers.Main).launch {
            publishPCClient?.let {
                if (pc == it) {
                    pc.setVideoEncParam(mVideoEncParam)
                    if (publishVideoSendEnabled) {
                        startLocalVideo(useFrontCamera, localRenderer)
                    } else {
                        stopLocalVideo()
                    }
                    mRTCListener?.onConnected()
                    if (publishAudioSendEnabled) {
                        startLocalAudio()
                    } else {
                        stopLocalAudio()
                    }
                }
            }
        }
    }

    override fun onDisconnected(pc: PeerConnectionClient) {
    }

    override fun onPeerConnectionClosed(pc: PeerConnectionClient) {
    }

    override fun onPeerConnectionStatsReady(
        pc: PeerConnectionClient,
        reports: Array<StatsReport>
    ) {
    }

    override fun onPeerConnectionError(pc: PeerConnectionClient, description: String) {
    }

    override fun onDataChannelMessage(pc: PeerConnectionClient, message: String) {
    }

    fun switchPublishCamera(frontCamera: Boolean) {
        if (useFrontCamera != frontCamera) {
            useFrontCamera = frontCamera
            publishPCClient?.switchCamera()
            setLocalRenderParams(publishRenderParams)
        }
    }

    private fun getPeerConnectionManagerByUserId(userId: String): PeerConnectionManager? {
        for (pcm in pcManagers) {
            if (userId == pcm.userId) {
                return pcm
            }
        }
        return null
    }

    private fun getPeerConnectionManagerByStreamUrl(url: String): PeerConnectionManager? {
        for (pcm in pcManagers) {
            if (url == pcm.sendSdpUrl) {
                return pcm
            }
        }
        return null
    }

    private fun getPeerConnectionManagerByVideoSink(videoSink: ProxyVideoSink): PeerConnectionManager? {
        for (pcm in pcManagers) {
            if (videoSink == pcm.videoSink) {
                return pcm
            }
        }
        return null
    }

    private fun getPeerConnectionManagerByPc(pc: PeerConnectionClient?): PeerConnectionManager? {
        for (pcm in pcManagers) {
            if (pc == pcm.client) {
                return pcm
            }
        }
        return null
    }

    companion object {
        private val TAG: String = RTCManager::class.java.name
    }
}

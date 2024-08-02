package com.wx.rtc.rtc

import android.content.Context
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
    private var publishUrl: String? = null
    private var unpublishUrl: String? = null
    private var useFrontCamera = false
    private var publishAudioSendEnabled = false
    private var publishVideoSendEnabled = false
    private var publishVideoMute = false
    private var publishAudioMute = false
    private var publishRenderParams: WXRTCRenderParams? = null
    private var remoteVideoAllMute = false
    private var remoteAudioAllMute = false
    private var remoteAudioAllVolume = 0
    private val eglBase: EglBase = EglBase.create()
    private var mContext: Context? = null
    private var mVideoEncParam: WXRTCVideoEncParam = WXRTCVideoEncParam()
    private var mPublishSendOfferJob: Job? = null
    private var mRTCReconnectJob: Job? = null
    private var mDeletePublishJob: Job? = null

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
            client.setParameters(param)
            val size = getVideoResolution(param.videoResolution)
            client.changeVideoSource(size.width, size.height, param.videoFps)
            client.setVideoBitrate(param.videoMinBitrate, param.videoMaxBitrate)
        }
    }

    fun setRTCListener(listener: RTCListener?) {
        this.mRTCListener = listener
    }

    fun startPublish(publishUrl: String, userId: String) {
        this.publishUrl = publishUrl
        mStartPublish = true

        val size = getVideoResolution(mVideoEncParam.videoResolution)

        val peerConnectionParameters =
            PeerConnectionParameters(
                true, true, false, false,
                false, false, size.width, size.height, mVideoEncParam.videoFps,
                mVideoEncParam.videoMaxBitrate, "H264 Baseline",
                true,
                true,
                0, "opus",
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false, null
            )

        publishPCClient = PeerConnectionClient(
            mContext!!.applicationContext,
            eglBase,
            peerConnectionParameters,
            this
        )
        val options = PeerConnectionFactory.Options()

        options.networkIgnoreMask = 0
        //        options.disableEncryption = true;
//        options.disableNetworkMonitor = true;
        publishPCClient!!.setLocalVideoTrackEnabled(publishVideoSendEnabled)
        publishPCClient!!.setRemoteVideoTrackEnabled(false)
        publishPCClient!!.setLocalAudioTrackEnabled(publishAudioSendEnabled)
        publishPCClient!!.createPeerConnectionFactory(options)

        localProxyVideoSink.setTarget(userId, localRenderer)

        setRTCVideoParam(mVideoEncParam)

        publishRenderParams?.let {
            setLocalRenderParams(it)
        }

        muteLocalVideo(publishVideoMute)
        muteLocalAudio(publishAudioMute)

        publishPCClient!!.startCall(localProxyVideoSink, null)
    }

    fun setUnpublishUrl(unpublishUrl: String) {
        this.unpublishUrl = unpublishUrl
    }

    fun stopAllPC() {
        mStartPublish = false

        publishPCClient?.isNeedReconnect = false

        unpublish()

        stopAllPull()
    }

    fun startOnePull(pullUrl: String, userId: String) {
        var pcm = getPeerConnectionManagerByUserId(userId)

        val pc = startPull(userId, pullUrl, true, true)

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
                pcm.videoSink?.let {
                    startRemoteVideo(userId, pcm.videoSink?.target as SurfaceViewRenderer)
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

        pc.startCall(null, pcm.videoSink)
    }

    private fun reconnect(pc: PeerConnectionClient) {
        if (webRTCReconnectNum < Config.RECONNECT_MAX_NUM) {
            Log.d(TAG, "webrtc reconnect...")
            if (mRTCReconnectJob?.isActive == true) {
                mRTCReconnectJob!!.cancel()
            }
            mRTCReconnectJob = CoroutineScope(Dispatchers.IO).launch {
                delay(RECONNECT_MILLIS)
                withContext(Dispatchers.Main) {
                    if (pc == publishPCClient) {
                        init(mContext!!)
                    } else {
                        val audioRecvEnabled = pc.isAudioRecvEnabled
                        val videoRecvEnabled = pc.isVideoRecvEnabled

                        val pcm = getPeerConnectionManagerByPc(pc) ?: return@withContext

                        val streamUrl = pcm.sendSdpUrl!!
                        val userId = pcm.userId!!

                        pcm.videoSink?.release()

                        pcManagers.remove(pcm)

                        startPull(userId, streamUrl, audioRecvEnabled, videoRecvEnabled)
                    }
                    webRTCReconnectNum++
                }
            }
        } else {
            Log.e(TAG, "webSocket reconnect fail, reconnect num more than $RECONNECT_MAX_NUM, please check url!")
        }
    }

    private fun setLocalRenderer(renderer: SurfaceViewRenderer?) {
        localProxyVideoSink.target = renderer

        if (this.localRenderer != null && (renderer != null) && (this.localRenderer === renderer)) {
            return
        }

        localRenderer?.release()
        localRenderer = null

        renderer?.let {
            it.release()
            it.init(eglBase.eglBaseContext, null)
            //            it.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            it.setEnableHardwareScaler(false /* enabled */)
        }

        this.localRenderer = renderer
        if (publishRenderParams == null) {
            publishRenderParams = WXRTCRenderParams()
        }
        publishRenderParams?.let {
            setLocalRenderParams(it)
        }
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
        if (localRenderer != null && renderer === localRenderer) {
            setLocalRenderer(null)
        }

        var pcm = getPeerConnectionManagerByUserId(userId)

        if (pcm != null) {
            if (pcm.videoSink != null && pcm.videoSink!!.target != null && renderer != null && pcm.videoSink!!.target === renderer) {
                return
            }

            renderer?.let {
                it.release()
                it.init(eglBase.eglBaseContext, null)
                //            it.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                it.setEnableHardwareScaler(false /* enabled */)
            }

            if (pcm.videoSink != null) {
                if (pcm.videoSink!!.target !== localRenderer) {
                    pcm.videoSink!!.release()
                }

                pcm.videoSink!!.target = renderer
            } else {
                val remoteVideoSink = ProxyVideoSink()
                remoteVideoSink.setTarget(userId, renderer)

                pcm.videoSink = remoteVideoSink
            }
        } else {
            pcm = PeerConnectionManager()
            pcm.userId = userId

            renderer?.let {
                it.release()
                it.init(eglBase.eglBaseContext, null)
                //            it.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                it.setEnableHardwareScaler(false /* enabled */)
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
        renderer?.let {
            setRendererRenderParams(false, it, pcm.renderParams!!)
        }
    }

    fun stopRemoteVideo(userId: String) {
        getPeerConnectionManagerByUserId(userId)?.let { pcm ->
            pcm.videoRecvEnabled = false
            pcm.videoSink?.release()
        }
    }

    fun stopAllRemoteVideo() {
        for (pcm in pcManagers) {
            pcm.videoRecvEnabled = false
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
            if (isLocalrenderer) {
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
        unpublish()
        setLocalRenderer(null)
        stopAllPull()

        publishPCClient = null

        if (mPublishSendOfferJob?.isActive == true) {
            mPublishSendOfferJob!!.cancel()
            mPublishSendOfferJob = null
        }
        if (mRTCReconnectJob?.isActive == true) {
            mRTCReconnectJob!!.cancel()
            mRTCReconnectJob = null
        }
        if (mDeletePublishJob?.isActive == true) {
            mDeletePublishJob!!.cancel()
            mDeletePublishJob = null
        }

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

    private fun startPull(
        userId: String,
        streamUrl: String,
        audioRecvEnabled: Boolean,
        videoRecvEnabled: Boolean
    ): PeerConnectionClient {
        val audioSendEnabled = false
        val videoSendEnabled = false

        val size = getVideoResolution(mVideoEncParam.videoResolution)

        val peerConnectionParameters =
            PeerConnectionParameters(
                audioSendEnabled, videoSendEnabled, audioRecvEnabled, videoRecvEnabled,
                false, false, size.width, size.height, mVideoEncParam.videoFps,
                mVideoEncParam.videoMaxBitrate, "H264 Baseline",
                true,
                true,
                0, "opus",
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false, null
            )

        val pc = PeerConnectionClient(
            mContext!!.applicationContext,
            eglBase,
            peerConnectionParameters,
            this
        )
        val options = PeerConnectionFactory.Options()

        options.networkIgnoreMask = 0
        //        options.disableEncryption = true;
//        options.disableNetworkMonitor = true;
        pc.setLocalVideoTrackEnabled(videoSendEnabled)
        pc.setRemoteVideoTrackEnabled(videoRecvEnabled)
        pc.setLocalAudioTrackEnabled(audioSendEnabled)
        pc.createPeerConnectionFactory(options)

        return pc
    }

    private fun sendPublishSdp(pc: PeerConnectionClient, sdp: SessionDescription) {
        sendOfferSdp(pc, publishUrl!!, sdp)
    }

    private fun sendPullSdp(pc: PeerConnectionClient, sdp: SessionDescription) {
        getPeerConnectionManagerByPc(pc)?.let { pcm ->
            sendOfferSdp(pc, pcm.sendSdpUrl!!, sdp)
        }
    }

    private fun sendOfferSdp(pc: PeerConnectionClient, url: String, sdp: SessionDescription) {
        if (pc == publishPCClient) {
            if (mPublishSendOfferJob?.isActive == true) {
                mPublishSendOfferJob!!.cancel()
            }
        }

        val sdpDes = sdp.description
        val client = OkHttpClient()

        val body: RequestBody = sdpDes.toRequestBody("application/sdp".toMediaType())
        val requst: Request = Request.Builder()
            .url(url)
            .header("Content-type", "application/sdp")
            .post(body)
            .build()
        client.newCall(requst).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "$url onFailure: $e")
                if (pc == publishPCClient) {
                    if (mPublishSendOfferJob?.isActive == true) {
                        mPublishSendOfferJob!!.cancel()
                    }
                }
                val job = CoroutineScope(Dispatchers.IO).launch {
                    delay(2000L)
                    if (mStartPublish) {
                        Log.e(TAG, "sendOfferSdp onResponse unsuccess sendOfferSdp")
                        sendOfferSdp(pc, url, sdp)
                    }
                }
                if (pc == publishPCClient) {
                    mPublishSendOfferJob = job
                }
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val sdpString = response.body!!.string()
                    Log.e(TAG, "$url onResponse: $sdpString")

                    if (pc == publishPCClient) {
                        if (mPublishSendOfferJob?.isActive == true) {
                            mPublishSendOfferJob!!.cancel()
                        }
                    }
                    if (mDeletePublishJob?.isActive == true) {
                        mDeletePublishJob!!.cancel()
                    }

                    val answerSdp = SessionDescription(
                        SessionDescription.Type.fromCanonicalForm("answer"),
                        sdpString
                    )
                    pc!!.setRemoteDescription(answerSdp)
                } else {
                    if (pc == publishPCClient) {
                        if (response.code == 502) {
                            Log.e(TAG, "sendOfferSdp 502 deletePublish")
                            deletePublish()
                        }
                        if (mPublishSendOfferJob?.isActive == true) {
                            mPublishSendOfferJob!!.cancel()
                        }
                    }
                    val job = CoroutineScope(Dispatchers.IO).launch {
                        delay(2000L)
                        if (mStartPublish) {
                            Log.e(TAG, "sendOfferSdp onResponse unsuccess sendOfferSdp")
                            sendOfferSdp(pc, url, sdp)
                        }
                    }
                    if (pc == publishPCClient) {
                        mPublishSendOfferJob = job
                    }
                }
            }
        })
    }

    private fun deletePublish() {
        if (TextUtils.isEmpty(unpublishUrl)) {
            return
        }

        if (mDeletePublishJob?.isActive == true) {
            mDeletePublishJob!!.cancel()
        }

        val client = OkHttpClient()

        val requst: Request = Request.Builder()
            .url(unpublishUrl!!)
            .delete()
            .build()
        client.newCall(requst).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "deletePublish onFailure: $e")
                if (mDeletePublishJob?.isActive == true) {
                    mDeletePublishJob!!.cancel()
                }
                mDeletePublishJob = CoroutineScope(Dispatchers.IO).launch {
                    delay(1000L)
                    Log.e(TAG, "deletePublish onFailure deletePublish")
                    deletePublish()
                }
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (mDeletePublishJob?.isActive == true) {
                    mDeletePublishJob!!.cancel()
                }
                if (response.isSuccessful) {
                    Log.e(
                        TAG, "deletePublish onResponse: " + response.body!!
                            .string()
                    )
                } else {
                    mDeletePublishJob = CoroutineScope(Dispatchers.IO).launch {
                        delay(1000L)
                        Log.e(TAG, "deletePublish onFailure deletePublish")
                        deletePublish()
                    }
                }
            }
        })
    }

    private fun unpublish() {
        Log.e(TAG, "unpublish onResponse")
        deletePublish()

        publishPCClient?.let { client ->
            client.stopVideoSource()
            client.setLocalVideoTrackEnabled(false)

            client.stopAudioCapture()
            client.setLocalAudioTrackEnabled(false)

            client.close()
        }
    }


    private fun stopPull(userId: String) {
        getPeerConnectionManagerByUserId(userId)?.let { pcm ->
            pcm.client?.let { client ->
                stopPull(client)
            }
        }
    }

    private fun stopPull(pc: PeerConnectionClient) {
        pc.close()
    }

    private fun stopAllPull() {
        stopAllRemoteVideo()

        for (pcm in pcManagers) {
            pcm.videoSink?.release()
            pcm.client?.let { client ->
                client.isNeedReconnect = false
                client.close()
                pcm.client = null
            }
        }
        pcManagers.clear()
    }

    override fun onLocalDescription(pc: PeerConnectionClient, sdp: SessionDescription) {
        CoroutineScope(Dispatchers.Main).launch {
            publishPCClient?.let {
                if (pc == it) {
                    if (publishVideoSendEnabled) {
                        startLocalVideo(useFrontCamera, localRenderer)
                    } else {
                        stopLocalVideo()
                    }
                }
            }
            pc.setVideoBitrate(mVideoEncParam.videoMinBitrate, mVideoEncParam.videoMaxBitrate)
        }
    }

    override fun onIceCandidate(pc: PeerConnectionClient, candidate: IceCandidate) {

    }

    override fun onIceCandidatesRemoved(
        pc: PeerConnectionClient,
        candidates: Array<IceCandidate>
    ) {
    }

    override fun onIceGatheringComplete(pc: PeerConnectionClient, sdp: SessionDescription) {
        CoroutineScope(Dispatchers.Main).launch {
            if (pc == publishPCClient) {
                if (mStartPublish) {
                    sendPublishSdp(pc, sdp)
                }
            } else {
                sendPullSdp(pc, sdp)
            }
        }
    }

    override fun onIceConnected(pc: PeerConnectionClient) {
        val delta = System.currentTimeMillis() - callStartedTimeMs
    }

    override fun onIceDisconnected(pc: PeerConnectionClient) {
    }

    override fun onConnected(pc: PeerConnectionClient) {
        if (mRTCReconnectJob?.isActive == true) {
            mRTCReconnectJob!!.cancel()
        }
        val delta = System.currentTimeMillis() - callStartedTimeMs
        CoroutineScope(Dispatchers.Main).launch {
            publishPCClient?.let {
                if (pc == it) {
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
        CoroutineScope(Dispatchers.Main).launch {
            if (pc == publishPCClient) {
                unpublish()
            } else {
                stopPull(pc)
            }
        }
    }

    override fun onPeerConnectionClosed(pc: PeerConnectionClient) {
        CoroutineScope(Dispatchers.Main).launch {
            if (pc.isNeedReconnect) {
                reconnect(pc)
            } else {
                if (pc == publishPCClient) {
                    publishPCClient = null
                    mRTCListener?.onClose()
                } else {
                    val pcm = getPeerConnectionManagerByPc(pc) ?: return@launch

                    pcm.videoSink?.release()

                    pcManagers.remove(pcm)
                }
            }
        }
    }

    override fun onPeerConnectionStatsReady(
        pc: PeerConnectionClient,
        reports: Array<StatsReport>
    ) {
    }

    override fun onPeerConnectionError(pc: PeerConnectionClient, description: String) {
        CoroutineScope(Dispatchers.Main).launch {
            pc.isNeedReconnect = true
            if (pc == publishPCClient) {
                unpublish()
            } else {
                stopPull(pc)
            }
        }
    }

    override fun onDataChannelMessage(pc: PeerConnectionClient, message: String) {
    }

    fun switchPublishCamera(frontCamera: Boolean) {
        if (useFrontCamera != frontCamera) {
            publishPCClient?.switchCamera()
        }
        useFrontCamera = frontCamera
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

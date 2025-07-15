package com.wx.rtc.rtc

import com.wx.rtc.PlatformContext
import com.wx.rtc.RTCVideoContainerView
import com.wx.rtc.WXRTCDef.WXRTCRenderParams
import com.wx.rtc.WXRTCDef.WXRTCVideoEncParam
import com.wx.rtc.snapshotVideo
import com.wx.rtc.utils.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
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
    private lateinit var mContext: PlatformContext
    private var mVideoEncParam: WXRTCVideoEncParam = WXRTCVideoEncParam()

    private val pcManagers: MutableList<PeerConnectionManager> = ArrayList()
    private var localContainerView: RTCVideoContainerView? = null
    private var mRTCListener: RTCListener? = null
    private var mStartPublish = false

    fun init(context: PlatformContext) {
        this.mContext = context
    }

    fun setRTCVideoParam(param: WXRTCVideoEncParam) {
        this.mVideoEncParam = param
        publishPCClient?.setVideoEncParam(param)
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
            mContext, true, publishUrl, this
        ).apply {
            this.setRemoteVideoTrackEnabled(false)
            this.createPeerConnectionFactory()

            setLocalRenderer(localContainerView)

            setRTCVideoParam(mVideoEncParam)

            setLocalRenderParams(publishRenderParams)

            muteLocalVideo(publishVideoMute)
            muteLocalAudio(publishAudioMute)

            this.startCall()
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
        } else {
            pcm.client?.close()

            if (pcm.videoRecvEnabled) {
                pcm.videoContainerView?.let {
                    startRemoteView(userId, it)
                }
            }
            if (pcm.videoRecvMute) {
                pc.setRemoteVideoTrackEnabled(pcm.videoRecvMute)
            }
            if (pcm.audioRecvMute) {
                pc.setRemoteAudioTrackEnabled(pcm.audioRecvMute)
            }
        }
        pcm.sendSdpUrl = pullUrl
        pcm.client = pc

        pc.isNeedReconnect = pcm.needReconnect
        pc.startCall()
    }

    private fun setLocalRenderer(view: RTCVideoContainerView?) {
        if (view != null) {
            if (this.localContainerView != null && this.localContainerView === view) {
                return
            }
        }

        this.localContainerView = view

        setLocalRenderParams(publishRenderParams)
    }

    fun startLocalPreview(frontCamera: Boolean, view: RTCVideoContainerView?) {
        useFrontCamera = frontCamera
        publishVideoSendEnabled = true
        view?.setVisible(true)
        publishPCClient?.startVideoSource(frontCamera, view)
        setLocalRenderer(view)
    }

    fun updateLocalView(view: RTCVideoContainerView?) {
        view?.setVisible(true)
        publishPCClient?.updateVideoSource(view)
        setLocalRenderer(view)
    }

    fun stopLocalPreview() {
        publishVideoSendEnabled = false
        publishPCClient?.stopVideoSource()
        setLocalRenderer(null)
    }

    fun muteLocalVideo(mute: Boolean) {
        publishVideoMute = mute
        publishPCClient?.setLocalVideoTrackEnabled(!mute)
    }

    fun startRemoteView(userId: String, view: RTCVideoContainerView?) {
//        if (localContainerView != null && localContainerView === view) {
//            setLocalRenderer(null)
//        }

        var pcm = getPeerConnectionManagerByUserId(userId)

        if (pcm != null) {

        } else {
            pcm = PeerConnectionManager()
            pcm.userId = userId

            pcManagers.add(pcm)
        }

        pcm.videoRecvEnabled = true

        pcm.client?.setRemoteView(view)
        pcm.videoContainerView = view

        if (pcm.renderParams == null) {
            pcm.renderParams = WXRTCRenderParams()
        }

        if (!pcm.videoRecvMute) {
            pcm.client?.setRemoteVideoTrackEnabled(true)
        }

        view?.let {
            setRendererRenderParams(false, it, pcm.renderParams!!)
        }
    }

    fun updateRemoteView(userId: String, view: RTCVideoContainerView?) {
        if (localContainerView != null && localContainerView === view) {
            setLocalRenderer(null)
        }

        var pcm = getPeerConnectionManagerByUserId(userId)

        if (pcm != null) {
            if (pcm.videoContainerView != null && view != null && pcm.videoContainerView === view) {
                return
            }

            pcm.client?.setRemoteView(view)
            pcm.videoContainerView = view

            if (pcm.renderParams == null) {
                pcm.renderParams = WXRTCRenderParams()
            }

            view?.let {
                setRendererRenderParams(false, it, pcm.renderParams!!)
            }
        }
    }

    fun stopRemoteView(userId: String) {
        getPeerConnectionManagerByUserId(userId)?.let { pcm ->
            pcm.videoRecvEnabled = false
            pcm.client?.setRemoteVideoTrackEnabled(false)
            pcm.client?.setRemoteView(null)
//            pcm.videoContainerView?.removeVideoView()
            pcm.videoContainerView = null
        }
    }

    fun stopAllRemoteView() {
        for (pcm in pcManagers) {
            pcm.videoRecvEnabled = false
            pcm.client?.setRemoteVideoTrackEnabled(false)
            pcm.client?.setRemoteView(null)
//            pcm.videoContainerView?.removeVideoView()
            pcm.videoContainerView = null
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
        isLocalRenderer: Boolean,
        containerView: RTCVideoContainerView?,
        params: WXRTCRenderParams
    ) {
        containerView?.setRendererRenderParams(params, isScreenCapture, isLocalRenderer, useFrontCamera)
    }

    fun setLocalRenderParams(params: WXRTCRenderParams) {
        publishRenderParams = params
        localContainerView?.let {
            setRendererRenderParams(true, it, params)
        }
    }

    fun setRemoteRenderParams(userId: String, params: WXRTCRenderParams) {
        val pcm = getPeerConnectionManagerByUserId(userId)
        if (pcm != null) {
            pcm.renderParams = params
            pcm.videoContainerView?.let {
                setRendererRenderParams(false, it, params)
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

    fun startScreenCapture(encParam: WXRTCVideoEncParam?, view: RTCVideoContainerView?) {
        publishVideoSendEnabled = true
        isScreenCapture = true
        publishPCClient?.startScreenCapture(view)
        setLocalRenderer(view)
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
        return snapshotVideo(userId, localContainerView)
    }

    fun snapshotRemoteVideo(userId: String): Boolean {
        val pcm = getPeerConnectionManagerByUserId(userId)
//        if (pcm?.videoSink == null || pcm.videoSink!!.target == null || pcm.client == null) {
//            return false
//        }
//        return snapshotVideo(userId, pcm.videoSink!!.target as RTCVideoContainerView?)
        return snapshotVideo(userId, pcm?.videoContainerView)
    }

    private fun snapshotVideo(userId: String, renderer: RTCVideoContainerView?): Boolean {
        if (renderer == null || renderer.getVideoView() == null) {
            return false
        }
        renderer.getVideoView()?.snapshotVideo(mContext) {
            mRTCListener?.onSnapshot(userId, it)
        }
        return true
    }

    fun destory() {
        publishPCClient?.isNeedReconnect = false
        stopPublish()
        setLocalRenderer(null)
        stopAllPull()

        publishPCClient = null
    }

    private fun startPull(userId: String, streamUrl: String): PeerConnectionClient {
        return PeerConnectionClient(mContext, false, streamUrl, this).apply {
            this.createPeerConnectionFactory()
        }
    }

    private fun stopPublish() {
        log(TAG, "unpublish onResponse")
        stopLocalPreview()
        stopScreenCapture()
        stopLocalAudio()

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
//            pcm.videoSink?.release()
//            pcm.videoContainerView?.removeVideoView()
            pcm.videoContainerView = null
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
        stopAllRemoteView()

        for (pcm in pcManagers) {
//            pcm.videoSink?.release()
//            pcm.videoContainerView?.removeVideoView()
            pcm.videoContainerView = null
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
        val delta = Clock.System.now().toEpochMilliseconds() - callStartedTimeMs
    }

    override fun onIceDisconnected(pc: PeerConnectionClient) {
    }

    override fun onConnected(pc: PeerConnectionClient) {
        val delta = Clock.System.now().toEpochMilliseconds() - callStartedTimeMs
        CoroutineScope(Dispatchers.Main).launch {
            publishPCClient?.let {
                if (pc == it) {
                    pc.setVideoEncParam(mVideoEncParam)
                    if (publishVideoSendEnabled) {
                        startLocalPreview(useFrontCamera, localContainerView)
                    } else {
                        stopLocalPreview()
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
        pc: PeerConnectionClient, status: Map<String, Any>
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

    private fun getPeerConnectionManagerByPc(pc: PeerConnectionClient?): PeerConnectionManager? {
        for (pcm in pcManagers) {
            if (pc == pcm.client) {
                return pcm
            }
        }
        return null
    }

    companion object {
        private val TAG: String = RTCManager::class.simpleName ?: "RTCManager"
    }
}

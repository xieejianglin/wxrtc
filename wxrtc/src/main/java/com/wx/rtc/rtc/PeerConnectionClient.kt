package com.wx.rtc.rtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import com.wx.rtc.WXRTCDef.WXRTCVideoEncParam
import com.wx.rtc.utils.ActivityUtils
import com.wx.rtc.utils.RTCUtils
import com.wx.rtc.utils.RTCUtils.getVideoResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.CandidatePairChangeEvent
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnection.IceGatheringState
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnection.PeerConnectionState
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnection.SdpSemantics
import org.webrtc.PeerConnection.SignalingState
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import org.webrtc.RtpTransceiver.RtpTransceiverInit
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SoftwareVideoDecoderFactory
import org.webrtc.SoftwareVideoEncoderFactory
import org.webrtc.StatsReport
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoEncoderFactory
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoProcessor.FrameAdaptationParameters
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordErrorCallback
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordStateCallback
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackErrorCallback
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackStateCallback
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern


/*
*  Copyright 2014 The WebRTC Project Authors. All rights reserved.
*
*  Use of this source code is governed by a BSD-style license
*  that can be found in the LICENSE file in the root of the source
*  tree. An additional intellectual property rights grant can be found
*  in the file PATENTS.  All contributing project authors may
*  be found in the AUTHORS file in the root of the source tree.
*/

/**
 * Peer connection client implementation.
 *
 *
 * All public methods are routed to local looper thread.
 * All PeerConnectionEvents callbacks are invoked from the same looper thread.
 * This class is a singleton.
 */
internal class PeerConnectionClient(
    private val appContext: Context, private val rootEglBase: EglBase,
    private val userId: String, private val sendSdpUrl: String,
    private val isPublish: Boolean, private val events: PeerConnectionEvents
) {
    // Executor thread is started once in private ctor and is used for all
    // peer connection API calls to ensure new peer connection factory is
    // created on the same thread as previously destroyed factory.
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val pcObserver = PCObserver()
    private val sdpObserver = SDPObserver()
    private val statsTimer = Timer()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var preferIsac = false
    private var videoCapturerStopped = true
    private var isError = false
    private var isClosing = false
    var localRender: VideoSink? = null
    var remoteSink: VideoSink? = null
    private val peerConnectionParameters: PeerConnectionParameters = PeerConnectionParameters()
    private var signalingParameters: SignalingParameters? = null
    private var videoParam: WXRTCVideoEncParam = WXRTCVideoEncParam()
    private var screenVideoEncParam: WXRTCVideoEncParam = WXRTCVideoEncParam()
    private var audioConstraints: MediaConstraints? = null
    private var sdpMediaConstraints: MediaConstraints? = null

    // Queued remote ICE candidates are consumed only after both local and
    // remote descriptions are set. Similarly local ICE candidates are sent to
    // remote peer after both local and remote description are set.
    private var queuedRemoteCandidates: MutableList<IceCandidate>? = null
    private var isInitiator = false
    private var localSdp: SessionDescription? = null // either offer or answer SDP
    private var videoCapturer: VideoCapturer? = null

    // enableVideo is set to true if video should be rendered and sent.
    //    private boolean renderVideo = true;
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var localVideoSender: RtpSender? = null
    private var localAudioSender: RtpSender? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var dataChannel: DataChannel? = null
    private val dataChannelEnabled = peerConnectionParameters.dataChannelParameters != null

    private var iceHostGet = false
    private var iceStunGet = false
    private var iceTurnGet = false
    private var iceComplete = false

    private var frameVideoSink: VideoSink? = null

    private var remoteAudioEnabled = true
    private var remoteAudioVolume = 100
    private var callStartedTimeMs: Long = 0

    private var cameraDeviceName: String? = null
    private var cameraEnumerator: CameraEnumerator? = null
    private var cameraVideoCapturer: CameraVideoCapturer? = null

    private val CAPTURE_PERMISSION_REQUEST_CODE = 1

    var isNeedReconnect: Boolean = true
//    var isPublish: Boolean = false
//    private var sendSdpUrl: String? = null
    var unpublishUrl: String? = null

    /**
     * Peer connection parameters.
     */
    class DataChannelParameters(
        val ordered: Boolean = true, val maxRetransmitTimeMs: Int = -1, val maxRetransmits: Int = -1,
        val protocol: String = "", val negotiated: Boolean = false, val id: Int = -1
    )

    /**
     * Peer connection parameters.
     */
    class PeerConnectionParameters(
        val loopback: Boolean = false,
        val tracing: Boolean = false,
        val videoCodec: String = VIDEO_CODEC_H264_BASELINE,
        val videoCodecHwAcceleration: Boolean = true,
        val videoFlexfecEnabled: Boolean = true,
        val audioStartBitrate: Int = 0,
        val audioCodec: String = AUDIO_CODEC_OPUS,
        val noAudioProcessing: Boolean = false,
        val aecDump: Boolean = false,
        val saveInputAudioToFile: Boolean = false,
        val useOpenSLES: Boolean = false,
        val disableBuiltInAEC: Boolean = false,
        val disableBuiltInAGC: Boolean = false,
        val disableBuiltInNS: Boolean = false,
        val disableWebRtcAGCAndHPF: Boolean = false,
        val enableRtcEventLog: Boolean = false,
        val dataChannelParameters: DataChannelParameters? = null
    )

    /**
     * Peer connection events.
     */
    interface PeerConnectionEvents {
        /**
         * Callback fired once local SDP is created and set.
         */
        fun onLocalDescription(pc: PeerConnectionClient, sdp: SessionDescription)

        /**
         * Callback fired once local Ice candidate is generated.
         */
        fun onIceCandidate(pc: PeerConnectionClient, candidate: IceCandidate)

        /**
         * Callback fired once local ICE candidates are removed.
         */
        fun onIceCandidatesRemoved(pc: PeerConnectionClient, candidates: Array<IceCandidate>)

        /**
         * Callback fired once local Ice candidate generate state.
         */
        fun onIceGatheringComplete(pc: PeerConnectionClient, sdp: SessionDescription)

        /**
         * Callback fired once connection is established (IceConnectionState is
         * CONNECTED).
         */
        fun onIceConnected(pc: PeerConnectionClient)

        /**
         * Callback fired once connection is disconnected (IceConnectionState is
         * DISCONNECTED).
         */
        fun onIceDisconnected(pc: PeerConnectionClient)

        /**
         * Callback fired once DTLS connection is established (PeerConnectionState
         * is CONNECTED).
         */
        fun onConnected(pc: PeerConnectionClient)

        /**
         * Callback fired once DTLS connection is disconnected (PeerConnectionState
         * is DISCONNECTED).
         */
        fun onDisconnected(pc: PeerConnectionClient)

        /**
         * Callback fired once peer connection is closed.
         */
        fun onPeerConnectionClosed(pc: PeerConnectionClient)

        /**
         * Callback fired once peer connection statistics is ready.
         */
        fun onPeerConnectionStatsReady(pc: PeerConnectionClient, reports: Array<StatsReport>)

        /**
         * Callback fired once peer connection error happened.
         */
        fun onPeerConnectionError(pc: PeerConnectionClient, description: String)

        fun onDataChannelMessage(pc: PeerConnectionClient, message: String)
    }

    /**
     * Create a PeerConnectionClient with the specified parameters. PeerConnectionClient takes
     * ownership of |eglBase|.
     */
    init {
        Log.d(TAG, "Preferred video codec: ${getSdpVideoCodecName(peerConnectionParameters)}")
        val fieldTrials = getFieldTrials(peerConnectionParameters)
        executor.execute {
            Log.d(TAG, "Initialize WebRTC. Field trials: $fieldTrials")
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .setFieldTrials(fieldTrials)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
            )
        }
    }

    fun setVideoEncParam(param: WXRTCVideoEncParam) {
        if (videoParam.videoResolution != param.videoResolution || videoParam.videoFps != param.videoFps) {
            val size = getVideoResolution(param.videoResolution)
            changeVideoSource(size.width, size.height, param.videoFps)
        }
        if (videoParam.videoMinBitrate != param.videoMinBitrate || videoParam.videoMaxBitrate != param.videoMaxBitrate) {
            setVideoBitrate(param.videoMinBitrate, param.videoMaxBitrate)
        }
        videoParam = param
        screenVideoEncParam = param
    }

    fun setScreenEncParamCapture(param: WXRTCVideoEncParam) {
//        if (screenVideoEncParam.videoResolution != param.videoResolution || screenVideoEncParam.videoFps != param.videoFps) {
            val size = getVideoResolution(param.videoResolution)
            changeVideoSource(size.width, size.height, param.videoFps)
//        }
//        if (screenVideoEncParam.videoMinBitrate != param.videoMinBitrate || screenVideoEncParam.videoMaxBitrate != param.videoMaxBitrate) {
            setVideoBitrate(param.videoMinBitrate, param.videoMaxBitrate)
//        }
        screenVideoEncParam = param
    }

    /**
     * This function should only be called once.
     */
    fun createPeerConnectionFactory(options: PeerConnectionFactory.Options) {
        check(factory == null) { "PeerConnectionFactory has already been constructed" }
        executor.execute { createPeerConnectionFactoryInternal(options) }
    }

    private fun createPeerConnection(signalingParameters: SignalingParameters?) {
        this.signalingParameters = signalingParameters
        executor.execute {
            try {
                createMediaConstraintsInternal()
                createPeerConnectionInternal()
                maybeCreateAndStartRtcEventLog()
            } catch (e: Exception) {
                reportError("Failed to create peer connection: " + e.message)
                throw e
            }
        }
    }

    fun startCall(localRender: VideoSink?, remoteSink: VideoSink?) {
        callStartedTimeMs = System.currentTimeMillis()
        this.localRender = localRender
        this.remoteSink = remoteSink
        executor.execute {
            onConnectedToRoomInternal()
//            remoteSink?.let {
//                remoteVideoTrack?.addSink(it)
//            }
        }
    }

    private fun onConnectedToRoomInternal() {
        createOffer()
    }

    private fun createVideoCapturer(frontCamera: Boolean): VideoCapturer? {
        Logging.d(TAG, "Creating capturer using camera2 API first.")
        return createCameraCapturer(Camera2Enumerator(appContext), frontCamera) ?:
                createCameraCapturer(Camera1Enumerator(false), frontCamera)
//        return createCameraCapturer(Camera1Enumerator(false), frontCamera) ?:
//                createCameraCapturer(Camera2Enumerator(appContext), frontCamera)
    }

    private fun createCameraCapturer(
        enumerator: CameraEnumerator,
        frontCamera: Boolean
    ): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        var targetDeviceName = ""
        if (frontCamera) {
            for (deviceName in deviceNames) {
                if (enumerator.isFrontFacing(deviceName)) {
                    targetDeviceName = deviceName
                    break
                }
            }
        } else {
            for (deviceName in deviceNames) {
                if (!enumerator.isFrontFacing(deviceName)) {
                    targetDeviceName = deviceName
                    break
                }
            }
        }
        if (targetDeviceName.isEmpty() && deviceNames.size > 0) {
            targetDeviceName = deviceNames[0]
        }
        if (targetDeviceName.isNotEmpty()) {
            return enumerator.createCapturer(targetDeviceName, object : CameraVideoCapturer.CameraEventsHandler {
                override fun onCameraError(param1String: String?) {
                }

                override fun onCameraDisconnected() {
                }

                override fun onCameraFreezed(param1String: String?) {

                }

                override fun onCameraOpening(param1String: String?) {
                    cameraDeviceName = param1String
                }

                override fun onFirstFrameAvailable() {
                }

                override fun onCameraClosed(param1String: String?) {
                    cameraDeviceName?.let {
                        if (it == param1String) {
                            cameraDeviceName = null
                        }
                    }
                }

            }).let { videoCapturer ->
                cameraVideoCapturer = videoCapturer
                cameraEnumerator = enumerator
                videoCapturer
            }
        }
        return null
    }

    private fun createScreenCapturer() : VideoCapturer {
        return ScreenCapturerAndroid(
            mediaProjectionPermissionResultData, object : MediaProjection.Callback() {
                override fun onStop() {
                    reportError("User revoked permission to capture the screen.")
                }
            })
    }

    fun close() {
        executor.execute { this.closeInternal() }
    }

    val isPublishClient: Boolean
        get() = isPublish

    val isCameraOpened: Boolean
        get() = this.cameraDeviceName == null

    private fun createPeerConnectionFactoryInternal(options: PeerConnectionFactory.Options?) {
        isError = false
        if (peerConnectionParameters.tracing) {
            PeerConnectionFactory.startInternalTracingCapture(
                Environment.getExternalStorageDirectory().absolutePath + File.separator
                        + "webrtc-trace.txt"
            )
        }
        // Check if ISAC is used by default.
        preferIsac =
            peerConnectionParameters.audioCodec != null && peerConnectionParameters.audioCodec == AUDIO_CODEC_ISAC
        // It is possible to save a copy in raw PCM format on a file by checking
        // the "Save input audio to file" checkbox in the Settings UI. A callback
        // interface is set when this flag is enabled. As a result, a copy of recorded
        // audio samples are provided to this client directly from the native audio
        // layer in Java.
        if (peerConnectionParameters.saveInputAudioToFile) {
            if (!peerConnectionParameters.useOpenSLES) {
                Log.d(TAG, "Enable recording of microphone input audio to file")
                //                saveRecordedAudioToFile = new RecordedAudioToFileController(executor);
            } else {
                // then the "Save inut audio to file" option shall be grayed out.
                Log.e(TAG, "Recording of input audio is not supported for OpenSL ES")
            }
        }
        audioDeviceModule = createJavaAudioDevice()
        // Create peer connection factory.
        if (options != null) {
            Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask)
        }
        val enableH264HighProfile = VIDEO_CODEC_H264_HIGH == peerConnectionParameters.videoCodec
        val encoderFactory: VideoEncoderFactory
        val decoderFactory: VideoDecoderFactory
        if (peerConnectionParameters.videoCodecHwAcceleration) {
            encoderFactory = DefaultVideoEncoderFactory(
                rootEglBase.eglBaseContext, false,  /* enableIntelVp8Encoder */enableH264HighProfile
            )
            decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)
        } else {
            encoderFactory = SoftwareVideoEncoderFactory()
            decoderFactory = SoftwareVideoDecoderFactory()
        }
        factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        Log.d(TAG, "Peer connection factory created.")

        //        adm.release();
        val iceCandidates: List<IceCandidate> = ArrayList()
        val offerSdp: SessionDescription? = null
        val iceServers: List<IceServer> = ArrayList()

        //        iceServers.add(PeerConnection.IceServer.builder("turn:113.125.177.190")
//                .setUsername("keda")
//                .setPassword("keda1234")
//                .createIceServer());
//        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
//                .createIceServer());
        val params = SignalingParameters(iceServers, offerSdp, iceCandidates)
        signalingParameters = params
        createPeerConnection(params)
    }

    private fun createJavaAudioDevice(): JavaAudioDeviceModule {
        // Enable/disable OpenSL ES playback.
        if (!peerConnectionParameters.useOpenSLES) {
            Log.w(TAG, "External OpenSLES ADM not implemented yet.")
        }
        // Set audio record error callbacks.
        val audioRecordErrorCallback: AudioRecordErrorCallback = object : AudioRecordErrorCallback {
            override fun onWebRtcAudioRecordInitError(errorMessage: String) {
                Log.e(TAG, "onWebRtcAudioRecordInitError: $errorMessage")
                reportError(errorMessage)
            }

            override fun onWebRtcAudioRecordStartError(
                errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode, errorMessage: String
            ) {
                Log.e(TAG, "onWebRtcAudioRecordStartError: $errorCode. $errorMessage")
                reportError(errorMessage)
            }

            override fun onWebRtcAudioRecordError(errorMessage: String) {
                Log.e(TAG, "onWebRtcAudioRecordError: $errorMessage")
                reportError(errorMessage)
            }
        }
        val audioTrackErrorCallback: AudioTrackErrorCallback = object : AudioTrackErrorCallback {
            override fun onWebRtcAudioTrackInitError(errorMessage: String) {
                Log.e(TAG, "onWebRtcAudioTrackInitError: $errorMessage")
                reportError(errorMessage)
            }

            override fun onWebRtcAudioTrackStartError(
                errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode, errorMessage: String
            ) {
                Log.e(TAG, "onWebRtcAudioTrackStartError: $errorCode. $errorMessage")
                reportError(errorMessage)
            }

            override fun onWebRtcAudioTrackError(errorMessage: String) {
                Log.e(TAG, "onWebRtcAudioTrackError: $errorMessage")
                reportError(errorMessage)
            }
        }
        // Set audio record state callbacks.
        val audioRecordStateCallback: AudioRecordStateCallback = object : AudioRecordStateCallback {
            override fun onWebRtcAudioRecordStart() {
                Log.i(TAG, "Audio recording starts")
            }

            override fun onWebRtcAudioRecordStop() {
                Log.i(TAG, "Audio recording stops")
            }
        }
        // Set audio track state callbacks.
        val audioTrackStateCallback: AudioTrackStateCallback = object : AudioTrackStateCallback {
            override fun onWebRtcAudioTrackStart() {
                Log.i(TAG, "Audio playout starts")
            }

            override fun onWebRtcAudioTrackStop() {
                Log.i(TAG, "Audio playout stops")
            }
        }

        return JavaAudioDeviceModule.builder(appContext)
//                .setSamplesReadyCallback(saveRecordedAudioToFile)
            .setUseHardwareAcousticEchoCanceler(!peerConnectionParameters.disableBuiltInAEC)
            .setUseHardwareNoiseSuppressor(!peerConnectionParameters.disableBuiltInNS)
            .setAudioRecordErrorCallback(audioRecordErrorCallback)
            .setAudioTrackErrorCallback(audioTrackErrorCallback)
            .setAudioRecordStateCallback(audioRecordStateCallback)
            .setAudioTrackStateCallback(audioTrackStateCallback)
            .createAudioDeviceModule()
    }

    private fun createMediaConstraintsInternal() {
        // Create video constraints if video call is enabled.
        if (isPublish) {
            val size = RTCUtils.getVideoResolution(videoParam.videoResolution)
            Logging.d(TAG, "Capturing format: ${size.width}x${size.height}@${videoParam.videoFps}")
        }
        // Create audio constraints.
        audioConstraints = MediaConstraints()
        // added for audio performance measurements
        if (peerConnectionParameters.noAudioProcessing) {
            Log.d(TAG, "Disabling audio processing")
            audioConstraints!!.mandatory.add(
                MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false")
            )
            audioConstraints!!.mandatory.add(
                MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false")
            )
            audioConstraints!!.mandatory.add(
                MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false")
            )
            audioConstraints!!.mandatory.add(
                MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false")
            )
        }
        // Create SDP constraints.
        sdpMediaConstraints = MediaConstraints().apply {
            mandatory?.let {
                it.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", (!isPublish).toString()))
                it.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", (!isPublish).toString()))
            }
        }
    }

    private fun createPeerConnectionInternal() {
        if (factory == null || isError) {
            Log.e(TAG, "Peerconnection factory is not created")
            return
        }
        Log.d(TAG, "Create peer connection.")
        queuedRemoteCandidates = ArrayList()
        val rtcConfig = RTCConfiguration(signalingParameters!!.iceServers)
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
//        rtcConfig.tcpCandidatePolicy = TcpCandidatePolicy.DISABLED;
//        rtcConfig.bundlePolicy = BundlePolicy.MAXBUNDLE;
//        rtcConfig.rtcpMuxPolicy = RtcpMuxPolicy.REQUIRE;
//        rtcConfig.continualGatheringPolicy = ContinualGatheringPolicy.GATHER_CONTINUALLY;
//        // Use ECDSA encryption.
//        rtcConfig.keyType = KeyType.ECDSA;
//        // Enable DTLS for normal calls and disable for loopback calls.
//        rtcConfig.enableDtlsSrtp = !peerConnectionParameters.loopback;
        rtcConfig.sdpSemantics = SdpSemantics.UNIFIED_PLAN
        //        rtcConfig.networkPreference = PeerConnection.AdapterType.CELLULAR;
//        rtcConfig.disableIpv6 = true;
        peerConnection = factory!!.createPeerConnection(rtcConfig, pcObserver)
        if (dataChannelEnabled) {
            val init = DataChannel.Init().apply {
                ordered = peerConnectionParameters.dataChannelParameters!!.ordered
                negotiated = peerConnectionParameters.dataChannelParameters.negotiated
                maxRetransmits = peerConnectionParameters.dataChannelParameters.maxRetransmits
                maxRetransmitTimeMs = peerConnectionParameters.dataChannelParameters.maxRetransmitTimeMs
                id = peerConnectionParameters.dataChannelParameters.id
                protocol = peerConnectionParameters.dataChannelParameters.protocol
            }
            dataChannel = peerConnection!!.createDataChannel("ApprtcDemo data", init)

            dataChannel?.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {
                    Log.d(
                        TAG,
                        "Data channel buffered amount changed: " + dataChannel?.label() + ": " + dataChannel?.state()
                    )
                }

                override fun onStateChange() {
                    if (dataChannel == null) {
                        return
                    }
                    Log.d(
                        TAG,
                        "Data channel state changed: " + dataChannel!!.label() + ": " + dataChannel!!.state()
                    )
                    if (dataChannel!!.state() == DataChannel.State.OPEN) {
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(10L)
                            sendMessage("hello world")
                        }
                    }
                }

                override fun onMessage(buffer: DataChannel.Buffer) {
                    if (buffer.binary) {
                        Log.d(TAG, "Received binary msg over $dataChannel")
                        return
                    }
                    val data = buffer.data
                    val bytes = ByteArray(data.capacity())
                    data[bytes]
                    val strData = String(bytes, Charset.forName("UTF-8"))
                    Log.d(TAG, "Data channel Got msg: " + strData + " over " + dataChannel?.label())

                    events.onDataChannelMessage(this@PeerConnectionClient, strData)
                }
            })
        }
        isInitiator = false
        // Set INFO libjingle logging.
        // NOTE: this _must_ happen while |factory| is alive!
        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO)
        val mediaStreamLabels = listOf("ARDAMS")
        if (isPublish) {
            localVideoSender = peerConnection!!.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, mediaStreamLabels)).sender
            localAudioSender = peerConnection!!.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, mediaStreamLabels)).sender
//            peerConnection!!.addTrack(createVideoTrack(false), mediaStreamLabels)
//            peerConnection!!.addTrack(createAudioTrack(), mediaStreamLabels)
        } else {
            remoteVideoTrack = peerConnection!!.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO).receiver.track() as VideoTrack?
            // We can add the renderers right away because we don't need to wait for an
            // answer to get the remote track.
//            remoteVideoTrack = getRemoteVideoTrack();
            remoteVideoTrack!!.setEnabled(true)
            remoteVideoTrack!!.addSink(remoteSink)
        }

        if (peerConnectionParameters.aecDump) {
            try {
                val aecDumpFileDescriptor =
                    ParcelFileDescriptor.open(
                        File(
                            Environment.getExternalStorageDirectory().path
                                    + File.separator + "Download/audio.aecdump"
                        ),
                        ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
                                or ParcelFileDescriptor.MODE_TRUNCATE
                    )
                factory!!.startAecDump(aecDumpFileDescriptor.detachFd(), -1)
            } catch (e: IOException) {
                Log.e(TAG, "Can not open aecdump file", e)
            }
        }
        Log.d(TAG, "Peer connection created.")
    }

    private fun createRtcEventLogOutputFile(): File {
        val dateFormat: DateFormat = SimpleDateFormat("yyyyMMdd_hhmm_ss", Locale.getDefault())
        val date = Date()
        val outputFileName = "event_log_" + dateFormat.format(date) + ".log"
        return File(
            appContext.getDir(RTCEVENTLOG_OUTPUT_DIR_NAME, Context.MODE_PRIVATE), outputFileName
        )
    }

    private fun maybeCreateAndStartRtcEventLog() {
        if (peerConnection == null) {
            return
        }
        if (!peerConnectionParameters.enableRtcEventLog) {
            Log.d(TAG, "RtcEventLog is disabled.")
            return
        }
    }

    private fun closeInternal() {
        isClosing = true
        if (peerConnectionParameters.aecDump) {
            factory?.stopAecDump()
        }
        Log.d(TAG, "Closing peer connection.")
        statsTimer.cancel()
        dataChannel?.dispose()
        dataChannel = null
        Log.d(TAG, "Closing audio source.")
        audioSource?.dispose()
        audioSource = null
        audioDeviceModule?.release()
        audioDeviceModule = null
        Log.d(TAG, "Stopping capture.")
        try {
            videoCapturer?.stopCapture()
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
        videoCapturer?.dispose()
        videoCapturer = null
        Log.d(TAG, "Closing video source.")
        videoSource?.dispose()
        videoSource = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        localRender = null
        peerConnection?.dispose()
        peerConnection = null
        localVideoSender = null
        localAudioSender = null
        localVideoTrack = null
        localAudioTrack = null
        Log.d(TAG, "Closing peer connection factory.")
        factory?.dispose()
        factory = null
        //        rootEglBase.release();
        Log.d(TAG, "Closing peer connection done.")
        events.onPeerConnectionClosed(this)
        PeerConnectionFactory.stopInternalTracingCapture()
        PeerConnectionFactory.shutdownInternalTracer()
    }

    private val stats: Unit
        get() {
            if (peerConnection == null || isError) {
                return
            }
            val success = peerConnection!!.getStats({ reports ->
                events.onPeerConnectionStatsReady(
                    this@PeerConnectionClient,
                    reports
                )
            }, null)
            if (!success) {
                Log.e(TAG, "getStats() returns false!")
            }
        }

    private fun enableStatsEvents(enable: Boolean, periodMs: Int) {
        if (enable) {
            try {
                statsTimer.schedule(object : TimerTask() {
                    override fun run() {
                        executor.execute { stats }
                    }
                }, 0, periodMs.toLong())
            } catch (e: Exception) {
                Log.e(TAG, "Can not schedule statistics timer", e)
            }
        } else {
            statsTimer.cancel()
        }
    }

    private fun sendOfferSdp(sdp: SessionDescription) {
        val sdpDes = sdp.description
        val client = OkHttpClient()

        val body: RequestBody = sdpDes.toRequestBody("application/sdp".toMediaType())
        val requst: Request = Request.Builder()
            .url(sendSdpUrl)
            .header("Content-type", "application/sdp")
            .post(body)
            .build()
        client.newCall(requst).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "$sendSdpUrl onFailure: $e")
                CoroutineScope(Dispatchers.IO).launch {
                    delay(1000L)
                    Log.e(TAG, "sendOfferSdp onResponse unsuccess sendOfferSdp")
                    sendOfferSdp(sdp)
                }
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val sdpString = response.body!!.string()
                    Log.e(TAG, "$sendSdpUrl onResponse: $sdpString")

                    val answerSdp = SessionDescription(
                        SessionDescription.Type.fromCanonicalForm("answer"),
                        sdpString
                    )
                    setRemoteDescription(answerSdp)
                } else {
                    if (response.code == 502 && isPublish) {
                        Log.e(TAG, "sendOfferSdp 502 deletePublish")
                        deletePublish({
                            CoroutineScope(Dispatchers.IO).launch {
                                delay(200L)
                                sendOfferSdp(sdp)
                            }
                        })
                    }else{
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(1000L)
                            Log.e(TAG, "sendOfferSdp onResponse unsuccess sendOfferSdp")
                            sendOfferSdp(sdp)
                        }
                    }
                }
            }
        })
    }

    private fun deletePublish(successBlock: (()->Unit)? = null, failureBlock: (()->Unit)? = null) {
        unpublishUrl?.let{ url ->
            val client = OkHttpClient()

            val requst: Request = Request.Builder()
                .url(url)
                .delete()
                .build()
            client.newCall(requst).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "deletePublish onFailure: $e")
                    failureBlock?.invoke()
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(1000L)
                        Log.e(TAG, "deletePublish onFailure deletePublish")
                        deletePublish(successBlock, failureBlock)
                    }
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        Log.e(TAG, "deletePublish onResponse: " + response.body!!.string())
                        successBlock?.invoke()
                    } else {
                        failureBlock?.invoke()
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(1000L)
                            Log.e(TAG, "deletePublish onFailure deletePublish")
                            deletePublish(successBlock, failureBlock)
                        }
                    }
                }
            })
        } ?: let {
            CoroutineScope(Dispatchers.IO).launch {
                delay(1000L)
                deletePublish(successBlock, failureBlock)
            }
        }
    }

    fun startAudioCapture() {
        executor.execute {
            if (localAudioSender == null) {
                return@execute
            }
            if (audioSource == null) {
                val track = createAudioTrack()
                localAudioSender?.setTrack(track, true)
            }

            audioDeviceModule?.resumeRecord()
        }
    }

    fun stopAudioCapture() {
        executor.execute {
            audioDeviceModule?.pauseRecord()
        }
    }

    fun setLocalVideoTrackEnabled(enable: Boolean) {
        executor.execute {
            Log.d(TAG, "${localVideoTrack?:"null"} setLocalVideoTrackEnabled $enable")
            localVideoTrack?.setEnabled(enable)
        }
    }

    fun setRemoteVideoTrackEnabled(enable: Boolean) {
        executor.execute {
            remoteVideoTrack?.setEnabled(enable)
        }
    }

    fun setLocalAudioTrackEnabled(enable: Boolean) {
        executor.execute {
            localAudioTrack?.setEnabled(enable)
        }
    }

    fun setRemoteAudioTrackEnabled(enable: Boolean) {
        remoteAudioEnabled = enable
        executor.execute {
            val track = remoteAudioTrack
            track?.setEnabled(enable)
        }
    }

    fun setRemoteAudioTrackVolume(volume: Int) {
        remoteAudioVolume = volume
        executor.execute {
            val track = remoteAudioTrack
            track?.setVolume(volume.toDouble())
        }
    }

    val isFrontCamera: Boolean
        get() {
            return cameraEnumerator?.let {
                it.isFrontFacing(cameraDeviceName)
            } ?: false
        }

    val isCameraZoomSupported: Boolean
        get() {
            return cameraVideoCapturer?.let {
                it.isZoomSupported
            } ?: false
        }

    val cameraMaxZoom: Int
        get() {
            return cameraVideoCapturer?.let {
                it.maxZoom
            } ?: 0
        }

    var cameraZoom: Int
        get() {
            return cameraVideoCapturer?.let {
                it.zoom
            } ?: 0
        }
        set(value) {
            cameraVideoCapturer?.let {
                it.zoom = value
            }
        }

    private fun createOffer() {
        executor.execute {
            if (peerConnection != null && !isError) {
                Log.d(TAG, "PC Create OFFER")
                isInitiator = true
                peerConnection!!.createOffer(sdpObserver, sdpMediaConstraints)
            }
        }
    }

    private fun createAnswer() {
        executor.execute {
            if (peerConnection != null && !isError) {
                Log.d(TAG, "PC create ANSWER")
                isInitiator = false
                peerConnection!!.createAnswer(sdpObserver, sdpMediaConstraints)
            }
        }
    }

    private fun addLocalIceCandidate(candidate: IceCandidate?) {
        executor.execute {
            if (!isError) {
                peerConnection?.addIceCandidate(candidate)
            }
        }
    }

    private fun addRemoteIceCandidate(candidate: IceCandidate) {
        executor.execute {
            if (peerConnection != null && !isError) {
                if (queuedRemoteCandidates != null) {
                    queuedRemoteCandidates!!.add(candidate)
                } else {
                    peerConnection!!.addIceCandidate(candidate)
                }
            }
        }
    }

    private fun removeRemoteIceCandidates(candidates: Array<IceCandidate>?) {
        executor.execute {
            if (peerConnection == null || isError) {
                return@execute
            }
            // Drain the queued remote candidates if there is any so that
            // they are processed in the proper order.
//            drainCandidates();
            peerConnection!!.removeIceCandidates(candidates)
        }
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        executor.execute {
            if (peerConnection == null || isError) {
                return@execute
            }
            var sdpDescription = sdp.description
            if (preferIsac) {
                sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true)
            }
            if (isPublish) {
                sdpDescription =
                    preferCodec(
                        sdpDescription,
                        getSdpVideoCodecName(peerConnectionParameters),
                        false
                    )
            }
            if (peerConnectionParameters.audioStartBitrate > 0) {
                sdpDescription = setStartBitrate(
                    AUDIO_CODEC_OPUS,
                    false,
                    sdpDescription,
                    peerConnectionParameters.audioStartBitrate
                )
            }
            Log.d(TAG, "Set remote SDP.")
            val sdpRemote = SessionDescription(sdp.type, sdpDescription)
            peerConnection!!.setRemoteDescription(sdpObserver, sdpRemote)
        }
    }

    fun startVideoSource(frontCamera: Boolean) {
        executor.execute {
            if (localVideoSender == null) {
                return@execute
            }
            if (videoCapturer == null || !(videoCapturer is CameraVideoCapturer)) {
                val track = createVideoTrack(false)
                localVideoSender?.setTrack(track, true)
            }

            try {
                videoCapturer?.stopCapture()
            } catch (e: InterruptedException) {
            }

            this.videoCapturer = createVideoCapturer(frontCamera)?.apply {
                initialize(
                    surfaceTextureHelper,
                    appContext,
                    videoSource!!.capturerObserver
                )
                Log.d(TAG, "Restart video source.")
                val size = RTCUtils.getVideoResolution(videoParam.videoResolution)
                startCapture(size.width, size.height, videoParam.videoFps)
            }
        }
    }

    fun stopVideoSource() {
        executor.execute {
            if (videoCapturer != null && !videoCapturerStopped) {
                Log.d(TAG, "Stop video source.")
                try {
                    videoCapturer!!.stopCapture()
                } catch (e: InterruptedException) {
                }
            }
        }
    }

    private fun changeVideoSource(width: Int, height: Int, framerate: Int) {
        executor.execute {
            if (!videoCapturerStopped) {
                videoCapturer?.let {
                    Log.d(TAG, "change video source.")
                    it.changeCaptureFormat(width, height, framerate)
                }
            }
        }
    }

    fun setVideoMaxBitrate(maxBitrateKbps: Int?) {
        executor.execute {
            if (peerConnection == null || localVideoSender == null || isError) {
                return@execute
            }
            Log.d(TAG, "Requested max video bitrate: $maxBitrateKbps")
            if (localVideoSender == null) {
                Log.w(TAG, "Sender is not ready.")
                return@execute
            }
            val parameters = localVideoSender!!.parameters
            if (parameters.encodings.size == 0) {
                Log.w(TAG, "RtpParameters are not ready.")
                return@execute
            }
            for (encoding in parameters.encodings) {
                // Null value means no limit.
                encoding.maxBitrateBps =
                    if (maxBitrateKbps == null) null else maxBitrateKbps * BPS_IN_KBPS
            }
            if (!localVideoSender!!.setParameters(parameters)) {
                Log.e(TAG, "RtpSender.setParameters failed.")
            }
            Log.d(TAG, "Configured max video bitrate to: $maxBitrateKbps")
        }
    }

    fun setVideoBitrate(minBitrateKbps: Int?, maxBitrateKbps: Int?) {
        executor.execute {
            if (peerConnection == null || localVideoSender == null || isError) {
                return@execute
            }
            Log.d(TAG, "Requested min video bitrate: $minBitrateKbps")
            Log.d(TAG, "Requested max video bitrate: $maxBitrateKbps")
            if (localVideoSender == null) {
                Log.w(TAG, "Sender is not ready.")
                return@execute
            }
            val parameters = localVideoSender!!.parameters
            if (parameters.encodings.size == 0) {
                Log.w(TAG, "RtpParameters are not ready.")
                return@execute
            }
            for (encoding in parameters.encodings) {
                // Null value means no limit.
                encoding.maxBitrateBps = if (maxBitrateKbps == null) null else maxBitrateKbps * BPS_IN_KBPS
                encoding.minBitrateBps = if (minBitrateKbps == null) null else minBitrateKbps * BPS_IN_KBPS
            }
            if (!localVideoSender!!.setParameters(parameters)) {
                Log.e(TAG, "RtpSender.setParameters failed.")
            }
            Log.d(TAG, "Configured min video bitrate to: $minBitrateKbps")
            Log.d(TAG, "Configured max video bitrate to: $maxBitrateKbps")
        }
    }

    private fun reportError(errorMessage: String) {
        Log.e(TAG, "Peerconnection error: $errorMessage")
        executor.execute {
            if (!isError) {
                events.onPeerConnectionError(this, errorMessage)
                isError = true
            }
        }
    }

    private fun createAudioTrack(): AudioTrack? {
        audioSource = factory!!.createAudioSource(audioConstraints)
        localAudioTrack = factory!!.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        //        localAudioTrack.setEnabled(false);
//        audioDeviceModule!!.pauseRecord()
        return localAudioTrack
    }

    private fun createVideoTrack(isScreencast: Boolean): VideoTrack? {
        surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
        videoSource = factory!!.createVideoSource(isScreencast)

        localVideoTrack = factory!!.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        localVideoTrack?.setEnabled(true)
        localVideoTrack?.addSink(localRender)

        videoSource?.setVideoProcessor(object : VideoProcessor {
            override fun onFrameCaptured(frame: VideoFrame, parameters: FrameAdaptationParameters) {
//                applyFrameAdaptationParameters(frame, parameters)?.let {
//                    this.onFrameCaptured(it)
//                    it.release()
//                }
                this.onFrameCaptured(frame)
            }

            override fun setSink(videoSink: VideoSink?) {
                frameVideoSink = videoSink
            }

            override fun onCapturerStarted(b: Boolean) {
                videoCapturerStopped = false
            }

            override fun onCapturerStopped() {
                videoCapturerStopped = true
            }

            override fun onFrameCaptured(videoFrame: VideoFrame) {
                frameVideoSink?.onFrame(videoFrame)
            }
        })
        return localVideoTrack
    }

    private val remoteAudioTrack: AudioTrack?
        get() {
            if (peerConnection != null) {
                for (receiver in peerConnection!!.receivers) {
                    val track = receiver.track()
                    if (track is AudioTrack) {
                        return track
                    }
                }
            }
            return null
        }

    private fun drainCandidates() {
        queuedRemoteCandidates?.let {
            Log.d(TAG, "Add ${it.size} remote candidates")
            for (candidate in it) {
                peerConnection!!.addIceCandidate(candidate)
            }
            queuedRemoteCandidates = null
        }
    }

    private fun switchCameraInternal() {
        if (videoCapturer is CameraVideoCapturer) {
            if (!isPublish || isError) {
                Log.e(
                    TAG,
                    "Failed to switch camera. Video: $isPublish . Error : $isError"
                )
                return  // No video is sent or only one camera is available or error happened.
            }
            Log.d(TAG, "Switch camera")
            val cameraVideoCapturer = videoCapturer as CameraVideoCapturer
            cameraVideoCapturer.switchCamera(null)
        } else {
            Log.d(TAG, "Will not switch camera, video caputurer is not a camera")
        }
    }

    fun switchCamera() {
        executor.execute { this.switchCameraInternal() }
    }

    fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        executor.execute { changeCaptureFormatInternal(width, height, framerate) }
    }

    private fun changeCaptureFormatInternal(width: Int, height: Int, framerate: Int) {
        if (!isPublish || isError || videoCapturer == null) {
            Log.e(
                TAG,
                "Failed to change capture format. Video: $isPublish"
                        + ". Error : " + isError
            )
            return
        }
        Log.d(TAG, "changeCaptureFormat: " + width + "x" + height + "@" + framerate)
        videoSource!!.adaptOutputFormat(width, height, framerate)
    }

    fun startScreenCapture() {
        executor.execute {
            if (localVideoSender == null) {
                return@execute
            }
            if (videoCapturer == null || !(videoCapturer is ScreenCapturerAndroid)) {
                val track = createVideoTrack(true)
                localVideoSender?.setTrack(track, true)
            }

            try {
                videoCapturer?.stopCapture()
            } catch (e: InterruptedException) {
            }

            if (mediaProjectionPermissionResultData != null) {
                this.videoCapturer = createScreenCapturer().apply {
                    initialize(
                        surfaceTextureHelper,
                        appContext,
                        videoSource!!.capturerObserver
                    )
                    Log.d(TAG, "Restart video source.")
                    val size = RTCUtils.getVideoResolution(screenVideoEncParam.videoResolution)
                    startCapture(size.width, size.height, screenVideoEncParam.videoFps)

                    setScreenEncParamCapture(screenVideoEncParam)
                }
            } else {
                ActivityUtils.getTopActivity()?.let {
                    it.startActivity(Intent(it, WXScreenCaptureAssistantActivity::class.java))

                    CoroutineScope(Dispatchers.IO).launch {
                        while (true) {
                            delay(100L)
                            if (mediaProjectionPermissionResultData != null) {
                                Log.d(TAG, "WXScreenCaptureAssistantActivity mediaProjectionPermissionResultData")

                                withContext(Dispatchers.Main) {
                                    this@PeerConnectionClient.videoCapturer = createScreenCapturer().apply {
                                        initialize(
                                            surfaceTextureHelper,
                                            appContext,
                                            videoSource!!.capturerObserver
                                        )
                                        Log.d(TAG, "Restart video source.")
                                        val size = RTCUtils.getVideoResolution(screenVideoEncParam.videoResolution)
                                        startCapture(size.width, size.height, screenVideoEncParam.videoFps)

                                        setScreenEncParamCapture(screenVideoEncParam)
                                    }
                                }
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    fun stopScreenCapture() {
        executor.execute {
            if (localVideoSender == null) {
                return@execute
            }

            videoCapturer?.let {
                if (it is ScreenCapturerAndroid){
                    it.stopCapture()
                }
            }
        }
    }

    fun pauseScreenCapture() {
        executor.execute {
            if (videoCapturer == null || !(videoCapturer is ScreenCapturerAndroid)) {
                return@execute
            }

            localVideoSender?.track()?.setEnabled(false)
        }
    }

    fun resumeScreenCapture() {
        executor.execute {
            if (videoCapturer == null || !(videoCapturer is ScreenCapturerAndroid)) {
                return@execute
            }

            localVideoSender?.track()?.setEnabled(true)
        }
    }

    fun sendMessage(message: String) {
        if (dataChannel == null) {
            return
        }

        var returnBuffer: DataChannel.Buffer? = null
        try {
            returnBuffer =
                DataChannel.Buffer(ByteBuffer.wrap(message.toByteArray(charset("UTF-8"))), false)
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }
        returnBuffer?.let {
            dataChannel!!.send(it)
        }
    }

    // Implementation detail: observe ICE & stream changes and react accordingly.
    private inner class PCObserver : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            executor.execute {
                Log.d(
                    TAG,
                    "onIceCandidate iceGatheringState: " + peerConnection!!.iceGatheringState()
                )
                if (peerConnectionParameters.loopback) {
                    addRemoteIceCandidate(candidate)
                }
                events.onIceCandidate(this@PeerConnectionClient, candidate)

                if (candidate.sdp.contains("host")) {
                    iceHostGet = true
                } else if (candidate.sdp.contains("srflx")) {
                    iceStunGet = true
                } else if (candidate.sdp.contains("relay")) {
                    iceTurnGet = true
                }
                if (iceHostGet && (iceStunGet || iceTurnGet) && !iceComplete) {
                    iceComplete = true
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(1000L)
                        sendOfferSdp(peerConnection!!.localDescription)
                        events.onIceGatheringComplete(
                            this@PeerConnectionClient, peerConnection!!.localDescription
                        )
                    }
                }
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
            executor.execute {
                if (peerConnectionParameters.loopback) {
                    removeRemoteIceCandidates(candidates)
                }
                events.onIceCandidatesRemoved(this@PeerConnectionClient, candidates)
            }
        }

        override fun onSignalingChange(newState: SignalingState) {
            Log.d(TAG, "SignalingState: $newState")
        }

        override fun onIceConnectionChange(newState: IceConnectionState) {
            executor.execute {
                Log.d(TAG, "IceConnectionState: $newState")
                if (newState == IceConnectionState.CONNECTED) {
                    events.onIceConnected(this@PeerConnectionClient)
                } else if (newState == IceConnectionState.DISCONNECTED) {
                    events.onIceDisconnected(this@PeerConnectionClient)
                } else if (newState == IceConnectionState.FAILED) {
                    reportError("ICE connection failed.")
                }
            }
        }

        override fun onConnectionChange(newState: PeerConnectionState) {
            executor.execute {
                Log.d(TAG, "PeerConnectionState: $newState")
                if (newState == PeerConnectionState.CONNECTED) {
                    iceHostGet = false
                    iceStunGet = false
                    iceTurnGet = false
                    iceComplete = false
                    enableStatsEvents(true, 1000)
                    if (!isPublish) {
                        stopVideoSource()
                    }

                    setRemoteAudioTrackEnabled(remoteAudioEnabled)

                    //                    setRemoteAudioTrackVolume(remoteAudioVolume);
                    events.onConnected(this@PeerConnectionClient)
                } else if (newState == PeerConnectionState.DISCONNECTED) {
                    events.onDisconnected(this@PeerConnectionClient)
                    peerConnection?.close()
                } else if (newState == PeerConnectionState.FAILED) {
                    reportError("DTLS connection failed.")
                } else if (newState == PeerConnectionState.CLOSED) {
                    if (!isClosing) {
                        peerConnection?.dispose()
                        peerConnection = null
                    }

                    if (isNeedReconnect) {
                        executor.execute {
                            CoroutineScope(Dispatchers.IO).launch {
                                withContext(Dispatchers.Main) {
                                    isError = false
                                    try {
                                        createPeerConnectionInternal()
                                    } catch (e: Exception) {
                                        reportError("Failed to create peer connection: " + e.message)
                                        throw e
                                    }
                                    startCall(localRender, remoteSink)
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun onIceGatheringChange(newState: IceGatheringState) {
            executor.execute {
                Log.d(TAG, "IceGatheringState: $newState")
                if (newState == IceGatheringState.COMPLETE && !iceComplete) {
                    iceComplete = true
                    sendOfferSdp(peerConnection!!.localDescription)
                    events.onIceGatheringComplete(
                        this@PeerConnectionClient,
                        peerConnection!!.localDescription
                    )
                }
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(TAG, "IceConnectionReceiving changed to $receiving")
        }

        override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent) {
            Log.d(TAG, "Selected candidate pair changed because: $event")
        }

        override fun onAddStream(stream: MediaStream) {
            Log.d(TAG, "onAddStream: ")
        }

        override fun onRemoveStream(stream: MediaStream) {
            Log.d(TAG, "onRemoveStream: ")
        }

        override fun onDataChannel(dc: DataChannel) {
            Log.d(TAG, "New Data channel " + dc.label())
            if (!dataChannelEnabled) {
                return
            }

            dc.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {
                    Log.d(
                        TAG,
                        "Data channel buffered amount changed: " + dc.label() + ": " + dc.state()
                    )
                }

                override fun onStateChange() {
                    Log.d(TAG, "Data channel state changed: " + dc.label() + ": " + dc.state())
                }

                override fun onMessage(buffer: DataChannel.Buffer) {
                    if (buffer.binary) {
                        Log.d(TAG, "Received binary msg over $dc")
                        return
                    }
                    val data = buffer.data
                    val bytes = ByteArray(data.capacity())
                    data[bytes]
                    val strData = String(bytes, Charset.forName("UTF-8"))
                    Log.d(TAG, "Got msg: $strData over $dc")
                }
            })
        }

        override fun onRenegotiationNeeded() {
            // No need to do anything; AppRTC follows a pre-agreed-upon
            // signaling/negotiation protocol.
        }

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
            Log.d(TAG, "onAddTrack: ")
        }
    }

    // Implementation detail: handle offer creation/signaling and answer setting,
    // as well as adding remote ICE candidates once the answer SDP is set.
    private inner class SDPObserver : SdpObserver {
        override fun onCreateSuccess(origSdp: SessionDescription) {
//            if (localSdp != null) {
//                reportError("Multiple SDP create.")
//                return
//            }
            var sdpDescription = origSdp.description
            if (!sdpDescription.contains("a=extmap-allow-mixed")) {
                val lines = sdpDescription.split("\r\n".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                var inertIndex = -1
                for (i in lines.indices) {
                    val line = lines[i]
                    if (line.startsWith("a=group")) {
                        inertIndex = i
                    }
                }
                if (inertIndex >= 0) {
                    val newLinesList: MutableList<String?> = ArrayList()
                    newLinesList.addAll(Arrays.asList(*lines).subList(0, inertIndex))
                    newLinesList.add("a=extmap-allow-mixed")
                    newLinesList.addAll(Arrays.asList(*lines).subList(inertIndex, lines.size - 1))
                    sdpDescription = joinString(newLinesList, "\r\n", true)
                }
            }
            if (preferIsac) {
                sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true)
            }
            if (isPublish) {
                sdpDescription =
                    preferCodec(
                        sdpDescription,
                        getSdpVideoCodecName(peerConnectionParameters),
                        false
                    )
            }
            val sdp = SessionDescription(origSdp.type, sdpDescription)
            localSdp = sdp
            executor.execute {
                if (peerConnection != null && !isError) {
                    Log.d(TAG, "Set local SDP from " + sdp.type)
                    peerConnection!!.setLocalDescription(sdpObserver, sdp)
                }
            }
        }

        override fun onSetSuccess() {
            executor.execute {
                if (peerConnection == null || isError) {
                    return@execute
                }
                if (isInitiator) {
                    // For offering peer connection we first create offer and set
                    // local SDP, then after receiving answer set remote SDP.
                    if (peerConnection!!.remoteDescription == null) {
                        // We've just set our local SDP so time to send it.
                        Log.d(TAG, "Local SDP set succesfully")
                        localSdp?.let {
                            events.onLocalDescription(this@PeerConnectionClient, it)
                        }
                    } else {
                        // We've just set remote description, so drain remote
                        // and send local ICE candidates.
                        Log.d(TAG, "Remote SDP set succesfully")
                        drainCandidates()
                    }
                } else {
                    // For answering peer connection we set remote SDP and then
                    // create answer and set local SDP.
                    if (peerConnection!!.localDescription != null) {
                        // We've just set our local SDP so time to send it, drain
                        // remote and send local ICE candidates.
                        Log.d(TAG, "Local SDP set succesfully")
                        localSdp?.let {
                            events.onLocalDescription(this@PeerConnectionClient, it)
                        }
                        drainCandidates()
                    } else {
                        // We've just set remote SDP - do nothing for now -
                        // answer will be created soon.
                        Log.d(TAG, "Remote SDP set succesfully")
                    }
                }
            }
        }

        override fun onCreateFailure(error: String) {
            reportError("createSDP error: $error")
        }

        override fun onSetFailure(error: String) {
            reportError("setSDP error: $error")
        }
    }

    //    private class DataChannelObserver implements DataChannel.Observer {
    //
    //        @Override
    //        public void onBufferedAmountChange(long previousAmount) {
    //            Log.d(TAG, "Data channel buffered amount changed: " + dc.label() + ": " + dc.state());
    //        }
    //
    //        @Override
    //        public void onStateChange() {
    //            Log.d(TAG, "Data channel state changed: " + dc.label() + ": " + dc.state());
    //        }
    //
    //        @Override
    //        public void onMessage(final DataChannel.Buffer buffer) {
    //            if (buffer.binary) {
    //                Log.d(TAG, "Received binary msg over " + dc);
    //                return;
    //            }
    //            ByteBuffer data = buffer.data;
    //            final byte[] bytes = new byte[data.capacity()];
    //            data.get(bytes);
    //            String strData = new String(bytes, Charset.forName("UTF-8"));
    //            Log.d(TAG, "Got msg: " + strData + " over " + dc);
    //        }
    //    }

    companion object {
        const val VIDEO_TRACK_ID: String = "ARDAMSv0"
        const val AUDIO_TRACK_ID: String = "ARDAMSa0"
        const val VIDEO_TRACK_TYPE: String = "video"
        private const val TAG = "PCRTCClient"
        private const val VIDEO_CODEC_VP8 = "VP8"
        private const val VIDEO_CODEC_VP9 = "VP9"
        private const val VIDEO_CODEC_H264 = "H264"
        private const val VIDEO_CODEC_H264_BASELINE = "H264 Baseline"
        private const val VIDEO_CODEC_H264_HIGH = "H264 High"
        private const val AUDIO_CODEC_OPUS = "opus"
        private const val AUDIO_CODEC_ISAC = "ISAC"
        private const val VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate"
        private const val VIDEO_FLEXFEC_FIELDTRIAL =
            "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/"
        private const val VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL = "WebRTC-IntelVP8/Enabled/"
        private const val DISABLE_WEBRTC_AGC_FIELDTRIAL =
            "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/"
        private const val AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate"
        private const val AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation"
        private const val AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl"
        private const val AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter"
        private const val AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression"
        private const val DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement"
        private const val HD_VIDEO_WIDTH = 1280
        private const val HD_VIDEO_HEIGHT = 720
        private const val BPS_IN_KBPS = 1000
        private const val RTCEVENTLOG_OUTPUT_DIR_NAME = "rtc_event_log"
        private fun getSdpVideoCodecName(parameters: PeerConnectionParameters): String {
            return when (parameters.videoCodec) {
                VIDEO_CODEC_VP8 -> VIDEO_CODEC_VP8
                VIDEO_CODEC_VP9 -> VIDEO_CODEC_VP9
                VIDEO_CODEC_H264_HIGH, VIDEO_CODEC_H264_BASELINE -> VIDEO_CODEC_H264
                else -> VIDEO_CODEC_VP8
            }
        }

        private fun getFieldTrials(peerConnectionParameters: PeerConnectionParameters): String {
            var fieldTrials = ""
            if (peerConnectionParameters.videoFlexfecEnabled) {
                fieldTrials += VIDEO_FLEXFEC_FIELDTRIAL
                Log.d(TAG, "Enable FlexFEC field trial.")
            }
            //        fieldTrials += VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;
            if (peerConnectionParameters.disableWebRtcAGCAndHPF) {
                fieldTrials += DISABLE_WEBRTC_AGC_FIELDTRIAL
                Log.d(TAG, "Disable WebRTC AGC field trial.")
            }
            return fieldTrials
        }

        private fun setStartBitrate(
            codec: String, isVideoCodec: Boolean, sdpDescription: String, bitrateKbps: Int
        ): String {
            val lines =
                sdpDescription.split("\r\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var rtpmapLineIndex = -1
            var sdpFormatUpdated = false
            var codecRtpMap: String? = null
            // Search for codec rtpmap in format
            // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
            var regex = "^a=rtpmap:(\\d+) $codec(/\\d+)+[\r]?$"
            var codecPattern = Pattern.compile(regex)
            for (i in lines.indices) {
                val codecMatcher = codecPattern.matcher(lines[i])
                if (codecMatcher.matches()) {
                    codecRtpMap = codecMatcher.group(1)
                    rtpmapLineIndex = i
                    break
                }
            }
            if (codecRtpMap == null) {
                Log.w(TAG, "No rtpmap for $codec codec")
                return sdpDescription
            }
            Log.d(
                TAG,
                "Found " + codec + " rtpmap " + codecRtpMap + " at " + lines[rtpmapLineIndex]
            )
            // Check if a=fmtp string already exist in remote SDP for this codec and
            // update it with new bitrate parameter.
            regex = "^a=fmtp:$codecRtpMap \\w+=\\d+.*[\r]?$"
            codecPattern = Pattern.compile(regex)
            for (i in lines.indices) {
                val codecMatcher = codecPattern.matcher(lines[i])
                if (codecMatcher.matches()) {
                    Log.d(TAG, "Found " + codec + " " + lines[i])
                    if (isVideoCodec) {
                        lines[i] += "; " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps
                    } else {
                        lines[i] += "; " + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000)
                    }
                    Log.d(TAG, "Update remote SDP line: " + lines[i])
                    sdpFormatUpdated = true
                    break
                }
            }
            val newSdpDescription = StringBuilder()
            for (i in lines.indices) {
                newSdpDescription.append(lines[i]).append("\r\n")
                // Append new a=fmtp line if no such line exist for a codec.
                if (!sdpFormatUpdated && i == rtpmapLineIndex) {
                    var bitrateSet = if (isVideoCodec) {
                        "a=fmtp:" + codecRtpMap + " " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps
                    } else {
                        ("a=fmtp:" + codecRtpMap + " " + AUDIO_CODEC_PARAM_BITRATE + "="
                                + (bitrateKbps * 1000))
                    }
                    Log.d(TAG, "Add remote SDP line: $bitrateSet")
                    newSdpDescription.append(bitrateSet).append("\r\n")
                }
            }
            return newSdpDescription.toString()
        }

        /**
         * Returns the line number containing "m=audio|video", or -1 if no such line exists.
         */
        private fun findMediaDescriptionLine(isAudio: Boolean, sdpLines: Array<String>): Int {
            val mediaDescription = if (isAudio) "m=audio " else "m=video "
            for (i in sdpLines.indices) {
                if (sdpLines[i].startsWith(mediaDescription)) {
                    return i
                }
            }
            return -1
        }

        private fun joinString(
            s: Iterable<CharSequence?>, delimiter: String, delimiterAtEnd: Boolean
        ): String {
            val iter = s.iterator()
            if (!iter.hasNext()) {
                return ""
            }
            val buffer = StringBuilder(iter.next()!!)
            while (iter.hasNext()) {
                buffer.append(delimiter).append(iter.next())
            }
            if (delimiterAtEnd) {
                buffer.append(delimiter)
            }
            return buffer.toString()
        }

        private fun movePayloadTypesToFront(
            preferredPayloadTypes: List<String?>, mLine: String
        ): String? {
            // The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
            val origLineParts =
                Arrays.asList(*mLine.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray())
            if (origLineParts.size <= 3) {
                Log.e(TAG, "Wrong SDP media description format: $mLine")
                return null
            }
            val header: List<String?> = origLineParts.subList(0, 3)
            val unpreferredPayloadTypes: MutableList<String?> =
                ArrayList(origLineParts.subList(3, origLineParts.size))
            unpreferredPayloadTypes.removeAll(preferredPayloadTypes)
            // Reconstruct the line with |preferredPayloadTypes| moved to the beginning of the payload
            // types.
            val newLineParts: MutableList<String?> = ArrayList()
            newLineParts.addAll(header)
            newLineParts.addAll(preferredPayloadTypes)
            newLineParts.addAll(unpreferredPayloadTypes)
            return joinString(newLineParts, " ", false /* delimiterAtEnd */)
        }

        private fun preferCodec(sdpDescription: String, codec: String, isAudio: Boolean): String {
            val lines =
                sdpDescription.split("\r\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val mLineIndex = findMediaDescriptionLine(isAudio, lines)
            if (mLineIndex == -1) {
                Log.w(TAG, "No mediaDescription line, so can't prefer $codec")
                return sdpDescription
            }
            // A list with all the payload types with name |codec|. The payload types are integers in the
            // range 96-127, but they are stored as strings here.
            val codecPayloadTypes: MutableList<String?> = ArrayList()
            // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
            val codecPattern = Pattern.compile("^a=rtpmap:(\\d+) $codec(/\\d+)+[\r]?$")
            for (line in lines) {
                val codecMatcher = codecPattern.matcher(line)
                if (codecMatcher.matches()) {
                    codecPayloadTypes.add(codecMatcher.group(1))
                }
            }
            if (codecPayloadTypes.isEmpty()) {
                Log.w(TAG, "No payload types with name $codec")
                return sdpDescription
            }
            val newMLine = movePayloadTypesToFront(codecPayloadTypes, lines[mLineIndex])
                ?: return sdpDescription
            Log.d(TAG, "Change media description from: " + lines[mLineIndex] + " to " + newMLine)
            lines[mLineIndex] = newMLine
            return joinString(Arrays.asList(*lines), "\r\n", true /* delimiterAtEnd */)
        }

        var mediaProjectionPermissionResultData : Intent? = null
    }
}

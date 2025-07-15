package com.wx.rtc.rtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import com.wx.rtc.PlatformContext
import com.wx.rtc.RTCVideoContainerView
import com.wx.rtc.WXRTCDef.WXRTCVideoEncParam
import com.wx.rtc.utils.AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT
import com.wx.rtc.utils.AUDIO_CODEC_ISAC
import com.wx.rtc.utils.AUDIO_CODEC_OPUS
import com.wx.rtc.utils.AUDIO_ECHO_CANCELLATION_CONSTRAINT
import com.wx.rtc.utils.AUDIO_HIGH_PASS_FILTER_CONSTRAINT
import com.wx.rtc.utils.AUDIO_NOISE_SUPPRESSION_CONSTRAINT
import com.wx.rtc.utils.AUDIO_TRACK_ID
import com.wx.rtc.utils.ActivityUtils
import com.wx.rtc.utils.BPS_IN_KBPS
import com.wx.rtc.utils.RTCEVENTLOG_OUTPUT_DIR_NAME
import com.wx.rtc.utils.RTCUtils.getVideoResolution
import com.wx.rtc.utils.VIDEO_CODEC_H264_HIGH
import com.wx.rtc.utils.VIDEO_TRACK_ID
import com.wx.rtc.utils.getFieldTrials
import com.wx.rtc.utils.getSdpVideoCodecName
import com.wx.rtc.utils.joinString
import com.wx.rtc.utils.preferCodec
import com.wx.rtc.utils.setStartBitrate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import org.webrtc.PeerConnectionDependencies
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
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
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
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


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
internal actual class PeerConnectionClient actual constructor(
    private val context: PlatformContext,
    private val isPublish: Boolean,
    private val sendSdpUrl: String,
    private val events: PeerConnectionEvents,
) {
    private val appContext: Context = context.applicationContext
//    private val events: PeerConnectionEvents
    private val networkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val clientScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
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
    val localVideoSink = ProxyVideoSink()
    val remoteVideoSink = ProxyVideoSink()
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
    private var localVideoContainerView: RTCVideoContainerView? = null
    private var remoteVideoContainerView: RTCVideoContainerView? = null
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
    private var remoteAudioVolume = -1
    private var callStartedTimeMs: Long = 0

    private var cameraDeviceName: String? = null
    private var cameraEnumerator: CameraEnumerator? = null
    private var cameraVideoCapturer: CameraVideoCapturer? = null

    actual var isNeedReconnect: Boolean = true
    actual var unpublishUrl: String? = null

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
                    .setFieldTrials(fieldTrials).setEnableInternalTracer(true)
                    .createInitializationOptions()
            )
        }
    }

    actual fun setVideoEncParam(param: WXRTCVideoEncParam) {
        if (videoParam.videoResolution != param.videoResolution || videoParam.videoFps != param.videoFps) {
            val size = getVideoResolution(param.videoResolutionMode, param.videoResolution)
            changeVideoSource(size.width, size.height, param.videoFps)
        }
        if (videoParam.videoMinBitrate != param.videoMinBitrate || videoParam.videoMaxBitrate != param.videoMaxBitrate) {
            setVideoBitrate(param.videoMinBitrate, param.videoMaxBitrate)
        }
        videoParam = param
        screenVideoEncParam = param
    }

    actual fun setScreenEncParamCapture(param: WXRTCVideoEncParam) {
//        if (screenVideoEncParam.videoResolution != param.videoResolution || screenVideoEncParam.videoFps != param.videoFps) {
            val size = getVideoResolution(param.videoResolutionMode, param.videoResolution)
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
    actual fun createPeerConnectionFactory() {
        val options = PeerConnectionFactory.Options()
        options.networkIgnoreMask = 0
        check(factory == null) { "PeerConnectionFactory has already been constructed" }
        executor.execute {
            createPeerConnectionFactoryInternal(options)
        }
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

    actual fun startCall() {
        callStartedTimeMs = System.currentTimeMillis()
        executor.execute {
            onConnectedToRoomInternal()
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
                override fun onCameraError(errorDescription: String?) {
                }

                override fun onCameraDisconnected() {
                }

                override fun onCameraFreezed(errorDescription: String?) {

                }

                override fun onCameraOpening(cameraName: String?) {
                    cameraDeviceName = cameraName
                }

                override fun onFirstFrameAvailable() {
                }

                override fun onCameraClosed(cameraName: String?) {
                    cameraDeviceName?.let {
                        if (it == cameraName) {
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

    actual fun close() {
        executor.execute {
            closeInternal()
        }
    }

    actual val isPublishClient: Boolean
        get() = isPublish

    actual val isCameraOpened: Boolean
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
                eglBase.eglBaseContext, false,  /* enableIntelVp8Encoder */enableH264HighProfile
            )
            decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
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
            val size = getVideoResolution(videoParam.videoResolutionMode, videoParam.videoResolution)
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
//        peerConnection = factory!!.createPeerConnection(rtcConfig, pcObserver)
        peerConnection = factory!!.createPeerConnection(rtcConfig, PeerConnectionDependencies.builder(pcObserver).createPeerConnectionDependencies())
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

            localVideoTrack?.let {
                if (it != localVideoSender?.track() && !it.isDispose) {
                    localVideoSender?.setTrack(it, true)
                }
            }
            localAudioTrack?.let {
                if (it != localAudioSender?.track() && !it.isDispose) {
                    localAudioSender?.setTrack(it, true)
                }
            }
        } else {
            remoteVideoTrack = peerConnection!!.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO).receiver.track() as VideoTrack?
            // We can add the renderers right away because we don't need to wait for an
            // answer to get the remote track.
//            remoteVideoTrack = getRemoteVideoTrack();
            remoteVideoTrack!!.setEnabled(true)
            remoteVideoTrack!!.addSink(remoteVideoSink)
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
        peerConnection?.dispose()
        peerConnection = null
        localVideoSender = null
        localAudioSender = null
        localVideoTrack = null
        localAudioTrack = null
        localVideoContainerView?.getVideoView()?.release()
        localVideoContainerView?.removeVideoView()
        localVideoContainerView = null
        Log.d(TAG, "Closing peer connection factory.")
        factory?.dispose()
        factory = null
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
            peerConnection!!.getStats{ report->
                events.onPeerConnectionStatsReady(
                    this@PeerConnectionClient,
                    report.statsMap
                )
            }
        }

    private fun enableStatsEvents(enable: Boolean, periodMs: Int) {
        if (enable) {
            try {
                statsTimer.schedule(object : TimerTask() {
                    override fun run() {
                        executor.execute {
                            stats
                        }
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
                networkScope.launch {
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
                            networkScope.launch {
                                delay(200L)
                                sendOfferSdp(sdp)
                            }
                        })
                    }else{
                        networkScope.launch {
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
                    networkScope.launch {
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
                        networkScope.launch {
                            delay(1000L)
                            Log.e(TAG, "deletePublish onFailure deletePublish")
                            deletePublish(successBlock, failureBlock)
                        }
                    }
                }
            })
        } ?: let {
            networkScope.launch {
                delay(1000L)
                deletePublish(successBlock, failureBlock)
            }
        }
    }

    actual fun startAudioCapture() {
        executor.execute {
//            if (localAudioSender == null) {
//                return@execute
//            }
            if (audioSource == null && audioConstraints != null) {
                val track = createAudioTrack()
                localAudioSender?.let { sender->
                    if (track != sender.track() && !track!!.isDispose) {
                        sender.setTrack(track, true)
                    }
                }
            }

            audioDeviceModule?.resumeRecord()
        }
    }

    actual fun stopAudioCapture() {
        executor.execute {
            audioDeviceModule?.pauseRecord()
        }
    }

    actual fun setLocalVideoTrackEnabled(enable: Boolean) {
        executor.execute {
            Log.d(TAG, "${localVideoTrack ?: "null"} setLocalVideoTrackEnabled $enable")
            localVideoTrack?.setEnabled(enable)
        }
    }

    actual fun setRemoteVideoTrackEnabled(enable: Boolean) {
        executor.execute {
            if (remoteVideoTrack?.isDispose == false) remoteVideoTrack?.setEnabled(enable)
        }
    }

    actual fun setLocalAudioTrackEnabled(enable: Boolean) {
        executor.execute {
            if (localAudioTrack?.isDispose == false) localAudioTrack?.setEnabled(enable)
        }
    }

    actual fun setRemoteAudioTrackEnabled(enable: Boolean) {
        remoteAudioEnabled = enable
        executor.execute {
            val track = remoteAudioTrack
            track?.setEnabled(enable)
        }
    }

    actual fun setRemoteAudioTrackVolume(volume: Int) {
        remoteAudioVolume = volume
        executor.execute {
            val track = remoteAudioTrack
            track?.setVolume(volume.toDouble())
        }
    }

    actual val isFrontCamera: Boolean
        get() {
            return cameraEnumerator?.isFrontFacing(cameraDeviceName) == true
        }

    actual val isCameraZoomSupported: Boolean
        get() {
            return cameraVideoCapturer?.isZoomSupported == true
        }

    actual val cameraMaxZoom: Int
        get() {
            return cameraVideoCapturer?.maxZoom ?: 0
        }

    actual var cameraZoom: Int
        get() {
            return cameraVideoCapturer?.zoom ?: 0
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
            } // Drain the queued remote candidates if there is any so that
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
                sdpDescription = preferCodec(
                    sdpDescription, getSdpVideoCodecName(peerConnectionParameters), false
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

    private fun createVideoView(view: RTCVideoContainerView?){
        if (view != null && view.getVideoView() == null) {
            val videoView = SurfaceViewRenderer(appContext).apply {
                init(eglBase.eglBaseContext, null)
                setEnableHardwareScaler(true /* enabled */)
            }
            view.addVideoView(videoView)
        }
    }

    actual fun startVideoSource(frontCamera: Boolean, view: RTCVideoContainerView?) {
        if (frontCamera == isFrontCamera && view != null && localVideoContainerView != null && view == localVideoContainerView) {
            return
        }
        executor.execute {
//            if (localVideoSender == null) {
//                return@execute
//            }

            createVideoView(view)

            val track = createVideoTrack(false, view)
            localVideoSender?.let { sender ->
                if (sender.track() != track && !track!!.isDispose) {
                    sender.setTrack(track, true)
                }
            }

            videoCapturer?.let {
                try {
                    it.stopCapture()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }

            videoCapturer = createVideoCapturer(frontCamera)?.apply {
                initialize(
                    surfaceTextureHelper, appContext, videoSource!!.capturerObserver
                )
                Log.d(TAG, "Restart video source.")
                val size = getVideoResolution(
                    videoParam.videoResolutionMode,
                    videoParam.videoResolution
                )
                startCapture(size.width, size.height, videoParam.videoFps)
            }
        }
    }

    actual fun updateVideoSource(view: RTCVideoContainerView?) {
        if (view != null && localVideoContainerView != null && view == localVideoContainerView) {
            return
        }
        executor.execute {
            createVideoView(view)

//                localVideoContainerView?.removeVideoView()

//                view?.addVideoView(localRenderer)
            localVideoSink.setTarget(view?.getVideoView())
            if (view == null) {
                localVideoContainerView?.getVideoView()?.clearImage()
            }
            localVideoContainerView = view
        }
    }

    actual fun stopVideoSource() {
        executor.execute {
//                localVideoContainerView?.removeVideoView()
            localVideoSink.setTarget(null)
            localVideoContainerView?.getVideoView()?.clearImage()
            localVideoContainerView = null

            if (videoCapturer != null && !videoCapturerStopped) {
                Log.d(TAG, "Stop video source.")
                try {
                    videoCapturer!!.stopCapture()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
    }

    actual fun setRemoteView(view: RTCVideoContainerView?) {
        executor.execute {
            createVideoView(view)
//                remoteVideoContainerView?.removeVideoView()

//                view?.addVideoView(remoteRenderer)
            remoteVideoSink.setTarget(view?.getVideoView())
            if (view == null) {
                remoteVideoContainerView?.getVideoView()?.clearImage()
            }
            remoteVideoContainerView = view
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

    actual fun setVideoMaxBitrate(maxBitrateKbps: Int?) {
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
            if (parameters.encodings.isEmpty()) {
                Log.w(TAG, "RtpParameters are not ready.")
                return@execute
            }
            for (encoding in parameters.encodings) { // Null value means no limit.
                encoding.maxBitrateBps = if (maxBitrateKbps == null) null else maxBitrateKbps * BPS_IN_KBPS
            }
            if (!localVideoSender!!.setParameters(parameters)) {
                Log.e(TAG, "RtpSender.setParameters failed.")
            }
            Log.d(TAG, "Configured max video bitrate to: $maxBitrateKbps")
        }
    }

    actual fun setVideoBitrate(minBitrateKbps: Int?, maxBitrateKbps: Int?) {
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
            if (parameters.encodings.isEmpty()) {
                Log.w(TAG, "RtpParameters are not ready.")
                return@execute
            }
            for (encoding in parameters.encodings) { // Null value means no limit.
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

    private fun createVideoTrack(isScreencast: Boolean, view: RTCVideoContainerView?): VideoTrack? {
        surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        videoSource = factory!!.createVideoSource(isScreencast)

        localVideoTrack = factory!!.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        localVideoTrack?.setEnabled(true)
        localVideoTrack?.addSink(localVideoSink)

//        localVideoContainerView?.removeVideoView()

//        view?.addVideoView(localRenderer)
        localVideoSink.setTarget(view?.getVideoView())
        if (view == null) {
            localVideoContainerView?.getVideoView()?.clearImage()
        }
        localVideoContainerView = view

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
                        if (remoteAudioVolume != -1) {
                            track.setVolume(remoteAudioVolume.toDouble())
                        }
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

    actual fun switchCamera() {
        executor.execute {
            switchCameraInternal()
        }
    }

    private fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        executor.execute {
            changeCaptureFormatInternal(width, height, framerate)
        }
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

    actual fun startScreenCapture(view: RTCVideoContainerView?) {
        executor.execute {
            if (localVideoSender == null) {
                return@execute
            }
            mainScope.launch {
                try {
                    videoCapturer?.stopCapture()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

                if (mediaProjectionPermissionResultData != null) {
                    if (videoCapturer == null || videoCapturer !is ScreenCapturerAndroid) {
                        val track = createVideoTrack(true, view)
                        if (track != localVideoSender?.track() && !track!!.isDispose) {
                            localVideoSender?.setTrack(track, true)
                        }
                    }

                    videoCapturer = createScreenCapturer().apply {
                        initialize(
                            surfaceTextureHelper, appContext, videoSource!!.capturerObserver
                        )
                        Log.d(TAG, "Restart video source.")
                        val size = getVideoResolution(
                            screenVideoEncParam.videoResolutionMode,
                            screenVideoEncParam.videoResolution
                        )
                        startCapture(size.width, size.height, screenVideoEncParam.videoFps)
                        mediaProjectionPermissionRequest = false
                        mediaProjectionPermissionResultData = null

                        setScreenEncParamCapture(screenVideoEncParam)
                    }
                } else {
                    ActivityUtils.getTopActivity()?.let {
                        it.startActivity(Intent(it, WXScreenCaptureAssistantActivity::class.java))

                        withContext(Dispatchers.Default) {
                            while (true) {
                                delay(200L)
                                if (mediaProjectionPermissionRequest) {
                                    if (mediaProjectionPermissionResultData != null) {
                                        Log.d(
                                            TAG,
                                            "WXScreenCaptureAssistantActivity mediaProjectionPermissionResultData"
                                        )
                                        withContext(Dispatchers.Main) {
                                            if (videoCapturer == null || videoCapturer !is ScreenCapturerAndroid) {
                                                val track = createVideoTrack(true, view)
                                                if (track != localVideoSender?.track() && !track!!.isDispose) {
                                                    localVideoSender?.setTrack(track, true)
                                                }
                                            }

                                            videoCapturer = createScreenCapturer().apply {
                                                initialize(
                                                    surfaceTextureHelper,
                                                    appContext,
                                                    videoSource!!.capturerObserver
                                                )
                                                Log.d(TAG, "Restart video source.")
                                                val size = getVideoResolution(
                                                    screenVideoEncParam.videoResolutionMode,
                                                    screenVideoEncParam.videoResolution
                                                )
                                                startCapture(
                                                    size.width,
                                                    size.height,
                                                    screenVideoEncParam.videoFps
                                                )

                                                setScreenEncParamCapture(screenVideoEncParam)
                                            }
                                        }
                                    }
                                    mediaProjectionPermissionResultData = null
                                    mediaProjectionPermissionRequest = false
                                    break
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    actual fun stopScreenCapture() {
        executor.execute {
            if (localVideoSender == null) {
                return@execute
            }

            videoCapturer?.let {
                if (it is ScreenCapturerAndroid) {
                    it.stopCapture()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.stopService(
                    Intent(
                        context,
                        WXScreenCaptureAssistantService::class.java
                    )
                )
            }
        }
    }

    actual fun pauseScreenCapture() {
        executor.execute {
            if (videoCapturer == null || videoCapturer !is ScreenCapturerAndroid) {
                return@execute
            }

            localVideoSender?.track()?.setEnabled(false)
        }
    }

    actual fun resumeScreenCapture() {
        executor.execute {
            if (videoCapturer == null || videoCapturer !is ScreenCapturerAndroid) {
                return@execute
            }

            localVideoSender?.track()?.setEnabled(true)
        }
    }

    actual fun sendMessage(message: String) {
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

//                    setRemoteAudioTrackVolume(remoteAudioVolume)
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
                            CoroutineScope(Dispatchers.Main).launch {
                                isError = false
                                try {
                                    createPeerConnectionInternal()
                                } catch (e: Exception) {
                                    reportError("Failed to create peer connection: " + e.message)
                                    throw e
                                }
                                startCall()
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
                    newLinesList.addAll(listOf(*lines).subList(0, inertIndex))
                    newLinesList.add("a=extmap-allow-mixed")
                    newLinesList.addAll(listOf(*lines).subList(inertIndex, lines.size - 1))
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
        private const val TAG = "PCRTCClient.android"

        var mediaProjectionPermissionResultData : Intent? = null
        var mediaProjectionPermissionRequest : Boolean = false
        val eglBase: EglBase = EglBase.create()
    }
}

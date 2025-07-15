package com.wx.rtc.rtc

import com.wx.rtc.PlatformContext
import com.wx.rtc.RTCVideoContainerView
import com.wx.rtc.WXRTCDef.WXRTCVideoEncParam


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
internal expect class PeerConnectionClient internal constructor(
    context: PlatformContext,
    isPublish: Boolean,
    sendSdpUrl: String,
    events: PeerConnectionEvents,
) {
    var isNeedReconnect: Boolean
    var unpublishUrl: String?

    fun setVideoEncParam(param: WXRTCVideoEncParam)

    fun setScreenEncParamCapture(param: WXRTCVideoEncParam)

    /**
     * This function should only be called once.
     */
    fun createPeerConnectionFactory()

    fun startCall()

    fun close()

    val isPublishClient: Boolean

    val isCameraOpened: Boolean

    fun startAudioCapture()

    fun stopAudioCapture()

    fun setLocalVideoTrackEnabled(enable: Boolean)

    fun setRemoteVideoTrackEnabled(enable: Boolean)

    fun setLocalAudioTrackEnabled(enable: Boolean)

    fun setRemoteAudioTrackEnabled(enable: Boolean)

    fun setRemoteAudioTrackVolume(volume: Int)

    val isFrontCamera: Boolean

    val isCameraZoomSupported: Boolean

    val cameraMaxZoom: Int

    var cameraZoom: Int

    fun startVideoSource(frontCamera: Boolean, view: RTCVideoContainerView?)

    fun updateVideoSource(view: RTCVideoContainerView?)

    fun stopVideoSource()

    fun setRemoteView(view: RTCVideoContainerView?)

    fun setVideoMaxBitrate(maxBitrateKbps: Int?)

    fun setVideoBitrate(minBitrateKbps: Int?, maxBitrateKbps: Int?)

    fun switchCamera()

    fun startScreenCapture(view: RTCVideoContainerView?)

    fun stopScreenCapture()

    fun pauseScreenCapture()

    fun resumeScreenCapture()

    fun sendMessage(message: String)
}

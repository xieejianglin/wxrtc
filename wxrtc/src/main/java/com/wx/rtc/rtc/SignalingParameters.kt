package com.wx.rtc.rtc

import org.webrtc.IceCandidate
import org.webrtc.PeerConnection.IceServer
import org.webrtc.SessionDescription

internal class SignalingParameters(
    @JvmField val iceServers: List<IceServer>,
    val offerSdp: SessionDescription?,
    val iceCandidates: List<IceCandidate>
)

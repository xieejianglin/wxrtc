package com.wx.rtc.rtc

internal interface PeerConnectionEvents {
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
    fun onPeerConnectionStatsReady(pc: PeerConnectionClient, status: Map<String, Any>)

    /**
     * Callback fired once peer connection error happened.
     */
    fun onPeerConnectionError(pc: PeerConnectionClient, description: String)

    fun onDataChannelMessage(pc: PeerConnectionClient, message: String)
}
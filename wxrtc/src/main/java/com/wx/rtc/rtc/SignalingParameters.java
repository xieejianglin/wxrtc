package com.wx.rtc.rtc;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.List;

public class SignalingParameters {
    public final List<PeerConnection.IceServer> iceServers;
    public final SessionDescription offerSdp;
    public final List<IceCandidate> iceCandidates;

    public SignalingParameters(List<PeerConnection.IceServer> iceServers,
                               SessionDescription offerSdp,
                               List<IceCandidate> iceCandidates) {
        this.iceServers = iceServers;
        this.offerSdp = offerSdp;
        this.iceCandidates = iceCandidates;
    }
}

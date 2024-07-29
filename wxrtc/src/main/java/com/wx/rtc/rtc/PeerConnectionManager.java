package com.wx.rtc.rtc;

import com.wx.rtc.WXRTCDef;

public class PeerConnectionManager {
    public String userId;
    public String sendSdpUrl;
    public boolean needReconnect;
    public boolean videoRecvEnabled;
    public boolean videoRecvMute;
    public boolean audioRecvMute;
    public float audioVolume;
    public ProxyVideoSink videoSink;
    public PeerConnectionClient client;
    public WXRTCDef.WXRTCRenderParams renderParams;
}

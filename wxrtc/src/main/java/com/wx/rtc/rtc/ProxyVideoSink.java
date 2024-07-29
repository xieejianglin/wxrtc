package com.wx.rtc.rtc;

import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

public class ProxyVideoSink implements VideoSink {
    public String streamId;
    private VideoSink target;

    private VideoFrame frame;

    @Override
    synchronized public void onFrame(VideoFrame frame) {
        this.frame = frame;
        if (target == null) {
            return;
        }
        target.onFrame(frame);
    }

    synchronized public void setTarget(VideoSink target) {
        this.target = target;
    }

    synchronized public void setTarget(String streamId, VideoSink target) {
        this.streamId = streamId;
        this.target = target;
    }

    public VideoSink getTarget() {
        return this.target;
    }

    synchronized public void release() {
        if (target != null && target instanceof SurfaceViewRenderer){
            if (!((SurfaceViewRenderer)target).released()) {
                ((SurfaceViewRenderer)target).release();
            }
        }
        target = null;
    }

    public VideoFrame getFrame(){
        return frame;
    }
}

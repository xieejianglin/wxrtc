package com.wx.rtc.bean;

import org.webrtc.SurfaceViewRenderer;

public class RoomMemberEntity<T> {
    private String           userId;
    private String           userName;
    private String           userAvatar;
    private int              audioVolume;
    // 用户是否打开了视频
    private boolean          isVideoAvailable;
    // 用户是否打开音频
    private boolean          isAudioAvailable;
    // 是否对用户静画
    private boolean          isMuteVideo;
    // 是否对用户静音
    private boolean          isMuteAudio;
    private SurfaceViewRenderer mVideoView;

    private T                customData;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserAvatar() {
        return userAvatar;
    }

    public void setUserAvatar(String userAvatar) {
        this.userAvatar = userAvatar;
    }

    public int getAudioVolume() {
        return audioVolume;
    }

    public void setAudioVolume(int audioVolume) {
        this.audioVolume = audioVolume;
    }

    public boolean isVideoAvailable() {
        return isVideoAvailable;
    }

    public void setVideoAvailable(boolean videoAvailable) {
        isVideoAvailable = videoAvailable;
    }

    public boolean isAudioAvailable() {
        return isAudioAvailable;
    }

    public void setAudioAvailable(boolean audioAvailable) {
        isAudioAvailable = audioAvailable;
    }

    public boolean isMuteVideo() {
        return isMuteVideo;
    }

    public void setMuteVideo(boolean muteVideo) {
        isMuteVideo = muteVideo;
    }

    public boolean isMuteAudio() {
        return isMuteAudio;
    }

    public void setMuteAudio(boolean muteAudio) {
        isMuteAudio = muteAudio;
    }

    public SurfaceViewRenderer getVideoView() {
        return mVideoView;
    }

    public void setVideoView(SurfaceViewRenderer mVideoView) {
        this.mVideoView = mVideoView;
    }

    public T getCustomData() {
        return customData;
    }

    public void setCustomData(T customData) {
        this.customData = customData;
    }
}

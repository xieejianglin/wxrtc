package com.wx.rtc.bean

import org.webrtc.SurfaceViewRenderer

class RoomMemberEntity<T> {
    var userId: String? = null
    var userName: String? = null
    var userAvatar: String? = null
    var audioVolume: Int = 0

    // 用户是否打开了视频
    var isVideoAvailable: Boolean = false

    // 用户是否打开音频
    var isAudioAvailable: Boolean = false

    // 是否对用户静画
    var isMuteVideo: Boolean = false

    // 是否对用户静音
    var isMuteAudio: Boolean = false
    var videoView: SurfaceViewRenderer? = null

    var customData: T? = null
}

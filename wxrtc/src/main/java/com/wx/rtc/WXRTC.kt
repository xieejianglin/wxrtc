package com.wx.rtc

import android.content.Context
import android.media.AudioManager
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.wx.rtc.WXRTCDef.WXRTCRenderParams
import com.wx.rtc.WXRTCDef.WXRTCVideoEncParam
import com.wx.rtc.WXRTCDef.Speaker
import com.wx.rtc.bean.CallCommand
import com.wx.rtc.bean.ProcessCommand
import com.wx.rtc.bean.RecordCommand
import com.wx.rtc.bean.RoomMsg
import com.wx.rtc.bean.SendCommandMessage
import com.wx.rtc.bean.SendCommandMessage.CallCmdDTO
import com.wx.rtc.bean.SendCommandMessage.ProcessCmdListDTO
import com.wx.rtc.bean.SendCommandMessage.RecordCmdDTO
import com.wx.rtc.bean.SignalCommand
import com.wx.rtc.rtc.RTCListener
import com.wx.rtc.rtc.RTCManager
import com.wx.rtc.socket.SocketListener
import com.wx.rtc.socket.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer
import java.io.File
import java.lang.ref.WeakReference

abstract class WXRTC {
    var userId: String? = null
        protected set
    var roomId: String = ""
        protected set
    var isLogin: Boolean = false
        protected set
    var isEnterRoom: Boolean = false
        protected set

    abstract fun init(context: Context)

    abstract fun setRTCVideoParam(param: WXRTCVideoEncParam)

    abstract fun setRTCListener(listener: WXRTCListener?)

    abstract fun setCallListener(listener: WXCallListener?)

    abstract fun login(appId: String, userId: String)

    abstract fun logout()

    abstract fun enterRoom(roomId: String)

    abstract fun exitRoom()

    abstract fun inviteCall(inviteId: String, roomId: String)

    abstract fun cancelInvitation()

    abstract fun cancelInvitation(inviteId: String)

    abstract fun acceptInvitation()

    abstract fun acceptInvitation(inviteId: String)

    abstract fun rejectInvitation()

    abstract fun rejectInvitation(inviteId: String)

    abstract fun invitationLineBusy(inviteId: String)

    abstract fun hangupCall()

    abstract fun hangupCall(inviteId: String)

    abstract fun sendRoomMsg(cmd: String, msg: String)

    abstract fun startProcess()

    abstract fun endProcess()

    fun startRecord() {
        startRecord(null)
    }

    fun startRecord(
        mixId: String? = null) {
        startRecord(mixId,null)
    }

    fun startRecord(
        mixId: String? = null,
        extraData: String? = null) {
        startRecord(mixId, extraData, false)
    }

    fun startRecord(
        mixId: String? = null,
        extraData: String? = null,
        needAfterAsr: Boolean = false) {
        startRecord(mixId, extraData, needAfterAsr,null)
    }

    fun startRecord(
        mixId: String? = null,
        extraData: String? = null,
        needAfterAsr: Boolean = false,
        hospitalId: String? = null) {
        startRecord(mixId, extraData, needAfterAsr, hospitalId, null)
    }

    abstract fun startRecord(
        mixId: String? = null,
        extraData: String? = null,
        needAfterAsr: Boolean = false,
        hospitalId: String? = null,
        spkList: List<Speaker>? = null)

    fun endAndStartRecord() {
        endAndStartRecord(null)
    }

    fun endAndStartRecord(
        mixId: String? = null) {
        endAndStartRecord(mixId,null)
    }

    fun endAndStartRecord(
        mixId: String? = null,
        extraData: String? = null) {
        endAndStartRecord(mixId, extraData, false)
    }

    fun endAndStartRecord(
        mixId: String? = null,
        extraData: String? = null,
        needAfterAsr: Boolean = false) {
        endAndStartRecord(mixId, extraData, needAfterAsr,null)
    }

    fun endAndStartRecord(
        mixId: String? = null,
        extraData: String? = null,
        needAfterAsr: Boolean = false,
        hospitalId: String? = null) {
        endAndStartRecord(mixId, extraData, needAfterAsr, hospitalId, null)
    }

    abstract fun endAndStartRecord(
        mixId: String? = null,
        extraData: String? = null,
        needAfterAsr: Boolean = false,
        hospitalId: String? = null,
        spkList: List<Speaker>? = null)

    abstract fun endRecord()

    abstract fun startAsr(hospitalId: String?, spkList: List<Speaker>?)

    abstract fun endAndStartAsr(hospitalId: String?, spkList: List<Speaker>?)

    abstract fun endAsr()

    abstract fun startLocalVideo(frontCamera: Boolean, renderer: SurfaceViewRenderer?)

    abstract fun updateLocalVideo(renderer: SurfaceViewRenderer?)

    abstract fun stopLocalVideo()

    abstract fun muteLocalVideo(mute: Boolean)

    abstract fun startRemoteVideo(userId: String, renderer: SurfaceViewRenderer?)

    abstract fun stopRemoteVideo(userId: String)

    abstract fun stopAllRemoteVideo()

    abstract fun muteRemoteVideo(userId: String, mute: Boolean)

    abstract fun muteAllRemoteVideo(mute: Boolean)

    abstract fun setLocalRenderParams(params: WXRTCRenderParams)

    abstract fun setRemoteRenderParams(userId: String, params: WXRTCRenderParams)

    abstract fun startLocalAudio()

    abstract fun stopLocalAudio()

    abstract fun muteLocalAudio(mute: Boolean)

    abstract fun muteRemoteAudio(userId: String, mute: Boolean)

    abstract fun muteAllRemoteAudio(mute: Boolean)

    abstract fun setRemoteAudioVolume(userId: String, volume: Int)

    abstract fun setAllRemoteAudioVolume(volume: Int)

    abstract fun startScreenCapture(encParam: WXRTCDef.WXRTCVideoEncParam?, renderer: SurfaceViewRenderer?)

    abstract fun stopScreenCapture()

    abstract fun pauseScreenCapture()

    abstract fun resumeScreenCapture()

    abstract fun setSpeakerOn(speakerOn: Boolean)

    abstract val isFrontCamera: Boolean
        get

    abstract fun switchCamera(frontCamera: Boolean)

    abstract val isCameraZoomSupported: Boolean
        get

    abstract val cameraMaxZoom: Int
        get

    abstract var cameraZoom: Int

    abstract fun snapshotVideo(userId: String, listener: WXRTCSnapshotListener?): Boolean

    abstract fun destory()

    companion object {

        @JvmStatic
        @JvmOverloads
        fun getInstance(url: String? = null): WXRTC {
            return WXRTCImpl.getInstance(url)
        }

        @JvmStatic
        fun destoryInstance() {
            WXRTCImpl.destoryInstance()
        }

        @JvmStatic
        fun getSpeaker(userId: Long, userName: String): Speaker {
            val speaker = Speaker()
            speaker.spkId = userId
            speaker.spkName = userName
            return speaker
        }
    }
}

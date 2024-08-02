package com.wx.rtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.wx.rtc.WXRTCDef.WXRTCRenderParams
import com.wx.rtc.WXRTCDef.WXRTCVideoEncParam
import com.wx.rtc.bean.CallCommand
import com.wx.rtc.bean.ProcessCommand
import com.wx.rtc.bean.RecordCommand
import com.wx.rtc.bean.ResultData
import com.wx.rtc.bean.RoomMsg
import com.wx.rtc.bean.SendCommandMessage
import com.wx.rtc.bean.SendCommandMessage.CallCmdDTO
import com.wx.rtc.bean.SendCommandMessage.ProcessCmdListDTO
import com.wx.rtc.bean.SendCommandMessage.RecordCmdDTO
import com.wx.rtc.bean.SignalCommand
import com.wx.rtc.bean.SpeakerDTO
import com.wx.rtc.rtc.RTCListener
import com.wx.rtc.rtc.RTCManager
import com.wx.rtc.socket.SocketListener
import com.wx.rtc.socket.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer
import java.io.File

class WXRTC : SocketListener, RTCListener {
    private var logToast: Toast? = null
    private var mContext: Context? = null
    private var mAppId: String? = null
    var userId: String? = null
        private set
    var roomId: String = ""
        private set
    private var mInviteId: String? = ""
    private var callStatus = WXRTCDef.Status.None
    private var callRole = WXRTCDef.Role.None
    var isLogin: Boolean = false
        private set
    var isEnterRoom: Boolean = false
        private set
    private var destorying = false
    private var speakerOn = true
    private var mRTCListener: WXRTCListener? = null
    private var mCallListener: WXCallListener? = null
    private var mSnapshotlistener: WXRTCSnapshotListener? = null

    private var currentRecordFile: String? = ""

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    private val mSocketManager = SocketManager()

    private val mRTCManager = RTCManager()

    fun init(context: Context) {
        this.mContext = context

        mSocketManager.init(context, socketUrl)
        mSocketManager.setListener(this)

        mRTCManager.init(context)
        mRTCManager.setRTCListener(this)
    }

    fun setRTCVideoParam(param: WXRTCVideoEncParam) {
        mRTCManager.setRTCVideoParam(param)
    }

    fun setRTCListener(listener: WXRTCListener?) {
        this.mRTCListener = listener
    }

    fun setCallListener(listener: WXCallListener?) {
        this.mCallListener = listener
    }

    fun login(appId: String, userId: String) {
        this.mAppId = appId
        this.userId = userId

        mSocketManager.startConnect()

        val message = SendCommandMessage()
        message.signal = SignalCommand.LOGIN
        message.appId = appId
        message.userId = userId

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    fun logout() {
        val message = SendCommandMessage()
        message.signal = SignalCommand.LOGOUT

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    fun enterRoom(roomId: String) {
        if (!isLogin) {
            mRTCListener?.onError(1, "请先登录")
            mCallListener?.onError(1, "请先登录")
            return
        }

        val message = SendCommandMessage()
        message.signal = SignalCommand.ENTER_ROOM
        message.roomId = roomId
        this.roomId = roomId

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    fun exitRoom() {
        val message = SendCommandMessage()
        message.signal = SignalCommand.EXIT_ROOM

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    fun inviteCall(inviteId: String, roomId: String) {
        val message = SendCommandMessage()
        message.signal = SignalCommand.CALL_CMD

        val cmd = CallCmdDTO()
        cmd.cmd = CallCommand.INVITE
        cmd.userId = inviteId
        cmd.roomId = roomId
        message.callCmd = cmd

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))

        this.mInviteId = inviteId
        this.callStatus = WXRTCDef.Status.Calling
        this.callRole = WXRTCDef.Role.Caller
    }

    @JvmOverloads
    fun cancelInvitation(inviteId: String = mInviteId!!) {
        val message = SendCommandMessage()
        message.signal = SignalCommand.CALL_CMD

        val cmd = CallCmdDTO()
        cmd.cmd = CallCommand.CANCEL
        cmd.userId = inviteId
        message.callCmd = cmd

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))

        this.callStatus = WXRTCDef.Status.None
        this.callRole = WXRTCDef.Role.None
        this.mInviteId = ""
    }

    @JvmOverloads
    fun acceptInvitation(inviteId: String = mInviteId!!) {
        val message = SendCommandMessage()
        message.signal = SignalCommand.CALL_CMD

        val cmd = CallCmdDTO()
        cmd.cmd = CallCommand.ACCEPT
        cmd.userId = inviteId
        message.callCmd = cmd

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))

        this.callStatus = WXRTCDef.Status.Connected
    }

    @JvmOverloads
    fun rejectInvitation(inviteId: String = mInviteId!!) {
        val message = SendCommandMessage()
        message.signal = SignalCommand.CALL_CMD

        val cmd = CallCmdDTO()
        cmd.cmd = CallCommand.REJECT
        cmd.userId = inviteId
        message.callCmd = cmd

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))

        this.callStatus = WXRTCDef.Status.None
        this.callRole = WXRTCDef.Role.None
        this.mInviteId = ""
    }

    fun invitationLineBusy(inviteId: String) {
        val message = SendCommandMessage()
        message.signal = SignalCommand.CALL_CMD

        val cmd = CallCmdDTO()
        cmd.cmd = CallCommand.LINE_BUSY
        cmd.userId = inviteId
        message.callCmd = cmd

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    @JvmOverloads
    fun hangupCall(inviteId: String = mInviteId!!) {
        val message = SendCommandMessage()
        message.signal = SignalCommand.CALL_CMD

        val cmd = CallCmdDTO()
        cmd.cmd = CallCommand.HANG_UP
        cmd.userId = inviteId
        message.callCmd = cmd

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))

        this.callStatus = WXRTCDef.Status.None
        this.callRole = WXRTCDef.Role.None
        this.mInviteId = ""
    }

    fun sendRoomMsg(cmd: String, msg: String) {
        if (TextUtils.isEmpty(roomId)) {
            logAndToast("需要进入房间才能发送房间消息")
            return
        }

        val message = SendCommandMessage()
        message.signal = SignalCommand.SEND_ROOM_MSG

        val roomMsg = RoomMsg()
        roomMsg.cmd = cmd
        roomMsg.message = msg
        message.roomMsg = roomMsg

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    fun startProcess() {
        val message = SendCommandMessage()
        message.signal = SignalCommand.START_PROCESS

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    fun endProcess() {
        val message = SendCommandMessage()
        message.signal = SignalCommand.END_PROCESS

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    private fun getRecordCommand(
        cmd: String,
        mixId: String?,
        extraData: String?,
        needAfterAsr: Boolean,
        hospitalId: String?,
        spkList: List<SpeakerDTO>?
    ): RecordCmdDTO {
        val dto = RecordCmdDTO()
        dto.cmd = cmd
        dto.endFileName = currentRecordFile
        dto.mixId = mixId
        dto.extraData = extraData
        dto.isNeedAfterAsr = needAfterAsr
        dto.hospitalId = hospitalId
        dto.spkList = spkList

        return dto
    }

    @JvmOverloads
    fun startRecord(
        mixId: String? = null,
        extraData: String? = null,
        needAfterAsr: Boolean = false,
        hospitalId: String? = null,
        spkList: List<SpeakerDTO>? = null
    ) {
        val message = SendCommandMessage()
        message.signal = SignalCommand.RECORD_CMD

        message.recordCmd = getRecordCommand(
            RecordCommand.START_RECORD,
            mixId,
            extraData,
            needAfterAsr,
            hospitalId,
            spkList
        )

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    @JvmOverloads
    fun endAndStartRecord(
        mixId: String? = null,
        extraData: String? = null,
        needAfterAsr: Boolean = false,
        hospitalId: String? = null,
        spkList: List<SpeakerDTO>? = null
    ) {
        val message = SendCommandMessage()
        message.signal = SignalCommand.RECORD_CMD

        message.recordCmd = getRecordCommand(
            RecordCommand.END_AND_START_RECORD,
            mixId,
            extraData,
            needAfterAsr,
            hospitalId,
            spkList
        )

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    fun endRecord() {
        val message = SendCommandMessage()
        message.signal = SignalCommand.RECORD_CMD

        val dto = RecordCmdDTO()
        dto.cmd = RecordCommand.END_RECORD
        message.recordCmd = dto

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    fun startAsr(hospitalId: String?, spkList: List<SpeakerDTO>?) {
        val message = SendCommandMessage()
        message.signal = SignalCommand.PROCESS_CMD

        val dto = ProcessCmdListDTO()
        dto.type = "audio"
        dto.cmd = ProcessCommand.START_ASR
        dto.hospitalId = hospitalId
        dto.spkList = spkList

        val list: MutableList<ProcessCmdListDTO> = ArrayList()
        list.add(dto)
        message.processCmdList = list

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    fun endAndStartAsr(hospitalId: String?, spkList: List<SpeakerDTO>?) {
        val message = SendCommandMessage()
        message.signal = SignalCommand.PROCESS_CMD

        val dto = ProcessCmdListDTO()
        dto.type = "audio"
        dto.cmd = ProcessCommand.END_AND_START_ASR
        dto.hospitalId = hospitalId
        dto.spkList = spkList

        val list: MutableList<ProcessCmdListDTO> = ArrayList()
        list.add(dto)
        message.processCmdList = list

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    fun endAsr() {
        val message = SendCommandMessage()
        message.signal = SignalCommand.PROCESS_CMD

        val dto = ProcessCmdListDTO()
        dto.type = "audio"
        dto.cmd = ProcessCommand.END_ASR

        val list: MutableList<ProcessCmdListDTO> = ArrayList()
        list.add(dto)
        message.processCmdList = list

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    fun startLocalVideo(frontCamera: Boolean, renderer: SurfaceViewRenderer?) {
        renderer?.visibility = View.VISIBLE
        mRTCManager.startLocalVideo(frontCamera, renderer)
    }

    fun updateLocalVideo(renderer: SurfaceViewRenderer?) {
        renderer?.visibility = View.VISIBLE
        mRTCManager.updateLocalVideo(renderer)
    }

    fun stopLocalVideo() {
        mRTCManager.stopLocalVideo()
    }

    fun muteLocalVideo(mute: Boolean) {
        mRTCManager.muteLocalVideo(mute)
    }

    fun startRemoteVideo(userId: String, renderer: SurfaceViewRenderer?) {
        renderer?.visibility = View.VISIBLE
        mRTCManager.startRemoteVideo(userId, renderer)
    }

    fun stopRemoteVideo(userId: String) {
        mRTCManager.stopRemoteVideo(userId)
    }

    fun stopAllRemoteVideo() {
        mRTCManager.stopAllRemoteVideo()
    }

    fun muteRemoteVideo(userId: String, mute: Boolean) {
        mRTCManager.muteRemoteVideo(userId, mute)
    }

    fun muteAllRemoteVideo(mute: Boolean) {
        mRTCManager.muteAllRemoteVideo(mute)
    }

    fun setLocalRenderParams(params: WXRTCRenderParams) {
        mRTCManager.setLocalRenderParams(params)
    }

    fun setRemoteRenderParams(userId: String, params: WXRTCRenderParams) {
        mRTCManager.setRemoteRenderParams(userId, params)
    }

    fun startLocalAudio() {
        setSpeakerOn(speakerOn)
        mRTCManager.startLocalAudio()
    }

    fun stopLocalAudio() {
        mRTCManager.stopLocalAudio()
    }

    fun muteLocalAudio(mute: Boolean) {
        mRTCManager.muteLocalAudio(mute)
    }

    fun muteRemoteAudio(userId: String, mute: Boolean) {
        mRTCManager.muteRemoteAudio(userId, mute)
    }

    fun muteAllRemoteAudio(mute: Boolean) {
        mRTCManager.muteAllRemoteAudio(mute)
    }

    fun setRemoteAudioVolume(userId: String, volume: Int) {
        mRTCManager.setRemoteAudioVolume(userId, volume)
    }

    fun setAllRemoteAudioVolume(volume: Int) {
        mRTCManager.setAllRemoteAudioVolume(volume)
    }

    fun setSpeakerOn(speakerOn: Boolean) {
        this.speakerOn = speakerOn
        val audioManager = mContext!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = speakerOn
    }

    fun switchCamera(frontCamera: Boolean) {
        mRTCManager.switchPublishCamera(frontCamera)
    }

    val isCameraZoomSupported: Boolean
        get() = mRTCManager.isCameraZoomSupported

    val cameraMaxZoom: Int
        get() = mRTCManager.cameraMaxZoom

    var cameraZoom: Int
        get() = mRTCManager.cameraZoom
        set(value) {
            mRTCManager.cameraZoom = value
        }

    fun snapshotVideo(userId: String, listener: WXRTCSnapshotListener?): Boolean {
        this.mSnapshotlistener = listener
        if (userId == this.userId) {
            return mRTCManager.snapshotLocalVideo(userId)
        }
        return mRTCManager.snapshotRemoteVideo(userId)
    }

    private fun onCallCancelled(userId: String) {
        mCallListener?.onCallCancelled(userId)
        callRole = WXRTCDef.Role.None
        callStatus = WXRTCDef.Status.None
    }

    private fun logAndToast(msg: String) {
        Log.d(TAG, msg)
        CoroutineScope(Dispatchers.Main).launch {
            if (logToast != null) {
                logToast!!.cancel()
            }
            logToast = Toast.makeText(mContext, msg, Toast.LENGTH_SHORT)
            logToast?.show()
        }
    }

    fun destory() {
        destorying = true

        if (mSocketManager.isConnected) {
            exitRoom()

            if (!isEnterRoom) {
                destoryInternal()
            }
        } else {
            destoryInternal()
        }
    }

    private fun destoryInternal() {
        setSpeakerOn(false)

        setRTCListener(null)
        setCallListener(null)

        mRTCManager.destory()

        mSocketManager.destroy()

        if (logToast != null) {
            logToast!!.cancel()
        }

        isEnterRoom = false
        INSTANCE = null
    }


    override fun onError(errCode: Int, errMsg: String) {
        mRTCListener?.onError(errCode, errMsg)
        mCallListener?.onError(errCode, errMsg)
    }

    override fun onSocketOpen() {
        if (isLogin && !mAppId.isNullOrEmpty() && !userId.isNullOrEmpty()) {
            login(mAppId!!, userId!!)
        }
    }

    override fun onLogin() {
        isLogin = true

        if (isEnterRoom && !roomId.isNullOrEmpty()) {
            enterRoom(roomId)
        }

        mRTCListener?.onLogin()
    }

    override fun onLogout(reason: Int) {
        isLogin = false
        userId = null

        mRTCListener?.onLogout(reason)

        if (destorying) {
            destoryInternal()
        }
    }

    override fun onEnterRoom(publishUrl: String) {
        var needOnEnterRoom = true
        if (isEnterRoom) {
            needOnEnterRoom = false
        }

        isEnterRoom = true

        mRTCManager.startPublish(publishUrl, userId!!)

        if (needOnEnterRoom) {
            mRTCListener?.onEnterRoom()
        }
    }

    override fun onExitRoom(reason: Int) {
        isEnterRoom = false
        roomId = ""

        mRTCManager.stopAllPC()

        mRTCListener?.onExitRoom(reason)
    }

    override fun onGetUnpublishUrl(unpublishUrl: String) {
        mRTCManager.setUnpublishUrl(unpublishUrl)
    }

    override fun onRemoteUserEnterRoom(pullUrl: String, userId: String) {
        mRTCManager.startOnePull(pullUrl, userId)
        mRTCListener?.onRemoteUserEnterRoom(userId)
        mCallListener?.onUserJoin(userId)
    }

    override fun onRemoteUserLeaveRoom(userId: String, reason: Int) {
        mRTCListener?.onRemoteUserLeaveRoom(userId, reason)
        mCallListener?.onUserLeave(userId)
    }

    override fun onRecvRoomMsg(userId: String, cmd: String, message: String) {
        mRTCListener?.onRecvRoomMsg(userId, cmd, message)
    }

    override fun onRecvCallMsg(userId: String, cmd: String, roomId: String) {
        when (cmd) {
            CallCommand.NEW_INVITATION_RECEIVED -> {
                if (callStatus == WXRTCDef.Status.Calling || callStatus == WXRTCDef.Status.Connected) {
                    invitationLineBusy(this.userId!!)
                    return
                }
                callRole = WXRTCDef.Role.Callee
                callStatus = WXRTCDef.Status.Calling
                mInviteId = userId
                mCallListener?.onCallReceived(userId)
            }

            CallCommand.INVITATION_NO_RESP -> {
                mCallListener?.onUserNoResponse(userId)
                onCallCancelled(this.userId!!)
            }

            CallCommand.INVITATION_REJECTED -> {
                mCallListener?.onUserReject(userId)
                onCallCancelled(this.userId!!)
            }

            CallCommand.INVITATION_LINE_BUSY -> {
                mCallListener?.onUserLineBusy(userId)
                onCallCancelled(this.userId!!)
            }

            CallCommand.INVITATION_CANCELED -> onCallCancelled(userId)
            CallCommand.INVITATION_ACCEPTED -> {
                callStatus = WXRTCDef.Status.Connected
                mCallListener?.onCallBegin(roomId, callRole)
            }

            CallCommand.CALL_END -> {
                mCallListener?.onCallEnd(roomId, callRole)
                callRole = WXRTCDef.Role.None
                callStatus = WXRTCDef.Status.None
            }

            else -> {}
        }
    }

    override fun onResult(resultData: ResultData) {
        mRTCListener?.onResult(resultData)
    }

    override fun onRecordStart(fileName: String) {
        currentRecordFile = fileName
        mRTCListener?.onRecordStart(fileName)
    }

    override fun onRecordEnd(fileName: String) {
        if (fileName == currentRecordFile) {
            currentRecordFile = ""
        }
        mRTCListener?.onRecordEnd(fileName)
    }


    override fun onConnected() {
    }

    override fun onClose() {
    }

    override fun onSnapshot(userId: String, file: File) {
        mSnapshotlistener?.onSnapshot(userId, file)
    }

    companion object {
        private val TAG: String = WXRTC::class.java.name

        private var INSTANCE: WXRTC? = null
        private var socketUrl: String? = null
        val instance: WXRTC?
            get() = getInstance(null)

        fun getInstance(url: String? = null): WXRTC {
            if (INSTANCE == null) {
                INSTANCE = WXRTC()
                socketUrl = url
            }
            return INSTANCE!!
        }

        fun getSpeaker(userId: Long, userName: String): SpeakerDTO {
            val speaker = SpeakerDTO()
            speaker.spkId = userId
            speaker.spkName = userName
            return speaker
        }
    }
}

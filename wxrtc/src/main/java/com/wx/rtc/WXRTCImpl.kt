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
import com.wx.rtc.bean.P2PMsg
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

class WXRTCImpl : WXRTC(), SocketListener, RTCListener {
    private var logToast: Toast? = null
    private var mContext: Context? = null
    private var mInviteId: String? = ""
    private var callStatus = WXRTCDef.Status.None
    private var callRole = WXRTCDef.Role.None
    private var destorying = false
    private var speakerOn = true
    private var mRTCListener: WXRTCListener? = null
    private var mCallListener: WXCallListener? = null
    private var mSnapshotlistener: WXRTCSnapshotListener? = null

    private var currentRecordFile: String? = null

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    private val mSocketManager = SocketManager()

    private val mRTCManager = RTCManager()

    override fun init(context: Context) {
        this.mContext = context

        mSocketManager.init(context, socketUrl)
        mSocketManager.setListener(this)

        mRTCManager.init(context)
        mRTCManager.setRTCListener(this)
    }

    override fun setRTCVideoParam(param: WXRTCVideoEncParam) {
        mRTCManager.setRTCVideoParam(param)
    }

    override fun setRTCListener(listener: WXRTCListener?) {
        this.mRTCListener = listener
    }

    override fun setCallListener(listener: WXCallListener?) {
        this.mCallListener = listener
    }

    override fun login(appId: String, userId: String) {
        this.mAppId = appId
        this.mUserId = userId

        mSocketManager.startConnect()

        val message = SendCommandMessage()
        message.signal = SignalCommand.LOGIN
        message.appId = appId
        message.userId = userId
        message.connectUrl = socketUrl

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    override fun logout() {
        val message = SendCommandMessage()
        message.signal = SignalCommand.LOGOUT

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    override fun enterRoom(roomId: String) {
        val message = SendCommandMessage()
        message.signal = SignalCommand.ENTER_ROOM
        message.roomId = roomId
        this.mRoomId = roomId

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    override fun exitRoom() {
        val message = SendCommandMessage()
        message.signal = SignalCommand.EXIT_ROOM

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    override fun inviteCall(inviteId: String, roomId: String) {
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

    override fun cancelInvitation(){
        mInviteId?.let {
            cancelInvitation(it)
        }
    }

    override fun cancelInvitation(inviteId: String) {
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

    override fun acceptInvitation() {
        acceptInvitation(mInviteId!!)
    }

    override fun acceptInvitation(inviteId: String) {
        val message = SendCommandMessage()
        message.signal = SignalCommand.CALL_CMD

        val cmd = CallCmdDTO()
        cmd.cmd = CallCommand.ACCEPT
        cmd.userId = inviteId
        message.callCmd = cmd

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))

        this.callStatus = WXRTCDef.Status.Connected
    }

    override fun rejectInvitation(){
        mInviteId?.let {
            rejectInvitation(it)
        }
    }

    override fun rejectInvitation(inviteId: String) {
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

    override fun invitationLineBusy(inviteId: String) {
        val message = SendCommandMessage()
        message.signal = SignalCommand.CALL_CMD

        val cmd = CallCmdDTO()
        cmd.cmd = CallCommand.LINE_BUSY
        cmd.userId = inviteId
        message.callCmd = cmd

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    override fun hangupCall(){
        mInviteId?.let {
            hangupCall(it)
        }
    }

    override fun hangupCall(inviteId: String) {
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

    override fun sendP2PMsg(userId: String, msg: String) {
        val message = SendCommandMessage()
        message.signal = SignalCommand.SEND_P2P_MSG

        val p2pMsg = P2PMsg()
        p2pMsg.from = mUserId
        p2pMsg.to = userId
        p2pMsg.message = msg
        message.p2pMsg = p2pMsg

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    override fun sendRoomMsg(cmd: String, msg: String) {
        if (TextUtils.isEmpty(mRoomId)) {
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

    override fun startProcess() {
        val message = SendCommandMessage()
        message.signal = SignalCommand.START_PROCESS

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    override fun endProcess() {
        val message = SendCommandMessage()
        message.signal = SignalCommand.END_PROCESS

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    private fun getRecordCommand(
        cmd: String,
        mixId: String?,
        extraData: String?,
        needAfterAsr: Boolean?,
        hospitalId: String?,
        spkList: List<Speaker>?
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

    override fun startRecord(
        mixId: String?,
        extraData: String?,
        needAfterAsr: Boolean?,
        hospitalId: String?,
        spkList: List<Speaker>?
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

    override fun endAndStartRecord(
        mixId: String?,
        extraData: String?,
        needAfterAsr: Boolean,
        hospitalId: String?,
        spkList: List<Speaker>?
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

    override fun endRecord() {
        val message = SendCommandMessage()
        message.signal = SignalCommand.RECORD_CMD

        val dto = RecordCmdDTO()
        dto.cmd = RecordCommand.END_RECORD
        message.recordCmd = dto

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage::class.java))
    }

    override fun startAsr(hospitalId: String?, spkList: List<Speaker>?) {
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

    override fun endAndStartAsr(hospitalId: String?, spkList: List<Speaker>?) {
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

    override fun endAsr() {
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

    override fun startLocalVideo(frontCamera: Boolean, renderer: SurfaceViewRenderer?) {
        renderer?.visibility = View.VISIBLE
        mRTCManager.startLocalVideo(frontCamera, renderer)

        if (mRoomId.isNotEmpty()) {
            val message = SendCommandMessage()
            message.signal = SignalCommand.VIDEO_AVAILABLE
            message.available = true

            mSocketManager.sendWebSocketMessage(
                gson.toJson(
                    message,
                    SendCommandMessage::class.java
                )
            )
        }
    }

    override fun updateLocalVideo(renderer: SurfaceViewRenderer?) {
        renderer?.visibility = View.VISIBLE
        mRTCManager.updateLocalVideo(renderer)
    }

    override fun stopLocalVideo() {
        mRTCManager.stopLocalVideo()

        if (mRoomId.isNotEmpty()) {
            val message = SendCommandMessage()
            message.signal = SignalCommand.VIDEO_AVAILABLE
            message.available = false

            mSocketManager.sendWebSocketMessage(
                gson.toJson(
                    message,
                    SendCommandMessage::class.java
                )
            )
        }
    }

    override fun muteLocalVideo(mute: Boolean) {
        mRTCManager.muteLocalVideo(mute)

        if (mRoomId.isNotEmpty()) {
            val message = SendCommandMessage()
            message.signal = SignalCommand.VIDEO_AVAILABLE
            message.available = !mute

            mSocketManager.sendWebSocketMessage(
                gson.toJson(
                    message,
                    SendCommandMessage::class.java
                )
            )
        }
    }

    override fun startRemoteVideo(userId: String, renderer: SurfaceViewRenderer?) {
        renderer?.visibility = View.VISIBLE
        mRTCManager.startRemoteVideo(userId, renderer)
    }

    override fun updateRemoteVideo(userId: String, renderer: SurfaceViewRenderer?) {
        renderer?.visibility = View.VISIBLE
        mRTCManager.updateRemoteVideo(userId, renderer)
    }

    override fun stopRemoteVideo(userId: String) {
        mRTCManager.stopRemoteVideo(userId)
    }

    override fun stopAllRemoteVideo() {
        mRTCManager.stopAllRemoteVideo()
    }

    override fun muteRemoteVideo(userId: String, mute: Boolean) {
        mRTCManager.muteRemoteVideo(userId, mute)
    }

    override fun muteAllRemoteVideo(mute: Boolean) {
        mRTCManager.muteAllRemoteVideo(mute)
    }

    override fun setLocalRenderParams(params: WXRTCRenderParams) {
        mRTCManager.setLocalRenderParams(params)
    }

    override fun setRemoteRenderParams(userId: String, params: WXRTCRenderParams) {
        mRTCManager.setRemoteRenderParams(userId, params)
    }

    override fun startLocalAudio() {
        setSpeakerOn(speakerOn)
        mRTCManager.startLocalAudio()

        if (mRoomId.isNotEmpty()) {
            val message = SendCommandMessage()
            message.signal = SignalCommand.AUDIO_AVAILABLE
            message.available = true

            mSocketManager.sendWebSocketMessage(
                gson.toJson(
                    message,
                    SendCommandMessage::class.java
                )
            )
        }
    }

    override fun stopLocalAudio() {
        mRTCManager.stopLocalAudio()

        if (mRoomId.isNotEmpty()) {
            val message = SendCommandMessage()
            message.signal = SignalCommand.AUDIO_AVAILABLE
            message.available = false

            mSocketManager.sendWebSocketMessage(
                gson.toJson(
                    message,
                    SendCommandMessage::class.java
                )
            )
        }
    }

    override fun muteLocalAudio(mute: Boolean) {
        mRTCManager.muteLocalAudio(mute)

        if (mRoomId.isNotEmpty()) {
            val message = SendCommandMessage()
            message.signal = SignalCommand.AUDIO_AVAILABLE
            message.available = !mute

            mSocketManager.sendWebSocketMessage(
                gson.toJson(
                    message,
                    SendCommandMessage::class.java
                )
            )
        }
    }

    override fun muteRemoteAudio(userId: String, mute: Boolean) {
        mRTCManager.muteRemoteAudio(userId, mute)
    }

    override fun muteAllRemoteAudio(mute: Boolean) {
        mRTCManager.muteAllRemoteAudio(mute)
    }

    override fun setRemoteAudioVolume(userId: String, volume: Int) {
        mRTCManager.setRemoteAudioVolume(userId, volume)
    }

    override fun setAllRemoteAudioVolume(volume: Int) {
        mRTCManager.setAllRemoteAudioVolume(volume)
    }

    override fun startScreenCapture(encParam: WXRTCVideoEncParam?, renderer: SurfaceViewRenderer?) {
        mRTCManager.startScreenCapture(encParam, renderer)

        if (mRoomId.isNotEmpty()) {
            val message = SendCommandMessage()
            message.signal = SignalCommand.VIDEO_AVAILABLE
            message.available = true

            mSocketManager.sendWebSocketMessage(
                gson.toJson(
                    message,
                    SendCommandMessage::class.java
                )
            )
        }
    }

    override fun stopScreenCapture() {
        mRTCManager.stopScreenCapture()

        if (mRoomId.isNotEmpty()) {
            val message = SendCommandMessage()
            message.signal = SignalCommand.VIDEO_AVAILABLE
            message.available = false

            mSocketManager.sendWebSocketMessage(
                gson.toJson(
                    message,
                    SendCommandMessage::class.java
                )
            )
        }
    }

    override fun pauseScreenCapture() {
        mRTCManager.pauseScreenCapture()

        if (mRoomId.isNotEmpty()) {
            val message = SendCommandMessage()
            message.signal = SignalCommand.VIDEO_AVAILABLE
            message.available = false

            mSocketManager.sendWebSocketMessage(
                gson.toJson(
                    message,
                    SendCommandMessage::class.java
                )
            )
        }
    }

    override fun resumeScreenCapture() {
        mRTCManager.resumeScreenCapture()

        if (mRoomId.isNotEmpty()) {
            val message = SendCommandMessage()
            message.signal = SignalCommand.VIDEO_AVAILABLE
            message.available = true

            mSocketManager.sendWebSocketMessage(
                gson.toJson(
                    message,
                    SendCommandMessage::class.java
                )
            )
        }
    }

    override fun setSpeakerOn(speakerOn: Boolean) {
        this.speakerOn = speakerOn
        val audioManager = mContext!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = speakerOn
    }

    override val isFrontCamera: Boolean
        get() = mRTCManager.isFrontCamera

    override fun switchCamera(frontCamera: Boolean) {
        mRTCManager.switchPublishCamera(frontCamera)
    }

    override val isCameraZoomSupported: Boolean
        get() = mRTCManager.isCameraZoomSupported

    override val cameraMaxZoom: Int
        get() = mRTCManager.cameraMaxZoom

    override var cameraZoom: Int
        get() = mRTCManager.cameraZoom
        set(value) {
            mRTCManager.cameraZoom = value
        }

    override fun snapshotVideo(userId: String, listener: WXRTCSnapshotListener?): Boolean {
        this.mSnapshotlistener = listener
        if (userId == this.mUserId) {
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

    override fun destory() {
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
        if (isLogin && !mAppId.isNullOrEmpty() && !mUserId.isNullOrEmpty()) {
            login(mAppId!!, mUserId!!)
        }
    }

    override fun onLogin() {
        isLogin = true

        if (isEnterRoom && !mRoomId.isNullOrEmpty()) {
            enterRoom(mRoomId)
        }

        mRTCListener?.onLogin()
    }

    override fun onLogout(reason: Int) {
        isLogin = false
        mUserId = null

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

        mUserId?.let {
            mRTCManager.startPublish(publishUrl, it)
        }

        if (needOnEnterRoom) {
            mRTCListener?.onEnterRoom()
        }
    }

    override fun onExitRoom(reason: Int) {
        isEnterRoom = false
        mRoomId = ""

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
        mRTCManager.stopPull(userId)
        mRTCListener?.onRemoteUserLeaveRoom(userId, reason)
        mCallListener?.onUserLeave(userId)
    }

    override fun onUserVideoAvailable(userId: String, available: Boolean) {
        mRTCListener?.onUserVideoAvailable(userId, available)
        mCallListener?.onUserVideoAvailable(userId, available)
    }

    override fun onUserAudioAvailable(userId: String, available: Boolean) {
        mRTCListener?.onUserAudioAvailable(userId, available)
        mCallListener?.onUserAudioAvailable(userId, available)
    }

    override fun onRecvP2PMsg(fromUserId: String, message: String?) {
        mRTCListener?.onRecvP2PMsg(fromUserId, message)
        mCallListener?.onRecvP2PMsg(fromUserId, message)
    }

    override fun onRecvRoomMsg(userId: String, cmd: String, message: String?) {
        mRTCListener?.onRecvRoomMsg(userId, cmd, message)
        mCallListener?.onRecvRoomMsg(userId, cmd, message)
    }

    override fun onRecvCallMsg(userId: String?, cmd: String, roomId: String?) {
        when (cmd) {
            CallCommand.NEW_INVITATION_RECEIVED -> {
                if (callStatus == WXRTCDef.Status.Calling || callStatus == WXRTCDef.Status.Connected) {
                    invitationLineBusy(this.mUserId!!)
                    return
                }
                callRole = WXRTCDef.Role.Callee
                callStatus = WXRTCDef.Status.Calling
                mInviteId = userId
                mCallListener?.onCallReceived(userId!!)
            }

            CallCommand.INVITATION_NO_RESP -> {
                mCallListener?.onUserNoResponse(userId!!)
                onCallCancelled(this.mUserId!!)
            }

            CallCommand.INVITATION_REJECTED -> {
                mCallListener?.onUserReject(userId!!)
                onCallCancelled(this.mUserId!!)
            }

            CallCommand.INVITATION_LINE_BUSY -> {
                mCallListener?.onUserLineBusy(userId!!)
                onCallCancelled(this.mUserId!!)
            }

            CallCommand.INVITATION_CANCELED -> onCallCancelled(userId!!)
            CallCommand.INVITATION_ACCEPTED -> {
                callStatus = WXRTCDef.Status.Connected
                mCallListener?.onCallBegin(roomId!!, callRole)
            }

            CallCommand.CALL_END -> {
                mCallListener?.onCallEnd(roomId!!, callRole)
                callRole = WXRTCDef.Role.None
                callStatus = WXRTCDef.Status.None
            }

            else -> {}
        }
    }

    override fun onResult(processData: WXRTCDef.ProcessData) {
        mRTCListener?.onProcessResult(processData)
    }

    override fun onRecordStart(fileName: String) {
        currentRecordFile = fileName
        mRTCListener?.onRecordStart(fileName)
    }

    override fun onRecordEnd(fileName: String) {
        if (fileName == currentRecordFile) {
            currentRecordFile = null
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
        private val TAG: String = WXRTCImpl::class.java.name

        private var INSTANCE: WXRTCImpl? = null
        private var socketUrl: String? = null

        @JvmStatic
        @JvmOverloads
        fun getInstance(url: String? = null): WXRTC {
            synchronized(WXRTCImpl.Companion::class.java) {
                if (INSTANCE == null) {
                    INSTANCE = WXRTCImpl()
                    socketUrl = url
                }
                return INSTANCE!!
            }
        }

        @JvmStatic
        fun destoryInstance() {
            synchronized(WXRTCImpl.Companion::class.java) {
                INSTANCE?.destory()
                INSTANCE = null
            }
        }
    }
}

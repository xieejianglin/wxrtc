package com.wx.rtc

import android.content.Context
import android.media.AudioManager
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import com.wx.rtc.WXRTCDef.WXRTCRenderParams
import com.wx.rtc.WXRTCDef.WXRTCVideoEncParam
import com.wx.rtc.WXRTCDef.Speaker
import com.wx.rtc.bean.CallCommand
import com.wx.rtc.bean.ProcessCommand
import com.wx.rtc.bean.RecordCommand
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

    private val mSocketManager = SocketManager()

    private val mRTCManager = RTCManager()

    override fun init(context: Context) {
        this.mContext = context

        mSocketManager.init(context, mSocketUrl)
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

        val message = mNetworkType?.let {
            "{\"signal\":\"${SignalCommand.LOGIN}\", \"app_id\":\"$appId\", \"user_id\":\"$userId\", \"connect_url\":\"$mSocketUrl\", \"network_type\":$it}"
        } ?: "{\"signal\":\"${SignalCommand.LOGIN}\", \"app_id\":\"$appId\", \"user_id\":\"$userId\", \"connect_url\":\"$mSocketUrl\"}"

        mSocketManager.sendWebSocketMessage(message)
    }

    override fun logout() {
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.LOGOUT}\"}")
    }

    override fun enterRoom(roomId: String) {
        this.mRoomId = roomId

        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.ENTER_ROOM}\", \"room_id\":\"$roomId\"}")
    }

    override fun exitRoom() {
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.EXIT_ROOM}\"}")
    }

    override fun inviteCall(inviteId: String, roomId: String) {
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.CALL_CMD}\", \"call_cmd\":{\"cmd\":\"${CallCommand.INVITE}\",\"user_id\":\"$inviteId\",\"room_id\":\"$roomId\"}}")

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
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.CALL_CMD}\",\"call_cmd\":{\"cmd\":\"${CallCommand.CANCEL}\", \"user_id\":\"$inviteId\"}}")

        this.callStatus = WXRTCDef.Status.None
        this.callRole = WXRTCDef.Role.None
        this.mInviteId = ""
    }

    override fun acceptInvitation() {
        acceptInvitation(mInviteId!!)
    }

    override fun acceptInvitation(inviteId: String) {
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.CALL_CMD}\",\"call_cmd\":{\"cmd\":\"${CallCommand.ACCEPT}\", \"user_id\":\"$inviteId\"}}")

        this.callStatus = WXRTCDef.Status.Connected
    }

    override fun rejectInvitation(){
        mInviteId?.let {
            rejectInvitation(it)
        }
    }

    override fun rejectInvitation(inviteId: String) {
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.CALL_CMD}\",\"call_cmd\":{\"cmd\":\"${CallCommand.REJECT}\", \"user_id\":\"$inviteId\"}}")

        this.callStatus = WXRTCDef.Status.None
        this.callRole = WXRTCDef.Role.None
        this.mInviteId = ""
    }

    override fun invitationLineBusy(inviteId: String) {
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.CALL_CMD}\",\"call_cmd\":{\"cmd\":\"${CallCommand.LINE_BUSY}\", \"user_id\":\"$inviteId\"}}")
    }

    override fun hangupCall(){
        mInviteId?.let {
            hangupCall(it)
        }
    }

    override fun hangupCall(inviteId: String) {
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.CALL_CMD}\",\"call_cmd\":{\"cmd\":\"${CallCommand.HANG_UP}\", \"user_id\":\"$inviteId\"}}")

        this.callStatus = WXRTCDef.Status.None
        this.callRole = WXRTCDef.Role.None
        this.mInviteId = ""
    }

    override fun sendP2PMsg(userId: String, msg: String) {
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.SEND_P2P_MSG}\",\"p2p_msg\":{\"from\":\"$mUserId\", \"to\":\"$userId\", \"message\":\"$msg\"}}")
    }

    override fun sendRoomMsg(cmd: String, msg: String) {
        if (TextUtils.isEmpty(mRoomId)) {
            logAndToast("需要进入房间才能发送房间消息")
            return
        }

        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.SEND_ROOM_MSG}\",\"room_msg\":{\"cmd\":\"$cmd\", \"message\":\"$msg\"}}")
    }

    override fun startProcess() {
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.START_PROCESS}\"}")
    }

    override fun endProcess() {
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.END_PROCESS}\"}")
    }

    private fun getRecordCommand(
        cmd: String,
        mixId: String?,
        extraData: String?,
        needAfterAsr: Boolean?,
        hospitalId: String?,
        spkList: List<Speaker>?
    ): String {
        val sb = StringBuilder()
        sb.append("{\"cmd\":\"$cmd\"")
        currentRecordFile?.let {
            sb.append(",\"end_file_name\":\"$it\"")
        }
        mixId?.let {
            sb.append(",\"mix_id\":\"$it\"")
        }
        extraData?.let {
            sb.append(",\"extra_data\":\"${it.replace("\"", "\\\"")}\"")
        }
        needAfterAsr?.let {
            sb.append(",\"need_after_asr\":$it")
        }
        hospitalId?.let {
            sb.append(",\"hospital_id\":\"$it\"")
        }
        spkList?.let { list->
            sb.append(",\"spk_list\":[")
            list.forEachIndexed { index, item ->
                if (index > 0){
                    sb.append(",")
                }
                sb.append("{")
                item.spkId?.let {
                    sb.append("\"spk_id\":\"${item.spkId}\"")
                }
                item.spkName?.let {
                    if (item.spkId != null) {
                        sb.append(",")
                    }
                    sb.append("\"spk_name\":\"${item.spkName}\"")
                }
                sb.append("}")
            }
            sb.append("]")
        }
        sb.append("}")

        return sb.toString()
    }

    override fun startRecord(
        mixId: String?,
        extraData: String?,
        needAfterAsr: Boolean?,
        hospitalId: String?,
        spkList: List<Speaker>?
    ) {
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.RECORD_CMD}\", \"record_cmd\":${
            getRecordCommand(
                RecordCommand.START_RECORD,
                mixId,
                extraData,
                needAfterAsr,
                hospitalId,
                spkList
            )
        }}")
    }

    override fun endAndStartRecord(
        mixId: String?,
        extraData: String?,
        needAfterAsr: Boolean,
        hospitalId: String?,
        spkList: List<Speaker>?
    ) {
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.RECORD_CMD}\", \"record_cmd\":${
            getRecordCommand(
                RecordCommand.END_AND_START_RECORD,
                mixId,
                extraData,
                needAfterAsr,
                hospitalId,
                spkList
            )
        }}")
    }

    override fun endRecord() {
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.RECORD_CMD}\", \"record_cmd\":{\"cmd\":\"${RecordCommand.END_RECORD}\"}}")
    }

    override fun startAsr(hospitalId: String?, spkList: List<Speaker>?) {
        val sb = StringBuilder()
        sb.append("{\"signal\":\"${SignalCommand.PROCESS_CMD}\"")
        sb.append(", \"process_cmd_list\":[{\"type\":\"audio\", \"cmd\":\"${ProcessCommand.START_ASR}\"")
        hospitalId?.let {
            sb.append(",\"hospital_id\":\"$it\"")
        }
        spkList?.let { list->
            sb.append(",\"spk_list\":[")
            list.forEachIndexed { index, item ->
                if (index > 0){
                    sb.append(",")
                }
                sb.append("{")
                item.spkId?.let {
                    sb.append("\"spk_id\":\"${item.spkId}\"")
                }
                item.spkName?.let {
                    if (item.spkId != null) {
                        sb.append(",")
                    }
                    sb.append("\"spk_name\":\"${item.spkName}\"")
                }
                sb.append("}")
            }
            sb.append("]")
        }
        sb.append("}]}")

        mSocketManager.sendWebSocketMessage(sb.toString())
    }

    override fun endAndStartAsr(hospitalId: String?, spkList: List<Speaker>?) {
        val sb = StringBuilder()
        sb.append("{\"signal\":\"${SignalCommand.PROCESS_CMD}\"")
        sb.append(", \"process_cmd_list\":[{\"type\":\"audio\", \"cmd\":\"${ProcessCommand.END_AND_START_ASR}\"")
        hospitalId?.let {
            sb.append(",\"hospital_id\":\"$it\"")
        }
        spkList?.let { list->
            sb.append(",\"spk_list\":[")
            list.forEachIndexed { index, item ->
                if (index > 0){
                    sb.append(",")
                }
                sb.append("{")
                item.spkId?.let {
                    sb.append("\"spk_id\":\"${item.spkId}\"")
                }
                item.spkName?.let {
                    if (item.spkId != null) {
                        sb.append(",")
                    }
                    sb.append("\"spk_name\":\"${item.spkName}\"")
                }
                sb.append("}")
            }
            sb.append("]")
        }
        sb.append("}]}")

        mSocketManager.sendWebSocketMessage(sb.toString())
    }

    override fun endAsr() {
        mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.PROCESS_CMD}\",\"process_cmd_list\":[{\"type\":\"audio\", \"cmd\":\"${ProcessCommand.END_ASR}\"}]}")
    }

    override fun startLocalVideo(frontCamera: Boolean, renderer: SurfaceViewRenderer?) {
        renderer?.visibility = View.VISIBLE
        mRTCManager.startLocalVideo(frontCamera, renderer)

        if (mRoomId.isNotEmpty()) {
            mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.VIDEO_AVAILABLE}\",\"available\":true}")
        }
    }

    override fun updateLocalVideo(renderer: SurfaceViewRenderer?) {
        renderer?.visibility = View.VISIBLE
        mRTCManager.updateLocalVideo(renderer)
    }

    override fun stopLocalVideo() {
        mRTCManager.stopLocalVideo()

        if (mRoomId.isNotEmpty()) {
            mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.VIDEO_AVAILABLE}\",\"available\":false}")
        }
    }

    override fun muteLocalVideo(mute: Boolean) {
        mRTCManager.muteLocalVideo(mute)

        if (mRoomId.isNotEmpty()) {
            mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.VIDEO_AVAILABLE}\",\"available\":${!mute}}")
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
            mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.AUDIO_AVAILABLE}\",\"available\":${true}}")
        }
    }

    override fun stopLocalAudio() {
        mRTCManager.stopLocalAudio()

        if (mRoomId.isNotEmpty()) {
            mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.AUDIO_AVAILABLE}\",\"available\":${false}}")
        }
    }

    override fun muteLocalAudio(mute: Boolean) {
        mRTCManager.muteLocalAudio(mute)

        if (mRoomId.isNotEmpty()) {
            mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.AUDIO_AVAILABLE}\",\"available\":${!mute}}")
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
            mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.VIDEO_AVAILABLE}\",\"available\":${true}}")
        }
    }

    override fun stopScreenCapture() {
        mRTCManager.stopScreenCapture()

        if (mRoomId.isNotEmpty()) {
            mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.VIDEO_AVAILABLE}\",\"available\":${false}}")
        }
    }

    override fun pauseScreenCapture() {
        mRTCManager.pauseScreenCapture()

        if (mRoomId.isNotEmpty()) {
            mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.VIDEO_AVAILABLE}\",\"available\":${false}}")
        }
    }

    override fun resumeScreenCapture() {
        mRTCManager.resumeScreenCapture()

        if (mRoomId.isNotEmpty()) {
            mSocketManager.sendWebSocketMessage("{\"signal\":\"${SignalCommand.VIDEO_AVAILABLE}\",\"available\":${true}}")
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
        private var mSocketUrl: String? = null
        private var mNetworkType: Int? = null

        @JvmStatic
        @JvmOverloads
        fun getInstance(socketUrl: String? = null, networkType: Int? = null): WXRTC {
            synchronized(WXRTCImpl.Companion::class.java) {
                if (INSTANCE == null) {
                    INSTANCE = WXRTCImpl()
                    mSocketUrl = socketUrl
                    mNetworkType = networkType
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

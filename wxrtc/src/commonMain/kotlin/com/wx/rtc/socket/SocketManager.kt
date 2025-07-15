package com.wx.rtc.socket

import com.wx.rtc.Config
import com.wx.rtc.PlatformContext
import com.wx.rtc.bean.RecvCommandMessage
import com.wx.rtc.bean.SignalCommand
import com.wx.rtc.utils.JsonUtils.JSON
import com.wx.rtc.utils.log
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal class SocketManager() {
    private var mReconnectNum = 0
    private var mWSURL: String = Config.WS_URL
    private var mWSSession: DefaultClientWebSocketSession? = null
    private var mConnected = false
    private var mNeedReconnect = false
    private val mMessages = ArrayList<String>()
    private var mMessageJob: Job? = null
    private var mReconnectJob: Job? = null
    private var mContext: PlatformContext? = null
    private var mListener: SocketListener? = null
    private val socketScope = CoroutineScope(Dispatchers.IO)

    fun init(context: PlatformContext, url: String?) {
        this.mContext = context
        url?.let {
            this.mWSURL = it
        }
        mNeedReconnect = true

        if (mMessageJob?.isActive == true) {
            mMessageJob!!.cancel()
        }
        mMessageJob = socketScope.launch {
            while (true) {
                delay(100L)
                if (isConnected && !mMessages.isEmpty()) {
                    if (sendMessage(mMessages.last())) {
                        mMessages.remove(mMessages.last())
                    }
                }
            }
        }
    }

    fun setListener(listener: SocketListener?) {
        this.mListener = listener
    }

    val isConnected: Boolean
        get() = mWSSession != null && mConnected

    fun startConnect() {
        log(TAG, "WebSocket start connecting...")
        if (isConnected) {
            log(TAG, "WebSocket has connected successfully")
            return
        }

        socketScope.launch {
            try {
                mWSSession = NetworkUtils.httpClient.webSocketSession(mWSURL)
            } catch (e: Exception) {
                e.printStackTrace()
                if (isConnected) {
                    log(TAG, "other reason connect fail ")
                    mWSSession = null
                    mConnected = false
                }
                if (mNeedReconnect) {
                    reconnect()
                }
            }

            mWSSession?.let {
                mConnected = true
                launch {
                    it.incoming.receiveAsFlow()
                        .onCompletion { e ->
                        }
                        .catch { th ->
                            th.printStackTrace()
                            if (isConnected) {
                                log(TAG, "other reason connect fail ")
                                mWSSession = null
                                mConnected = false
                            }
                            if (mNeedReconnect) {
                                reconnect()
                            }
                        }
                        .collect {
                            val textFrame = it as? Frame.Text
                            textFrame?.readText()?.let {
                                println("receive : $it")
                                val message = JSON.decodeFromString<RecvCommandMessage>(it)
                                withContext(Dispatchers.Main) {
                                    if (message.code == 1) {
                                        if (!message.signal.isNullOrEmpty()) {
                                            when (message.signal) {
                                                SignalCommand.LOGIN_BACK -> {
                                                    mListener?.onLogin()
                                                }

                                                SignalCommand.LOGOUT_BACK -> {
                                                    mListener?.onLogout(0)
                                                }

                                                SignalCommand.ENTER_ROOM_BACK -> {
                                                    message.publishUrl?.let {
                                                        mListener?.onEnterRoom(it)
                                                    }
                                                }

                                                SignalCommand.EXIT_ROOM_BACK -> {
                                                    mListener?.onExitRoom(0)
                                                }

                                                SignalCommand.GET_UNPUBLISH -> {
                                                    message.unpublishUrl?.let {
                                                        mListener?.onGetUnpublishUrl(it)
                                                    }
                                                }

                                                SignalCommand.REMOTE_ENTER_ROOM -> {
                                                    if (!message.pullUrl.isNullOrEmpty() && !message.userId.isNullOrEmpty()) {
                                                        mListener?.onRemoteUserEnterRoom(
                                                            message.pullUrl!!, message.userId!!
                                                        )
                                                    }
                                                }

                                                SignalCommand.REMOTE_EXIT_ROOM -> {
                                                    message.userId?.let {
                                                        mListener?.onRemoteUserLeaveRoom(it, 0)
                                                    }
                                                }

                                                SignalCommand.VIDEO_AVAILABLE -> {
                                                    message.userId?.let {
                                                        mListener?.onUserVideoAvailable(
                                                            it, message.available ?: false
                                                        )
                                                    }
                                                }

                                                SignalCommand.AUDIO_AVAILABLE -> {
                                                    message.userId?.let {
                                                        mListener?.onUserAudioAvailable(
                                                            it, message.available ?: false
                                                        )
                                                    }
                                                }

                                                SignalCommand.START_RECORD_BACK -> {
                                                    message.recordFileName?.let {
                                                        mListener?.onRecordStart(it)
                                                    }
                                                }

                                                SignalCommand.END_RECORD_BACK -> {
                                                    message.recordFileName?.let {
                                                        mListener?.onRecordEnd(it)
                                                    }
                                                }

                                                SignalCommand.P2P_MSG_REV -> {
                                                    message.p2pMsg?.let {
                                                        if (!it.from.isNullOrEmpty()) {
                                                            mListener?.onRecvP2PMsg(
                                                                it.from!!, it.message
                                                            )
                                                        }
                                                    }
                                                }

                                                SignalCommand.ROOM_MSG_REV -> {
                                                    message.roomMsg?.let {
                                                        if (!message.userId.isNullOrEmpty() && !it.cmd.isNullOrEmpty()) {
                                                            mListener?.onRecvRoomMsg(
                                                                message.userId!!, it.cmd!!, it.message
                                                            )
                                                        }
                                                    }
                                                }

                                                SignalCommand.CALL_MSG_REV -> {
                                                    message.callMsg?.let {
                                                        if (!it.cmd.isNullOrEmpty()) {
                                                            mListener?.onRecvCallMsg(
                                                                it.userId, it.cmd!!, it.roomId
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (message.result != null && message.result!!.rst != null) {
                                            mListener?.onProcessResult(message.result!!)
                                        }
                                    } else {
                                        mListener?.onError(message.code, message.message ?: "")
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    fun sendWebSocketMessage(message: String): Boolean {
        val success = runBlocking {
            sendMessage(message)
        }
        if (!success) {
            if (mMessages.isEmpty() || mMessages.first() != message) {
                mMessages.add(0, message)
            }
            return false
        } else {
            log(TAG, "webSocket send $message")
            return true
        }
    }

    fun destroy() {
        socketScope.launch {
            if (closeConnect()) {
                mWSSession = null
                mConnected = false
            }
        }

        mMessages.clear()
        if (mMessageJob?.isActive == true) {
            mMessageJob!!.cancel()
            mMessageJob = null
        }
        if (mReconnectJob?.isActive == true) {
            mReconnectJob!!.cancel()
            mReconnectJob = null
        }
    }

    private fun reconnect() {
        if (mReconnectNum < Config.RECONNECT_MAX_NUM) {
            log(TAG, "webSocket reconnect...")
            if (mReconnectJob?.isActive == true) {
                mReconnectJob!!.cancel()
            }
            mReconnectJob = socketScope.launch {
                delay(Config.RECONNECT_MILLIS)

                startConnect()
                mReconnectNum++
            }
        } else {
            log(TAG, "webSocket reconnect fail, reconnect num more than 5, please check url!")
        }
    }

    private fun cancelConnect(): Boolean {
        if (isConnected) {
            mWSSession!!.cancel()
            return true
        }
        log(TAG, "webSocket has closed or not connect")
        return false
    }

    private suspend fun closeConnect(): Boolean {
        mNeedReconnect = false
        if (isConnected) {
            mWSSession!!.close()
            return true
        }
        log(TAG, "webSocket has closed or not connect")
        return false
    }

    private suspend fun sendMessage(text: String): Boolean {
        if (!isConnected) {
//            log(TAG,"webSocket is not connected");
            return false
        }
        mWSSession!!.send(text)
        return true
    }

    companion object {
        private val TAG: String = SocketManager::class.simpleName ?: "SocketManager"
    }
}

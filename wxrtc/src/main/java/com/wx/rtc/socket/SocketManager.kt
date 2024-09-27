package com.wx.rtc.socket

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.wx.rtc.Config
import com.wx.rtc.bean.RecvCommandMessage
import com.wx.rtc.bean.SignalCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.OkHttpClient.Builder
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.LinkedList
import java.util.concurrent.TimeUnit

internal class SocketManager {
    private var mReconnectNum = 0
    private var mWSURL: String = Config.WS_URL
    private var mOkHttpClient: OkHttpClient? = null
    private var mRequest: Request? = null
    private var mWebSocket: WebSocket? = null
    private var mConnected = false
    private var mNeedReconnect = false
    private val mMessages = LinkedList<String>()
    private var mMessageJob: Job? = null
    private var mReconnectJob: Job? = null
    private var mContext: Context? = null
    private var mListener: SocketListener? = null

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun init(context: Context, url: String?) {
        this.mContext = context
        url?.let {
            this.mWSURL = it
        }
        mNeedReconnect = true

        if (mOkHttpClient == null) {
            mOkHttpClient = Builder()
                .retryOnConnectionFailure(true) //允许失败重试
                .pingInterval(30, TimeUnit.SECONDS) //心跳
                .readTimeout(20, TimeUnit.SECONDS) //设置读取超时时间
                .writeTimeout(20, TimeUnit.SECONDS) //设置写入超时时间
                .connectTimeout(10, TimeUnit.SECONDS)
                .build()
        }

        if (mRequest == null) {
            mRequest = Request.Builder()
                .url(mWSURL)
                .build()
        }

        if (mMessageJob?.isActive == true) {
            mMessageJob!!.cancel()
        }
        mMessageJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(100L)
                if (isConnected && !mMessages.isEmpty()) {
                    if (sendMessage(mMessages.last())) {
                        mMessages.removeLast()
                    }
                }
            }
        }
    }

    fun setListener(listener: SocketListener?) {
        this.mListener = listener
    }

    val isConnected: Boolean
        get() = mWebSocket != null && mConnected

    fun startConnect() {
        Log.d(TAG, "WebSocket start connecting...")
        if (isConnected) {
            Log.d(TAG, "WebSocket has connected successfully")
            return
        }
        mOkHttpClient!!.newWebSocket(mRequest!!, webSocketListener)
    }

    fun sendWebSocketMessage(message: String): Boolean {
        if (!sendMessage(message)) {
            if (mMessages.isEmpty() || mMessages.first() != message) {
                mMessages.addFirst(message)
            }
            return false
        } else {
            Log.e(TAG, "webSocket send $message")
            return true
        }
    }

    fun destroy() {
        if (closeConnect()) {
            mWebSocket = null
            mConnected = false
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

    private val webSocketListener: WebSocketListener
        get() = object : WebSocketListener() {
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)

                if (mWebSocket != null || mConnected) {
                    mWebSocket = null
                    mConnected = false
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                //                if (cancelWebSocketConnect()) {
//                    setWebSocket(null);
//                    webSocketConnected = false;
//                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                if (isConnected) {
                    Log.e(TAG, "other reason connect fail ")
                    mWebSocket = null
                    mConnected = false
                }
                if (mNeedReconnect) {
                    reconnect()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                if (mWebSocket !== webSocket) {
                    return
                }
                Log.d(TAG, "enter WebSocketListener onMessage(), String: $text")
                val message = gson.fromJson(text, RecvCommandMessage::class.java)

                CoroutineScope(Dispatchers.Main).launch {
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
                                            message.pullUrl!!,
                                            message.userId!!
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
                                        mListener?.onUserVideoAvailable(it, message.available?:false)
                                    }
                                }
                                SignalCommand.AUDIO_AVAILABLE -> {
                                    message.userId?.let {
                                        mListener?.onUserAudioAvailable(it, message.available?:false)
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
                                SignalCommand.ROOM_MSG_REV -> {
                                    message.roomMsg?.let {
                                        if (!message.userId.isNullOrEmpty() && !it.cmd.isNullOrEmpty() && !it.message.isNullOrEmpty()) {
                                            mListener?.onRecvRoomMsg(
                                                message.userId!!,
                                                it.cmd!!,
                                                it.message!!
                                            )
                                        }
                                    }
                                }
                                SignalCommand.CALL_MSG_REV -> {
                                    message.callMsg?.let {
                                        if (!message.userId.isNullOrEmpty() && !it.cmd.isNullOrEmpty() && !it.roomId.isNullOrEmpty()) {
                                            mListener?.onRecvCallMsg(
                                                message.userId!!,
                                                it.cmd!!,
                                                it.roomId!!
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (message.result != null && message.result!!.rst != null) {
                            mListener?.onResult(message.result!!)
                        }
                    } else {
                        mListener?.onError(message.code, message.message?:"")
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                super.onMessage(webSocket, bytes)
                Log.d(TAG, "enter WebSocketListener onMessage(), bytes: $bytes")
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                mWebSocket = webSocket
                mConnected = true
                mReconnectNum = 0
                if (mReconnectJob?.isActive == true) {
                    mReconnectJob!!.cancel()
                }
                mListener?.onSocketOpen()
            }
        }

    private fun reconnect() {
        if (mReconnectNum < Config.RECONNECT_MAX_NUM) {
            Log.d(TAG, "webSocket reconnect...")
            if (mReconnectJob?.isActive == true) {
                mReconnectJob!!.cancel()
            }
            mReconnectJob = CoroutineScope(Dispatchers.IO).launch {
                delay(Config.RECONNECT_MILLIS)

                startConnect()
                mReconnectNum++
            }
        } else {
            Log.e(TAG, "webSocket reconnect fail, reconnect num more than 5, please check url!")
        }
    }

    private fun cancelConnect(): Boolean {
        if (isConnected) {
            mWebSocket!!.cancel()
            return true
        }
        Log.d(TAG, "webSocket has closed or not connect")
        return false
    }

    private fun closeConnect(): Boolean {
        mNeedReconnect = false
        if (isConnected) {
            return mWebSocket!!.close(1000, "webSocket is closing")
        }
        Log.d(TAG, "webSocket has closed or not connect")
        return false
    }

    private fun sendMessage(text: String): Boolean {
        if (!isConnected) {
//            Log.e(TAG,"webSocket is not connected");
            return false
        }
        return mWebSocket!!.send(text)
    }

    private fun sendMessage(bytes: ByteString): Boolean {
        if (!isConnected) {
            Log.e(TAG, "webSocket is not connected")
            return false
        }
        return mWebSocket!!.send(bytes)
    }

    companion object {
        private val TAG: String = SocketManager::class.java.name
    }
}

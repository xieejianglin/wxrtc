package com.wx.rtc.socket

import android.content.Context
import android.util.Log
import com.wx.rtc.Config
import com.wx.rtc.WXRTCDef
import com.wx.rtc.bean.CallMsg
import com.wx.rtc.bean.P2PMsg
import com.wx.rtc.bean.RecvCommandMessage
import com.wx.rtc.bean.RoomMsg
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
import org.json.JSONObject
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
//                val message = gson.fromJson(text, RecvCommandMessage::class.java)

                val message = RecvCommandMessage()
                var root:JSONObject? = null
                try {
                    root = JSONObject(text)

                    if (root.has("code")) {
                        message.code = root.getInt("code")
                    }
                    if (root.has("message")) {
                        message.message = root.getString("message")
                    }
                    if (root.has("signal")) {
                        message.signal = root.getString("signal")
                    }
                    if (root.has("publish_url")) {
                        message.publishUrl = root.getString("publish_url")
                    }
                    if (root.has("unpublish_url")) {
                        message.unpublishUrl = root.getString("unpublish_url")
                    }
                    if (root.has("user_id")) {
                        message.userId = root.getString("user_id")
                    }
                    if (root.has("pull_url")) {
                        message.pullUrl = root.getString("pull_url")
                    }
                    if (root.has("available")) {
                        message.available = root.getBoolean("available")
                    }
                    if (root.has("record_file_name")) {
                        message.recordFileName = root.getString("record_file_name")
                    }
                    if (root.has("p2p_msg")) {
                        val msgObject = root.getJSONObject("p2p_msg")
                        val p2PMsg = P2PMsg()
                        if (msgObject.has("from")) {
                            p2PMsg.from = msgObject.getString("from")
                        }
                        if (msgObject.has("to")) {
                            p2PMsg.to = msgObject.getString("to")
                        }
                        if (msgObject.has("message")) {
                            p2PMsg.message = msgObject.getString("message")
                        }
                        message.p2pMsg = p2PMsg
                    }
                    if (root.has("room_msg")) {
                        val msgObject = root.getJSONObject("room_msg")
                        val roomMsg = RoomMsg()
                        if (msgObject.has("cmd")) {
                            roomMsg.cmd = msgObject.getString("cmd")
                        }
                        if (msgObject.has("message")) {
                            roomMsg.message = msgObject.getString("message")
                        }
                        message.roomMsg = roomMsg
                    }
                    if (root.has("call_msg")) {
                        val msgObject = root.getJSONObject("call_msg")
                        val callMsg = CallMsg()
                        if (msgObject.has("cmd")) {
                            callMsg.cmd = msgObject.getString("cmd")
                        }
                        if (msgObject.has("user_id")) {
                            callMsg.userId = msgObject.getString("user_id")
                        }
                        if (msgObject.has("room_id")) {
                            callMsg.roomId = msgObject.getString("room_id")
                        }
                        message.callMsg = callMsg
                    }
                    if (root.has("result")) {
                        val msgObject = root.getJSONObject("result")
                        val resultData = WXRTCDef.ProcessData()
                        if (msgObject.has("rst")) {
                            resultData.rst = msgObject.getInt("rst")
                        }
                        if (msgObject.has("need_focus")) {
                            resultData.need_focus = msgObject.getInt("need_focus")
                        }
                        if (msgObject.has("focus_point")) {
                            val array = msgObject.getJSONArray("focus_point")
                            val points = ArrayList<Float>()
                            for (i in 0 until array.length()) {
                                points.add(array.getDouble(i).toFloat())
                            }
                            resultData.focus_point = points
                        }
                        if (msgObject.has("drop_speed")) {
                            resultData.drop_speed = msgObject.getString("drop_speed")
                        }
                        if (msgObject.has("scale")) {
                            resultData.scale = msgObject.getString("scale")
                        }
                        if (msgObject.has("need_magnify")) {
                            resultData.need_magnify = msgObject.getInt("need_magnify")
                        }
                        if (msgObject.has("barcodeDate")) {
                            resultData.barcodeDate = msgObject.getString("barcodeDate")
                        }
                        if (msgObject.has("high_pressure")) {
                            resultData.high_pressure = msgObject.getString("high_pressure")
                        }
                        if (msgObject.has("low_pressure")) {
                            resultData.low_pressure = msgObject.getString("low_pressure")
                        }
                        if (msgObject.has("pulse")) {
                            resultData.pulse = msgObject.getString("pulse")
                        }
                        if (msgObject.has("has_csf")) {
                            resultData.has_csf = msgObject.getInt("has_csf")
                        }
                        if (msgObject.has("right_eye")) {
                            val eyeObject = msgObject.getJSONObject("right_eye")
                            val eyeMark = WXRTCDef.EyeMark()
                            if (eyeObject.has("normal")) {
                                eyeMark.normal = eyeObject.getInt("normal")
                            }
                            if (eyeObject.has("femtosecond")) {
                                eyeMark.femtosecond = eyeObject.getInt("femtosecond")
                            }
                            if (eyeObject.has("astigmatism")) {
                                eyeMark.astigmatism = eyeObject.getInt("astigmatism")
                            }
                            resultData.right_eye = eyeMark
                        }
                        if (msgObject.has("left_eye")) {
                            val eyeObject = msgObject.getJSONObject("left_eye")
                            val eyeMark = WXRTCDef.EyeMark()
                            if (eyeObject.has("normal")) {
                                eyeMark.normal = eyeObject.getInt("normal")
                            }
                            if (eyeObject.has("femtosecond")) {
                                eyeMark.femtosecond = eyeObject.getInt("femtosecond")
                            }
                            if (eyeObject.has("astigmatism")) {
                                eyeMark.astigmatism = eyeObject.getInt("astigmatism")
                            }
                            resultData.left_eye = eyeMark
                        }
                        if (msgObject.has("pid")) {
                            resultData.pid = msgObject.getString("pid")
                        }
                        if (msgObject.has("asr_result")) {
                            resultData.asr_result = msgObject.getString("asr_result")
                        }
                        if (msgObject.has("gesture")) {
                            resultData.gesture = msgObject.getInt("gesture")
                        }
                        if (msgObject.has("oxygen_saturation")) {
                            resultData.oxygen_saturation = msgObject.getString("oxygen_saturation")
                        }
                        if (msgObject.has("weight_scale")) {
                            resultData.weight_scale = msgObject.getString("weight_scale")
                        }
                        if (msgObject.has("respiratory_rate")) {
                            resultData.respiratory_rate = msgObject.getString("respiratory_rate")
                        }
                        if (msgObject.has("capture_image_url")) {
                            resultData.capture_image_url = msgObject.getString("capture_image_url")
                        }
                        message.result = resultData
                    }
                } catch (throwable: Throwable) {
                    mListener?.onError(0, "解析socket返回异常")
                    return
                }

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
                                SignalCommand.P2P_MSG_REV -> {
                                    message.p2pMsg?.let {
                                        if (!it.from.isNullOrEmpty()) {
                                            mListener?.onRecvP2PMsg(
                                                it.from!!,
                                                it.message
                                            )
                                        }
                                    }
                                }
                                SignalCommand.ROOM_MSG_REV -> {
                                    message.roomMsg?.let {
                                        if (!message.userId.isNullOrEmpty() && !it.cmd.isNullOrEmpty()) {
                                            mListener?.onRecvRoomMsg(
                                                message.userId!!,
                                                it.cmd!!,
                                                it.message
                                            )
                                        }
                                    }
                                }
                                SignalCommand.CALL_MSG_REV -> {
                                    message.callMsg?.let {
                                        if (!it.cmd.isNullOrEmpty()) {
                                            mListener?.onRecvCallMsg(
                                                it.userId,
                                                it.cmd!!,
                                                it.roomId
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

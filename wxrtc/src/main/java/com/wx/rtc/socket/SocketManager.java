package com.wx.rtc.socket;

import static com.wx.rtc.Config.RECONNECT_MAX_NUM;
import static com.wx.rtc.Config.RECONNECT_MILLIS;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wx.rtc.Config;
import com.wx.rtc.bean.CallMsg;
import com.wx.rtc.bean.RecvCommandMessage;
import com.wx.rtc.bean.RoomMsg;
import com.wx.rtc.bean.SignalCommand;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class SocketManager {
    private final static String TAG = SocketManager.class.getName();

    private int mReconnectNum;
    private String mWSURL = Config.WS_URL;
    private OkHttpClient mOkHttpClient;
    private Request mRequest;
    private WebSocket mWebSocket;
    private boolean mConnected;
    private boolean mNeedReconnect;
    private LinkedList<String> mMessages = new LinkedList<>();
    private Disposable mMessageDisposable = null;
    private Disposable mReconnectTimerDisposable = null;
    private Context mContext = null;
    private SocketListener mListener = null;

    private Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public void init(Context context, String url){
        this.mContext = context;
        if (!TextUtils.isEmpty(url)){
            this.mWSURL = url;
        }
        mNeedReconnect = true;

        if (mOkHttpClient == null) {
            mOkHttpClient = new OkHttpClient.Builder()
                    .retryOnConnectionFailure(true)//允许失败重试
                    .pingInterval(30, TimeUnit.SECONDS) //心跳
                    .readTimeout(20, TimeUnit.SECONDS)//设置读取超时时间
                    .writeTimeout(20, TimeUnit.SECONDS)//设置写入超时时间
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .build();
        }

        if (mRequest == null) {
            mRequest = new Request.Builder()
                    .url(mWSURL)
                    .build();
        }

        if (mMessageDisposable != null && mMessageDisposable.isDisposed()){
            mMessageDisposable.dispose();
        }
        mMessageDisposable = Observable.interval(100, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(l->{
                    if (isConnected() && !mMessages.isEmpty()){
                        if(sendMessage(mMessages.getLast())){
                            mMessages.removeLast();
                        }
                    }
                });
    }

    public void setListener(SocketListener listener) {
        this.mListener = listener;
    }

    public boolean isConnected() {
        return mWebSocket != null && mConnected;
    }

    public void startConnect() {
        Log.d(TAG,"WebSocket start connecting...");
        if (isConnected()) {
            Log.d(TAG,"WebSocket has connected successfully");
            return;
        }
        mOkHttpClient.newWebSocket(mRequest, getWebSocketListener());
    }

    public boolean sendWebSocketMessage(String message) {
        if (!sendMessage(message)){
            if (mMessages.isEmpty() || !mMessages.getFirst().equals(message)) {
                mMessages.addFirst(message);
            }
            return false;
        }else{
            Log.e(TAG, "webSocket send " + message);
            return true;
        }
    }

    public void destroy(){
        if (closeConnect()) {
            mWebSocket = null;
            mConnected = false;
        }

        mMessages.clear();
        if (mMessageDisposable != null && !mMessageDisposable.isDisposed()){
            mMessageDisposable.dispose();
            mMessageDisposable = null;
        }
        if (mReconnectTimerDisposable != null && !mReconnectTimerDisposable.isDisposed()) {
            mReconnectTimerDisposable.dispose();
            mReconnectTimerDisposable = null;
        }
    }

    private WebSocketListener getWebSocketListener() {
        return new WebSocketListener(){
            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                super.onClosed(webSocket, code, reason);

                if (mWebSocket != null || mConnected) {
                    mWebSocket = null;
                    mConnected = false;
                }
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                super.onClosing(webSocket, code, reason);
//                if (cancelWebSocketConnect()) {
//                    setWebSocket(null);
//                    webSocketConnected = false;
//                }
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                super.onFailure(webSocket, t, response);
                if (isConnected()) {
                    Log.e(TAG,"other reason connect fail ");
                    mWebSocket = null;
                    mConnected = false;
                }
                if (mNeedReconnect) {
                    reconnect();
                }
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                super.onMessage(webSocket, text);
                if (mWebSocket != webSocket){
                    return;
                }
                Log.d(TAG,"enter WebSocketListener onMessage(), String: " + text);
                RecvCommandMessage message = gson.fromJson(text, RecvCommandMessage.class);

                new Handler(mContext.getMainLooper()).post(()->{
                    if (message.getCode() == 1) {
                        if (!TextUtils.isEmpty(message.getSignal())) {
                            if (message.getSignal().equals(SignalCommand.LOGIN_BACK)) {
                                if (mListener != null) {
                                    mListener.onLogin();
                                }
                            }else if (message.getSignal().equals(SignalCommand.LOGOUT_BACK)) {
                                if (mListener != null) {
                                    mListener.onLogout(0);
                                }
                            }else if (message.getSignal().equals(SignalCommand.ENTER_ROOM_BACK)) {
                                if (mListener != null) {
                                    mListener.onEnterRoom(message.getPublishUrl());
                                }
                            }else if (message.getSignal().equals(SignalCommand.EXIT_ROOM_BACK)) {
                                if (mListener != null) {
                                    mListener.onExitRoom(0);
                                }
                            } else if (message.getSignal().equals(SignalCommand.GET_UNPUBLISH)) {
                                if (mListener != null) {
                                    mListener.onGetUnpublishUrl(message.getUnpublishUrl());
                                }
                            } else if (message.getSignal().equals(SignalCommand.REMOTE_ENTER_ROOM)) {
                                if (mListener != null) {
                                    mListener.onRemoteUserEnterRoom(message.getPullUrl(), message.getUserId());
                                }
                            } else if (message.getSignal().equals(SignalCommand.REMOTE_EXIT_ROOM)) {
                                if (mListener != null) {
                                    mListener.onRemoteUserLeaveRoom(message.getUserId(), 0);
                                }
                            } else if (message.getSignal().equals(SignalCommand.START_RECORD_BACK)) {
                                if (mListener != null) {
                                    mListener.onRecordStart(message.getRecordFileName());
                                }
                            } else if (message.getSignal().equals(SignalCommand.END_RECORD_BACK)) {
                                if (mListener != null) {
                                    mListener.onRecordEnd(message.getRecordFileName());
                                }
                            } else if (message.getSignal().equals(SignalCommand.ROOM_MSG_REV)) {
                                RoomMsg roomMsg = message.getRoomMsg();
                                if (mListener != null) {
                                    mListener.onRecvRoomMsg(message.getUserId(), roomMsg.getCmd(), roomMsg.getMessage());
                                }
                            } else if (message.getSignal().equals(SignalCommand.CALL_MSG_REV)) {
                                CallMsg roomMsg = message.getCallMsg();
                                if (mListener != null) {
                                    mListener.onRecvCallMsg(roomMsg.getUserId(), roomMsg.getCmd(), roomMsg.getRoomId());
                                }
                            }
                        }

                        if (message.getResult() != null) {
                            if (mListener != null){
                                mListener.onResult(message.getResult());
                            }
                        }
                    }else{
                        if (mListener != null){
                            mListener.onError(message.getCode(), message.getMessage());
                        }
                    }
                });
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
                super.onMessage(webSocket, bytes);
                Log.d(TAG,"enter WebSocketListener onMessage(), bytes: " + bytes.toString());
            }

            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                super.onOpen(webSocket, response);
                mWebSocket = webSocket;
                mConnected = true;
                mReconnectNum = 0;
                if (mReconnectTimerDisposable != null && !mReconnectTimerDisposable.isDisposed()){
                    mReconnectTimerDisposable.dispose();
                }
                if (mListener != null){
                    mListener.onSocketOpen();
                }
            }
        };
    }

    private void reconnect() {
        if (mReconnectNum < RECONNECT_MAX_NUM) {
            Log.d(TAG,"webSocket reconnect...");
            if (mReconnectTimerDisposable != null && !mReconnectTimerDisposable.isDisposed()){
                mReconnectTimerDisposable.dispose();
            }
            mReconnectTimerDisposable = Observable.timer(RECONNECT_MILLIS, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(l->{
                        startConnect();
                        mReconnectNum++;
                    });
        } else {
            Log.e(TAG,"webSocket reconnect fail, reconnect num more than 5, please check url!");
        }
    }

    private boolean cancelConnect() {
        if (isConnected()) {
            mWebSocket.cancel();
            return true;
        }
        Log.d(TAG,"webSocket has closed or not connect");
        return false;
    }

    private boolean closeConnect() {
        mNeedReconnect = false;
        if (isConnected()) {
            return mWebSocket.close(1000,"webSocket is closing");
        }
        Log.d(TAG,"webSocket has closed or not connect");
        return false;
    }

    private boolean sendMessage(String text) {
        if (!isConnected()) {
//            Log.e(TAG,"webSocket is not connected");
            return false;
        }
        return mWebSocket.send(text);
    }

    private boolean sendMessage(ByteString bytes) {
        if (!isConnected()) {
            Log.e(TAG,"webSocket is not connected");
            return false;
        }
        return mWebSocket.send(bytes);
    }
}

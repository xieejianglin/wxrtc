package com.wx.rtc;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wx.rtc.bean.CallCommand;
import com.wx.rtc.bean.ProcessCommand;
import com.wx.rtc.bean.RecordCommand;
import com.wx.rtc.bean.ResultData;
import com.wx.rtc.bean.RoomMsg;
import com.wx.rtc.bean.SendCommandMessage;
import com.wx.rtc.bean.SignalCommand;
import com.wx.rtc.rtc.RTCListener;
import com.wx.rtc.rtc.RTCManager;
import com.wx.rtc.socket.SocketListener;
import com.wx.rtc.socket.SocketManager;

import org.webrtc.SurfaceViewRenderer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WXRTC implements SocketListener, RTCListener {
    private final static String TAG = WXRTC.class.getName();

    private Toast logToast;
    private static WXRTC INSTANCE = null;
    private static String socketUrl = null;
    private Context mContext;
    private String mAppId;
    private String mUserId;
    private String mRoomId = "";
    private String mInviteId = "";
    private WXRTCDef.Status callStatus = WXRTCDef.Status.None;
    private WXRTCDef.Role callRole = WXRTCDef.Role.None;
    private boolean mLogin = false;
    private boolean enterRoom = false;
    private boolean destorying = false;
    private boolean speakerOn = true;
    private WXRTCListener mRTCListener = null;
    private WXCallListener mCallListener = null;

    private String currentRecordFile = "";
    
    private Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private SocketManager mSocketManager = new SocketManager();

    private RTCManager mRTCManager = new RTCManager();

    public static WXRTC getInstance(){
        return getInstance(null);
    }

    public static WXRTC getInstance(String url){
        if (INSTANCE == null){
            INSTANCE = new WXRTC();
            socketUrl = url;
        }
        return INSTANCE;
    }

    public void init(Context context){
        this.mContext = context;

        mSocketManager.init(context, socketUrl);
        mSocketManager.setListener(this);

        mRTCManager.init(context);
        mRTCManager.setRTCListener(this);
    }

    public void setRTCVideoParam(WXRTCDef.WXRTCVideoEncParam param){
        mRTCManager.setRTCVideoParam(param);
    }

    public void setRTCListener(WXRTCListener listener){
        this.mRTCListener = listener;
    }

    public void setCallListener(WXCallListener listener){
        this.mCallListener = listener;
    }

    public void login(String appId, String userId){
        this.mAppId = appId;
        this.mUserId = userId;

        mSocketManager.startConnect();

        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.LOGIN);
        message.setAppId(appId);
        message.setUserId(userId);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));
    }

    public void logout(){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.LOGOUT);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));
    }

    public void enterRoom(String roomId){
        if (!mLogin){
            if (mRTCListener != null) {
                mRTCListener.onError(1, "请先登录");
            }
            if (mCallListener != null) {
                mCallListener.onError(1, "请先登录");
            }
            return;
        }

        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.ENTER_ROOM);
        message.setRoomId(roomId);
        this.mRoomId = roomId;

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));
    }

    public void exitRoom(){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.EXIT_ROOM);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));
    }

    public void inviteCall(String inviteId, String roomId){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.CALL_CMD);

        SendCommandMessage.CallCmdDTO cmd = new SendCommandMessage.CallCmdDTO();
        cmd.setCmd(CallCommand.INVITE);
        cmd.setUserId(inviteId);
        cmd.setRoomId(roomId);
        message.setCallCmd(cmd);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));

        this.mInviteId = inviteId;
        this.callStatus = WXRTCDef.Status.Calling;
        this.callRole = WXRTCDef.Role.Caller;
    }

    public void cancelInvitation(){
        cancelInvitation(mInviteId);
    }

    public void cancelInvitation(String inviteId){

        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.CALL_CMD);

        SendCommandMessage.CallCmdDTO cmd = new SendCommandMessage.CallCmdDTO();
        cmd.setCmd(CallCommand.CANCEL);
        cmd.setUserId(inviteId);
        message.setCallCmd(cmd);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));

        this.callStatus = WXRTCDef.Status.None;
        this.callRole = WXRTCDef.Role.None;
        this.mInviteId = "";
    }

    public void acceptInvitation(){
        acceptInvitation(mInviteId);
    }

    public void acceptInvitation(String inviteId){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.CALL_CMD);

        SendCommandMessage.CallCmdDTO cmd = new SendCommandMessage.CallCmdDTO();
        cmd.setCmd(CallCommand.ACCEPT);
        cmd.setUserId(inviteId);
        message.setCallCmd(cmd);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));

        this.callStatus = WXRTCDef.Status.Connected;
    }

    public void rejectInvitation(){
        rejectInvitation(mInviteId);
    }

    public void rejectInvitation(String inviteId){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.CALL_CMD);

        SendCommandMessage.CallCmdDTO cmd = new SendCommandMessage.CallCmdDTO();
        cmd.setCmd(CallCommand.REJECT);
        cmd.setUserId(inviteId);
        message.setCallCmd(cmd);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));

        this.callStatus = WXRTCDef.Status.None;
        this.callRole = WXRTCDef.Role.None;
        this.mInviteId = "";
    }

    public void invitationLineBusy(String inviteId){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.CALL_CMD);

        SendCommandMessage.CallCmdDTO cmd = new SendCommandMessage.CallCmdDTO();
        cmd.setCmd(CallCommand.LINE_BUSY);
        cmd.setUserId(inviteId);
        message.setCallCmd(cmd);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));
    }

    public void hangupCall(){
        hangupCall(mInviteId);
    }

    public void hangupCall(String inviteId){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.CALL_CMD);

        SendCommandMessage.CallCmdDTO cmd = new SendCommandMessage.CallCmdDTO();
        cmd.setCmd(CallCommand.HANG_UP);
        cmd.setUserId(inviteId);
        message.setCallCmd(cmd);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));

        this.callStatus = WXRTCDef.Status.None;
        this.callRole = WXRTCDef.Role.None;
        this.mInviteId = "";

    }

    public void sendRoomMsg(String cmd, String msg){
        if (TextUtils.isEmpty(mRoomId)){
            logAndToast("需要进入房间才能发送房间消息");
            return;
        }

        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.SEND_ROOM_MSG);

        RoomMsg roomMsg = new RoomMsg();
        roomMsg.setCmd(cmd);
        roomMsg.setMessage(msg);
        message.setRoomMsg(roomMsg);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));
    }

    public void startProcess(){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.START_PROCESS);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));
    }

    public void endProcess(){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.END_PROCESS);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));
    }

    public static SendCommandMessage.SpeakerDTO getSpeaker(Long userId, String userName){
        SendCommandMessage.SpeakerDTO speaker = new SendCommandMessage.SpeakerDTO();
        speaker.setSpkId(userId);
        speaker.setSpkName(userName);
        return speaker;
    }

    private SendCommandMessage.RecordCmdDTO getRecordCommand(String cmd, String mixId, String extraData, boolean needAfterAsr, String hospitalId, List<SendCommandMessage.SpeakerDTO> spkList){
        SendCommandMessage.RecordCmdDTO dto = new SendCommandMessage.RecordCmdDTO();
        dto.setCmd(cmd);
        dto.setEndFileName(currentRecordFile);
        dto.setMixId(mixId);
        dto.setExtraData(extraData);
        dto.setNeedAfterAsr(needAfterAsr);
        dto.setHospitalId(hospitalId);
        dto.setSpkList(spkList);

        return dto;
    }

    public void startRecord(){
        startRecord(null);
    }

    public void startRecord(String extraData){
        startRecord(extraData, false);
    }

    public void startRecord(String extraData, boolean needAfterAsr){
        startRecord(null, extraData, needAfterAsr, null, null);
    }

    public void startRecord(String mixId, String extraData, boolean needAfterAsr){
        startRecord(mixId, extraData, needAfterAsr, null, null);
    }

    public void startRecord(String extraData, boolean needAfterAsr, String hospitalId, List<SendCommandMessage.SpeakerDTO> spkList){
        startRecord(null, extraData, needAfterAsr, hospitalId, spkList);
    }

    public void startRecord(String mixId, String extraData, boolean needAfterAsr, String hospitalId, List<SendCommandMessage.SpeakerDTO> spkList){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.RECORD_CMD);

        message.setRecordCmd(getRecordCommand(RecordCommand.START_RECORD, mixId, extraData, needAfterAsr, hospitalId, spkList));

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));
    }

    public void endAndStartRecord(){
        endAndStartRecord(null);
    }

    public void endAndStartRecord(String extraData){
        endAndStartRecord(extraData, false);
    }

    public void endAndStartRecord(String extraData, boolean needAfterAsr){
        endAndStartRecord(null, extraData, needAfterAsr, null, null);
    }

    public void endAndStartRecord(String mixId, String extraData, boolean needAfterAsr){
        endAndStartRecord(mixId, extraData, needAfterAsr, null, null);
    }

    public void endAndStartRecord(String mixId, String extraData, boolean needAfterAsr, String hospitalId, List<SendCommandMessage.SpeakerDTO> spkList){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.RECORD_CMD);

        message.setRecordCmd(getRecordCommand(RecordCommand.END_AND_START_RECORD, mixId, extraData, needAfterAsr, hospitalId, spkList));

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));
    }

    public void endRecord(){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.RECORD_CMD);

        SendCommandMessage.RecordCmdDTO dto = new SendCommandMessage.RecordCmdDTO();
        dto.setCmd(RecordCommand.END_RECORD);
        message.setRecordCmd(dto);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));
    }

    public void startAsr(String hospitalId, List<SendCommandMessage.SpeakerDTO> spkList){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.PROCESS_CMD);

        SendCommandMessage.ProcessCmdListDTO dto = new SendCommandMessage.ProcessCmdListDTO();
        dto.setType("audio");
        dto.setCmd(ProcessCommand.START_ASR);
        dto.setHospitalId(hospitalId);
        dto.setSpkList(spkList);

        List<SendCommandMessage.ProcessCmdListDTO> list = new ArrayList<>();
        list.add(dto);
        message.setProcessCmdList(list);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));
    }

    public void endAndStartAsr(String hospitalId, List<SendCommandMessage.SpeakerDTO> spkList){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.PROCESS_CMD);

        SendCommandMessage.ProcessCmdListDTO dto = new SendCommandMessage.ProcessCmdListDTO();
        dto.setType("audio");
        dto.setCmd(ProcessCommand.END_AND_START_ASR);
        dto.setHospitalId(hospitalId);
        dto.setSpkList(spkList);

        List<SendCommandMessage.ProcessCmdListDTO> list = new ArrayList<>();
        list.add(dto);
        message.setProcessCmdList(list);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));
    }

    public void endAsr(){
        SendCommandMessage message = new SendCommandMessage();
        message.setSignal(SignalCommand.PROCESS_CMD);

        SendCommandMessage.ProcessCmdListDTO dto = new SendCommandMessage.ProcessCmdListDTO();
        dto.setType("audio");
        dto.setCmd(ProcessCommand.END_ASR);

        List<SendCommandMessage.ProcessCmdListDTO> list = new ArrayList<>();
        list.add(dto);
        message.setProcessCmdList(list);

        mSocketManager.sendWebSocketMessage(gson.toJson(message, SendCommandMessage.class));
    }

    public boolean isLogin(){
        return mLogin;
    }

    public boolean isEnterRoom(){
        return enterRoom;
    }

    public String getUserId(){
        return mUserId;
    }

    public String getRoomId(){
        return mRoomId;
    }

    public void startLocalVideo(boolean frontCamera, SurfaceViewRenderer renderer){
        if (renderer != null) {
            renderer.setVisibility(View.VISIBLE);
        }
        mRTCManager.startLocalVideo(frontCamera, renderer);
    }

    public void updateLocalVideo(SurfaceViewRenderer renderer){
        if (renderer != null) {
            renderer.setVisibility(View.VISIBLE);
        }
        mRTCManager.updateLocalVideo(renderer);
    }

    public void stopLocalVideo(){
        mRTCManager.stopLocalVideo();
    }

    public void muteLocalVideo(boolean mute){
        mRTCManager.muteLocalVideo(mute);
    }

    public void startRemoteVideo(String userId, SurfaceViewRenderer renderer){
        if (renderer != null) {
            renderer.setVisibility(View.VISIBLE);
        }
        mRTCManager.startRemoteVideo(userId, renderer);
    }

    public void stopRemoteVideo(String userId){
        mRTCManager.stopRemoteVideo(userId);
    }

    public void stopAllRemoteVideo(){
        mRTCManager.stopAllRemoteVideo();
    }

    public void muteRemoteVideo(String userId, boolean mute){
        mRTCManager.muteRemoteVideo(userId, mute);
    }

    public void muteAllRemoteVideo(boolean mute){
        mRTCManager.muteAllRemoteVideo(mute);
    }

    public void setLocalRenderParams(WXRTCDef.WXRTCRenderParams params){
        mRTCManager.setLocalRenderParams(params);
    }

    public void setRemoteRenderParams(String userId, WXRTCDef.WXRTCRenderParams params){
        mRTCManager.setRemoteRenderParams(userId, params);
    }

    public void startLocalAudio(){
        setSpeakerOn(speakerOn);
        mRTCManager.startLocalAudio();
    }

    public void stopLocalAudio(){
        mRTCManager.stopLocalAudio();
    }

    public void muteLocalAudio(boolean mute){
        mRTCManager.muteLocalAudio(mute);
    }

    public void muteRemoteAudio(String userId, boolean mute){
        mRTCManager.muteRemoteAudio(userId, mute);
    }

    public void muteAllRemoteAudio(boolean mute){
        mRTCManager.muteAllRemoteAudio(mute);
    }

    public void setRemoteAudioVolume(String userId, int volume){
        mRTCManager.setRemoteAudioVolume(userId, volume);
    }

    public void setAllRemoteAudioVolume(int volume){
        mRTCManager.setAllRemoteAudioVolume(volume);
    }

    public void setSpeakerOn(boolean speakerOn){
        this.speakerOn = speakerOn;
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(speakerOn);
    }

    public void switchCamera(boolean frontCamera){
        mRTCManager.switchPublishCamera(frontCamera);
    }

    public boolean isCameraZoomSupported(){
        return mRTCManager.isCameraZoomSupported();
    }

    public int getCameraMaxZoom(){
        return mRTCManager.getCameraMaxZoom();
    }

    public void setCameraZoom(int value){
        mRTCManager.setCameraZoom(value);
    }

    public int getCameraZoom(){
        return mRTCManager.getCameraZoom();
    }

    public boolean snapshotVideo(String userId){
        if (userId.equals(mUserId)) {
            return mRTCManager.snapshotLocalVideo();
        }
        return mRTCManager.snapshotRemoteVideo(userId);
    }

    private void onCallCancelled(String userId){
        if (mCallListener != null) {
            mCallListener.onCallCancelled(userId);
        }
        callRole = WXRTCDef.Role.None;
        callStatus = WXRTCDef.Status.None;
    }

    private void logAndToast(String msg) {
        Log.d(TAG, msg);
        new Handler(mContext.getMainLooper()).post(()->{
            if (logToast != null) {
                logToast.cancel();
            }
            logToast = Toast.makeText(mContext, msg, Toast.LENGTH_SHORT);
            logToast.show();
        });
    }

    public void destory() {
        destorying = true;

        if (mSocketManager.isConnected()) {
            exitRoom();

            if (!enterRoom) {
                destoryInternal();
            }
        } else {
            destoryInternal();
        }
    }

    private void destoryInternal(){
        setSpeakerOn(false);

        setRTCListener(null);
        setCallListener(null);

        mRTCManager.destory();

        mSocketManager.destroy();

        if (logToast != null) {
            logToast.cancel();
        }

        enterRoom = false;
        INSTANCE = null;
    }


    @Override
    public void onError(int errCode, String errMsg) {
        if (mRTCListener != null){
            mRTCListener.onError(errCode, errMsg);
        }
        if (mCallListener != null){
            mCallListener.onError(errCode, errMsg);
        }
    }

    @Override
    public void onSocketOpen() {
        if (mLogin && !TextUtils.isEmpty(mAppId) && !TextUtils.isEmpty(mUserId)){
            login(mAppId, mUserId);
        }
    }

    @Override
    public void onLogin() {
        mLogin = true;

        if (enterRoom && TextUtils.isEmpty(mRoomId)) {
            enterRoom(mRoomId);
        }

        if (mRTCListener != null){
            mRTCListener.onLogin();
        }
    }

    @Override
    public void onLogout(int reason) {
        mLogin = false;
        mUserId = null;

        if (mRTCListener != null){
            mRTCListener.onLogout(reason);
        }

        if (destorying) {
            destoryInternal();
        }
    }

    @Override
    public void onEnterRoom(String publishUrl) {
        boolean needOnEnterRoom = true;
        if (enterRoom){
            needOnEnterRoom = false;
        }

        enterRoom = true;

        mRTCManager.startPublish(publishUrl, mUserId);

        if (needOnEnterRoom && mRTCListener != null) {
            mRTCListener.onEnterRoom();
        }
    }

    @Override
    public void onExitRoom(int reason) {
        enterRoom = false;
        mRoomId = "";

        mRTCManager.stopAllPC();

        if (mRTCListener != null){
            mRTCListener.onExitRoom(reason);
        }
    }

    @Override
    public void onGetUnpublishUrl(String unpublishUrl) {
        mRTCManager.setUnpublishUrl(unpublishUrl);
    }

    @Override
    public void onRemoteUserEnterRoom(String pullUrl, String userId) {
        mRTCManager.startOnePull(pullUrl, userId);
        if (mRTCListener != null){
            mRTCListener.onRemoteUserEnterRoom(userId);
        }
        if (mCallListener != null){
            mCallListener.onUserJoin(userId);
        }
    }

    @Override
    public void onRemoteUserLeaveRoom(String userId, int reason) {
        if (mRTCListener != null){
            mRTCListener.onRemoteUserLeaveRoom(userId, reason);
        }
        if (mCallListener != null){
            mCallListener.onUserLeave(userId);
        }
    }

    @Override
    public void onRecvRoomMsg(String userId, String cmd, String message) {
        if (mRTCListener != null){
            mRTCListener.onRecvRoomMsg(userId, cmd, message);
        }
    }

    @Override
    public void onRecvCallMsg(String userId, String cmd, String roomId) {
        switch (cmd){
            case CallCommand.NEW_INVITATION_RECEIVED:
                if (callStatus == WXRTCDef.Status.Calling || callStatus == WXRTCDef.Status.Connected){
                    invitationLineBusy(mUserId);
                    return;
                }
                callRole = WXRTCDef.Role.Callee;
                callStatus = WXRTCDef.Status.Calling;
                mInviteId = userId;
                if (mCallListener != null) {
                    mCallListener.onCallReceived(userId);
                }
                break;
            case CallCommand.INVITATION_NO_RESP:
                if (mCallListener != null) {
                    mCallListener.onUserNoResponse(userId);
                }
                onCallCancelled(mUserId);
                break;
            case CallCommand.INVITATION_REJECTED:
                if (mCallListener != null) {
                    mCallListener.onUserReject(userId);
                }
                onCallCancelled(mUserId);
                break;
            case CallCommand.INVITATION_LINE_BUSY:
                if (mCallListener != null) {
                    mCallListener.onUserLineBusy(userId);
                }
                onCallCancelled(mUserId);
                break;
            case CallCommand.INVITATION_CANCELED:
                onCallCancelled(userId);
                break;
            case CallCommand.INVITATION_ACCEPTED:
                callStatus = WXRTCDef.Status.Connected;
                if (mCallListener != null) {
                    mCallListener.onCallBegin(roomId, callRole);
                }
                break;
            case CallCommand.CALL_END:
                if (mCallListener != null) {
                    mCallListener.onCallEnd(roomId, callRole);
                }
                callRole = WXRTCDef.Role.None;
                callStatus = WXRTCDef.Status.None;
                break;
            default:
                break;
        }
    }

    @Override
    public void onResult(ResultData resultData) {
        if (mRTCListener != null){
            mRTCListener.onResult(resultData);
        }
    }

    @Override
    public void onRecordStart(String fileName) {
        currentRecordFile = fileName;
        if (mRTCListener != null){
            mRTCListener.onRecordStart(fileName);
        }
    }

    @Override
    public void onRecordEnd(String fileName) {
        if (fileName.equals(currentRecordFile)){
            currentRecordFile = "";
        }
        if (mRTCListener != null){
            mRTCListener.onRecordEnd(fileName);
        }
    }




    @Override
    public void onConnected() {

    }

    @Override
    public void onClose() {

    }

    @Override
    public void onScreenShot(File file) {
        if (mRTCListener != null){
            mRTCListener.onScreenShot(file);
        }
    }
}

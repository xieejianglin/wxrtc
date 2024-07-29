package com.wx.rtc.rtc;

import static com.wx.rtc.Config.RECONNECT_MAX_NUM;
import static com.wx.rtc.Config.RECONNECT_MILLIS;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wx.rtc.WXRTCDef;
import com.wx.rtc.utils.RTCUtils;

import org.jetbrains.annotations.NotNull;
import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import top.zibin.luban.Luban;

public class RTCManager implements PeerConnectionClient.PeerConnectionEvents{
    private final static String TAG = RTCManager.class.getName();

    @Nullable
    private PeerConnectionClient publishPCClient;
    private long callStartedTimeMs;
    private int webRTCReconnectNum;
    private String publishUrl;
    private String unpublishUrl;
    private boolean useFrontCamera = false;
    private boolean publishAudioSendEnabled = false;
    private boolean publishVideoSendEnabled = false;
    private boolean publishVideoMute = false;
    private boolean publishAudioMute = false;
    private WXRTCDef.WXRTCRenderParams publishRenderParams;
    private boolean remoteVideoAllMute = false;
    private boolean remoteAudioAllMute = false;
    private int remoteAudioAllVolume;
    private final EglBase eglBase = EglBase.create();
    private Context mContext;
    private WXRTCDef.WXRTCVideoEncParam mVideoEncParam = new WXRTCDef.WXRTCVideoEncParam();
    private Disposable mPublishSendOfferTimerDisposable = null;
    private Disposable mRTCReconnectTimerDisposable = null;
    private Disposable mDeletePublishTimerDisposable = null;

    private final ProxyVideoSink localProxyVideoSink = new ProxyVideoSink();

    private final List<PeerConnectionManager> pcManagers = new ArrayList<>();
    private SurfaceViewRenderer localRenderer = null;
    private RTCListener mRTCListener = null;
    private boolean mStartPublish = false;


    public void init(Context context){
        this.mContext = context;
    }

    public void setRTCVideoParam(WXRTCDef.WXRTCVideoEncParam param){
        this.mVideoEncParam = param;
        if (publishPCClient != null) {
            publishPCClient.setParameters(param);
            Size size = RTCUtils.getVideoResolution(param.videoResolution);
            publishPCClient.changeVideoSource(size.getWidth(), size.getHeight(), param.videoFps);
            publishPCClient.setVideoBitrate(param.videoMinBitrate, param.videoMaxBitrate);
        }
    }

    public void setRTCListener(RTCListener listener){
        this.mRTCListener = listener;
    }

    public void startPublish(String publishUrl, String userId){
        this.publishUrl = publishUrl;
        mStartPublish = true;

        Size size = RTCUtils.getVideoResolution(mVideoEncParam.videoResolution);

        PeerConnectionClient.PeerConnectionParameters peerConnectionParameters =
                new PeerConnectionClient.PeerConnectionParameters(true, true, false, false,
                        false, false, size.getWidth(), size.getHeight(), mVideoEncParam.videoFps,
                        mVideoEncParam.videoMaxBitrate, "H264 Baseline",
                        true,
                        true,
                        0, "opus",
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false, null);

        publishPCClient = new PeerConnectionClient(mContext.getApplicationContext(), eglBase, peerConnectionParameters, this);
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();

        options.networkIgnoreMask = 0;
//        options.disableEncryption = true;
//        options.disableNetworkMonitor = true;
        publishPCClient.setLocalVideoTrackEnabled(publishVideoSendEnabled);
        publishPCClient.setRemoteVideoTrackEnabled(false);
        publishPCClient.setLocalAudioTrackEnabled(publishAudioSendEnabled);
        publishPCClient.createPeerConnectionFactory(options);

        localProxyVideoSink.setTarget(userId, localRenderer);

        if (mVideoEncParam != null) {
            setRTCVideoParam(mVideoEncParam);
        }
        if (publishRenderParams != null) {
            setLocalRenderParams(publishRenderParams);
        }
//        if (publishVideoSendEnabled) {
//            if (localRenderer != null) {
//                startLocalVideo(useFrontCamera, localRenderer);
//            }
//        } else {
//            stopLocalVideo();
//        }
        muteLocalVideo(publishVideoMute);
        muteLocalAudio(publishAudioMute);

        publishPCClient.startCall(localProxyVideoSink, null);
    }

    public void setUnpublishUrl(String unpublishUrl){
        this.unpublishUrl = unpublishUrl;
    }

    public void stopAllPC(){
        mStartPublish = false;

        if (publishPCClient != null) {
            publishPCClient.setNeedReconnect(false);
        }
        unpublish();

        stopAllPull();
    }

    public void startOnePull(String pullUrl, String userId){
        PeerConnectionManager pcm = getPeerConnectionManagerByUserId(userId);

        PeerConnectionClient pc = startPull(userId, pullUrl, true, true);

        if (pcm == null) {
            pcm = new PeerConnectionManager();
            pcm.userId = userId;
            pcManagers.add(pcm);

            if (remoteVideoAllMute) {
                pc.setRemoteAudioTrackEnabled(remoteVideoAllMute);
            }
            if (remoteAudioAllMute) {
                pc.setRemoteAudioTrackEnabled(remoteAudioAllMute);
            }
//            pc.setRemoteAudioTrackVolume(remoteAudioAllVolume);
        } else {
            if (pcm.client != null) {
                pcm.client.close();
            }

            if (pcm.videoRecvEnabled && pcm.videoSink != null) {
                startRemoteVideo(userId, (SurfaceViewRenderer)pcm.videoSink.getTarget());
            }
            if (pcm.videoRecvMute) {
                pc.setRemoteVideoTrackEnabled(pcm.videoRecvMute);
            }
            if (pcm.audioRecvMute) {
                pc.setRemoteAudioTrackEnabled(pcm.audioRecvMute);
            }

//            pc.setRemoteAudioTrackVolume(pcm.audioVolume);
        }
        pcm.sendSdpUrl = pullUrl;
        pcm.client = pc;

        if (pcm.videoSink == null) {
            pcm.videoSink = new ProxyVideoSink();
        }

        pc.startCall(null, pcm.videoSink);
    }

    private void reconnect(PeerConnectionClient pc) {
        if (webRTCReconnectNum < RECONNECT_MAX_NUM) {
            Log.d(TAG,"webrtc reconnect...");
            if (mRTCReconnectTimerDisposable != null && !mRTCReconnectTimerDisposable.isDisposed()) {
                mRTCReconnectTimerDisposable.dispose();
            }
            mRTCReconnectTimerDisposable = Observable.timer(RECONNECT_MILLIS, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(l->{
                        if (pc == publishPCClient) {
                            init(mContext);
                        } else {
                            boolean audioRecvEnabled = pc.isAudioRecvEnabled();
                            boolean videoRecvEnabled = pc.isVideoRecvEnabled();

                            PeerConnectionManager pcm = getPeerConnectionManagerByPc(pc);

                            if (pcm == null){
                                return;
                            }
                            String streamUrl = pcm.sendSdpUrl;
                            String userId = pcm.userId;

                            if (pcm.videoSink != null) {
                                pcm.videoSink.release();
                            }

                            pcManagers.remove(pcm);

                            startPull(userId, streamUrl, audioRecvEnabled, videoRecvEnabled);
                        }
                        webRTCReconnectNum++;
                    });
        } else {
            Log.e(TAG,"webSocket reconnect fail, reconnect num more than 5, please check url!");
        }
    }

    private void setLocalRenderer(SurfaceViewRenderer renderer){
        localProxyVideoSink.setTarget(renderer);

        if (this.localRenderer != null && renderer != null && this.localRenderer == renderer){
            return;
        }

        if (this.localRenderer != null){
            this.localRenderer.release();
            this.localRenderer = null;
        }

        if (renderer != null) {
            renderer.release();
            renderer.init(eglBase.getEglBaseContext(), null);
//            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            renderer.setEnableHardwareScaler(false /* enabled */);
        }

        this.localRenderer = renderer;
        if (publishRenderParams == null) {
            publishRenderParams = new WXRTCDef.WXRTCRenderParams();
        }
        if (publishRenderParams != null) {
            setLocalRenderParams(publishRenderParams);
        }
    }

    public void startLocalVideo(boolean frontCamera, SurfaceViewRenderer renderer){
        useFrontCamera = frontCamera;
        publishVideoSendEnabled = true;
        if (publishPCClient != null) {
            publishPCClient.startVideoSource(frontCamera);
        }
        setLocalRenderer(renderer);
    }

    public void updateLocalVideo(SurfaceViewRenderer renderer){
        setLocalRenderer(renderer);
    }

    public void stopLocalVideo(){
        publishVideoSendEnabled = false;
        if (publishPCClient != null) {
            publishPCClient.stopVideoSource();
        }
        setLocalRenderer(null);
    }

    public void muteLocalVideo(boolean mute){
        publishVideoMute = mute;
        if (publishPCClient != null) {
            publishPCClient.setLocalVideoTrackEnabled(!mute);
        }
    }

    public void startRemoteVideo(String userId, SurfaceViewRenderer renderer){

        if (localRenderer != null && renderer == localRenderer) {
            setLocalRenderer(null);
        }

        PeerConnectionManager pcm = getPeerConnectionManagerByUserId(userId);

        if (pcm != null){
            if (pcm.videoSink != null && pcm.videoSink.getTarget() != null && renderer != null && pcm.videoSink.getTarget() == renderer){
                return;
            }

            if (renderer != null) {
                renderer.release();
                renderer.init(eglBase.getEglBaseContext(), null);
//                renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                renderer.setEnableHardwareScaler(false /* enabled */);
            }

            if (pcm.videoSink != null) {
                if (pcm.videoSink.getTarget() != localRenderer) {
                    pcm.videoSink.release();
                }

                pcm.videoSink.setTarget(renderer);
            }else{
                ProxyVideoSink remoteVideoSink = new ProxyVideoSink();
                remoteVideoSink.setTarget(userId, renderer);

                pcm.videoSink = remoteVideoSink;
            }
        }else{
            pcm = new PeerConnectionManager();
            pcm.userId = userId;

            if (renderer != null) {
                renderer.release();
                renderer.init(eglBase.getEglBaseContext(), null);
                renderer.setEnableHardwareScaler(false /* enabled */);
            }

            ProxyVideoSink remoteVideoSink = new ProxyVideoSink();
            remoteVideoSink.setTarget(userId, renderer);

            pcm.videoSink = remoteVideoSink;

            pcManagers.add(pcm);
        }

        pcm.videoRecvEnabled = true;

        if (pcm.renderParams == null) {
            pcm.renderParams = new WXRTCDef.WXRTCRenderParams();
        }
        setRendererRenderParams(false, renderer, pcm.renderParams);
    }

    public void stopRemoteVideo(String userId){
        PeerConnectionManager pcm = getPeerConnectionManagerByUserId(userId);
        if (pcm != null) {
            pcm.videoRecvEnabled = false;
            if (pcm.videoSink != null) {
                pcm.videoSink.release();
            }
        }
    }

    public void stopAllRemoteVideo(){
        for (PeerConnectionManager pcm : pcManagers) {
            pcm.videoRecvEnabled = false;
            if (pcm.videoSink != null) {
                pcm.videoSink.release();
            }
        }
    }

    public void muteRemoteVideo(String userId, boolean mute){
        PeerConnectionManager pcm = getPeerConnectionManagerByUserId(userId);
        if (pcm != null) {
            if (pcm.client != null) {
                pcm.client.setRemoteVideoTrackEnabled(!mute);
            }
        } else {
            pcm = new PeerConnectionManager();
            pcm.userId = userId;
            pcManagers.add(pcm);
        }
        pcm.videoRecvMute = mute;
    }

    public void muteAllRemoteVideo(boolean mute){
        remoteVideoAllMute = mute;
        for (PeerConnectionManager pcm : pcManagers) {
            pcm.videoRecvMute = mute;
            if (pcm.client != null) {
                pcm.client.setRemoteVideoTrackEnabled(!mute);
            }
        }
    }

    private void setRendererRenderParams(boolean isLocalrenderer, SurfaceViewRenderer renderer, WXRTCDef.WXRTCRenderParams params){
        if (params.fillMode == WXRTCDef.WXRTC_VIDEO_RENDER_MODE_FILL) {
            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        }else if (params.fillMode == WXRTCDef.WXRTC_VIDEO_RENDER_MODE_FIT) {
            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        }
        if (params.mirrorType == WXRTCDef.WXRTC_VIDEO_MIRROR_TYPE_AUTO) {
            if (isLocalrenderer) {
                renderer.setMirror(useFrontCamera);
            } else {
                renderer.setMirror(false);
            }
        } else if (params.mirrorType == WXRTCDef.WXRTC_VIDEO_MIRROR_TYPE_ENABLE) {
            renderer.setMirror(true);
        } else if (params.mirrorType == WXRTCDef.WXRTC_VIDEO_MIRROR_TYPE_DISABLE) {
            renderer.setMirror(false);
        }
    }

    public void setLocalRenderParams(WXRTCDef.WXRTCRenderParams params){
        publishRenderParams = params;
        if (localRenderer != null) {
            setRendererRenderParams(true, localRenderer, params);
        }
    }

    public void setRemoteRenderParams(String userId, WXRTCDef.WXRTCRenderParams params){
        PeerConnectionManager pcm = getPeerConnectionManagerByUserId(userId);
        if (pcm != null) {
            pcm.renderParams = params;
            if (pcm.videoSink != null && pcm.videoSink.getTarget() != null) {
                SurfaceViewRenderer renderer = (SurfaceViewRenderer)pcm.videoSink.getTarget();
                setRendererRenderParams(false, renderer, params);
            }
        }else{
            PeerConnectionManager manager = new PeerConnectionManager();
            manager.userId = userId;
            manager.renderParams = params;

            pcManagers.add(manager);
        }
    }

    public void startLocalAudio(){
        publishAudioSendEnabled = true;
        if (publishPCClient != null) {
            publishPCClient.startAudioCapture();
        }
    }

    public void stopLocalAudio(){
        publishAudioSendEnabled = false;
        if (publishPCClient != null) {
            publishPCClient.stopAudioCapture();
        }
    }

    public void muteLocalAudio(boolean mute) {
        publishAudioMute = mute;
        if (publishPCClient != null) {
            publishPCClient.setLocalAudioTrackEnabled(!mute);
        }
    }

    public void muteRemoteAudio(String userId, boolean mute){
        PeerConnectionManager pcm = getPeerConnectionManagerByUserId(userId);

        if (pcm != null) {
            if (pcm.client != null) {
                pcm.client.setRemoteAudioTrackEnabled(!mute);
            }
        } else {
            pcm = new PeerConnectionManager();
            pcm.userId = userId;
            pcManagers.add(pcm);
        }
        pcm.audioRecvMute = mute;
    }

    public void muteAllRemoteAudio(boolean mute){
        remoteAudioAllMute = mute;
        for (PeerConnectionManager pcm : pcManagers) {
            pcm.audioRecvMute = mute;
            if (pcm.client != null) {
                pcm.client.setRemoteAudioTrackEnabled(!mute);
            }
        }
    }

    public void setRemoteAudioVolume(String userId, int volume){
        PeerConnectionManager pcm = getPeerConnectionManagerByUserId(userId);

        if (pcm != null) {
            if (pcm.client != null) {
                pcm.client.setRemoteAudioTrackVolume(volume);
            }
        } else {
            pcm = new PeerConnectionManager();
            pcm.userId = userId;
            pcManagers.add(pcm);
        }
        pcm.audioVolume = volume;
    }

    public void setAllRemoteAudioVolume(int volume){
        remoteAudioAllVolume = volume;
        for (PeerConnectionManager pcm : pcManagers) {
            pcm.audioVolume = volume;
            if (pcm.client != null) {
                pcm.client.setRemoteAudioTrackVolume(volume);
            }
        }
    }

    public boolean isCameraZoomSupported(){
        if (publishPCClient != null){
            return publishPCClient.isCameraZoomSupported();
        }
        return false;
    }

    public int getCameraMaxZoom(){
        if (publishPCClient != null){
            return publishPCClient.getCameraMaxZoom();
        }
        return 0;
    }

    public void setCameraZoom(int value){
        if (publishPCClient != null){
            publishPCClient.setCameraZoom(value);
        }
    }

    public int getCameraZoom(){
        if (publishPCClient != null){
            return publishPCClient.getCameraZoom();
        }
        return 0;
    }

    public boolean snapshotLocalVideo () {
        return snapshotVideo(localRenderer);
    }

    public boolean snapshotRemoteVideo(String userId){
        PeerConnectionManager pcm = getPeerConnectionManagerByUserId(userId);
        if (pcm == null || pcm.videoSink == null || pcm.videoSink.getTarget() == null || pcm.client == null) {
            return false;
        }
        return snapshotVideo((SurfaceViewRenderer)pcm.videoSink.getTarget());
    }

    private boolean snapshotVideo(SurfaceViewRenderer renderer){
        if (renderer == null){
            return false;
        }
        renderer.addFrameListener(new EglRenderer.FrameListener() {
            @Override
            public void onFrame(Bitmap bitmap) {
                renderer.post(()->{
                    renderer.removeFrameListener(this);

                    Flowable.just(bitmap)
                            .observeOn(Schedulers.io())
                            .map(new Function<Bitmap, String>() {
                                @Override public String apply(@NonNull Bitmap bm) throws Exception {
                                    return saveImage(bm);
                                }
                            })
                            .map(new Function<String, File>() {
                                @Override public File apply(@NonNull String filePath) throws Exception {
                                    // 同步方法直接返回压缩后的文件
                                    return Luban.with(mContext).ignoreBy(100).get(filePath);
                                }
                            })
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(file->{
                                if (mRTCListener != null){
                                    mRTCListener.onScreenShot(file);
                                }
                            });
                });
            }
        }, 1);
        return true;
    }

    public void destory(){
        if(publishPCClient != null) {
            publishPCClient.setNeedReconnect(false);
        }
        unpublish();
        setLocalRenderer(null);
        stopAllPull();

        publishPCClient = null;

        if (mPublishSendOfferTimerDisposable != null && !mPublishSendOfferTimerDisposable.isDisposed()) {
            mPublishSendOfferTimerDisposable.dispose();
            mPublishSendOfferTimerDisposable = null;
        }
        if (mRTCReconnectTimerDisposable != null && !mRTCReconnectTimerDisposable.isDisposed()) {
            mRTCReconnectTimerDisposable.dispose();
            mRTCReconnectTimerDisposable = null;
        }
        if (mDeletePublishTimerDisposable != null && !mDeletePublishTimerDisposable.isDisposed()) {
            mDeletePublishTimerDisposable.dispose();
            mDeletePublishTimerDisposable = null;
        }

        eglBase.release();
    }

    private String saveImage(Bitmap bitmap) {
        String filePath = mContext.getApplicationContext().getExternalCacheDir().getParentFile().getAbsolutePath()  + File.separator + "shot.jpg";
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);

        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            fos.write(stream.toByteArray());
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return filePath;
    }

    private PeerConnectionClient startPull(final String userId, final String streamUrl, boolean audioRecvEnabled, boolean videoRecvEnabled){

        boolean audioSendEnabled = false;
        boolean videoSendEnabled = false;

        Size size = RTCUtils.getVideoResolution(mVideoEncParam.videoResolution);

        PeerConnectionClient.PeerConnectionParameters peerConnectionParameters =
                new PeerConnectionClient.PeerConnectionParameters(audioSendEnabled, videoSendEnabled, audioRecvEnabled, videoRecvEnabled,
                        false, false, size.getWidth(), size.getHeight(), mVideoEncParam.videoFps,
                        mVideoEncParam.videoMaxBitrate, "H264 Baseline",
                        true,
                        true,
                        0, "opus",
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false, null);

        PeerConnectionClient pc = new PeerConnectionClient(mContext.getApplicationContext(), eglBase, peerConnectionParameters, this);
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();

        options.networkIgnoreMask = 0;
//        options.disableEncryption = true;
//        options.disableNetworkMonitor = true;
        pc.setLocalVideoTrackEnabled(videoSendEnabled);
        pc.setRemoteVideoTrackEnabled(videoRecvEnabled);
        pc.setLocalAudioTrackEnabled(audioSendEnabled);
        pc.createPeerConnectionFactory(options);

        return pc;
    }

    private void sendPublishSdp(final PeerConnectionClient pc, final SessionDescription sdp){
        sendOfferSdp(pc, publishUrl, sdp);
    }

    private void sendPullSdp(final PeerConnectionClient pc, final SessionDescription sdp){
        PeerConnectionManager pcm = getPeerConnectionManagerByPc(pc);
        if (pcm != null) {
            String url = pcm.sendSdpUrl;
            sendOfferSdp(pc, url, sdp);
        }
    }

    private void sendOfferSdp(final PeerConnectionClient pc, String url, final SessionDescription sdp){
        if (pc == publishPCClient) {
            if (mPublishSendOfferTimerDisposable != null && !mPublishSendOfferTimerDisposable.isDisposed()) {
                mPublishSendOfferTimerDisposable.dispose();
            }
        }

        String sdpDes = sdp.description;
        OkHttpClient client = new OkHttpClient();

        RequestBody body = RequestBody.create(sdpDes, MediaType.parse("application/sdp"));
        Request requst = new Request.Builder()
                .url(url)
                .header("Content-type", "application/sdp")
                .post(body)
                .build();
        client.newCall(requst).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.e(TAG, url+ " onFailure: " + e );
                if (pc == publishPCClient) {
                    if (mPublishSendOfferTimerDisposable != null && !mPublishSendOfferTimerDisposable.isDisposed()) {
                        mPublishSendOfferTimerDisposable.dispose();
                    }
                }
                Disposable disposable = Observable.timer(2, TimeUnit.SECONDS)
                        .subscribe(l->{
                            if (mStartPublish) {
                                Log.e(TAG, "sendOfferSdp onResponse unsuccess sendOfferSdp");
                                sendOfferSdp(pc, url, sdp);
                            }
                        });
                if (pc == publishPCClient) {
                    mPublishSendOfferTimerDisposable = disposable;
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String sdpString = response.body().string();
                    Log.e(TAG, url + " onResponse: " + sdpString);

                    if (pc == publishPCClient) {
                        if (mPublishSendOfferTimerDisposable != null && !mPublishSendOfferTimerDisposable.isDisposed()) {
                            mPublishSendOfferTimerDisposable.dispose();
                        }
                    }
                    if (mDeletePublishTimerDisposable != null && !mDeletePublishTimerDisposable.isDisposed()) {
                        mDeletePublishTimerDisposable.dispose();
                    }

                    SessionDescription answerSdp = new SessionDescription(SessionDescription.Type.fromCanonicalForm("answer"), sdpString);
                    pc.setRemoteDescription(answerSdp);
                }else{
                    if (pc == publishPCClient) {
                        if (response.code() == 502){
                            Log.e(TAG, "sendOfferSdp 502 deletePublish");
                            deletePublish();
                        }
                        if (mPublishSendOfferTimerDisposable != null && !mPublishSendOfferTimerDisposable.isDisposed()) {
                            mPublishSendOfferTimerDisposable.dispose();
                        }
                    }
                    Disposable disposable = Observable.timer(2, TimeUnit.SECONDS)
                            .subscribe(l->{
                                if (mStartPublish) {
                                    Log.e(TAG, "sendOfferSdp onResponse unsuccess sendOfferSdp");
                                    sendOfferSdp(pc, url, sdp);
                                }
                            });
                    if (pc == publishPCClient) {
                        mPublishSendOfferTimerDisposable = disposable;
                    }
                }
            }
        });

    }

    private void deletePublish(){
        if (TextUtils.isEmpty(unpublishUrl)){
            return;
        }

        if (mDeletePublishTimerDisposable != null && !mDeletePublishTimerDisposable.isDisposed()) {
            mDeletePublishTimerDisposable.dispose();
        }

        OkHttpClient client = new OkHttpClient();

        Request requst = new Request.Builder()
                .url(unpublishUrl)
                .delete()
                .build();
        client.newCall(requst).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.e(TAG, "deletePublish onFailure: " + e );
                if (mDeletePublishTimerDisposable != null && !mDeletePublishTimerDisposable.isDisposed()) {
                    mDeletePublishTimerDisposable.dispose();
                }
                mDeletePublishTimerDisposable = Observable.timer(1, TimeUnit.SECONDS)
                        .subscribe(l->{
                            Log.e(TAG, "deletePublish onFailure deletePublish");
                            deletePublish();
                        });
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.e(TAG, "deletePublish onResponse: " + response.body().string());
                    if (mDeletePublishTimerDisposable != null && !mDeletePublishTimerDisposable.isDisposed()) {
                        mDeletePublishTimerDisposable.dispose();
                    }
                }else{
                    if (mDeletePublishTimerDisposable != null && !mDeletePublishTimerDisposable.isDisposed()) {
                        mDeletePublishTimerDisposable.dispose();
                    }
                    mDeletePublishTimerDisposable = Observable.timer(1, TimeUnit.SECONDS)
                            .subscribe(l->{
                                Log.e(TAG, "deletePublish onResponse deletePublish");
                                deletePublish();
                            });
                }
            }
        });
    }

    private void unpublish() {
        Log.e(TAG, "unpublish onResponse");
        deletePublish();

        if (publishPCClient != null) {
            publishPCClient.stopVideoSource();
            publishPCClient.setLocalVideoTrackEnabled(false);
        }
//        setLocalRenderer(null);

        if (publishPCClient != null) {
            publishPCClient.stopAudioCapture();
            publishPCClient.setLocalAudioTrackEnabled(false);
        }

        if (publishPCClient != null) {
            publishPCClient.close();
        }
    }


    private void stopPull(String userId){
        PeerConnectionManager pcm = getPeerConnectionManagerByUserId(userId);
        if (pcm != null){
            stopPull(pcm.client);
        }
    }

    private void stopPull(PeerConnectionClient pc){
        PeerConnectionManager pcm = getPeerConnectionManagerByPc(pc);
        pc.close();
    }

    private void stopAllPull(){
        stopAllRemoteVideo();

        for (PeerConnectionManager pcm: pcManagers) {
            if (pcm.videoSink != null) {
                pcm.videoSink.release();
            }
            if (pcm.client != null) {
                pcm.client.setNeedReconnect(false);
                pcm.client.close();
                pcm.client = null;
            }
        }
        pcManagers.clear();
    }

    @Override
    public void onLocalDescription(final PeerConnectionClient pc, final SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        new Handler(mContext.getMainLooper()).post(() -> {
            if (publishPCClient != null && pc == publishPCClient) {
                if (publishVideoSendEnabled) {
                    startLocalVideo(useFrontCamera, localRenderer);
                } else {
                    stopLocalVideo();
                }
            }

            pc.setVideoBitrate(mVideoEncParam.videoMinBitrate, mVideoEncParam.videoMaxBitrate);
        });
    }

    @Override
    public void onIceCandidate(final PeerConnectionClient pc, final IceCandidate candidate) {
        new Handler(mContext.getMainLooper()).post(() -> {
        });
    }

    @Override
    public void onIceCandidatesRemoved(final PeerConnectionClient pc, final IceCandidate[] candidates) {
        new Handler(mContext.getMainLooper()).post(() -> {
        });
    }

    @Override
    public void onIceGatheringComplete(final PeerConnectionClient pc, SessionDescription sdp){
        new Handler(mContext.getMainLooper()).post(() -> {
            if (pc == publishPCClient) {
                if (mStartPublish) {
                    sendPublishSdp(pc, sdp);
                }
            }else{
                sendPullSdp(pc, sdp);
            }
        });
    }

    @Override
    public void onIceConnected(final PeerConnectionClient pc) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        new Handler(mContext.getMainLooper()).post(() -> {});
    }

    @Override
    public void onIceDisconnected(final PeerConnectionClient pc) {
        new Handler(mContext.getMainLooper()).post(() -> {});
    }

    @Override
    public void onConnected(final PeerConnectionClient pc) {
        if (mRTCReconnectTimerDisposable != null && !mRTCReconnectTimerDisposable.isDisposed()) {
            mRTCReconnectTimerDisposable.dispose();
        }
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        new Handler(mContext.getMainLooper()).post(() -> {
            if (publishPCClient != null && pc == publishPCClient) {
                if (mRTCListener != null){
                    mRTCListener.onConnected();
                }
//                if (publishVideoSendEnabled) {
//                    startLocalVideo(useFrontCamera, localRenderer);
//                }
                if (publishAudioSendEnabled) {
                    startLocalAudio();
                } else {
                    stopLocalAudio();
                }
            }
        });
    }

    @Override
    public void onDisconnected(final PeerConnectionClient pc) {
        new Handler(mContext.getMainLooper()).post(() -> {
            if (pc == null) {
                return;
            }
            if (pc == publishPCClient){
                unpublish();
            } else {
                stopPull(pc);
            }
        });
    }

    @Override
    public void onPeerConnectionClosed(final PeerConnectionClient pc) {
        new Handler(mContext.getMainLooper()).post(() -> {

            if (pc == null) {
                return;
            }

            if (pc.isNeedReconnect()){
                reconnect(pc);
            }else {
                if (pc == publishPCClient) {
                    publishPCClient = null;
                    if (mRTCListener != null){
                        mRTCListener.onClose();
                    }
                } else {
                    PeerConnectionManager pcm = getPeerConnectionManagerByPc(pc);
                    if (pcm == null){
                        return;
                    }

                    if (pcm.videoSink != null) {
                        pcm.videoSink.release();
                    }

                    pcManagers.remove(pcm);
                }
            }
        });
    }

    @Override
    public void onPeerConnectionStatsReady(final PeerConnectionClient pc, final StatsReport[] reports) {
        new Handler(mContext.getMainLooper()).post(() -> {

        });
    }

    @Override
    public void onPeerConnectionError(final PeerConnectionClient pc, final String description) {
//        reportError(description);
        new Handler(mContext.getMainLooper()).post(() -> {

            if (pc == null) {
                return;
            }
            pc.setNeedReconnect(true);

            if (pc == publishPCClient){
                unpublish();
            } else {
                stopPull(pc);
            }
        });
    }

    @Override
    public void onDataChannelMessage(final PeerConnectionClient pc, String message) {

    }

    public void switchPublishCamera(boolean frontCamera){
        if (useFrontCamera != frontCamera && publishPCClient != null) {
            publishPCClient.switchCamera();
        }
        useFrontCamera = frontCamera;
    }

    private PeerConnectionManager getPeerConnectionManagerByUserId(String userId){
        for (PeerConnectionManager pcm: pcManagers) {
            if (userId.equals(pcm.userId)){
                return pcm;
            }
        }
        return null;
    }

    private PeerConnectionManager getPeerConnectionManagerByStreamUrl(String url){
        for (PeerConnectionManager pcm: pcManagers) {
            if (url.equals(pcm.sendSdpUrl)){
                return pcm;
            }
        }
        return null;
    }

    private PeerConnectionManager getPeerConnectionManagerByVideoSink(ProxyVideoSink videoSink){
        for (PeerConnectionManager pcm: pcManagers) {
            if (videoSink == pcm.videoSink){
                return pcm;
            }
        }
        return null;
    }

    private PeerConnectionManager getPeerConnectionManagerByPc(PeerConnectionClient pc){
        for (PeerConnectionManager pcm: pcManagers) {
            if (pc == pcm.client){
                return pcm;
            }
        }
        return null;
    }
}

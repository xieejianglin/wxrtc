package org.webrtc.voiceengine;

import static android.media.AudioTrack.PLAYSTATE_PLAYING;
import static android.media.AudioTrack.STATE_INITIALIZED;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Process;
import androidx.annotation.Nullable;
import java.nio.ByteBuffer;
import org.webrtc.ContextUtils;
import org.webrtc.Logging;
import org.webrtc.ThreadUtils;

public class WebRtcAudioTrack {
  private static final boolean DEBUG = false;
  
  private static final String TAG = "WebRtcAudioTrack";
  
  private static final int BITS_PER_SAMPLE = 16;
  
  private static final int CALLBACK_BUFFER_SIZE_MS = 10;
  
  private static final int BUFFERS_PER_SECOND = 1000 / CALLBACK_BUFFER_SIZE_MS;
  
  private static final long AUDIO_TRACK_THREAD_JOIN_TIMEOUT_MS = 2000L;
  
  private static final int DEFAULT_USAGE = AudioAttributes.USAGE_VOICE_COMMUNICATION;
  
  private static int usageAttribute = DEFAULT_USAGE;
  
  private final long nativeAudioTrack;
  
  private final AudioManager audioManager;
  
  public static synchronized void setAudioTrackUsageAttribute(int usage) {
    Logging.w(TAG, "Default usage attribute is changed from: 2 to " + usage);
    usageAttribute = usage;
  }
  
  private final ThreadUtils.ThreadChecker threadChecker = new ThreadUtils.ThreadChecker();
  
  private ByteBuffer byteBuffer;
  
  @Nullable
  private AudioTrack audioTrack;
  
  @Nullable
  private AudioTrackThread audioThread;
  
  private static volatile boolean speakerMute;
  
  private byte[] emptyBytes;
  
  @Nullable
  private static WebRtcAudioTrackErrorCallback errorCallbackOld;
  
  @Nullable
  private static ErrorCallback errorCallback;
  
  public enum AudioTrackStartErrorCode {
    AUDIO_TRACK_START_EXCEPTION, AUDIO_TRACK_START_STATE_MISMATCH;
  }
  
  @Deprecated
  public static void setErrorCallback(WebRtcAudioTrackErrorCallback errorCallback) {
    Logging.d(TAG, "Set error callback (deprecated");
    errorCallbackOld = errorCallback;
  }
  
  public static void setErrorCallback(ErrorCallback errorCallback) {
    Logging.d(TAG, "Set extended error callback");
    WebRtcAudioTrack.errorCallback = errorCallback;
  }
  
  private class AudioTrackThread extends Thread {
    private volatile boolean keepAlive = true;
    
    public AudioTrackThread(String name) {
      super(name);
    }
    
    public void run() {
      Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
      Logging.d(TAG, "AudioTrackThread" + WebRtcAudioUtils.getThreadInfo());
      WebRtcAudioTrack.assertTrue((WebRtcAudioTrack.this.audioTrack.getPlayState() == PLAYSTATE_PLAYING));
      int sizeInBytes = WebRtcAudioTrack.this.byteBuffer.capacity();
      while (this.keepAlive) {
        WebRtcAudioTrack.this.nativeGetPlayoutData(sizeInBytes, WebRtcAudioTrack.this.nativeAudioTrack);
        WebRtcAudioTrack.assertTrue((sizeInBytes <= WebRtcAudioTrack.this.byteBuffer.remaining()));
        if (WebRtcAudioTrack.speakerMute) {
          WebRtcAudioTrack.this.byteBuffer.clear();
          WebRtcAudioTrack.this.byteBuffer.put(WebRtcAudioTrack.this.emptyBytes);
          WebRtcAudioTrack.this.byteBuffer.position(0);
        } 
        int bytesWritten = WebRtcAudioTrack.this.audioTrack.write(WebRtcAudioTrack.this.byteBuffer, sizeInBytes, AudioTrack.WRITE_BLOCKING);
        if (bytesWritten != sizeInBytes) {
          Logging.e(TAG, "AudioTrack.write played invalid number of bytes: " + bytesWritten);
          if (bytesWritten < 0) {
            this.keepAlive = false;
            WebRtcAudioTrack.this.reportWebRtcAudioTrackError("AudioTrack.write failed: " + bytesWritten);
          } 
        } 
        WebRtcAudioTrack.this.byteBuffer.rewind();
      } 
      if (WebRtcAudioTrack.this.audioTrack != null) {
        Logging.d(TAG, "Calling AudioTrack.stop...");
        try {
          WebRtcAudioTrack.this.audioTrack.stop();
          Logging.d(TAG, "AudioTrack.stop is done.");
        } catch (IllegalStateException e) {
          Logging.e(TAG, "AudioTrack.stop failed: " + e.getMessage());
        } 
      } 
    }
    
    public void stopThread() {
      Logging.d(TAG, "stopThread");
      this.keepAlive = false;
    }
  }
  
  WebRtcAudioTrack(long nativeAudioTrack) {
    this.threadChecker.checkIsOnValidThread();
    Logging.d(TAG, "ctor" + WebRtcAudioUtils.getThreadInfo());
    this.nativeAudioTrack = nativeAudioTrack;
    this
      .audioManager = (AudioManager)ContextUtils.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
  }
  
  private int initPlayout(int sampleRate, int channels, double bufferSizeFactor) {
    this.threadChecker.checkIsOnValidThread();
    Logging.d(TAG, "initPlayout(sampleRate=" + sampleRate + ", channels=" + channels + ", bufferSizeFactor=" + bufferSizeFactor + ")");
    int bytesPerFrame = channels * (BITS_PER_SAMPLE / 8);
    this.byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * sampleRate / BUFFERS_PER_SECOND);
    Logging.d(TAG, "byteBuffer.capacity: " + this.byteBuffer.capacity());
    this.emptyBytes = new byte[this.byteBuffer.capacity()];
    nativeCacheDirectBufferAddress(this.byteBuffer, this.nativeAudioTrack);
    int channelConfig = channelCountToConfiguration(channels);
    int minBufferSizeInBytes = (int)(AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT) * bufferSizeFactor);
    Logging.d(TAG, "minBufferSizeInBytes: " + minBufferSizeInBytes);
    if (minBufferSizeInBytes < this.byteBuffer.capacity()) {
      reportWebRtcAudioTrackInitError("AudioTrack.getMinBufferSize returns an invalid value.");
      return -1;
    } 
    if (this.audioTrack != null) {
      reportWebRtcAudioTrackInitError("Conflict with existing AudioTrack.");
      return -1;
    } 
    try {
      this.audioTrack = createAudioTrack(sampleRate, channelConfig, minBufferSizeInBytes);
    } catch (IllegalArgumentException e) {
      reportWebRtcAudioTrackInitError(e.getMessage());
      releaseAudioResources();
      return -1;
    } 
    if (this.audioTrack == null || this.audioTrack.getState() != STATE_INITIALIZED) {
      reportWebRtcAudioTrackInitError("Initialization of audio track failed.");
      releaseAudioResources();
      return -1;
    } 
    logMainParameters();
    logMainParametersExtended();
    return minBufferSizeInBytes;
  }
  
  private boolean startPlayout() {
    this.threadChecker.checkIsOnValidThread();
    Logging.d(TAG, "startPlayout");
    assertTrue((this.audioTrack != null));
    assertTrue((this.audioThread == null));
    try {
      this.audioTrack.play();
    } catch (IllegalStateException e) {
      reportWebRtcAudioTrackStartError(AudioTrackStartErrorCode.AUDIO_TRACK_START_EXCEPTION, "AudioTrack.play failed: " + e
          .getMessage());
      releaseAudioResources();
      return false;
    } 
    if (this.audioTrack.getPlayState() != PLAYSTATE_PLAYING) {
      reportWebRtcAudioTrackStartError(AudioTrackStartErrorCode.AUDIO_TRACK_START_STATE_MISMATCH, "AudioTrack.play failed - incorrect state :" + this.audioTrack
          
          .getPlayState());
      releaseAudioResources();
      return false;
    } 
    this.audioThread = new AudioTrackThread("AudioTrackJavaThread");
    this.audioThread.start();
    return true;
  }
  
  private boolean stopPlayout() {
    this.threadChecker.checkIsOnValidThread();
    Logging.d(TAG, "stopPlayout");
    assertTrue((this.audioThread != null));
    logUnderrunCount();
    this.audioThread.stopThread();
    Logging.d(TAG, "Stopping the AudioTrackThread...");
    this.audioThread.interrupt();
    if (!ThreadUtils.joinUninterruptibly(this.audioThread, AUDIO_TRACK_THREAD_JOIN_TIMEOUT_MS)) {
      Logging.e(TAG, "Join of AudioTrackThread timed out.");
      WebRtcAudioUtils.logAudioState(TAG);
    } 
    Logging.d(TAG, "AudioTrackThread has now been stopped.");
    this.audioThread = null;
    releaseAudioResources();
    return true;
  }
  
  private int getStreamMaxVolume() {
    this.threadChecker.checkIsOnValidThread();
    Logging.d(TAG, "getStreamMaxVolume");
    assertTrue((this.audioManager != null));
    return this.audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
  }
  
  private boolean setStreamVolume(int volume) {
    this.threadChecker.checkIsOnValidThread();
    Logging.d(TAG, "setStreamVolume(" + volume + ")");
    assertTrue((this.audioManager != null));
    if (this.audioManager.isVolumeFixed()) {
      Logging.e(TAG, "The device implements a fixed volume policy.");
      return false;
    } 
    this.audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, volume, 0);
    return true;
  }
  
  private int getStreamVolume() {
    this.threadChecker.checkIsOnValidThread();
    Logging.d(TAG, "getStreamVolume");
    assertTrue((this.audioManager != null));
    return this.audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
  }
  
  private void logMainParameters() {
    Logging.d(TAG, "AudioTrack: session ID: " + this.audioTrack
        .getAudioSessionId() + ", channels: " + this.audioTrack
        .getChannelCount() + ", sample rate: " + this.audioTrack
        .getSampleRate() + ", max gain: " + 
        
        AudioTrack.getMaxVolume());
  }
  
  private static AudioTrack createAudioTrack(int sampleRateInHz, int channelConfig, int bufferSizeInBytes) {
    Logging.d(TAG, "createAudioTrack");
    int nativeOutputSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_VOICE_CALL);
    Logging.d(TAG, "nativeOutputSampleRate: " + nativeOutputSampleRate);
    if (sampleRateInHz != nativeOutputSampleRate)
      Logging.w(TAG, "Unable to use fast mode since requested sample rate is not native"); 
    if (usageAttribute != AudioAttributes.USAGE_VOICE_COMMUNICATION)
      Logging.w(TAG, "A non default usage attribute is used: " + usageAttribute); 
    return new AudioTrack((new AudioAttributes.Builder())
        .setUsage(usageAttribute)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build(), (new AudioFormat.Builder())
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(sampleRateInHz)
        .setChannelMask(channelConfig)
        .build(), bufferSizeInBytes, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
  }
  
  private void logBufferSizeInFrames() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
      Logging.d(TAG, "AudioTrack: buffer size in frames: " + this.audioTrack.getBufferSizeInFrames());
  }
  
  private int getBufferSizeInFrames() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
      return this.audioTrack.getBufferSizeInFrames(); 
    return -1;
  }
  
  private void logBufferCapacityInFrames() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
      Logging.d(TAG, "AudioTrack: buffer capacity in frames: " + this.audioTrack.getBufferCapacityInFrames());
  }
  
  private void logMainParametersExtended() {
    logBufferSizeInFrames();
    logBufferCapacityInFrames();
  }
  
  private void logUnderrunCount() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
      Logging.d(TAG, "underrun count: " + this.audioTrack.getUnderrunCount()); 
  }
  
  private static void assertTrue(boolean condition) {
    if (!condition)
      throw new AssertionError("Expected condition to be true"); 
  }
  
  private int channelCountToConfiguration(int channels) {
    return (channels == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
  }
  
  private native void nativeCacheDirectBufferAddress(ByteBuffer paramByteBuffer, long paramLong);
  
  private native void nativeGetPlayoutData(int paramInt, long paramLong);
  
  public static void setSpeakerMute(boolean mute) {
    Logging.w(TAG, "setSpeakerMute(" + mute + ")");
    speakerMute = mute;
  }
  
  private void releaseAudioResources() {
    Logging.d(TAG, "releaseAudioResources");
    if (this.audioTrack != null) {
      this.audioTrack.release();
      this.audioTrack = null;
    } 
  }
  
  private void reportWebRtcAudioTrackInitError(String errorMessage) {
    Logging.e(TAG, "Init playout error: " + errorMessage);
    WebRtcAudioUtils.logAudioState(TAG);
    if (errorCallbackOld != null)
      errorCallbackOld.onWebRtcAudioTrackInitError(errorMessage); 
    if (errorCallback != null)
      errorCallback.onWebRtcAudioTrackInitError(errorMessage); 
  }
  
  private void reportWebRtcAudioTrackStartError(AudioTrackStartErrorCode errorCode, String errorMessage) {
    Logging.e(TAG, "Start playout error: " + errorCode + ". " + errorMessage);
    WebRtcAudioUtils.logAudioState(TAG);
    if (errorCallbackOld != null)
      errorCallbackOld.onWebRtcAudioTrackStartError(errorMessage); 
    if (errorCallback != null)
      errorCallback.onWebRtcAudioTrackStartError(errorCode, errorMessage); 
  }
  
  private void reportWebRtcAudioTrackError(String errorMessage) {
    Logging.e(TAG, "Run-time playback error: " + errorMessage);
    WebRtcAudioUtils.logAudioState(TAG);
    if (errorCallbackOld != null)
      errorCallbackOld.onWebRtcAudioTrackError(errorMessage); 
    if (errorCallback != null)
      errorCallback.onWebRtcAudioTrackError(errorMessage); 
  }
  
  public static interface ErrorCallback {
    void onWebRtcAudioTrackInitError(String param1String);
    
    void onWebRtcAudioTrackStartError(WebRtcAudioTrack.AudioTrackStartErrorCode param1AudioTrackStartErrorCode, String param1String);
    
    void onWebRtcAudioTrackError(String param1String);
  }
  
  @Deprecated
  public static interface WebRtcAudioTrackErrorCallback {
    void onWebRtcAudioTrackInitError(String param1String);
    
    void onWebRtcAudioTrackStartError(String param1String);
    
    void onWebRtcAudioTrackError(String param1String);
  }
}

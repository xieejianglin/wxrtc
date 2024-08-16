package org.webrtc.voiceengine;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Process;
import androidx.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.webrtc.Logging;
import org.webrtc.ThreadUtils;

public class WebRtcAudioRecord {
  private static final boolean DEBUG = false;
  
  private static final String TAG = "WebRtcAudioRecord";
  
  private static final int BITS_PER_SAMPLE = 16;
  
  private static final int CALLBACK_BUFFER_SIZE_MS = 10;
  
  private static final int BUFFERS_PER_SECOND = 1000 / CALLBACK_BUFFER_SIZE_MS;
  
  private static final int BUFFER_SIZE_FACTOR = 2;
  
  private static final long AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS = 2000L;
  
  private static final int DEFAULT_AUDIO_SOURCE = getDefaultAudioSource();
  
  private static int audioSource = DEFAULT_AUDIO_SOURCE;
  
  private final long nativeAudioRecord;
  
  @Nullable
  private WebRtcAudioEffects effects;
  
  private ByteBuffer byteBuffer;
  
  @Nullable
  private AudioRecord audioRecord;
  
  @Nullable
  private AudioRecordThread audioThread;
  
  private static volatile boolean microphoneMute;
  
  private byte[] emptyBytes;
  
  @Nullable
  private static WebRtcAudioRecordErrorCallback errorCallback;
  
  @Nullable
  private static WebRtcAudioRecordSamplesReadyCallback audioSamplesReadyCallback;
  
  public enum AudioRecordStartErrorCode {
    AUDIO_RECORD_START_EXCEPTION, AUDIO_RECORD_START_STATE_MISMATCH;
  }
  
  public static void setErrorCallback(WebRtcAudioRecordErrorCallback errorCallback) {
    Logging.d(TAG, "Set error callback");
    WebRtcAudioRecord.errorCallback = errorCallback;
  }
  
  public static class AudioSamples {
    private final int audioFormat;
    
    private final int channelCount;
    
    private final int sampleRate;
    
    private final byte[] data;
    
    private AudioSamples(AudioRecord audioRecord, byte[] data) {
      this.audioFormat = audioRecord.getAudioFormat();
      this.channelCount = audioRecord.getChannelCount();
      this.sampleRate = audioRecord.getSampleRate();
      this.data = data;
    }
    
    public int getAudioFormat() {
      return this.audioFormat;
    }
    
    public int getChannelCount() {
      return this.channelCount;
    }
    
    public int getSampleRate() {
      return this.sampleRate;
    }
    
    public byte[] getData() {
      return this.data;
    }
  }
  
  public static void setOnAudioSamplesReady(WebRtcAudioRecordSamplesReadyCallback callback) {
    audioSamplesReadyCallback = callback;
  }
  
  private class AudioRecordThread extends Thread {
    private volatile boolean keepAlive = true;
    
    public AudioRecordThread(String name) {
      super(name);
    }
    
    public void run() {
      Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
      Logging.d(TAG, "AudioRecordThread" + WebRtcAudioUtils.getThreadInfo());
      WebRtcAudioRecord.assertTrue((WebRtcAudioRecord.this.audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING));
      long lastTime = System.nanoTime();
      while (this.keepAlive) {
        int bytesRead = WebRtcAudioRecord.this.audioRecord.read(WebRtcAudioRecord.this.byteBuffer, WebRtcAudioRecord.this.byteBuffer.capacity());
        if (bytesRead == WebRtcAudioRecord.this.byteBuffer.capacity()) {
          if (WebRtcAudioRecord.microphoneMute) {
            WebRtcAudioRecord.this.byteBuffer.clear();
            WebRtcAudioRecord.this.byteBuffer.put(WebRtcAudioRecord.this.emptyBytes);
          } 
          if (this.keepAlive)
            WebRtcAudioRecord.this.nativeDataIsRecorded(bytesRead, WebRtcAudioRecord.this.nativeAudioRecord); 
          if (WebRtcAudioRecord.audioSamplesReadyCallback != null) {
            byte[] data = Arrays.copyOf(WebRtcAudioRecord.this.byteBuffer.array(), WebRtcAudioRecord.this.byteBuffer.capacity());
            WebRtcAudioRecord.audioSamplesReadyCallback.onWebRtcAudioRecordSamplesReady(new WebRtcAudioRecord.AudioSamples(WebRtcAudioRecord.this.audioRecord, data));
          } 
          continue;
        } 
        String errorMessage = "AudioRecord.read failed: " + bytesRead;
        Logging.e(TAG, errorMessage);
        if (bytesRead == -3) {
          this.keepAlive = false;
          WebRtcAudioRecord.this.reportWebRtcAudioRecordError(errorMessage);
        } 
      } 
      try {
        if (WebRtcAudioRecord.this.audioRecord != null)
          WebRtcAudioRecord.this.audioRecord.stop(); 
      } catch (IllegalStateException e) {
        Logging.e(TAG, "AudioRecord.stop failed: " + e.getMessage());
      } 
    }
    
    public void stopThread() {
      Logging.d(TAG, "stopThread");
      this.keepAlive = false;
    }
  }
  
  WebRtcAudioRecord(long nativeAudioRecord) {
    Logging.d(TAG, "ctor" + WebRtcAudioUtils.getThreadInfo());
    this.nativeAudioRecord = nativeAudioRecord;
    this.effects = WebRtcAudioEffects.create();
  }
  
  private boolean enableBuiltInAEC(boolean enable) {
    Logging.d(TAG, "enableBuiltInAEC(" + enable + ")");
    if (this.effects == null) {
      Logging.e(TAG, "Built-in AEC is not supported on this platform");
      return false;
    } 
    return this.effects.setAEC(enable);
  }
  
  private boolean enableBuiltInNS(boolean enable) {
    Logging.d(TAG, "enableBuiltInNS(" + enable + ")");
    if (this.effects == null) {
      Logging.e(TAG, "Built-in NS is not supported on this platform");
      return false;
    } 
    return this.effects.setNS(enable);
  }
  
  private int initRecording(int sampleRate, int channels) {
    Logging.d(TAG, "initRecording(sampleRate=" + sampleRate + ", channels=" + channels + ")");
    if (this.audioRecord != null) {
      reportWebRtcAudioRecordInitError("InitRecording called twice without StopRecording.");
      return -1;
    } 
    int bytesPerFrame = channels * (BITS_PER_SAMPLE / 8);
    int framesPerBuffer = sampleRate / BUFFERS_PER_SECOND;
    this.byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer);
    Logging.d(TAG, "byteBuffer.capacity: " + this.byteBuffer.capacity());
    this.emptyBytes = new byte[this.byteBuffer.capacity()];
    nativeCacheDirectBufferAddress(this.byteBuffer, this.nativeAudioRecord);
    int channelConfig = channelCountToConfiguration(channels);
    int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
    if (minBufferSize == -1 || minBufferSize == -2) {
      reportWebRtcAudioRecordInitError("AudioRecord.getMinBufferSize failed: " + minBufferSize);
      return -1;
    } 
    Logging.d(TAG, "AudioRecord.getMinBufferSize: " + minBufferSize);
    int bufferSizeInBytes = Math.max(BUFFER_SIZE_FACTOR * minBufferSize, this.byteBuffer.capacity());
    Logging.d(TAG, "bufferSizeInBytes: " + bufferSizeInBytes);
    try {
      this.audioRecord = new AudioRecord(audioSource, sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes);
    } catch (IllegalArgumentException e) {
      reportWebRtcAudioRecordInitError("AudioRecord ctor error: " + e.getMessage());
      releaseAudioResources();
      return -1;
    } 
    if (this.audioRecord == null || this.audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
      reportWebRtcAudioRecordInitError("Failed to create a new AudioRecord instance");
      releaseAudioResources();
      return -1;
    } 
    if (this.effects != null)
      this.effects.enable(this.audioRecord.getAudioSessionId()); 
    logMainParameters();
    logMainParametersExtended();
    return framesPerBuffer;
  }
  
  private boolean startRecording() {
    Logging.d(TAG, "startRecording");
    assertTrue((this.audioRecord != null));
    assertTrue((this.audioThread == null));
    try {
      this.audioRecord.startRecording();
    } catch (IllegalStateException e) {
      reportWebRtcAudioRecordStartError(AudioRecordStartErrorCode.AUDIO_RECORD_START_EXCEPTION, "AudioRecord.startRecording failed: " + e.getMessage());
      return false;
    } 
    if (this.audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
      reportWebRtcAudioRecordStartError(AudioRecordStartErrorCode.AUDIO_RECORD_START_STATE_MISMATCH, "AudioRecord.startRecording failed - incorrect state :" + this.audioRecord.getRecordingState());
      return false;
    } 
    this.audioThread = new AudioRecordThread("AudioRecordJavaThread");
    this.audioThread.start();
    return true;
  }
  
  private boolean stopRecording() {
    Logging.d(TAG, "stopRecording");
    assertTrue((this.audioThread != null));
    this.audioThread.stopThread();
    if (!ThreadUtils.joinUninterruptibly(this.audioThread, AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS)) {
      Logging.e(TAG, "Join of AudioRecordJavaThread timed out");
      WebRtcAudioUtils.logAudioState(TAG);
    } 
    this.audioThread = null;
    if (this.effects != null)
      this.effects.release(); 
    releaseAudioResources();
    return true;
  }
  
  private void logMainParameters() {
    Logging.d(TAG, "AudioRecord: session ID: " + this.audioRecord
        .getAudioSessionId() + ", channels: " + this.audioRecord
        .getChannelCount() + ", sample rate: " + this.audioRecord
        .getSampleRate());
  }
  
  private void logMainParametersExtended() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
      Logging.d(TAG, "AudioRecord: buffer size in frames: " + this.audioRecord.getBufferSizeInFrames());
  }
  
  private static void assertTrue(boolean condition) {
    if (!condition)
      throw new AssertionError("Expected condition to be true"); 
  }
  
  private int channelCountToConfiguration(int channels) {
    return (channels == 1) ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO;
  }
  
  private native void nativeCacheDirectBufferAddress(ByteBuffer paramByteBuffer, long paramLong);
  
  private native void nativeDataIsRecorded(int paramInt, long paramLong);
  
  public static synchronized void setAudioSource(int source) {
    Logging.w(TAG, "Audio source is changed from: " + audioSource + " to " + source);
    audioSource = source;
  }
  
  private static int getDefaultAudioSource() {
    return MediaRecorder.AudioSource.VOICE_COMMUNICATION;
  }
  
  public static void setMicrophoneMute(boolean mute) {
    Logging.w(TAG, "setMicrophoneMute(" + mute + ")");
    microphoneMute = mute;
  }
  
  private void releaseAudioResources() {
    Logging.d(TAG, "releaseAudioResources");
    if (this.audioRecord != null) {
      this.audioRecord.release();
      this.audioRecord = null;
    } 
  }
  
  private void reportWebRtcAudioRecordInitError(String errorMessage) {
    Logging.e(TAG, "Init recording error: " + errorMessage);
    WebRtcAudioUtils.logAudioState(TAG);
    if (errorCallback != null)
      errorCallback.onWebRtcAudioRecordInitError(errorMessage); 
  }
  
  private void reportWebRtcAudioRecordStartError(AudioRecordStartErrorCode errorCode, String errorMessage) {
    Logging.e(TAG, "Start recording error: " + errorCode + ". " + errorMessage);
    WebRtcAudioUtils.logAudioState(TAG);
    if (errorCallback != null)
      errorCallback.onWebRtcAudioRecordStartError(errorCode, errorMessage); 
  }
  
  private void reportWebRtcAudioRecordError(String errorMessage) {
    Logging.e(TAG, "Run-time recording error: " + errorMessage);
    WebRtcAudioUtils.logAudioState(TAG);
    if (errorCallback != null)
      errorCallback.onWebRtcAudioRecordError(errorMessage); 
  }
  
  public static interface WebRtcAudioRecordErrorCallback {
    void onWebRtcAudioRecordInitError(String param1String);
    
    void onWebRtcAudioRecordStartError(WebRtcAudioRecord.AudioRecordStartErrorCode param1AudioRecordStartErrorCode, String param1String);
    
    void onWebRtcAudioRecordError(String param1String);
  }
  
  public static interface WebRtcAudioRecordSamplesReadyCallback {
    void onWebRtcAudioRecordSamplesReady(WebRtcAudioRecord.AudioSamples param1AudioSamples);
  }
}

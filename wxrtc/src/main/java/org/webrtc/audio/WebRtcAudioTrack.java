package org.webrtc.audio;

import static android.media.AudioTrack.STATE_INITIALIZED;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Process;
import androidx.annotation.Nullable;
import java.nio.ByteBuffer;
import org.webrtc.CalledByNative;
import org.webrtc.Logging;
import org.webrtc.ThreadUtils;

class WebRtcAudioTrack {
  private static final String TAG = "WebRtcAudioTrackExternal";
  
  private static final int BITS_PER_SAMPLE = 16;
  
  private static final int CALLBACK_BUFFER_SIZE_MS = 10;
  
  private static final int BUFFERS_PER_SECOND = 1000 / CALLBACK_BUFFER_SIZE_MS;
  
  private static final long AUDIO_TRACK_THREAD_JOIN_TIMEOUT_MS = 2000L;
  
  private static final int DEFAULT_USAGE = AudioAttributes.USAGE_VOICE_COMMUNICATION;
  
  private static final int AUDIO_TRACK_START = 0;
  
  private static final int AUDIO_TRACK_STOP = 1;
  
  private long nativeAudioTrack;
  
  private final Context context;
  
  private final AudioManager audioManager;
  
  private final ThreadUtils.ThreadChecker threadChecker = new ThreadUtils.ThreadChecker();
  
  private ByteBuffer byteBuffer;
  
  @Nullable
  private final AudioAttributes audioAttributes;
  
  @Nullable
  private AudioTrack audioTrack;
  
  @Nullable
  private AudioTrackThread audioThread;
  
  private final VolumeLogger volumeLogger;
  
  private volatile boolean speakerMute;
  
  private byte[] emptyBytes;
  
  private boolean useLowLatency;
  
  private int initialBufferSizeInFrames;
  
  @Nullable
  private final JavaAudioDeviceModule.AudioTrackErrorCallback errorCallback;
  
  @Nullable
  private final JavaAudioDeviceModule.AudioTrackStateCallback stateCallback;
  
  private class AudioTrackThread extends Thread {
    private volatile boolean keepAlive = true;
    
    private LowLatencyAudioBufferManager bufferManager;
    
    public AudioTrackThread(String name) {
      super(name);
      this.bufferManager = new LowLatencyAudioBufferManager();
    }
    
    public void run() {
      Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
      Logging.d(TAG, "AudioTrackThread" + WebRtcAudioUtils.getThreadInfo());
      WebRtcAudioTrack.assertTrue((WebRtcAudioTrack.this.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING));
      WebRtcAudioTrack.this.doAudioTrackStateCallback(AUDIO_TRACK_START);
      int sizeInBytes = WebRtcAudioTrack.this.byteBuffer.capacity();
      while (this.keepAlive) {
        WebRtcAudioTrack.nativeGetPlayoutData(WebRtcAudioTrack.this.nativeAudioTrack, sizeInBytes);
        WebRtcAudioTrack.assertTrue((sizeInBytes <= WebRtcAudioTrack.this.byteBuffer.remaining()));
        if (WebRtcAudioTrack.this.speakerMute) {
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
        if (WebRtcAudioTrack.this.useLowLatency)
          this.bufferManager.maybeAdjustBufferSize(WebRtcAudioTrack.this.audioTrack); 
        WebRtcAudioTrack.this.byteBuffer.rewind();
      } 
    }
    
    public void stopThread() {
      Logging.d(TAG, "stopThread");
      this.keepAlive = false;
    }
  }
  
  @CalledByNative
  WebRtcAudioTrack(Context context, AudioManager audioManager) {
    this(context, audioManager, null, null, null, false, true);
  }
  
  WebRtcAudioTrack(Context context, AudioManager audioManager, @Nullable AudioAttributes audioAttributes, @Nullable JavaAudioDeviceModule.AudioTrackErrorCallback errorCallback, @Nullable JavaAudioDeviceModule.AudioTrackStateCallback stateCallback, boolean useLowLatency, boolean enableVolumeLogger) {
    this.threadChecker.detachThread();
    this.context = context;
    this.audioManager = audioManager;
    this.audioAttributes = audioAttributes;
    this.errorCallback = errorCallback;
    this.stateCallback = stateCallback;
    this.volumeLogger = enableVolumeLogger ? new VolumeLogger(audioManager) : null;
    this.useLowLatency = useLowLatency;
    Logging.d(TAG, "ctor" + WebRtcAudioUtils.getThreadInfo());
  }
  
  @CalledByNative
  public void setNativeAudioTrack(long nativeAudioTrack) {
    this.nativeAudioTrack = nativeAudioTrack;
  }
  
  @CalledByNative
  private int initPlayout(int sampleRate, int channels, double bufferSizeFactor) {
    this.threadChecker.checkIsOnValidThread();
    Logging.d(TAG, "initPlayout(sampleRate=" + sampleRate + ", channels=" + channels + ", bufferSizeFactor=" + bufferSizeFactor + ")");
    int bytesPerFrame = channels * (BITS_PER_SAMPLE / 8);
    this.byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * sampleRate / BUFFERS_PER_SECOND);
    Logging.d(TAG, "byteBuffer.capacity: " + this.byteBuffer.capacity());
    this.emptyBytes = new byte[this.byteBuffer.capacity()];
    nativeCacheDirectBufferAddress(this.nativeAudioTrack, this.byteBuffer);
    int channelConfig = channelCountToConfiguration(channels);
    int minBufferSizeInBytes = (int)(AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT) * bufferSizeFactor);
    Logging.d(TAG, "minBufferSizeInBytes: " + minBufferSizeInBytes);
    if (minBufferSizeInBytes < this.byteBuffer.capacity()) {
      reportWebRtcAudioTrackInitError("AudioTrack.getMinBufferSize returns an invalid value.");
      return -1;
    } 
    if (bufferSizeFactor > 1.0D)
      this.useLowLatency = false; 
    if (this.audioTrack != null) {
      reportWebRtcAudioTrackInitError("Conflict with existing AudioTrack.");
      return -1;
    } 
    try {
      if (this.useLowLatency && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        this.audioTrack = createAudioTrackOnOreoOrHigher(sampleRate, channelConfig, minBufferSizeInBytes, this.audioAttributes);
      } else {
        this.audioTrack = createAudioTrackBeforeOreo(sampleRate, channelConfig, minBufferSizeInBytes, this.audioAttributes);
      } 
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      this.initialBufferSizeInFrames = this.audioTrack.getBufferSizeInFrames();
    } else {
      this.initialBufferSizeInFrames = -1;
    } 
    logMainParameters();
    logMainParametersExtended();
    return minBufferSizeInBytes;
  }
  
  @CalledByNative
  private boolean startPlayout() {
    this.threadChecker.checkIsOnValidThread();
    if (this.volumeLogger != null)
      this.volumeLogger.start(); 
    Logging.d(TAG, "startPlayout");
    assertTrue((this.audioTrack != null));
    assertTrue((this.audioThread == null));
    try {
      this.audioTrack.play();
    } catch (IllegalStateException e) {
      reportWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode.AUDIO_TRACK_START_EXCEPTION, "AudioTrack.play failed: " + e
          .getMessage());
      releaseAudioResources();
      return false;
    } 
    if (this.audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
      reportWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode.AUDIO_TRACK_START_STATE_MISMATCH, "AudioTrack.play failed - incorrect state :" + this.audioTrack
          .getPlayState());
      releaseAudioResources();
      return false;
    } 
    this.audioThread = new AudioTrackThread("AudioTrackJavaThread");
    this.audioThread.start();
    return true;
  }
  
  @CalledByNative
  private boolean stopPlayout() {
    this.threadChecker.checkIsOnValidThread();
    if (this.volumeLogger != null)
      this.volumeLogger.stop(); 
    Logging.d(TAG, "stopPlayout");
    assertTrue((this.audioThread != null));
    logUnderrunCount();
    this.audioThread.stopThread();
    Logging.d(TAG, "Stopping the AudioTrackThread...");
    this.audioThread.interrupt();
    if (!ThreadUtils.joinUninterruptibly(this.audioThread, AUDIO_TRACK_THREAD_JOIN_TIMEOUT_MS)) {
      Logging.e(TAG, "Join of AudioTrackThread timed out.");
      WebRtcAudioUtils.logAudioState(TAG, this.context, this.audioManager);
    } 
    Logging.d(TAG, "AudioTrackThread has now been stopped.");
    this.audioThread = null;
    if (this.audioTrack != null) {
      Logging.d(TAG, "Calling AudioTrack.stop...");
      try {
        this.audioTrack.stop();
        Logging.d(TAG, "AudioTrack.stop is done.");
        doAudioTrackStateCallback(AUDIO_TRACK_STOP);
      } catch (IllegalStateException e) {
        Logging.e(TAG, "AudioTrack.stop failed: " + e.getMessage());
      } 
    } 
    releaseAudioResources();
    return true;
  }
  
  @CalledByNative
  private int getStreamMaxVolume() {
    this.threadChecker.checkIsOnValidThread();
    Logging.d(TAG, "getStreamMaxVolume");
    return this.audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
  }
  
  @CalledByNative
  private boolean setStreamVolume(int volume) {
    this.threadChecker.checkIsOnValidThread();
    Logging.d(TAG, "setStreamVolume(" + volume + ")");
    if (this.audioManager.isVolumeFixed()) {
      Logging.e(TAG, "The device implements a fixed volume policy.");
      return false;
    } 
    this.audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, volume, 0);
    return true;
  }
  
  @CalledByNative
  private int getStreamVolume() {
    this.threadChecker.checkIsOnValidThread();
    Logging.d(TAG, "getStreamVolume");
    return this.audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
  }
  
  @CalledByNative
  private int GetPlayoutUnderrunCount() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      if (this.audioTrack != null)
        return this.audioTrack.getUnderrunCount(); 
      return -1;
    } 
    return -2;
  }
  
  private void logMainParameters() {
    Logging.d(TAG, "AudioTrack: session ID: " + this.audioTrack
        
        .getAudioSessionId() + ", channels: " + this.audioTrack
        .getChannelCount() + ", sample rate: " + this.audioTrack
        .getSampleRate() + ", max gain: " + 
        
        AudioTrack.getMaxVolume());
  }
  
  private static void logNativeOutputSampleRate(int requestedSampleRateInHz) {
    int nativeOutputSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_VOICE_CALL);
    Logging.d(TAG, "nativeOutputSampleRate: " + nativeOutputSampleRate);
    if (requestedSampleRateInHz != nativeOutputSampleRate)
      Logging.w(TAG, "Unable to use fast mode since requested sample rate is not native"); 
  }
  
  private static AudioAttributes getAudioAttributes(@Nullable AudioAttributes overrideAttributes) {
    AudioAttributes.Builder attributesBuilder = (new AudioAttributes.Builder()).setUsage(DEFAULT_USAGE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
    if (overrideAttributes != null) {
      if (overrideAttributes.getUsage() != AudioAttributes.USAGE_UNKNOWN)
        attributesBuilder.setUsage(overrideAttributes.getUsage()); 
      if (overrideAttributes.getContentType() != AudioAttributes.CONTENT_TYPE_UNKNOWN)
        attributesBuilder.setContentType(overrideAttributes.getContentType()); 
      attributesBuilder.setFlags(overrideAttributes.getFlags());
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        attributesBuilder = applyAttributesOnQOrHigher(attributesBuilder, overrideAttributes); 
    } 
    return attributesBuilder.build();
  }
  
  private static AudioTrack createAudioTrackBeforeOreo(int sampleRateInHz, int channelConfig, int bufferSizeInBytes, @Nullable AudioAttributes overrideAttributes) {
    Logging.d(TAG, "createAudioTrackBeforeOreo");
    logNativeOutputSampleRate(sampleRateInHz);
    return new AudioTrack(getAudioAttributes(overrideAttributes), (new AudioFormat.Builder())
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(sampleRateInHz)
        .setChannelMask(channelConfig)
        .build(), bufferSizeInBytes, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
  }
  
  @TargetApi(Build.VERSION_CODES.O)
  private static AudioTrack createAudioTrackOnOreoOrHigher(int sampleRateInHz, int channelConfig, int bufferSizeInBytes, @Nullable AudioAttributes overrideAttributes) {
    Logging.d(TAG, "createAudioTrackOnOreoOrHigher");
    logNativeOutputSampleRate(sampleRateInHz);
    return (new AudioTrack.Builder())
      .setAudioAttributes(getAudioAttributes(overrideAttributes))
      .setAudioFormat((new AudioFormat.Builder())
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(sampleRateInHz)
        .setChannelMask(channelConfig)
        .build())
      .setBufferSizeInBytes(bufferSizeInBytes)
      .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
      .setTransferMode(AudioTrack.MODE_STREAM)
      .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
      .build();
  }
  
  @TargetApi(Build.VERSION_CODES.Q)
  private static AudioAttributes.Builder applyAttributesOnQOrHigher(AudioAttributes.Builder builder, AudioAttributes overrideAttributes) {
    return builder.setAllowedCapturePolicy(overrideAttributes.getAllowedCapturePolicy());
  }
  
  private void logBufferSizeInFrames() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
      Logging.d(TAG, "AudioTrack: buffer size in frames: " + this.audioTrack.getBufferSizeInFrames());
  }
  
  @CalledByNative
  private int getBufferSizeInFrames() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
      return this.audioTrack.getBufferSizeInFrames(); 
    return -1;
  }
  
  @CalledByNative
  private int getInitialBufferSizeInFrames() {
    return this.initialBufferSizeInFrames;
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
  
  private static native void nativeCacheDirectBufferAddress(long paramLong, ByteBuffer paramByteBuffer);
  
  private static native void nativeGetPlayoutData(long paramLong, int paramInt);
  
  public void setSpeakerMute(boolean mute) {
    Logging.w(TAG, "setSpeakerMute(" + mute + ")");
    this.speakerMute = mute;
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
    WebRtcAudioUtils.logAudioState(TAG, this.context, this.audioManager);
    if (this.errorCallback != null)
      this.errorCallback.onWebRtcAudioTrackInitError(errorMessage); 
  }
  
  private void reportWebRtcAudioTrackStartError(JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
    Logging.e(TAG, "Start playout error: " + errorCode + ". " + errorMessage);
    WebRtcAudioUtils.logAudioState(TAG, this.context, this.audioManager);
    if (this.errorCallback != null)
      this.errorCallback.onWebRtcAudioTrackStartError(errorCode, errorMessage); 
  }
  
  private void reportWebRtcAudioTrackError(String errorMessage) {
    Logging.e(TAG, "Run-time playback error: " + errorMessage);
    WebRtcAudioUtils.logAudioState(TAG, this.context, this.audioManager);
    if (this.errorCallback != null)
      this.errorCallback.onWebRtcAudioTrackError(errorMessage); 
  }
  
  private void doAudioTrackStateCallback(int audioState) {
    Logging.d(TAG, "doAudioTrackStateCallback: " + audioState);
    if (this.stateCallback != null)
      if (audioState == AUDIO_TRACK_START) {
        this.stateCallback.onWebRtcAudioTrackStart();
      } else if (audioState == AUDIO_TRACK_STOP) {
        this.stateCallback.onWebRtcAudioTrackStop();
      } else {
        Logging.e(TAG, "Invalid audio state");
      }  
  }
}

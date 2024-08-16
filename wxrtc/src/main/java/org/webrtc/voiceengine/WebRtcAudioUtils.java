package org.webrtc.voiceengine;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import java.util.Arrays;
import java.util.List;
import org.webrtc.ContextUtils;
import org.webrtc.Logging;

public final class WebRtcAudioUtils {
  private static final String TAG = "WebRtcAudioUtils";
  
  private static final String[] BLACKLISTED_OPEN_SL_ES_MODELS = new String[0];
  
  private static final String[] BLACKLISTED_AEC_MODELS = new String[0];
  
  private static final String[] BLACKLISTED_NS_MODELS = new String[0];
  
  private static final int DEFAULT_SAMPLE_RATE_HZ = 16000;
  
  private static int defaultSampleRateHz = DEFAULT_SAMPLE_RATE_HZ;
  
  private static boolean isDefaultSampleRateOverridden;
  
  private static boolean useWebRtcBasedAcousticEchoCanceler;
  
  private static boolean useWebRtcBasedNoiseSuppressor;
  
  public static synchronized void setWebRtcBasedAcousticEchoCanceler(boolean enable) {
    useWebRtcBasedAcousticEchoCanceler = enable;
  }
  
  public static synchronized void setWebRtcBasedNoiseSuppressor(boolean enable) {
    useWebRtcBasedNoiseSuppressor = enable;
  }
  
  public static synchronized void setWebRtcBasedAutomaticGainControl(boolean enable) {
    Logging.w(TAG, "setWebRtcBasedAutomaticGainControl() is deprecated");
  }
  
  public static synchronized boolean useWebRtcBasedAcousticEchoCanceler() {
    if (useWebRtcBasedAcousticEchoCanceler)
      Logging.w(TAG, "Overriding default behavior; now using WebRTC AEC!"); 
    return useWebRtcBasedAcousticEchoCanceler;
  }
  
  public static synchronized boolean useWebRtcBasedNoiseSuppressor() {
    if (useWebRtcBasedNoiseSuppressor)
      Logging.w(TAG, "Overriding default behavior; now using WebRTC NS!"); 
    return useWebRtcBasedNoiseSuppressor;
  }
  
  public static synchronized boolean useWebRtcBasedAutomaticGainControl() {
    return true;
  }
  
  public static boolean isAcousticEchoCancelerSupported() {
    return WebRtcAudioEffects.canUseAcousticEchoCanceler();
  }
  
  public static boolean isNoiseSuppressorSupported() {
    return WebRtcAudioEffects.canUseNoiseSuppressor();
  }
  
  public static boolean isAutomaticGainControlSupported() {
    return false;
  }
  
  public static synchronized void setDefaultSampleRateHz(int sampleRateHz) {
    isDefaultSampleRateOverridden = true;
    defaultSampleRateHz = sampleRateHz;
  }
  
  public static synchronized boolean isDefaultSampleRateOverridden() {
    return isDefaultSampleRateOverridden;
  }
  
  public static synchronized int getDefaultSampleRateHz() {
    return defaultSampleRateHz;
  }
  
  public static List<String> getBlackListedModelsForAecUsage() {
    return Arrays.asList(BLACKLISTED_AEC_MODELS);
  }
  
  public static List<String> getBlackListedModelsForNsUsage() {
    return Arrays.asList(BLACKLISTED_NS_MODELS);
  }
  
  public static String getThreadInfo() {
    return "@[name=" + Thread.currentThread().getName() + ", id=" + Thread.currentThread().getId() + "]";
  }
  
  public static boolean runningOnEmulator() {
    return (Build.HARDWARE.equals("goldfish") && Build.BRAND.startsWith("generic_"));
  }
  
  public static boolean deviceIsBlacklistedForOpenSLESUsage() {
    List<String> blackListedModels = Arrays.asList(BLACKLISTED_OPEN_SL_ES_MODELS);
    return blackListedModels.contains(Build.MODEL);
  }
  
  static void logDeviceInfo(String tag) {
    Logging.d(tag, "Android SDK: " + Build.VERSION.SDK_INT + ", Release: " + Build.VERSION.RELEASE + ", Brand: " + Build.BRAND + ", Device: " + Build.DEVICE + ", Id: " + Build.ID + ", Hardware: " + Build.HARDWARE + ", Manufacturer: " + Build.MANUFACTURER + ", Model: " + Build.MODEL + ", Product: " + Build.PRODUCT);
  }
  
  static void logAudioState(String tag) {
    logDeviceInfo(tag);
    Context context = ContextUtils.getApplicationContext();
    AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
    logAudioStateBasic(tag, audioManager);
    logAudioStateVolume(tag, audioManager);
    logAudioDeviceInfo(tag, audioManager);
  }
  
  private static void logAudioStateBasic(String tag, AudioManager audioManager) {
    Logging.d(tag,
            "Audio State: "
                    + ("audio mode: " + modeToString(audioManager.getMode()))
                    + (", has mic: " + hasMicrophone())
                    + (", mic muted: " + audioManager.isMicrophoneMute())
                    + (", music active: " + audioManager.isMusicActive())
                    + (", speakerphone: " + audioManager.isSpeakerphoneOn())
                    + (", BT SCO: " + audioManager.isBluetoothScoOn()));
  }
  
  private static void logAudioStateVolume(String tag, AudioManager audioManager) {
    int[] streams = {
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM
    };
    Logging.d(tag, "Audio State: ");
    boolean fixedVolume = audioManager.isVolumeFixed();
    Logging.d(tag, "  fixed volume=" + fixedVolume);
    if (!fixedVolume)
      for (int stream : streams) {
        StringBuilder info = new StringBuilder();
        info.append("  " + streamTypeToString(stream) + ": ");
        info.append("volume=").append(audioManager.getStreamVolume(stream));
        info.append(", max=").append(audioManager.getStreamMaxVolume(stream));
        logIsStreamMute(tag, audioManager, stream, info);
        Logging.d(tag, info.toString());
      }  
  }
  
  private static void logIsStreamMute(String tag, AudioManager audioManager, int stream, StringBuilder info) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
      info.append(", muted=").append(audioManager.isStreamMute(stream)); 
  }
  
  private static void logAudioDeviceInfo(String tag, AudioManager audioManager) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
      return; 
    AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
    if (devices.length == 0)
      return; 
    Logging.d(tag, "Audio Devices: ");
    for (AudioDeviceInfo device : devices) {
      StringBuilder info = new StringBuilder();
      info.append("  ").append(deviceTypeToString(device.getType()));
      info.append(device.isSource() ? "(in): " : "(out): ");
      if ((device.getChannelCounts()).length > 0) {
        info.append("channels=").append(Arrays.toString(device.getChannelCounts()));
        info.append(", ");
      } 
      if ((device.getEncodings()).length > 0) {
        info.append("encodings=").append(Arrays.toString(device.getEncodings()));
        info.append(", ");
      } 
      if ((device.getSampleRates()).length > 0) {
        info.append("sample rates=").append(Arrays.toString(device.getSampleRates()));
        info.append(", ");
      } 
      info.append("id=").append(device.getId());
      Logging.d(tag, info.toString());
    } 
  }

  static String modeToString(int mode) {
    switch (mode) {
      case AudioManager.MODE_IN_CALL:
        return "MODE_IN_CALL";
      case AudioManager.MODE_IN_COMMUNICATION:
        return "MODE_IN_COMMUNICATION";
      case AudioManager.MODE_NORMAL:
        return "MODE_NORMAL";
      case AudioManager.MODE_RINGTONE:
        return "MODE_RINGTONE";
      default:
        return "MODE_INVALID";
    }
  }

  private static String streamTypeToString(int stream) {
    switch (stream) {
      case AudioManager.STREAM_VOICE_CALL:
        return "STREAM_VOICE_CALL";
      case AudioManager.STREAM_MUSIC:
        return "STREAM_MUSIC";
      case AudioManager.STREAM_RING:
        return "STREAM_RING";
      case AudioManager.STREAM_ALARM:
        return "STREAM_ALARM";
      case AudioManager.STREAM_NOTIFICATION:
        return "STREAM_NOTIFICATION";
      case AudioManager.STREAM_SYSTEM:
        return "STREAM_SYSTEM";
      default:
        return "STREAM_INVALID";
    }
  }

  private static String deviceTypeToString(int type) {
    switch (type) {
      case AudioDeviceInfo.TYPE_UNKNOWN:
        return "TYPE_UNKNOWN";
      case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
        return "TYPE_BUILTIN_EARPIECE";
      case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
        return "TYPE_BUILTIN_SPEAKER";
      case AudioDeviceInfo.TYPE_WIRED_HEADSET:
        return "TYPE_WIRED_HEADSET";
      case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
        return "TYPE_WIRED_HEADPHONES";
      case AudioDeviceInfo.TYPE_LINE_ANALOG:
        return "TYPE_LINE_ANALOG";
      case AudioDeviceInfo.TYPE_LINE_DIGITAL:
        return "TYPE_LINE_DIGITAL";
      case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
        return "TYPE_BLUETOOTH_SCO";
      case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
        return "TYPE_BLUETOOTH_A2DP";
      case AudioDeviceInfo.TYPE_HDMI:
        return "TYPE_HDMI";
      case AudioDeviceInfo.TYPE_HDMI_ARC:
        return "TYPE_HDMI_ARC";
      case AudioDeviceInfo.TYPE_USB_DEVICE:
        return "TYPE_USB_DEVICE";
      case AudioDeviceInfo.TYPE_USB_ACCESSORY:
        return "TYPE_USB_ACCESSORY";
      case AudioDeviceInfo.TYPE_DOCK:
        return "TYPE_DOCK";
      case AudioDeviceInfo.TYPE_FM:
        return "TYPE_FM";
      case AudioDeviceInfo.TYPE_BUILTIN_MIC:
        return "TYPE_BUILTIN_MIC";
      case AudioDeviceInfo.TYPE_FM_TUNER:
        return "TYPE_FM_TUNER";
      case AudioDeviceInfo.TYPE_TV_TUNER:
        return "TYPE_TV_TUNER";
      case AudioDeviceInfo.TYPE_TELEPHONY:
        return "TYPE_TELEPHONY";
      case AudioDeviceInfo.TYPE_AUX_LINE:
        return "TYPE_AUX_LINE";
      case AudioDeviceInfo.TYPE_IP:
        return "TYPE_IP";
      case AudioDeviceInfo.TYPE_BUS:
        return "TYPE_BUS";
      case AudioDeviceInfo.TYPE_USB_HEADSET:
        return "TYPE_USB_HEADSET";
      default:
        return "TYPE_UNKNOWN(" + type + ")";
    }
  }
  
  private static boolean hasMicrophone() {
    return ContextUtils.getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
  }
}

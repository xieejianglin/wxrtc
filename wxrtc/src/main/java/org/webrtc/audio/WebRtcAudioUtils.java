package org.webrtc.audio;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaRecorder.AudioSource;
import android.os.Build;
import java.util.Arrays;
import org.webrtc.Logging;

final class WebRtcAudioUtils {
  private static final String TAG = "WebRtcAudioUtilsExternal";
  
  public static String getThreadInfo() {
    return "@[name=" + Thread.currentThread().getName() + ", id=" + Thread.currentThread().getId() + "]";
  }
  
  public static boolean runningOnEmulator() {
    return (Build.HARDWARE.equals("goldfish") && Build.BRAND.startsWith("generic_"));
  }
  
  static void logDeviceInfo(String tag) {
    Logging.d(tag, "Android SDK: " + Build.VERSION.SDK_INT + ", Release: " + Build.VERSION.RELEASE + ", Brand: " + Build.BRAND + ", Device: " + Build.DEVICE + ", Id: " + Build.ID + ", Hardware: " + Build.HARDWARE + ", Manufacturer: " + Build.MANUFACTURER + ", Model: " + Build.MODEL + ", Product: " + Build.PRODUCT);
  }
  
  static void logAudioState(String tag, Context context, AudioManager audioManager) {
    logDeviceInfo(tag);
    logAudioStateBasic(tag, context, audioManager);
    logAudioStateVolume(tag, audioManager);
    logAudioDeviceInfo(tag, audioManager);
  }
  
  static String deviceTypeToString(int type) {
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
  
  @TargetApi(Build.VERSION_CODES.N)
  public static String audioSourceToString(int source) {
    switch (source) {
      case AudioSource.DEFAULT:
        return "DEFAULT";
      case AudioSource.MIC:
        return "MIC";
      case AudioSource.VOICE_UPLINK:
        return "VOICE_UPLINK";
      case AudioSource.VOICE_DOWNLINK:
        return "VOICE_DOWNLINK";
      case AudioSource.VOICE_CALL:
        return "VOICE_CALL";
      case AudioSource.CAMCORDER:
        return "CAMCORDER";
      case AudioSource.VOICE_RECOGNITION:
        return "VOICE_RECOGNITION";
      case AudioSource.VOICE_COMMUNICATION:
        return "VOICE_COMMUNICATION";
      case AudioSource.UNPROCESSED:
        return "UNPROCESSED";
      case AudioSource.VOICE_PERFORMANCE:
        return "VOICE_PERFORMANCE";
      default:
        return "INVALID";
    }
  }
  
  public static String channelMaskToString(int mask) {
    switch (mask) {
      case AudioFormat.CHANNEL_IN_STEREO:
        return "IN_STEREO";
      case AudioFormat.CHANNEL_IN_MONO:
        return "IN_MONO";
      default:
        return "INVALID";
    }
  }
  
  @TargetApi(Build.VERSION_CODES.N)
  public static String audioEncodingToString(int enc) {
    switch (enc) {
      case AudioFormat.ENCODING_INVALID:
        return "INVALID";
      case AudioFormat.ENCODING_PCM_16BIT:
        return "PCM_16BIT";
      case AudioFormat.ENCODING_PCM_8BIT:
        return "PCM_8BIT";
      case AudioFormat.ENCODING_PCM_FLOAT:
        return "PCM_FLOAT";
      case AudioFormat.ENCODING_AC3:
      case AudioFormat.ENCODING_E_AC3:
        return "AC3";
      case AudioFormat.ENCODING_DTS:
        return "DTS";
      case AudioFormat.ENCODING_DTS_HD:
        return "DTS_HD";
      case AudioFormat.ENCODING_MP3:
        return "MP3";
      default:
        return "Invalid encoding: " + enc;
    }
  }
  
  private static void logAudioStateBasic(String tag, Context context, AudioManager audioManager) {
    Logging.d(tag,
            "Audio State: "
                    + ("audio mode: " + modeToString(audioManager.getMode()))
                    + (", has mic: " + hasMicrophone(context))
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
  
  private static boolean hasMicrophone(Context context) {
    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
  }
}

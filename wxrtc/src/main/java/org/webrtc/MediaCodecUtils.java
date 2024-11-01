package org.webrtc;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.os.Build;
import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

class MediaCodecUtils {
  private static final String TAG = "MediaCodecUtils";
  
  static final String EXYNOS_PREFIX = "OMX.Exynos.";
  
  static final String INTEL_PREFIX = "OMX.Intel.";
  
  static final String NVIDIA_PREFIX = "OMX.Nvidia.";
  
  static final String QCOM_PREFIX = "OMX.qcom.";
  
  static final String[] SOFTWARE_IMPLEMENTATION_PREFIXES = new String[] { "OMX.google.", "OMX.SEC.", "c2.android", "OMX.ffmpeg." };
  
  static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar32m4ka = 2141391873;
  
  static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar16m4ka = 2141391874;
  
  static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar64x32Tile2m8ka = 2141391875;
  
  static final int COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m = 2141391876;
  
  static final int[] DECODER_COLOR_FORMATS = new int[] { CodecCapabilities.COLOR_FormatYUV420Planar, CodecCapabilities.COLOR_FormatYUV420SemiPlanar, CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar, COLOR_QCOM_FORMATYVU420PackedSemiPlanar32m4ka, COLOR_QCOM_FORMATYVU420PackedSemiPlanar16m4ka, COLOR_QCOM_FORMATYVU420PackedSemiPlanar64x32Tile2m8ka, COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m };
  
  static final int[] ENCODER_COLOR_FORMATS = new int[] { CodecCapabilities.COLOR_FormatYUV420Planar, CodecCapabilities.COLOR_FormatYUV420SemiPlanar, CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar, COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m };
  
  static final int[] TEXTURE_COLOR_FORMATS = new int[] { CodecCapabilities.COLOR_FormatSurface };
  
  @Nullable
  static Integer selectColorFormat(int[] supportedColorFormats, MediaCodecInfo.CodecCapabilities capabilities) {
    for (int supportedColorFormat : supportedColorFormats) {
      for (int codecColorFormat : capabilities.colorFormats) {
        if (codecColorFormat == supportedColorFormat)
          return Integer.valueOf(codecColorFormat); 
      } 
    } 
    return null;
  }
  
  static boolean codecSupportsType(MediaCodecInfo info, VideoCodecMimeType type) {
    for (String mimeType : info.getSupportedTypes()) {
      if (type.mimeType().equals(mimeType))
        return true; 
    } 
    return false;
  }
  
  static Map<String, String> getCodecProperties(VideoCodecMimeType type, boolean highProfile) {
    switch (type) {
      case VP8:
      case VP9:
      case AV1:
      case H265:
        return new HashMap<>();
      case H264:
        return H264Utils.getDefaultH264Params(highProfile);
    } 
    throw new IllegalArgumentException("Unsupported codec: " + type);
  }
  
  static boolean isHardwareAccelerated(MediaCodecInfo info) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
      return isHardwareAcceleratedQOrHigher(info); 
    return !isSoftwareOnly(info);
  }
  
  @TargetApi(Build.VERSION_CODES.Q)
  private static boolean isHardwareAcceleratedQOrHigher(MediaCodecInfo codecInfo) {
    return codecInfo.isHardwareAccelerated();
  }
  
  static boolean isSoftwareOnly(MediaCodecInfo codecInfo) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
      return isSoftwareOnlyQOrHigher(codecInfo); 
    String name = codecInfo.getName();
    for (String prefix : SOFTWARE_IMPLEMENTATION_PREFIXES) {
      if (name.startsWith(prefix))
        return true; 
    } 
    return false;
  }
  
  @TargetApi(Build.VERSION_CODES.Q)
  private static boolean isSoftwareOnlyQOrHigher(MediaCodecInfo codecInfo) {
    return codecInfo.isSoftwareOnly();
  }
}

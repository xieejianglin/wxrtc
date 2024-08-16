package org.webrtc;

import java.util.HashMap;
import java.util.Map;

class H264Utils {
  public static final String H264_FMTP_PROFILE_LEVEL_ID = "profile-level-id";
  
  public static final String H264_FMTP_LEVEL_ASYMMETRY_ALLOWED = "level-asymmetry-allowed";
  
  public static final String H264_FMTP_PACKETIZATION_MODE = "packetization-mode";
  
  public static final String H264_PROFILE_CONSTRAINED_BASELINE = "42e0";
  
  public static final String H264_PROFILE_CONSTRAINED_HIGH = "640c";
  
  public static final String H264_LEVEL_3_1 = "1f";
  
  public static final String H264_CONSTRAINED_HIGH_3_1 = H264_PROFILE_CONSTRAINED_HIGH + H264_LEVEL_3_1;
  
  public static final String H264_CONSTRAINED_BASELINE_3_1 = H264_PROFILE_CONSTRAINED_BASELINE + H264_LEVEL_3_1;
  
  public static Map<String, String> getDefaultH264Params(boolean isHighProfile) {
    Map<String, String> params = new HashMap<>();
    params.put(H264_FMTP_LEVEL_ASYMMETRY_ALLOWED, "1");
    params.put(H264_FMTP_PACKETIZATION_MODE, "1");
    params.put(H264_FMTP_PROFILE_LEVEL_ID,
        isHighProfile ? H264_CONSTRAINED_HIGH_3_1 :
                H264_CONSTRAINED_BASELINE_3_1);
    return params;
  }
  
  public static VideoCodecInfo DEFAULT_H264_BASELINE_PROFILE_CODEC = new VideoCodecInfo("H264", 
      getDefaultH264Params(false));
  
  public static VideoCodecInfo DEFAULT_H264_HIGH_PROFILE_CODEC = new VideoCodecInfo("H264", 
      getDefaultH264Params(true));
  
  public static boolean isSameH264Profile(Map<String, String> params1, Map<String, String> params2) {
    return nativeIsSameH264Profile(params1, params2);
  }
  
  private static native boolean nativeIsSameH264Profile(Map<String, String> paramMap1, Map<String, String> paramMap2);
}

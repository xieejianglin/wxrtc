package org.webrtc;

import androidx.annotation.Nullable;

public interface VideoEncoderFactory {
  @Nullable
  @CalledByNative
  VideoEncoder createEncoder(VideoCodecInfo paramVideoCodecInfo);
  
  @CalledByNative
  VideoCodecInfo[] getSupportedCodecs();
  
  public static interface VideoEncoderSelector {
    @CalledByNative("VideoEncoderSelector")
    void onCurrentEncoder(VideoCodecInfo param1VideoCodecInfo);
    
    @Nullable
    @CalledByNative("VideoEncoderSelector")
    VideoCodecInfo onAvailableBitrate(int param1Int);
    
    @Nullable
    @CalledByNative("VideoEncoderSelector")
    default VideoCodecInfo onResolutionChange(int widht, int height) {
      return null;
    }
    
    @Nullable
    @CalledByNative("VideoEncoderSelector")
    VideoCodecInfo onEncoderBroken();
  }
  
  @CalledByNative
  default VideoCodecInfo[] getImplementations() {
    return getSupportedCodecs();
  }
  
  @CalledByNative
  default VideoEncoderSelector getEncoderSelector() {
    return null;
  }
}

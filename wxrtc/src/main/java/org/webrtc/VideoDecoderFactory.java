package org.webrtc;

import androidx.annotation.Nullable;

public interface VideoDecoderFactory {
  @Nullable
  @CalledByNative
  VideoDecoder createDecoder(VideoCodecInfo paramVideoCodecInfo);
  
  @CalledByNative
  default VideoCodecInfo[] getSupportedCodecs() {
    return new VideoCodecInfo[0];
  }
}

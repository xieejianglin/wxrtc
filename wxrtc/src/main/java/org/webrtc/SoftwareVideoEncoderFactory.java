package org.webrtc;

import androidx.annotation.Nullable;
import java.util.List;

public class SoftwareVideoEncoderFactory implements VideoEncoderFactory {
  private static final String TAG = "SoftwareVideoEncoderFactory";
  
  private final long nativeFactory = nativeCreateFactory();
  
  @Nullable
  public VideoEncoder createEncoder(VideoCodecInfo info) {
    final long nativeEncoder = nativeCreateEncoder(this.nativeFactory, info);
    if (nativeEncoder == 0L) {
      Logging.w(TAG, "Trying to create encoder for unsupported format. " + info);
      return null;
    } 
    return new WrappedNativeVideoEncoder() {
        public long createNativeVideoEncoder() {
          return nativeEncoder;
        }
        
        public boolean isHardwareEncoder() {
          return false;
        }
      };
  }
  
  public VideoCodecInfo[] getSupportedCodecs() {
    return nativeGetSupportedCodecs(this.nativeFactory).<VideoCodecInfo>toArray(new VideoCodecInfo[0]);
  }
  
  private static native long nativeCreateFactory();
  
  private static native long nativeCreateEncoder(long paramLong, VideoCodecInfo paramVideoCodecInfo);
  
  private static native List<VideoCodecInfo> nativeGetSupportedCodecs(long paramLong);
}

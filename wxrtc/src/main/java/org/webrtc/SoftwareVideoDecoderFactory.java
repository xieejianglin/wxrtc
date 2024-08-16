package org.webrtc;

import androidx.annotation.Nullable;
import java.util.List;

public class SoftwareVideoDecoderFactory implements VideoDecoderFactory {
  private static final String TAG = "SoftwareVideoDecoderFactory";
  
  private final long nativeFactory = nativeCreateFactory();
  
  @Nullable
  public VideoDecoder createDecoder(VideoCodecInfo info) {
    final long nativeDecoder = nativeCreateDecoder(this.nativeFactory, info);
    if (nativeDecoder == 0L) {
      Logging.w(TAG, "Trying to create decoder for unsupported format. " + info);
      return null;
    } 
    return new WrappedNativeVideoDecoder() {
        public long createNativeVideoDecoder() {
          return nativeDecoder;
        }
      };
  }
  
  public VideoCodecInfo[] getSupportedCodecs() {
    return nativeGetSupportedCodecs(this.nativeFactory).<VideoCodecInfo>toArray(new VideoCodecInfo[0]);
  }
  
  private static native long nativeCreateFactory();
  
  private static native long nativeCreateDecoder(long paramLong, VideoCodecInfo paramVideoCodecInfo);
  
  private static native List<VideoCodecInfo> nativeGetSupportedCodecs(long paramLong);
}

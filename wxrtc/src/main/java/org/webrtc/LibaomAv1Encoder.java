package org.webrtc;

public class LibaomAv1Encoder extends WrappedNativeVideoEncoder {
  public long createNativeVideoEncoder() {
    return nativeCreateEncoder();
  }
  
  static native long nativeCreateEncoder();
  
  public boolean isHardwareEncoder() {
    return false;
  }
}

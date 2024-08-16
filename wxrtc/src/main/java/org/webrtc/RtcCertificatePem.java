package org.webrtc;

public class RtcCertificatePem {
  public final String privateKey;
  
  public final String certificate;
  
  private static final long DEFAULT_EXPIRY = 2592000L;
  
  @CalledByNative
  public RtcCertificatePem(String privateKey, String certificate) {
    this.privateKey = privateKey;
    this.certificate = certificate;
  }
  
  @CalledByNative
  String getPrivateKey() {
    return this.privateKey;
  }
  
  @CalledByNative
  String getCertificate() {
    return this.certificate;
  }
  
  public static RtcCertificatePem generateCertificate() {
    return nativeGenerateCertificate(PeerConnection.KeyType.ECDSA, DEFAULT_EXPIRY);
  }
  
  public static RtcCertificatePem generateCertificate(PeerConnection.KeyType keyType) {
    return nativeGenerateCertificate(keyType, DEFAULT_EXPIRY);
  }
  
  public static RtcCertificatePem generateCertificate(long expires) {
    return nativeGenerateCertificate(PeerConnection.KeyType.ECDSA, expires);
  }
  
  public static RtcCertificatePem generateCertificate(PeerConnection.KeyType keyType, long expires) {
    return nativeGenerateCertificate(keyType, expires);
  }
  
  private static native RtcCertificatePem nativeGenerateCertificate(PeerConnection.KeyType paramKeyType, long paramLong);
}

package org.webrtc;

import androidx.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Objects;

public class JavaI420Buffer implements VideoFrame.I420Buffer {
  private final int width;
  
  private final int height;
  
  private final ByteBuffer dataY;
  
  private final ByteBuffer dataU;
  
  private final ByteBuffer dataV;
  
  private final int strideY;
  
  private final int strideU;
  
  private final int strideV;
  
  private final RefCountDelegate refCountDelegate;
  
  private JavaI420Buffer(int width, int height, ByteBuffer dataY, int strideY, ByteBuffer dataU, int strideU, ByteBuffer dataV, int strideV, @Nullable Runnable releaseCallback) {
    this.width = width;
    this.height = height;
    this.dataY = dataY;
    this.dataU = dataU;
    this.dataV = dataV;
    this.strideY = strideY;
    this.strideU = strideU;
    this.strideV = strideV;
    this.refCountDelegate = new RefCountDelegate(releaseCallback);
  }
  
  private static void checkCapacity(ByteBuffer data, int width, int height, int stride) {
    int minCapacity = stride * (height - 1) + width;
    if (data.capacity() < minCapacity)
      throw new IllegalArgumentException("Buffer must be at least " + minCapacity + " bytes, but was " + data
          .capacity()); 
  }
  
  public static JavaI420Buffer wrap(int width, int height, ByteBuffer dataY, int strideY, ByteBuffer dataU, int strideU, ByteBuffer dataV, int strideV, @Nullable Runnable releaseCallback) {
    if (dataY == null || dataU == null || dataV == null)
      throw new IllegalArgumentException("Data buffers cannot be null."); 
    if (!dataY.isDirect() || !dataU.isDirect() || !dataV.isDirect())
      throw new IllegalArgumentException("Data buffers must be direct byte buffers."); 
    dataY = dataY.slice();
    dataU = dataU.slice();
    dataV = dataV.slice();
    int chromaWidth = (width + 1) / 2;
    int chromaHeight = (height + 1) / 2;
    checkCapacity(dataY, width, height, strideY);
    checkCapacity(dataU, chromaWidth, chromaHeight, strideU);
    checkCapacity(dataV, chromaWidth, chromaHeight, strideV);
    return new JavaI420Buffer(width, height, dataY, strideY, dataU, strideU, dataV, strideV, releaseCallback);
  }
  
  public static JavaI420Buffer allocate(int width, int height) {
    int chromaHeight = (height + 1) / 2;
    int strideUV = (width + 1) / 2;
    int yPos = 0;
    int uPos = yPos + width * height;
    int vPos = uPos + strideUV * chromaHeight;
    ByteBuffer buffer = JniCommon.nativeAllocateByteBuffer(width * height + 2 * strideUV * chromaHeight);
    buffer.position(yPos);
    buffer.limit(uPos);
    ByteBuffer dataY = buffer.slice();
    buffer.position(uPos);
    buffer.limit(vPos);
    ByteBuffer dataU = buffer.slice();
    buffer.position(vPos);
    buffer.limit(vPos + strideUV * chromaHeight);
    ByteBuffer dataV = buffer.slice();
    return new JavaI420Buffer(width, height, dataY, width, dataU, strideUV, dataV, strideUV, () -> JniCommon.nativeFreeByteBuffer(buffer));
  }
  
  public int getWidth() {
    return this.width;
  }
  
  public int getHeight() {
    return this.height;
  }
  
  public ByteBuffer getDataY() {
    return this.dataY.slice();
  }
  
  public ByteBuffer getDataU() {
    return this.dataU.slice();
  }
  
  public ByteBuffer getDataV() {
    return this.dataV.slice();
  }
  
  public int getStrideY() {
    return this.strideY;
  }
  
  public int getStrideU() {
    return this.strideU;
  }
  
  public int getStrideV() {
    return this.strideV;
  }
  
  public VideoFrame.I420Buffer toI420() {
    retain();
    return this;
  }
  
  public void retain() {
    this.refCountDelegate.retain();
  }
  
  public void release() {
    this.refCountDelegate.release();
  }
  
  public VideoFrame.Buffer cropAndScale(int cropX, int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight) {
    return cropAndScaleI420(this, cropX, cropY, cropWidth, cropHeight, scaleWidth, scaleHeight);
  }
  
  public static VideoFrame.Buffer cropAndScaleI420(VideoFrame.I420Buffer buffer, int cropX, int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight) {
    if (cropWidth == scaleWidth && cropHeight == scaleHeight) {
      ByteBuffer dataY = buffer.getDataY();
      ByteBuffer dataU = buffer.getDataU();
      ByteBuffer dataV = buffer.getDataV();
      dataY.position(cropX + cropY * buffer.getStrideY());
      dataU.position(cropX / 2 + cropY / 2 * buffer.getStrideU());
      dataV.position(cropX / 2 + cropY / 2 * buffer.getStrideV());
      buffer.retain();
      Objects.requireNonNull(buffer);
      return wrap(scaleWidth, scaleHeight, dataY.slice(), buffer.getStrideY(), dataU.slice(), buffer.getStrideU(), dataV.slice(), buffer.getStrideV(), buffer::release);
    } 
    JavaI420Buffer newBuffer = allocate(scaleWidth, scaleHeight);
    nativeCropAndScaleI420(buffer.getDataY(), buffer.getStrideY(), buffer.getDataU(), buffer
        .getStrideU(), buffer.getDataV(), buffer.getStrideV(), cropX, cropY, cropWidth, cropHeight, newBuffer
        .getDataY(), newBuffer.getStrideY(), newBuffer.getDataU(), newBuffer
        .getStrideU(), newBuffer.getDataV(), newBuffer.getStrideV(), scaleWidth, scaleHeight);
    return newBuffer;
  }
  
  private static native void nativeCropAndScaleI420(ByteBuffer paramByteBuffer1, int paramInt1, ByteBuffer paramByteBuffer2, int paramInt2, ByteBuffer paramByteBuffer3, int paramInt3, int paramInt4, int paramInt5, int paramInt6, int paramInt7, ByteBuffer paramByteBuffer4, int paramInt8, ByteBuffer paramByteBuffer5, int paramInt9, ByteBuffer paramByteBuffer6, int paramInt10, int paramInt11, int paramInt12);
}

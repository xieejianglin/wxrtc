package org.webrtc;

import android.graphics.Matrix;
import android.opengl.GLES20;
import android.opengl.GLException;
import androidx.annotation.Nullable;
import java.nio.ByteBuffer;

public final class YuvConverter {
  private static final String TAG = "YuvConverter";
  
  private static final String FRAGMENT_SHADER = "uniform vec2 xUnit;\nuniform vec4 coeffs;\n\nvoid main() {\n  gl_FragColor.r = coeffs.a + dot(coeffs.rgb,\n      sample(tc - 1.5 * xUnit).rgb);\n  gl_FragColor.g = coeffs.a + dot(coeffs.rgb,\n      sample(tc - 0.5 * xUnit).rgb);\n  gl_FragColor.b = coeffs.a + dot(coeffs.rgb,\n      sample(tc + 0.5 * xUnit).rgb);\n  gl_FragColor.a = coeffs.a + dot(coeffs.rgb,\n      sample(tc + 1.5 * xUnit).rgb);\n}\n";
  
  private static class ShaderCallbacks implements GlGenericDrawer.ShaderCallbacks {
    private static final float[] yCoeffs = new float[] { 0.256788F, 0.504129F, 0.0979059F, 0.0627451F };
    
    private static final float[] uCoeffs = new float[] { -0.148223F, -0.290993F, 0.439216F, 0.501961F };
    
    private static final float[] vCoeffs = new float[] { 0.439216F, -0.367788F, -0.0714274F, 0.501961F };
    
    private int xUnitLoc;
    
    private int coeffsLoc;
    
    private float[] coeffs;
    
    private float stepSize;
    
    public void setPlaneY() {
      this.coeffs = yCoeffs;
      this.stepSize = 1.0F;
    }
    
    public void setPlaneU() {
      this.coeffs = uCoeffs;
      this.stepSize = 2.0F;
    }
    
    public void setPlaneV() {
      this.coeffs = vCoeffs;
      this.stepSize = 2.0F;
    }
    
    public void onNewShader(GlShader shader) {
      this.xUnitLoc = shader.getUniformLocation("xUnit");
      this.coeffsLoc = shader.getUniformLocation("coeffs");
    }
    
    public void onPrepareShader(GlShader shader, float[] texMatrix, int frameWidth, int frameHeight, int viewportWidth, int viewportHeight) {
      GLES20.glUniform4fv(this.coeffsLoc, 1, this.coeffs, 0);
      GLES20.glUniform2f(this.xUnitLoc, this.stepSize * texMatrix[0] / frameWidth, this.stepSize * texMatrix[1] / frameWidth);
    }
  }
  
  private final ThreadUtils.ThreadChecker threadChecker = new ThreadUtils.ThreadChecker();
  
  private final GlTextureFrameBuffer i420TextureFrameBuffer = new GlTextureFrameBuffer(GLES20.GL_RGBA);
  
  private final ShaderCallbacks shaderCallbacks = new ShaderCallbacks();
  
  private final GlGenericDrawer drawer = new GlGenericDrawer(FRAGMENT_SHADER, this.shaderCallbacks);
  
  private final VideoFrameDrawer videoFrameDrawer;
  
  public YuvConverter() {
    this(new VideoFrameDrawer());
  }
  
  public YuvConverter(VideoFrameDrawer videoFrameDrawer) {
    this.videoFrameDrawer = videoFrameDrawer;
    this.threadChecker.detachThread();
  }
  
  @Nullable
  public VideoFrame.I420Buffer convert(VideoFrame.TextureBuffer inputTextureBuffer) {
    try {
      return convertInternal(inputTextureBuffer);
    } catch (GLException e) {
      Logging.w(TAG, "Failed to convert TextureBuffer", (Throwable)e);
      return null;
    } 
  }
  
  private VideoFrame.I420Buffer convertInternal(VideoFrame.TextureBuffer inputTextureBuffer) {
    VideoFrame.TextureBuffer preparedBuffer = (VideoFrame.TextureBuffer)this.videoFrameDrawer.prepareBufferForViewportSize(inputTextureBuffer, inputTextureBuffer
        .getWidth(), inputTextureBuffer.getHeight());
    int frameWidth = preparedBuffer.getWidth();
    int frameHeight = preparedBuffer.getHeight();
    int stride = (frameWidth + 7) / 8 * 8;
    int uvHeight = (frameHeight + 1) / 2;
    int totalHeight = frameHeight + uvHeight;
    ByteBuffer i420ByteBuffer = JniCommon.nativeAllocateByteBuffer(stride * totalHeight);
    int viewportWidth = stride / 4;
    Matrix renderMatrix = new Matrix();
    renderMatrix.preTranslate(0.5F, 0.5F);
    renderMatrix.preScale(1.0F, -1.0F);
    renderMatrix.preTranslate(-0.5F, -0.5F);
    this.i420TextureFrameBuffer.setSize(viewportWidth, totalHeight);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, this.i420TextureFrameBuffer.getFrameBufferId());
    GlUtil.checkNoGLES2Error("glBindFramebuffer");
    this.shaderCallbacks.setPlaneY();
    VideoFrameDrawer.drawTexture(this.drawer, preparedBuffer, renderMatrix, frameWidth, frameHeight, 0, 0, viewportWidth, frameHeight);
    this.shaderCallbacks.setPlaneU();
    VideoFrameDrawer.drawTexture(this.drawer, preparedBuffer, renderMatrix, frameWidth, frameHeight, 0, frameHeight, viewportWidth / 2, uvHeight);
    this.shaderCallbacks.setPlaneV();
    VideoFrameDrawer.drawTexture(this.drawer, preparedBuffer, renderMatrix, frameWidth, frameHeight, viewportWidth / 2, frameHeight, viewportWidth / 2, uvHeight);
    GLES20.glReadPixels(0, 0, this.i420TextureFrameBuffer.getWidth(), this.i420TextureFrameBuffer.getHeight(), GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, i420ByteBuffer);
    GlUtil.checkNoGLES2Error("YuvConverter.convert");
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    final int yPos = 0;
    final int uPos = yPos + stride * frameHeight;
    final int vPos = uPos + stride / 2;
    i420ByteBuffer.position(yPos);
    i420ByteBuffer.limit(yPos + stride * frameHeight);
    ByteBuffer dataY = i420ByteBuffer.slice();
    i420ByteBuffer.position(uPos);
    int uvSize = stride * (uvHeight - 1) + stride / 2;
    i420ByteBuffer.limit(uPos + uvSize);
    ByteBuffer dataU = i420ByteBuffer.slice();
    i420ByteBuffer.position(vPos);
    i420ByteBuffer.limit(vPos + uvSize);
    ByteBuffer dataV = i420ByteBuffer.slice();
    preparedBuffer.release();
    return JavaI420Buffer.wrap(frameWidth, frameHeight, dataY, stride, dataU, stride, dataV, stride, () -> JniCommon.nativeFreeByteBuffer(i420ByteBuffer));
  }
  
  public void release() {
    this.threadChecker.checkIsOnValidThread();
    this.drawer.release();
    this.i420TextureFrameBuffer.release();
    this.videoFrameDrawer.release();
    this.threadChecker.detachThread();
  }
}

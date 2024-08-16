package org.webrtc;

import android.opengl.GLES20;

public class GlTextureFrameBuffer {
  private final int pixelFormat;
  
  private int frameBufferId;
  
  private int textureId;
  
  private int width;
  
  private int height;
  
  public GlTextureFrameBuffer(int pixelFormat) {
    switch (pixelFormat) {
      case GLES20.GL_RGB:
      case GLES20.GL_RGBA:
      case GLES20.GL_LUMINANCE:
        this.pixelFormat = pixelFormat;
        break;
      default:
        throw new IllegalArgumentException("Invalid pixel format: " + pixelFormat);
    } 
    this.width = 0;
    this.height = 0;
  }
  
  public void setSize(int width, int height) {
    if (width <= 0 || height <= 0)
      throw new IllegalArgumentException("Invalid size: " + width + "x" + height); 
    if (width == this.width && height == this.height)
      return; 
    this.width = width;
    this.height = height;
    if (this.textureId == 0)
      this.textureId = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D);
    if (this.frameBufferId == 0) {
      int[] frameBuffers = new int[1];
      GLES20.glGenFramebuffers(1, frameBuffers, 0);
      this.frameBufferId = frameBuffers[0];
    } 
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this.textureId);
    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, this.pixelFormat, width, height, 0, this.pixelFormat, GLES20.GL_UNSIGNED_BYTE, null);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    GlUtil.checkNoGLES2Error("GlTextureFrameBuffer setSize");
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, this.frameBufferId);
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, this.textureId, 0);
    int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
    if (status != GLES20.GL_FRAMEBUFFER_COMPLETE)
      throw new IllegalStateException("Framebuffer not complete, status: " + status); 
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
  }
  
  public int getWidth() {
    return this.width;
  }
  
  public int getHeight() {
    return this.height;
  }
  
  public int getFrameBufferId() {
    return this.frameBufferId;
  }
  
  public int getTextureId() {
    return this.textureId;
  }
  
  public void release() {
    GLES20.glDeleteTextures(1, new int[] { this.textureId }, 0);
    this.textureId = 0;
    GLES20.glDeleteFramebuffers(1, new int[] { this.frameBufferId }, 0);
    this.frameBufferId = 0;
    this.width = 0;
    this.height = 0;
  }
}

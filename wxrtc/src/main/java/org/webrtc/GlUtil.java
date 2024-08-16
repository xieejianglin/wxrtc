package org.webrtc;

import android.opengl.GLES20;
import android.opengl.GLException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GlUtil {
  public static class GlOutOfMemoryException extends GLException {
    public GlOutOfMemoryException(int error, String msg) {
      super(error, msg);
    }
  }
  
  public static void checkNoGLES2Error(String msg) {
    int error = GLES20.glGetError();
    if (error != GLES20.GL_NO_ERROR)
      throw (error == GLES20.GL_OUT_OF_MEMORY) ?
        new GlOutOfMemoryException(error, msg) : 
        new GLException(error, msg + ": GLES20 error: " + error);
  }
  
  public static FloatBuffer createFloatBuffer(float[] coords) {
    ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * 4);
    bb.order(ByteOrder.nativeOrder());
    FloatBuffer fb = bb.asFloatBuffer();
    fb.put(coords);
    fb.position(0);
    return fb;
  }
  
  public static int generateTexture(int target) {
    int[] textureArray = new int[1];
    GLES20.glGenTextures(1, textureArray, 0);
    int textureId = textureArray[0];
    GLES20.glBindTexture(target, textureId);
    GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    checkNoGLES2Error("generateTexture");
    return textureId;
  }
}

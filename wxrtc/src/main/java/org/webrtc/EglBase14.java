package org.webrtc;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;

public interface EglBase14 extends EglBase {
  public static interface EglConnection extends EglBase.EglConnection {
    EGLContext getContext();
    
    EGLDisplay getDisplay();
    
    EGLConfig getConfig();
  }
  
  public static interface Context extends EglBase.Context {
    EGLContext getRawContext();
  }
}

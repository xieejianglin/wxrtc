package org.webrtc;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

public interface EglBase10 extends EglBase {
  public static interface EglConnection extends EglBase.EglConnection {
    EGL10 getEgl();
    
    EGLContext getContext();
    
    EGLDisplay getDisplay();
    
    EGLConfig getConfig();
  }
  
  public static interface Context extends EglBase.Context {
    EGLContext getRawContext();
  }
}

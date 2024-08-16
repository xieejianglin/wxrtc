package org.webrtc;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;

public interface EglBase14 extends EglBase {
  interface EglConnection extends EglBase.EglConnection {
    EGLContext getContext();
    
    EGLDisplay getDisplay();
    
    EGLConfig getConfig();
  }
  
  interface Context extends EglBase.Context {
    EGLContext getRawContext();
  }
}

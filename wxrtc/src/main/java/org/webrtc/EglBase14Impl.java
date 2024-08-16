package org.webrtc;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLException;
import android.view.Surface;
import androidx.annotation.Nullable;

class EglBase14Impl implements EglBase14 {
  private static final String TAG = "EglBase14Impl";
  
  private static final EglConnection EGL_NO_CONNECTION = new EglConnection();
  
  private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
  
  private EglConnection eglConnection;
  
  public static class Context implements EglBase14.Context {
    private final EGLContext egl14Context;
    
    public EGLContext getRawContext() {
      return this.egl14Context;
    }
    
    public long getNativeEglContext() {
      return this.egl14Context.getNativeHandle();
    }
    
    public Context(EGLContext eglContext) {
      this.egl14Context = eglContext;
    }
  }
  
  public static class EglConnection implements EglBase14.EglConnection {
    private final EGLContext eglContext;
    
    private final EGLDisplay eglDisplay;
    
    private final EGLConfig eglConfig;
    
    private final RefCountDelegate refCountDelegate;
    
    public EglConnection(EGLContext sharedContext, int[] configAttributes) {
      this.eglDisplay = getEglDisplay();
      this.eglConfig = getEglConfig(this.eglDisplay, configAttributes);
      int openGlesVersion = EglBase.getOpenGlesVersionFromConfig(configAttributes);
      Logging.d(TAG, "Using OpenGL ES version " + openGlesVersion);
      this.eglContext = createEglContext(sharedContext, this.eglDisplay, this.eglConfig, openGlesVersion);
      this.refCountDelegate = new RefCountDelegate(() -> {
            synchronized (EglBase.lock) {
              EGL14.eglMakeCurrent(this.eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
              EGL14.eglDestroyContext(this.eglDisplay, this.eglContext);
            } 
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(this.eglDisplay);
          });
    }
    
    private EglConnection() {
      this.eglContext = EGL14.EGL_NO_CONTEXT;
      this.eglDisplay = EGL14.EGL_NO_DISPLAY;
      this.eglConfig = null;
      this.refCountDelegate = new RefCountDelegate(() -> {});
    }
    
    public void retain() {
      this.refCountDelegate.retain();
    }
    
    public void release() {
      this.refCountDelegate.release();
    }
    
    public EGLContext getContext() {
      return this.eglContext;
    }
    
    public EGLDisplay getDisplay() {
      return this.eglDisplay;
    }
    
    public EGLConfig getConfig() {
      return this.eglConfig;
    }
  }
  
  public EglBase14Impl(EGLContext sharedContext, int[] configAttributes) {
    this.eglConnection = new EglConnection(sharedContext, configAttributes);
  }
  
  public EglBase14Impl(EglConnection eglConnection) {
    this.eglConnection = eglConnection;
    this.eglConnection.retain();
  }
  
  public void createSurface(Surface surface) {
    createSurfaceInternal(surface);
  }
  
  public void createSurface(SurfaceTexture surfaceTexture) {
    createSurfaceInternal(surfaceTexture);
  }
  
  private void createSurfaceInternal(Object surface) {
    if (!(surface instanceof Surface) && !(surface instanceof SurfaceTexture))
      throw new IllegalStateException("Input must be either a Surface or SurfaceTexture"); 
    checkIsNotReleased();
    if (this.eglSurface != EGL14.EGL_NO_SURFACE)
      throw new RuntimeException("Already has an EGLSurface"); 
    int[] surfaceAttribs = { EGL14.EGL_NONE };
    this.eglSurface = EGL14.eglCreateWindowSurface(this.eglConnection
        .getDisplay(), this.eglConnection.getConfig(), surface, surfaceAttribs, 0);
    if (this.eglSurface == EGL14.EGL_NO_SURFACE)
      throw new GLException(EGL14.eglGetError(), "Failed to create window surface: 0x" + 
          Integer.toHexString(EGL14.eglGetError())); 
  }
  
  public void createDummyPbufferSurface() {
    createPbufferSurface(1, 1);
  }
  
  public void createPbufferSurface(int width, int height) {
    checkIsNotReleased();
    if (this.eglSurface != EGL14.EGL_NO_SURFACE)
      throw new RuntimeException("Already has an EGLSurface"); 
    int[] surfaceAttribs = { EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE };
    this.eglSurface = EGL14.eglCreatePbufferSurface(this.eglConnection
        .getDisplay(), this.eglConnection.getConfig(), surfaceAttribs, 0);
    if (this.eglSurface == EGL14.EGL_NO_SURFACE)
      throw new GLException(EGL14.eglGetError(), "Failed to create pixel buffer surface with size " + width + "x" + height + ": 0x" +
          Integer.toHexString(EGL14.eglGetError())); 
  }
  
  public Context getEglBaseContext() {
    return new Context(this.eglConnection.getContext());
  }
  
  public boolean hasSurface() {
    return (this.eglSurface != EGL14.EGL_NO_SURFACE);
  }
  
  public int surfaceWidth() {
    int[] widthArray = new int[1];
    EGL14.eglQuerySurface(this.eglConnection.getDisplay(), this.eglSurface, EGL14.EGL_WIDTH, widthArray, 0);
    return widthArray[0];
  }
  
  public int surfaceHeight() {
    int[] heightArray = new int[1];
    EGL14.eglQuerySurface(this.eglConnection.getDisplay(), this.eglSurface, EGL14.EGL_HEIGHT, heightArray, 0);
    return heightArray[0];
  }
  
  public void releaseSurface() {
    if (this.eglSurface != EGL14.EGL_NO_SURFACE) {
      EGL14.eglDestroySurface(this.eglConnection.getDisplay(), this.eglSurface);
      this.eglSurface = EGL14.EGL_NO_SURFACE;
    } 
  }
  
  private void checkIsNotReleased() {
    if (this.eglConnection == EGL_NO_CONNECTION)
      throw new RuntimeException("This object has been released"); 
  }
  
  public void release() {
    checkIsNotReleased();
    releaseSurface();
    this.eglConnection.release();
    this.eglConnection = EGL_NO_CONNECTION;
  }
  
  public void makeCurrent() {
    checkIsNotReleased();
    if (this.eglSurface == EGL14.EGL_NO_SURFACE)
      throw new RuntimeException("No EGLSurface - can't make current"); 
    synchronized (EglBase.lock) {
      if (!EGL14.eglMakeCurrent(this.eglConnection.getDisplay(), this.eglSurface, this.eglSurface, this.eglConnection.getContext()))
        throw new GLException(EGL14.eglGetError(), "eglMakeCurrent failed: 0x" + 
            Integer.toHexString(EGL14.eglGetError())); 
    } 
  }
  
  public void detachCurrent() {
    synchronized (EglBase.lock) {
      if (!EGL14.eglMakeCurrent(this.eglConnection.getDisplay(), EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT))
        throw new GLException(EGL14.eglGetError(), "eglDetachCurrent failed: 0x" + 
            Integer.toHexString(EGL14.eglGetError())); 
    } 
  }
  
  public void swapBuffers() {
    checkIsNotReleased();
    if (this.eglSurface == EGL14.EGL_NO_SURFACE)
      throw new RuntimeException("No EGLSurface - can't swap buffers"); 
    synchronized (EglBase.lock) {
      EGL14.eglSwapBuffers(this.eglConnection.getDisplay(), this.eglSurface);
    } 
  }
  
  public void swapBuffers(long timeStampNs) {
    checkIsNotReleased();
    if (this.eglSurface == EGL14.EGL_NO_SURFACE)
      throw new RuntimeException("No EGLSurface - can't swap buffers"); 
    synchronized (EglBase.lock) {
      EGLExt.eglPresentationTimeANDROID(this.eglConnection.getDisplay(), this.eglSurface, timeStampNs);
      EGL14.eglSwapBuffers(this.eglConnection.getDisplay(), this.eglSurface);
    } 
  }
  
  private static EGLDisplay getEglDisplay() {
    EGLDisplay eglDisplay = EGL14.eglGetDisplay(0);
    if (eglDisplay == EGL14.EGL_NO_DISPLAY)
      throw new GLException(EGL14.eglGetError(), "Unable to get EGL14 display: 0x" + 
          Integer.toHexString(EGL14.eglGetError())); 
    int[] version = new int[2];
    if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1))
      throw new GLException(EGL14.eglGetError(), "Unable to initialize EGL14: 0x" + 
          Integer.toHexString(EGL14.eglGetError())); 
    return eglDisplay;
  }
  
  private static EGLConfig getEglConfig(EGLDisplay eglDisplay, int[] configAttributes) {
    EGLConfig[] configs = new EGLConfig[1];
    int[] numConfigs = new int[1];
    if (!EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, configs, 0, configs.length, numConfigs, 0))
      throw new GLException(EGL14.eglGetError(), "eglChooseConfig failed: 0x" + 
          Integer.toHexString(EGL14.eglGetError())); 
    if (numConfigs[0] <= 0)
      throw new RuntimeException("Unable to find any matching EGL config"); 
    EGLConfig eglConfig = configs[0];
    if (eglConfig == null)
      throw new RuntimeException("eglChooseConfig returned null"); 
    return eglConfig;
  }
  
  private static EGLContext createEglContext(@Nullable EGLContext sharedContext, EGLDisplay eglDisplay, EGLConfig eglConfig, int openGlesVersion) {
    EGLContext eglContext;
    if (sharedContext != null && sharedContext == EGL14.EGL_NO_CONTEXT)
      throw new RuntimeException("Invalid sharedContext"); 
    int[] contextAttributes = { EGL14.EGL_CONTEXT_CLIENT_VERSION, openGlesVersion, EGL14.EGL_NONE };
    EGLContext rootContext = (sharedContext == null) ? EGL14.EGL_NO_CONTEXT : sharedContext;
    synchronized (EglBase.lock) {
      eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, rootContext, contextAttributes, 0);
    } 
    if (eglContext == EGL14.EGL_NO_CONTEXT)
      throw new GLException(EGL14.eglGetError(), "Failed to create EGL context: 0x" + 
          Integer.toHexString(EGL14.eglGetError())); 
    return eglContext;
  }
}

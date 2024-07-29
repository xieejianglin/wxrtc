package org.webrtc;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.GLException;
import android.view.Surface;
import android.view.SurfaceHolder;
import androidx.annotation.Nullable;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

class EglBase10Impl implements EglBase10 {
  private static final String TAG = "EglBase10Impl";
  
  private static final int EGL_CONTEXT_CLIENT_VERSION = 12440;
  
  private static final EglConnection EGL_NO_CONNECTION = new EglConnection();
  
  private EGLSurface eglSurface = EGL10.EGL_NO_SURFACE;
  
  private EglConnection eglConnection;
  
  private static class Context implements EglBase10.Context {
    private final EGL10 egl;
    
    private final EGLContext eglContext;
    
    private final EGLConfig eglContextConfig;
    
    public EGLContext getRawContext() {
      return this.eglContext;
    }
    
    public long getNativeEglContext() {
      EGLContext previousContext = this.egl.eglGetCurrentContext();
      EGLDisplay currentDisplay = this.egl.eglGetCurrentDisplay();
      EGLSurface previousDrawSurface = this.egl.eglGetCurrentSurface(12377);
      EGLSurface previousReadSurface = this.egl.eglGetCurrentSurface(12378);
      EGLSurface tempEglSurface = null;
      if (currentDisplay == EGL10.EGL_NO_DISPLAY)
        currentDisplay = this.egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY); 
      try {
        if (previousContext != this.eglContext) {
          int[] surfaceAttribs = { 12375, 1, 12374, 1, 12344 };
          tempEglSurface = this.egl.eglCreatePbufferSurface(currentDisplay, this.eglContextConfig, surfaceAttribs);
          if (!this.egl.eglMakeCurrent(currentDisplay, tempEglSurface, tempEglSurface, this.eglContext))
            throw new GLException(this.egl.eglGetError(), "Failed to make temporary EGL surface active: " + this.egl
                .eglGetError()); 
        } 
        return EglBase10Impl.nativeGetCurrentNativeEGLContext();
      } finally {
        if (tempEglSurface != null) {
          this.egl.eglMakeCurrent(currentDisplay, previousDrawSurface, previousReadSurface, previousContext);
          this.egl.eglDestroySurface(currentDisplay, tempEglSurface);
        } 
      } 
    }
    
    public Context(EGL10 egl, EGLContext eglContext, EGLConfig eglContextConfig) {
      this.egl = egl;
      this.eglContext = eglContext;
      this.eglContextConfig = eglContextConfig;
    }
  }
  
  public static class EglConnection implements EglBase10.EglConnection {
    private final EGL10 egl;
    
    private final EGLContext eglContext;
    
    private final EGLDisplay eglDisplay;
    
    private final EGLConfig eglConfig;
    
    private final RefCountDelegate refCountDelegate;
    
    public EglConnection(EGLContext sharedContext, int[] configAttributes) {
      this.egl = (EGL10)EGLContext.getEGL();
      this.eglDisplay = EglBase10Impl.getEglDisplay(this.egl);
      this.eglConfig = EglBase10Impl.getEglConfig(this.egl, this.eglDisplay, configAttributes);
      int openGlesVersion = EglBase.getOpenGlesVersionFromConfig(configAttributes);
      Logging.d("EglBase10Impl", "Using OpenGL ES version " + openGlesVersion);
      this.eglContext = EglBase10Impl.createEglContext(this.egl, sharedContext, this.eglDisplay, this.eglConfig, openGlesVersion);
      this.refCountDelegate = new RefCountDelegate(() -> {
            synchronized (EglBase.lock) {
              this.egl.eglMakeCurrent(this.eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            } 
            this.egl.eglDestroyContext(this.eglDisplay, this.eglContext);
            this.egl.eglTerminate(this.eglDisplay);
          });
    }
    
    private EglConnection() {
      this.egl = (EGL10)EGLContext.getEGL();
      this.eglContext = EGL10.EGL_NO_CONTEXT;
      this.eglDisplay = EGL10.EGL_NO_DISPLAY;
      this.eglConfig = null;
      this.refCountDelegate = new RefCountDelegate(() -> {
          
          });
    }
    
    public void retain() {
      this.refCountDelegate.retain();
    }
    
    public void release() {
      this.refCountDelegate.release();
    }
    
    public EGL10 getEgl() {
      return this.egl;
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
  
  public EglBase10Impl(EGLContext sharedContext, int[] configAttributes) {
    this.eglConnection = new EglConnection(sharedContext, configAttributes);
  }
  
  public EglBase10Impl(EglConnection eglConnection) {
    this.eglConnection = eglConnection;
    this.eglConnection.retain();
  }
  
  public void createSurface(Surface surface) {
    class FakeSurfaceHolder implements SurfaceHolder {
      private final Surface surface;
      
      FakeSurfaceHolder(Surface surface) {
        this.surface = surface;
      }
      
      public void addCallback(SurfaceHolder.Callback callback) {}
      
      public void removeCallback(SurfaceHolder.Callback callback) {}
      
      public boolean isCreating() {
        return false;
      }
      
      @Deprecated
      public void setType(int i) {}
      
      public void setFixedSize(int i, int i2) {}
      
      public void setSizeFromLayout() {}
      
      public void setFormat(int i) {}
      
      public void setKeepScreenOn(boolean b) {}
      
      @Nullable
      public Canvas lockCanvas() {
        return null;
      }
      
      @Nullable
      public Canvas lockCanvas(Rect rect) {
        return null;
      }
      
      public void unlockCanvasAndPost(Canvas canvas) {}
      
      @Nullable
      public Rect getSurfaceFrame() {
        return null;
      }
      
      public Surface getSurface() {
        return this.surface;
      }
    };
    createSurfaceInternal(new FakeSurfaceHolder(surface));
  }
  
  public void createSurface(SurfaceTexture surfaceTexture) {
    createSurfaceInternal(surfaceTexture);
  }
  
  private void createSurfaceInternal(Object nativeWindow) {
    if (!(nativeWindow instanceof SurfaceHolder) && !(nativeWindow instanceof SurfaceTexture))
      throw new IllegalStateException("Input must be either a SurfaceHolder or SurfaceTexture"); 
    checkIsNotReleased();
    if (this.eglSurface != EGL10.EGL_NO_SURFACE)
      throw new RuntimeException("Already has an EGLSurface"); 
    EGL10 egl = this.eglConnection.getEgl();
    int[] surfaceAttribs = { 12344 };
    this.eglSurface = egl.eglCreateWindowSurface(this.eglConnection
        .getDisplay(), this.eglConnection.getConfig(), nativeWindow, surfaceAttribs);
    if (this.eglSurface == EGL10.EGL_NO_SURFACE)
      throw new GLException(egl.eglGetError(), "Failed to create window surface: 0x" + 
          Integer.toHexString(egl.eglGetError())); 
  }
  
  public void createDummyPbufferSurface() {
    createPbufferSurface(1, 1);
  }
  
  public void createPbufferSurface(int width, int height) {
    checkIsNotReleased();
    if (this.eglSurface != EGL10.EGL_NO_SURFACE)
      throw new RuntimeException("Already has an EGLSurface"); 
    EGL10 egl = this.eglConnection.getEgl();
    int[] surfaceAttribs = { 12375, width, 12374, height, 12344 };
    this.eglSurface = egl.eglCreatePbufferSurface(this.eglConnection
        .getDisplay(), this.eglConnection.getConfig(), surfaceAttribs);
    if (this.eglSurface == EGL10.EGL_NO_SURFACE)
      throw new GLException(egl.eglGetError(), "Failed to create pixel buffer surface with size " + width + "x" + height + ": 0x" + 
          
          Integer.toHexString(egl.eglGetError())); 
  }
  
  public EglBase.Context getEglBaseContext() {
    return new Context(this.eglConnection
        .getEgl(), this.eglConnection.getContext(), this.eglConnection.getConfig());
  }
  
  public boolean hasSurface() {
    return (this.eglSurface != EGL10.EGL_NO_SURFACE);
  }
  
  public int surfaceWidth() {
    int[] widthArray = new int[1];
    this.eglConnection.getEgl().eglQuerySurface(this.eglConnection
        .getDisplay(), this.eglSurface, 12375, widthArray);
    return widthArray[0];
  }
  
  public int surfaceHeight() {
    int[] heightArray = new int[1];
    this.eglConnection.getEgl().eglQuerySurface(this.eglConnection
        .getDisplay(), this.eglSurface, 12374, heightArray);
    return heightArray[0];
  }
  
  public void releaseSurface() {
    if (this.eglSurface != EGL10.EGL_NO_SURFACE) {
      this.eglConnection.getEgl().eglDestroySurface(this.eglConnection.getDisplay(), this.eglSurface);
      this.eglSurface = EGL10.EGL_NO_SURFACE;
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
    if (this.eglSurface == EGL10.EGL_NO_SURFACE)
      throw new RuntimeException("No EGLSurface - can't make current"); 
    synchronized (EglBase.lock) {
      EGL10 egl = this.eglConnection.getEgl();
      if (!egl.eglMakeCurrent(this.eglConnection
          .getDisplay(), this.eglSurface, this.eglSurface, this.eglConnection.getContext()))
        throw new GLException(egl.eglGetError(), "eglMakeCurrent failed: 0x" + 
            Integer.toHexString(egl.eglGetError())); 
    } 
  }
  
  public void detachCurrent() {
    synchronized (EglBase.lock) {
      EGL10 egl = this.eglConnection.getEgl();
      if (!egl.eglMakeCurrent(this.eglConnection.getDisplay(), EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT))
        throw new GLException(egl.eglGetError(), "eglDetachCurrent failed: 0x" + 
            Integer.toHexString(egl.eglGetError())); 
    } 
  }
  
  public void swapBuffers() {
    checkIsNotReleased();
    if (this.eglSurface == EGL10.EGL_NO_SURFACE)
      throw new RuntimeException("No EGLSurface - can't swap buffers"); 
    synchronized (EglBase.lock) {
      this.eglConnection.getEgl().eglSwapBuffers(this.eglConnection.getDisplay(), this.eglSurface);
    } 
  }
  
  public void swapBuffers(long timeStampNs) {
    swapBuffers();
  }
  
  private static EGLDisplay getEglDisplay(EGL10 egl) {
    EGLDisplay eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL10.EGL_NO_DISPLAY)
      throw new GLException(egl.eglGetError(), "Unable to get EGL10 display: 0x" + 
          Integer.toHexString(egl.eglGetError())); 
    int[] version = new int[2];
    if (!egl.eglInitialize(eglDisplay, version))
      throw new GLException(egl.eglGetError(), "Unable to initialize EGL10: 0x" + 
          Integer.toHexString(egl.eglGetError())); 
    return eglDisplay;
  }
  
  private static EGLConfig getEglConfig(EGL10 egl, EGLDisplay eglDisplay, int[] configAttributes) {
    EGLConfig[] configs = new EGLConfig[1];
    int[] numConfigs = new int[1];
    if (!egl.eglChooseConfig(eglDisplay, configAttributes, configs, configs.length, numConfigs))
      throw new GLException(egl
          .eglGetError(), "eglChooseConfig failed: 0x" + Integer.toHexString(egl.eglGetError())); 
    if (numConfigs[0] <= 0)
      throw new RuntimeException("Unable to find any matching EGL config"); 
    EGLConfig eglConfig = configs[0];
    if (eglConfig == null)
      throw new RuntimeException("eglChooseConfig returned null"); 
    return eglConfig;
  }
  
  private static EGLContext createEglContext(EGL10 egl, @Nullable EGLContext sharedContext, EGLDisplay eglDisplay, EGLConfig eglConfig, int openGlesVersion) {
    EGLContext eglContext;
    if (sharedContext != null && sharedContext == EGL10.EGL_NO_CONTEXT)
      throw new RuntimeException("Invalid sharedContext"); 
    int[] contextAttributes = { 12440, openGlesVersion, 12344 };
    EGLContext rootContext = (sharedContext == null) ? EGL10.EGL_NO_CONTEXT : sharedContext;
    synchronized (EglBase.lock) {
      eglContext = egl.eglCreateContext(eglDisplay, eglConfig, rootContext, contextAttributes);
    } 
    if (eglContext == EGL10.EGL_NO_CONTEXT)
      throw new GLException(egl.eglGetError(), "Failed to create EGL context: 0x" + 
          Integer.toHexString(egl.eglGetError())); 
    return eglContext;
  }
  
  private static native long nativeGetCurrentNativeEGLContext();
}

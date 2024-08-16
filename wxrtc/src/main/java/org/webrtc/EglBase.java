//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.webrtc;

import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import javax.microedition.khronos.egl.EGL10;

public interface EglBase {
  // EGL wrapper for an actual EGLContext.
  public interface Context {
    public final static long NO_CONTEXT = 0;
    /**
     * Returns an EGL context that can be used by native code. Returns NO_CONTEXT if the method is
     * unsupported.
     *
     * @note This is currently only supported for EGL 1.4 and not for EGL 1.0.
     */
    long getNativeEglContext();
  }

  /**
   * Wraps the objects needed to interact with EGL that are independent of a particular EGLSurface.
   * In practice this means EGLContext, EGLDisplay and EGLConfig objects. Separating them out in a
   * standalone object allows for multiple EglBase instances to use the same underlying EGLContext,
   * while still operating on their own EGLSurface.
   */
  public interface EglConnection extends RefCounted {
    /** Analogous to corresponding EglBase#create below. */
    public static EglConnection create(@Nullable Context sharedContext, int[] configAttributes) {
      if (sharedContext == null) {
        return EglConnection.createEgl14(configAttributes);
      } else if (sharedContext instanceof EglBase14.Context) {
        return new EglBase14Impl.EglConnection(
                ((EglBase14.Context) sharedContext).getRawContext(), configAttributes);
      } else if (sharedContext instanceof EglBase10.Context) {
        return new EglBase10Impl.EglConnection(
                ((EglBase10.Context) sharedContext).getRawContext(), configAttributes);
      }
      throw new IllegalArgumentException("Unrecognized Context");
    }
    /** Analogous to corresponding EglBase#createEgl10 below. */
    public static EglConnection createEgl10(int[] configAttributes) {
      return new EglBase10Impl.EglConnection(/* sharedContext= */ null, configAttributes);
    }
    /** Analogous to corresponding EglBase#createEgl14 below. */
    public static EglConnection createEgl14(int[] configAttributes) {
      return new EglBase14Impl.EglConnection(/* sharedContext= */ null, configAttributes);
    }
  }

  public static final Object lock = new Object();
  public static final int EGL_OPENGL_ES2_BIT = 4;
  public static final int EGL_OPENGL_ES3_BIT = 0x40;
  public static final int EGL_RECORDABLE_ANDROID = 0x3142;

  public static final int[] CONFIG_PLAIN = configBuilder().createConfigAttributes();
  public static final int[] CONFIG_RGBA = configBuilder().setHasAlphaChannel(true).createConfigAttributes();
  public static final int[] CONFIG_PIXEL_BUFFER = configBuilder().setSupportsPixelBuffer(true).createConfigAttributes();
  public static final int[] CONFIG_PIXEL_RGBA_BUFFER = configBuilder().setHasAlphaChannel(true).setSupportsPixelBuffer(true).createConfigAttributes();
  public static final int[] CONFIG_RECORDABLE = configBuilder().setIsRecordable(true).createConfigAttributes();

  public static ConfigBuilder configBuilder() {
    return new ConfigBuilder();
  }

  static int getOpenGlesVersionFromConfig(int[] configAttributes) {
    for(int i = 0; i < configAttributes.length - 1; ++i) {
      if (configAttributes[i] == EGL10.EGL_RENDERABLE_TYPE) {
        switch (configAttributes[i + 1]) {
          case EGL_OPENGL_ES2_BIT:
            return 2;
          case EGL_OPENGL_ES3_BIT:
            return 3;
          default:
            return 1;
        }
      }
    }

    return 1;
  }

  static EglBase create(EglConnection eglConnection) {
    if (eglConnection == null) {
      return create();
    } else if (eglConnection instanceof EglBase14Impl.EglConnection) {
      return new EglBase14Impl((EglBase14Impl.EglConnection)eglConnection);
    } else if (eglConnection instanceof EglBase10Impl.EglConnection) {
      return new EglBase10Impl((EglBase10Impl.EglConnection)eglConnection);
    } else {
      throw new IllegalArgumentException("Unrecognized EglConnection");
    }
  }

  static EglBase create(@Nullable Context sharedContext, int[] configAttributes) {
    if (sharedContext == null) {
      return createEgl14(configAttributes);
    } else if (sharedContext instanceof EglBase14.Context) {
      return createEgl14((EglBase14.Context)sharedContext, configAttributes);
    } else if (sharedContext instanceof EglBase10.Context) {
      return createEgl10((EglBase10.Context)sharedContext, configAttributes);
    } else {
      throw new IllegalArgumentException("Unrecognized Context");
    }
  }

  static EglBase create() {
    return create(null, CONFIG_PLAIN);
  }

  static EglBase create(Context sharedContext) {
    return create(sharedContext, CONFIG_PLAIN);
  }

  static EglBase10 createEgl10(int[] configAttributes) {
    return new EglBase10Impl(null, configAttributes);
  }

  static EglBase10 createEgl10(EglBase10.Context sharedContext, int[] configAttributes) {
    return new EglBase10Impl(sharedContext == null ? null : sharedContext.getRawContext(), configAttributes);
  }

  static EglBase10 createEgl10(javax.microedition.khronos.egl.EGLContext sharedContext, int[] configAttributes) {
    return new EglBase10Impl(sharedContext, configAttributes);
  }

  static EglBase14 createEgl14(int[] configAttributes) {
    return new EglBase14Impl(null, configAttributes);
  }

  static EglBase14 createEgl14(EglBase14.Context sharedContext, int[] configAttributes) {
    return new EglBase14Impl(sharedContext == null ? null : sharedContext.getRawContext(), configAttributes);
  }

  static EglBase14 createEgl14(android.opengl.EGLContext sharedContext, int[] configAttributes) {
    return new EglBase14Impl(sharedContext, configAttributes);
  }

  void createSurface(Surface var1);

  void createSurface(SurfaceTexture var1);

  void createDummyPbufferSurface();

  void createPbufferSurface(int var1, int var2);

  Context getEglBaseContext();

  boolean hasSurface();

  int surfaceWidth();

  int surfaceHeight();

  void releaseSurface();

  void release();

  void makeCurrent();

  void detachCurrent();

  void swapBuffers();

  void swapBuffers(long var1);

  public static class ConfigBuilder {
    private int openGlesVersion = 2;
    private boolean hasAlphaChannel;
    private boolean supportsPixelBuffer;
    private boolean isRecordable;

    public ConfigBuilder() {
    }

    public ConfigBuilder setOpenGlesVersion(int version) {
      if (version >= 1 && version <= 3) {
        this.openGlesVersion = version;
        return this;
      } else {
        throw new IllegalArgumentException("OpenGL ES version " + version + " not supported");
      }
    }

    public ConfigBuilder setHasAlphaChannel(boolean hasAlphaChannel) {
      this.hasAlphaChannel = hasAlphaChannel;
      return this;
    }

    public ConfigBuilder setSupportsPixelBuffer(boolean supportsPixelBuffer) {
      this.supportsPixelBuffer = supportsPixelBuffer;
      return this;
    }

    public ConfigBuilder setIsRecordable(boolean isRecordable) {
      this.isRecordable = isRecordable;
      return this;
    }

    public int[] createConfigAttributes() {
      ArrayList<Integer> list = new ArrayList();
      list.add(EGL10.EGL_RED_SIZE);
      list.add(8);
      list.add(EGL10.EGL_GREEN_SIZE);
      list.add(8);
      list.add(EGL10.EGL_BLUE_SIZE);
      list.add(8);
      if (this.hasAlphaChannel) {
        list.add(EGL10.EGL_ALPHA_SIZE);
        list.add(8);
      }

      if (this.openGlesVersion == 2 || this.openGlesVersion == 3) {
        list.add(EGL10.EGL_RENDERABLE_TYPE);
        list.add(this.openGlesVersion == 3 ? EGL_OPENGL_ES3_BIT : EGL_OPENGL_ES2_BIT);
      }

      if (this.supportsPixelBuffer) {
        list.add(EGL10.EGL_SURFACE_TYPE);
        list.add(EGL10.EGL_PBUFFER_BIT);
      }

      if (this.isRecordable) {
        list.add(EGL_RECORDABLE_ANDROID);
        list.add(1);
      }

      list.add(EGL10.EGL_NONE);
      int[] res = new int[list.size()];

      for(int i = 0; i < list.size(); ++i) {
        res[i] = list.get(i);
      }

      return res;
    }
  }
}

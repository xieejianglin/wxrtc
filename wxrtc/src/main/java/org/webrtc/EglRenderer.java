//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.webrtc;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.Bitmap.Config;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.Surface;
import androidx.annotation.Nullable;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class EglRenderer implements VideoSink {
  private static final String TAG = "EglRenderer";
  private static final long LOG_INTERVAL_SEC = 4;
  protected final String name;
  private final Object handlerLock;
  @Nullable
  private Handler renderThreadHandler;
  private final ArrayList<FrameListenerAndParams> frameListeners;
  private volatile ErrorCallback errorCallback;
  private final Object fpsReductionLock;
  private long nextFrameTimeNs;
  private long minRenderPeriodNs;
  @Nullable
  private EglBase eglBase;
  private final VideoFrameDrawer frameDrawer;
  @Nullable
  private RendererCommon.GlDrawer drawer;
  private boolean usePresentationTimeStamp;
  private final Matrix drawMatrix;
  private final Object frameLock;
  @Nullable
  private VideoFrame pendingFrame;
  private final Object layoutLock;
  private float layoutAspectRatio;
  private boolean mirrorHorizontally;
  private boolean mirrorVertically;
  private final Object statisticsLock;
  private int framesReceived;
  private int framesDropped;
  private int framesRendered;
  private long statisticsStartTimeNs;
  private long renderTimeNs;
  private long renderSwapBufferTimeNs;
  private final GlTextureFrameBuffer bitmapTextureFramebuffer;
  private final Runnable logStatisticsRunnable;
  private final EglSurfaceCreation eglSurfaceCreationRunnable;

  public EglRenderer(String name) {
    this(name, new VideoFrameDrawer());
  }

  public EglRenderer(String name, VideoFrameDrawer videoFrameDrawer) {
    this.handlerLock = new Object();
    this.frameListeners = new ArrayList();
    this.fpsReductionLock = new Object();
    this.drawMatrix = new Matrix();
    this.frameLock = new Object();
    this.layoutLock = new Object();
    this.statisticsLock = new Object();
    this.bitmapTextureFramebuffer = new GlTextureFrameBuffer(GLES20.GL_RGBA);
    this.logStatisticsRunnable = new Runnable() {
      public void run() {
        EglRenderer.this.logStatistics();
        synchronized(EglRenderer.this.handlerLock) {
          if (EglRenderer.this.renderThreadHandler != null) {
            EglRenderer.this.renderThreadHandler.removeCallbacks(EglRenderer.this.logStatisticsRunnable);
            EglRenderer.this.renderThreadHandler.postDelayed(EglRenderer.this.logStatisticsRunnable, TimeUnit.SECONDS.toMillis(LOG_INTERVAL_SEC));
          }

        }
      }
    };
    this.eglSurfaceCreationRunnable = new EglSurfaceCreation();
    this.name = name;
    this.frameDrawer = videoFrameDrawer;
  }

  public void init(@Nullable EglBase.Context sharedContext, int[] configAttributes, RendererCommon.GlDrawer drawer, boolean usePresentationTimeStamp) {
    synchronized(this.handlerLock) {
      if (this.renderThreadHandler != null) {
        throw new IllegalStateException(this.name + "Already initialized");
      } else {
        this.logD("Initializing EglRenderer");
        this.drawer = drawer;
        this.usePresentationTimeStamp = usePresentationTimeStamp;
        HandlerThread renderThread = new HandlerThread(this.name + TAG);
        renderThread.start();
        this.renderThreadHandler = new HandlerWithExceptionCallback(renderThread.getLooper(), new Runnable() {
          public void run() {
            synchronized(EglRenderer.this.handlerLock) {
              EglRenderer.this.renderThreadHandler = null;
            }
          }
        });
        ThreadUtils.invokeAtFrontUninterruptibly(this.renderThreadHandler, () -> {
          if (sharedContext == null) {
            this.logD("EglBase10.create context");
            this.eglBase = EglBase.createEgl10(configAttributes);
          } else {
            this.logD("EglBase.create shared context");
            this.eglBase = EglBase.create(sharedContext, configAttributes);
          }

        });
        this.renderThreadHandler.post(this.eglSurfaceCreationRunnable);
        long currentTimeNs = System.nanoTime();
        this.resetStatistics(currentTimeNs);
        this.renderThreadHandler.postDelayed(this.logStatisticsRunnable, TimeUnit.SECONDS.toMillis(LOG_INTERVAL_SEC));
      }
    }
  }

  public void init(@Nullable EglBase.Context sharedContext, int[] configAttributes, RendererCommon.GlDrawer drawer) {
    this.init(sharedContext, configAttributes, drawer, false);
  }

  public void createEglSurface(Surface surface) {
    this.createEglSurfaceInternal(surface);
  }

  public void createEglSurface(SurfaceTexture surfaceTexture) {
    this.createEglSurfaceInternal(surfaceTexture);
  }

  private void createEglSurfaceInternal(Object surface) {
    this.eglSurfaceCreationRunnable.setSurface(surface);
    this.postToRenderThread(this.eglSurfaceCreationRunnable);
  }

  public void release() {
    this.logD("Releasing.");
    CountDownLatch eglCleanupBarrier = new CountDownLatch(1);
    synchronized(this.handlerLock) {
      if (this.renderThreadHandler == null) {
        this.logD("Already released");
        return;
      }

      this.renderThreadHandler.removeCallbacks(this.logStatisticsRunnable);
      this.renderThreadHandler.postAtFrontOfQueue(() -> {
        synchronized(EglBase.lock) {
          GLES20.glUseProgram(0);
        }

        if (this.drawer != null) {
          this.drawer.release();
          this.drawer = null;
        }

        this.frameDrawer.release();
        this.bitmapTextureFramebuffer.release();
        if (this.eglBase != null) {
          this.logD("eglBase detach and release.");
          this.eglBase.detachCurrent();
          this.eglBase.release();
          this.eglBase = null;
        }

        this.frameListeners.clear();
        eglCleanupBarrier.countDown();
      });
      Looper renderLooper = this.renderThreadHandler.getLooper();
      this.renderThreadHandler.post(() -> {
        this.logD("Quitting render thread.");
        renderLooper.quit();
      });
      this.renderThreadHandler = null;
    }

    ThreadUtils.awaitUninterruptibly(eglCleanupBarrier);
    synchronized(this.frameLock) {
      if (this.pendingFrame != null) {
        this.pendingFrame.release();
        this.pendingFrame = null;
      }
    }

    this.logD("Releasing done.");
  }

  private void resetStatistics(long currentTimeNs) {
    synchronized(this.statisticsLock) {
      this.statisticsStartTimeNs = currentTimeNs;
      this.framesReceived = 0;
      this.framesDropped = 0;
      this.framesRendered = 0;
      this.renderTimeNs = 0L;
      this.renderSwapBufferTimeNs = 0L;
    }
  }

  public void printStackTrace() {
    synchronized(this.handlerLock) {
      Thread renderThread = this.renderThreadHandler == null ? null : this.renderThreadHandler.getLooper().getThread();
      if (renderThread != null) {
        StackTraceElement[] renderStackTrace = renderThread.getStackTrace();
        if (renderStackTrace.length > 0) {
          this.logW("EglRenderer stack trace:");
          StackTraceElement[] var4 = renderStackTrace;
          int var5 = renderStackTrace.length;

          for(int var6 = 0; var6 < var5; ++var6) {
            StackTraceElement traceElem = var4[var6];
            this.logW(traceElem.toString());
          }
        }
      }

    }
  }

  public void setMirror(boolean mirror) {
    this.logD("setMirrorHorizontally: " + mirror);
    synchronized(this.layoutLock) {
      this.mirrorHorizontally = mirror;
    }
  }

  public void setMirrorVertically(boolean mirrorVertically) {
    this.logD("setMirrorVertically: " + mirrorVertically);
    synchronized(this.layoutLock) {
      this.mirrorVertically = mirrorVertically;
    }
  }

  public void setLayoutAspectRatio(float layoutAspectRatio) {
    this.logD("setLayoutAspectRatio: " + layoutAspectRatio);
    synchronized(this.layoutLock) {
      this.layoutAspectRatio = layoutAspectRatio;
    }
  }

  public void setFpsReduction(float fps) {
    this.logD("setFpsReduction: " + fps);
    synchronized(this.fpsReductionLock) {
      final long previousRenderPeriodNs = minRenderPeriodNs;
      if (fps <= 0) {
        minRenderPeriodNs = Long.MAX_VALUE;
      } else {
        minRenderPeriodNs = (long) (TimeUnit.SECONDS.toNanos(1) / fps);
      }
      if (minRenderPeriodNs != previousRenderPeriodNs) {
        // Fps reduction changed - reset frame time.
        nextFrameTimeNs = System.nanoTime();
      }

    }
  }

  public void disableFpsReduction() {
    this.setFpsReduction(Float.POSITIVE_INFINITY);
  }

  public void pauseVideo() {
    this.setFpsReduction(0);
  }

  public void addFrameListener(final FrameListener listener, final float scale) {
    addFrameListener(listener, scale, null, false /* applyFpsReduction */);
  }

  public void addFrameListener(final FrameListener listener, final float scale, final RendererCommon.GlDrawer drawerParam) {
    addFrameListener(listener, scale, drawerParam, false /* applyFpsReduction */);
  }

  public void addFrameListener(final FrameListener listener, final float scale,
                               @Nullable final RendererCommon.GlDrawer drawerParam, final boolean applyFpsReduction) {
    postToRenderThread(() -> {
      final RendererCommon.GlDrawer listenerDrawer = drawerParam == null ? drawer : drawerParam;
      frameListeners.add(new FrameListenerAndParams(listener, scale, listenerDrawer, applyFpsReduction));
    });
  }

  public void removeFrameListener(FrameListener listener) {
    CountDownLatch latch = new CountDownLatch(1);
    synchronized(this.handlerLock) {
      if (this.renderThreadHandler == null) {
        return;
      }

      if (Thread.currentThread() == this.renderThreadHandler.getLooper().getThread()) {
        throw new RuntimeException("removeFrameListener must not be called on the render thread.");
      }

      this.postToRenderThread(() -> {
        latch.countDown();
        final Iterator<FrameListenerAndParams> iter = frameListeners.iterator();
        while (iter.hasNext()) {
          if (iter.next().listener == listener) {
            iter.remove();
          }
        }

      });
    }

    ThreadUtils.awaitUninterruptibly(latch);
  }

  public void setErrorCallback(ErrorCallback errorCallback) {
    this.errorCallback = errorCallback;
  }

  public void onFrame(VideoFrame frame) {
    synchronized(this.statisticsLock) {
      ++this.framesReceived;
    }

    boolean dropOldFrame;
    synchronized(this.handlerLock) {
      if (this.renderThreadHandler == null) {
        this.logD("Dropping frame - Not initialized or already released.");
        return;
      }

      synchronized(this.frameLock) {
        dropOldFrame = this.pendingFrame != null;
        if (dropOldFrame) {
          this.pendingFrame.release();
        }

        this.pendingFrame = frame;
        this.pendingFrame.retain();
        this.renderThreadHandler.post(this::renderFrameOnRenderThread);
      }
    }

    if (dropOldFrame) {
      synchronized(this.statisticsLock) {
        ++this.framesDropped;
      }
    }

  }

  public void releaseEglSurface(Runnable completionCallback) {
    this.eglSurfaceCreationRunnable.setSurface(null);
    synchronized(this.handlerLock) {
      if (this.renderThreadHandler != null) {
        this.renderThreadHandler.removeCallbacks(this.eglSurfaceCreationRunnable);
        this.renderThreadHandler.postAtFrontOfQueue(() -> {
          if (this.eglBase != null) {
            this.eglBase.detachCurrent();
            this.eglBase.releaseSurface();
          }

          completionCallback.run();
        });
        return;
      }
    }

    completionCallback.run();
  }

  private void postToRenderThread(Runnable runnable) {
    synchronized(this.handlerLock) {
      if (this.renderThreadHandler != null) {
        this.renderThreadHandler.post(runnable);
      }
    }
  }

  private void clearSurfaceOnRenderThread(float r, float g, float b, float a) {
    if (this.eglBase != null && this.eglBase.hasSurface()) {
      this.logD("clearSurface");
      GLES20.glClearColor(r, g, b, a);
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
      eglBase.swapBuffers();
    }
  }

  public void clearImage() {
    this.clearImage(0, 0, 0, 0);
  }

  public void clearImage(float r, float g, float b, float a) {
    synchronized(this.handlerLock) {
      if (this.renderThreadHandler != null) {
        this.renderThreadHandler.postAtFrontOfQueue(() -> {
          this.clearSurfaceOnRenderThread(r, g, b, a);
        });
      }
    }
  }

  private void renderFrameOnRenderThread() {
    VideoFrame frame;
    synchronized(this.frameLock) {
      if (this.pendingFrame == null) {
        return;
      }
      frame = this.pendingFrame;
      this.pendingFrame = null;
    }

    if (this.eglBase != null && this.eglBase.hasSurface()) {
      boolean shouldRenderFrame;
      synchronized(this.fpsReductionLock) {
        if (this.minRenderPeriodNs == Long.MAX_VALUE) {
          shouldRenderFrame = false;
        } else if (this.minRenderPeriodNs <= 0L) {
          shouldRenderFrame = true;
        } else {
          long currentTimeNs = System.nanoTime();
          if (currentTimeNs < this.nextFrameTimeNs) {
            this.logD("Skipping frame rendering - fps reduction is active.");
            shouldRenderFrame = false;
          } else {
            this.nextFrameTimeNs += this.minRenderPeriodNs;
            this.nextFrameTimeNs = Math.max(this.nextFrameTimeNs, currentTimeNs);
            shouldRenderFrame = true;
          }
        }
      }

      final long startTimeNs = System.nanoTime();
      final float frameAspectRatio = frame.getRotatedWidth() / (float) frame.getRotatedHeight();
      float drawnAspectRatio;
      synchronized(this.layoutLock) {
        drawnAspectRatio = layoutAspectRatio != 0f ? layoutAspectRatio : frameAspectRatio;
      }

      float scaleX;
      float scaleY;

      if (frameAspectRatio > drawnAspectRatio) {
        scaleX = drawnAspectRatio / frameAspectRatio;
        scaleY = 1f;
      } else {
        scaleX = 1f;
        scaleY = frameAspectRatio / drawnAspectRatio;
      }

      drawMatrix.reset();
      drawMatrix.preTranslate(0.5f, 0.5f);
      drawMatrix.preScale(mirrorHorizontally ? -1f : 1f, mirrorVertically ? -1f : 1f);
      drawMatrix.preScale(scaleX, scaleY);
      drawMatrix.preTranslate(-0.5f, -0.5f);

      try {
        if (shouldRenderFrame) {
          GLES20.glClearColor(0 /* red */, 0 /* green */, 0 /* blue */, 0 /* alpha */);
          GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
          frameDrawer.drawFrame(frame, drawer, drawMatrix, 0 /* viewportX */, 0 /* viewportY */, eglBase.surfaceWidth(), eglBase.surfaceHeight());
          long swapBuffersStartTimeNs = System.nanoTime();
          if (this.usePresentationTimeStamp) {
            this.eglBase.swapBuffers(frame.getTimestampNs());
          } else {
            this.eglBase.swapBuffers();
          }

          long currentTimeNs = System.nanoTime();
          synchronized(this.statisticsLock) {
            ++this.framesRendered;
            this.renderTimeNs += currentTimeNs - startTimeNs;
            this.renderSwapBufferTimeNs += currentTimeNs - swapBuffersStartTimeNs;
          }
        }

        this.notifyCallbacks(frame, shouldRenderFrame);
      } catch (GlUtil.GlOutOfMemoryException var25) {
        this.logE("Error while drawing frame", var25);
        ErrorCallback errorCallback = this.errorCallback;
        if (errorCallback != null) {
          errorCallback.onGlOutOfMemory();
        }

        this.drawer.release();
        this.frameDrawer.release();
        this.bitmapTextureFramebuffer.release();
      } finally {
        frame.release();
      }

    } else {
      this.logD("Dropping frame - No surface");
      frame.release();
    }
  }

  private void notifyCallbacks(VideoFrame frame, boolean wasRendered) {
    if (!this.frameListeners.isEmpty()) {
      drawMatrix.reset();
      drawMatrix.preTranslate(0.5f, 0.5f);
      drawMatrix.preScale(mirrorHorizontally ? -1f : 1f, mirrorVertically ? -1f : 1f);
      drawMatrix.preScale(1f, -1f); // We want the output to be upside down for Bitmap.
      drawMatrix.preTranslate(-0.5f, -0.5f);
      Iterator<FrameListenerAndParams> it = this.frameListeners.iterator();

      while (it.hasNext()) {
        FrameListenerAndParams listenerAndParams = it.next();
        if (!wasRendered && listenerAndParams.applyFpsReduction) {
          continue;
        }
        it.remove();
        final int scaledWidth = (int) (listenerAndParams.scale * frame.getRotatedWidth());
        final int scaledHeight = (int) (listenerAndParams.scale * frame.getRotatedHeight());

        if (scaledWidth == 0 || scaledHeight == 0) {
          listenerAndParams.listener.onFrame(null);
          continue;
        }

        bitmapTextureFramebuffer.setSize(scaledWidth, scaledHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, this.bitmapTextureFramebuffer.getFrameBufferId());
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, bitmapTextureFramebuffer.getTextureId(), 0);
        GLES20.glClearColor(0 /* red */, 0 /* green */, 0 /* blue */, 0 /* alpha */);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        frameDrawer.drawFrame(frame, listenerAndParams.drawer, drawMatrix, 0 /* viewportX */, 0 /* viewportY */, scaledWidth, scaledHeight);
        final ByteBuffer bitmapBuffer = ByteBuffer.allocateDirect(scaledWidth * scaledHeight * 4);
        GLES20.glViewport(0, 0, scaledWidth, scaledHeight);
        GLES20.glReadPixels(0, 0, scaledWidth, scaledHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bitmapBuffer);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GlUtil.checkNoGLES2Error("EglRenderer.notifyCallbacks");
        final Bitmap bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(bitmapBuffer);
        listenerAndParams.listener.onFrame(bitmap);
      }
    }
  }

  private String averageTimeAsString(long sumTimeNs, int count) {
    return (count <= 0) ? "NA" : TimeUnit.NANOSECONDS.toMicros(sumTimeNs / count) + " us";
  }

  private void logStatistics() {
    DecimalFormat fpsFormat = new DecimalFormat("#.0");
    long currentTimeNs = System.nanoTime();
    synchronized(this.statisticsLock) {
      long elapsedTimeNs = currentTimeNs - this.statisticsStartTimeNs;
      if (elapsedTimeNs <= 0 || (minRenderPeriodNs == Long.MAX_VALUE && framesReceived == 0)) {
        return;
      }
      final float renderFps = framesRendered * TimeUnit.SECONDS.toNanos(1) / (float) elapsedTimeNs;
      logD("Duration: " + TimeUnit.NANOSECONDS.toMillis(elapsedTimeNs) + " ms."
              + " Frames received: " + framesReceived + "."
              + " Dropped: " + framesDropped + "."
              + " Rendered: " + framesRendered + "."
              + " Render fps: " + fpsFormat.format(renderFps) + "."
              + " Average render time: " + averageTimeAsString(renderTimeNs, framesRendered) + "."
              + " Average swapBuffer time: "
              + averageTimeAsString(renderSwapBufferTimeNs, framesRendered) + ".");
      resetStatistics(currentTimeNs);
    }
  }

  private void logE(String string, Throwable e) {
    Logging.e(TAG, this.name + string, e);
  }

  private void logD(String string) {
    Logging.d(TAG, this.name + string);
  }

  private void logW(String string) {
    Logging.w(TAG, this.name + string);
  }

  private class EglSurfaceCreation implements Runnable {
    private Object surface;

    private EglSurfaceCreation() {
    }

    public synchronized void setSurface(Object surface) {
      this.surface = surface;
    }

    public synchronized void run() {
      if (surface != null && eglBase != null && !eglBase.hasSurface()) {
        if (surface instanceof Surface) {
          eglBase.createSurface((Surface) surface);
        } else if (surface instanceof SurfaceTexture) {
          eglBase.createSurface((SurfaceTexture) surface);
        } else {
          throw new IllegalStateException("Invalid surface: " + surface);
        }
        eglBase.makeCurrent();
        // Necessary for YUV frames with odd width.
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
      }

    }
  }

  private static class HandlerWithExceptionCallback extends Handler {
    private final Runnable exceptionCallback;

    public HandlerWithExceptionCallback(Looper looper, Runnable exceptionCallback) {
      super(looper);
      this.exceptionCallback = exceptionCallback;
    }

    public void dispatchMessage(Message msg) {
      try {
        super.dispatchMessage(msg);
      } catch (Exception var3) {
        Logging.e(TAG, "Exception on EglRenderer thread", var3);
        this.exceptionCallback.run();
        throw var3;
      }
    }
  }

  public interface FrameListener {
    void onFrame(Bitmap frame);
  }

  public interface ErrorCallback {
    void onGlOutOfMemory();
  }

  private static class FrameListenerAndParams {
    public final FrameListener listener;
    public final float scale;
    public final RendererCommon.GlDrawer drawer;
    public final boolean applyFpsReduction;

    public FrameListenerAndParams(FrameListener listener, float scale, RendererCommon.GlDrawer drawer, boolean applyFpsReduction) {
      this.listener = listener;
      this.scale = scale;
      this.drawer = drawer;
      this.applyFpsReduction = applyFpsReduction;
    }
  }
}

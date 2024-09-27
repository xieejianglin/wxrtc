package org.webrtc;

import android.view.SurfaceHolder;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class SurfaceEglRenderer extends EglRenderer implements SurfaceHolder.Callback {
  private static final String TAG = "SurfaceEglRenderer";
  // Callback for reporting renderer events. Read-only after initialization so no lock required.
  private RendererCommon.RendererEvents rendererEvents;
  private final Object layoutLock = new Object();
  private boolean isRenderingPaused;
  private boolean isFirstFrameRendered;
  private int rotatedFrameWidth;
  private int rotatedFrameHeight;
  private int frameRotation;
  /**
   * In order to render something, you must first call init().
   */
  public SurfaceEglRenderer(String name) {
    super(name);
  }
  /**
   * Initialize this class, sharing resources with `sharedContext`. The custom `drawer` will be used
   * for drawing frames on the EGLSurface. This class is responsible for calling release() on
   * `drawer`. It is allowed to call init() to reinitialize the renderer after a previous
   * init()/release() cycle.
   */
  public void init(final EglBase.Context sharedContext,
                   RendererCommon.RendererEvents rendererEvents, final int[] configAttributes,
                   RendererCommon.GlDrawer drawer) {
    ThreadUtils.checkIsOnMainThread();
    this.rendererEvents = rendererEvents;
    synchronized (layoutLock) {
      isFirstFrameRendered = false;
      rotatedFrameWidth = 0;
      rotatedFrameHeight = 0;
      frameRotation = 0;
    }
    super.init(sharedContext, configAttributes, drawer);
  }
  @Override
  public void init(final EglBase.Context sharedContext, final int[] configAttributes,
                   RendererCommon.GlDrawer drawer) {
    init(sharedContext, null /* rendererEvents */, configAttributes, drawer);
  }
  /**
   * Limit render framerate.
   *
   * @param fps Limit render framerate to this value, or use Float.POSITIVE_INFINITY to disable fps
   *            reduction.
   */
  @Override
  public void setFpsReduction(float fps) {
    synchronized (layoutLock) {
      isRenderingPaused = fps == 0f;
    }
    super.setFpsReduction(fps);
  }
  @Override
  public void disableFpsReduction() {
    synchronized (layoutLock) {
      isRenderingPaused = false;
    }
    super.disableFpsReduction();
  }
  @Override
  public void pauseVideo() {
    synchronized (layoutLock) {
      isRenderingPaused = true;
    }
    super.pauseVideo();
  }
  // VideoSink interface.
  @Override
  public void onFrame(VideoFrame frame) {
    updateFrameDimensionsAndReportEvents(frame);
    super.onFrame(frame);
  }
  // SurfaceHolder.Callback interface.
  @Override
  public void surfaceCreated(final SurfaceHolder holder) {
    ThreadUtils.checkIsOnMainThread();
    createEglSurface(holder.getSurface());
  }
  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    ThreadUtils.checkIsOnMainThread();
    final CountDownLatch completionLatch = new CountDownLatch(1);
    releaseEglSurface(completionLatch::countDown);
    ThreadUtils.awaitUninterruptibly(completionLatch);
  }
  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    ThreadUtils.checkIsOnMainThread();
    logD("surfaceChanged: format: " + format + " size: " + width + "x" + height);
  }
  // Update frame dimensions and report any changes to `rendererEvents`.
  private void updateFrameDimensionsAndReportEvents(VideoFrame frame) {
    synchronized (layoutLock) {
      if (isRenderingPaused) {
        return;
      }
      if (!isFirstFrameRendered) {
        isFirstFrameRendered = true;
        logD("Reporting first rendered frame.");
        if (rendererEvents != null) {
          rendererEvents.onFirstFrameRendered();
        }
      }
      if (rotatedFrameWidth != frame.getRotatedWidth()
              || rotatedFrameHeight != frame.getRotatedHeight()
              || frameRotation != frame.getRotation()) {
        logD("Reporting frame resolution changed to " + frame.getBuffer().getWidth() + "x"
                + frame.getBuffer().getHeight() + " with rotation " + frame.getRotation());
        if (rendererEvents != null) {
          rendererEvents.onFrameResolutionChanged(
                  frame.getBuffer().getWidth(), frame.getBuffer().getHeight(), frame.getRotation());
        }
        rotatedFrameWidth = frame.getRotatedWidth();
        rotatedFrameHeight = frame.getRotatedHeight();
        frameRotation = frame.getRotation();
      }
    }
  }
  private void logD(String string) {
    Logging.d(TAG, name + ": " + string);
  }
}

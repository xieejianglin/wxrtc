package org.webrtc;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.view.Surface;
import androidx.annotation.Nullable;

public class ScreenCapturerAndroid implements VideoCapturer, VideoSink {
  private static final int DISPLAY_FLAGS = 3;
  
  private static final int VIRTUAL_DISPLAY_DPI = 400;
  
  private final Intent mediaProjectionPermissionResultData;
  
  private final MediaProjection.Callback mediaProjectionCallback;
  
  private int width;
  
  private int height;
  
  @Nullable
  private VirtualDisplay virtualDisplay;
  
  @Nullable
  private SurfaceTextureHelper surfaceTextureHelper;
  
  @Nullable
  private CapturerObserver capturerObserver;
  
  private long numCapturedFrames;
  
  @Nullable
  private MediaProjection mediaProjection;
  
  private boolean isDisposed;
  
  @Nullable
  private MediaProjectionManager mediaProjectionManager;
  
  public ScreenCapturerAndroid(Intent mediaProjectionPermissionResultData, MediaProjection.Callback mediaProjectionCallback) {
    this.mediaProjectionPermissionResultData = mediaProjectionPermissionResultData;
    this.mediaProjectionCallback = mediaProjectionCallback;
  }
  
  private void checkNotDisposed() {
    if (this.isDisposed)
      throw new RuntimeException("capturer is disposed."); 
  }
  
  @Nullable
  public MediaProjection getMediaProjection() {
    return this.mediaProjection;
  }
  
  public synchronized void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver capturerObserver) {
    checkNotDisposed();
    if (capturerObserver == null)
      throw new RuntimeException("capturerObserver not set."); 
    this.capturerObserver = capturerObserver;
    if (surfaceTextureHelper == null)
      throw new RuntimeException("surfaceTextureHelper not set."); 
    this.surfaceTextureHelper = surfaceTextureHelper;
    this.mediaProjectionManager = (MediaProjectionManager)applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
  }
  
  public synchronized void startCapture(int width, int height, int ignoredFramerate) {
    checkNotDisposed();
    this.width = width;
    this.height = height;
    this.mediaProjection = this.mediaProjectionManager.getMediaProjection(-1, this.mediaProjectionPermissionResultData);
    this.mediaProjection.registerCallback(this.mediaProjectionCallback, this.surfaceTextureHelper.getHandler());
    updateVirtualDisplay();
    this.capturerObserver.onCapturerStarted(true);
    this.surfaceTextureHelper.startListening(this);
  }
  
  public synchronized void stopCapture() {
    checkNotDisposed();
    ThreadUtils.invokeAtFrontUninterruptibly(this.surfaceTextureHelper.getHandler(), new Runnable() {
          public void run() {
            ScreenCapturerAndroid.this.surfaceTextureHelper.stopListening();
            ScreenCapturerAndroid.this.capturerObserver.onCapturerStopped();
            if (ScreenCapturerAndroid.this.virtualDisplay != null) {
              ScreenCapturerAndroid.this.virtualDisplay.release();
              ScreenCapturerAndroid.this.virtualDisplay = null;
            } 
            if (ScreenCapturerAndroid.this.mediaProjection != null) {
              ScreenCapturerAndroid.this.mediaProjection.unregisterCallback(ScreenCapturerAndroid.this.mediaProjectionCallback);
              ScreenCapturerAndroid.this.mediaProjection.stop();
              ScreenCapturerAndroid.this.mediaProjection = null;
            } 
          }
        });
  }
  
  public synchronized void dispose() {
    this.isDisposed = true;
  }
  
  public synchronized void changeCaptureFormat(int width, int height, int ignoredFramerate) {
    checkNotDisposed();
    this.width = width;
    this.height = height;
    if (this.virtualDisplay == null)
      return; 
    ThreadUtils.invokeAtFrontUninterruptibly(this.surfaceTextureHelper
        .getHandler(), this::updateVirtualDisplay);
  }
  
  private void updateVirtualDisplay() {
    this.surfaceTextureHelper.setTextureSize(this.width, this.height);
    if (this.virtualDisplay == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      createVirtualDisplay();
    } else {
      this.virtualDisplay.resize(this.width, this.height, VIRTUAL_DISPLAY_DPI);
      this.virtualDisplay.setSurface(new Surface(this.surfaceTextureHelper.getSurfaceTexture()));
    } 
  }
  
  private void createVirtualDisplay() {
    if (this.virtualDisplay != null)
      this.virtualDisplay.release(); 
    this.virtualDisplay = this.mediaProjection.createVirtualDisplay("WebRTC_ScreenCapture", this.width, this.height, VIRTUAL_DISPLAY_DPI, DISPLAY_FLAGS, new Surface(this.surfaceTextureHelper
          .getSurfaceTexture()), null, null);
  }
  
  public void onFrame(VideoFrame frame) {
    this.numCapturedFrames++;
    this.capturerObserver.onFrameCaptured(frame);
  }
  
  public boolean isScreencast() {
    return true;
  }
  
  public long getNumCapturedFrames() {
    return this.numCapturedFrames;
  }
}

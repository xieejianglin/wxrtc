package org.webrtc;

import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.util.Objects;

public class EglThread {
  private final ReleaseMonitor releaseMonitor;
  
  private final Handler handler;
  
  private final EglBase.EglConnection eglConnection;
  
  public static EglThread create(@Nullable ReleaseMonitor releaseMonitor, @Nullable EglBase.Context sharedContext, int[] configAttributes) {
    EglBase.EglConnection eglConnection;
    HandlerThread renderThread = new HandlerThread("EglThread");
    renderThread.start();
    Handler handler = new Handler(renderThread.getLooper());
    if (sharedContext == null) {
      eglConnection = EglBase.EglConnection.createEgl10(configAttributes);
    } else {
      eglConnection = EglBase.EglConnection.create(sharedContext, configAttributes);
    } 
    return new EglThread(
        (releaseMonitor != null) ? releaseMonitor : (eglThread -> true), handler, eglConnection);
  }
  
  @VisibleForTesting
  EglThread(ReleaseMonitor releaseMonitor, Handler handler, EglBase.EglConnection eglConnection) {
    this.releaseMonitor = releaseMonitor;
    this.handler = handler;
    this.eglConnection = eglConnection;
  }
  
  public void release() {
    if (!this.releaseMonitor.onRelease(this))
      return; 
    Objects.requireNonNull(this.eglConnection);
    this.handler.post(this.eglConnection::release);
    this.handler.getLooper().quitSafely();
  }
  
  public EglBase createEglBaseWithSharedConnection() {
    return EglBase.create(this.eglConnection);
  }
  
  public Handler getHandler() {
    return this.handler;
  }
  
  public static interface ReleaseMonitor {
    boolean onRelease(EglThread param1EglThread);
  }
}

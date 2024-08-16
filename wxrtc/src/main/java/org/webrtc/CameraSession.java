package org.webrtc;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Matrix;
import android.os.Build;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

interface CameraSession {
  void stop();

  boolean isZoomSupported();

  int getMaxZoom();

  int getZoom();

  void setZoom(int zoom);
  
  public static interface Events {
    void onCameraOpening();
    
    void onCameraError(CameraSession param1CameraSession, String param1String);
    
    void onCameraDisconnected(CameraSession param1CameraSession);
    
    void onCameraClosed(CameraSession param1CameraSession, String param1String);
    
    void onFrameCaptured(CameraSession param1CameraSession, VideoFrame param1VideoFrame);
  }
  
  public static interface CreateSessionCallback {
    void onDone(CameraSession param1CameraSession);
    
    void onFailure(CameraSession.FailureType param1FailureType, String param1String);
  }
  
  public enum FailureType {
    ERROR, DISCONNECTED;
  }

  static int getDeviceOrientation(Context context) {
    WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
    int rotation = wm.getDefaultDisplay().getRotation();

    switch (rotation) {
      case Surface.ROTATION_90:
        return 90;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_270:
        return 270;
      default:
        return Surface.ROTATION_0;
    }
  }
  
  static VideoFrame.TextureBuffer createTextureBufferWithModifiedTransformMatrix(TextureBufferImpl buffer, boolean mirror, int rotation) {
    Matrix transformMatrix = new Matrix();
    transformMatrix.preTranslate(0.5F, 0.5F);
    if (mirror)
      transformMatrix.preScale(-1.0F, 1.0F); 
    transformMatrix.preRotate(rotation);
    transformMatrix.preTranslate(-0.5F, -0.5F);
    return buffer.applyTransformMatrix(transformMatrix, buffer.getWidth(), buffer.getHeight());
  }
}

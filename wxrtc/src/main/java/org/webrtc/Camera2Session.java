package org.webrtc;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.util.Range;
import android.view.Surface;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

class Camera2Session implements CameraSession {
  private static final String TAG = "Camera2Session";
  
  private static final Histogram camera2StartTimeMsHistogram = Histogram.createCounts("WebRTC.Android.Camera2.StartTimeMs", 1, 10000, 50);
  
  private static final Histogram camera2StopTimeMsHistogram = Histogram.createCounts("WebRTC.Android.Camera2.StopTimeMs", 1, 10000, 50);
  
  private static final Histogram camera2ResolutionHistogram = Histogram.createEnumeration("WebRTC.Android.Camera2.Resolution", CameraEnumerationAndroid.COMMON_RESOLUTIONS
      .size());
  
  private final Handler cameraThreadHandler;
  
  private final CameraSession.CreateSessionCallback callback;
  
  private final CameraSession.Events events;
  
  private final Context applicationContext;
  
  private final CameraManager cameraManager;
  
  private final SurfaceTextureHelper surfaceTextureHelper;
  
  private final String cameraId;
  
  private final int width;
  
  private final int height;
  
  private final int framerate;

  private CaptureRequest.Builder captureRequestBuilder;
  
  private CameraCharacteristics cameraCharacteristics;
  
  private int cameraOrientation;
  
  private boolean isCameraFrontFacing;
  
  private int fpsUnitFactor;
  
  private CameraEnumerationAndroid.CaptureFormat captureFormat;
  
  @Nullable
  private CameraDevice cameraDevice;
  
  @Nullable
  private Surface surface;
  
  @Nullable
  private CameraCaptureSession captureSession;
  
  private enum SessionState {
    RUNNING, STOPPED;
  }
  
  private SessionState state = SessionState.RUNNING;
  
  private boolean firstFrameReported;
  
  private final long constructionTimeNs;

  private int mZoom;
  private float mStepWidth; // 每次改变的宽度大小
  private float mStepHeight; // 每次改变的高度大小
  
  private class CameraStateCallback extends CameraDevice.StateCallback {
    private String getErrorDescription(int errorCode) {
      switch (errorCode) {
        case 4:
          return "Camera device has encountered a fatal error.";
        case 3:
          return "Camera device could not be opened due to a device policy.";
        case 1:
          return "Camera device is in use already.";
        case 5:
          return "Camera service has encountered a fatal error.";
        case 2:
          return "Camera device could not be opened because there are too many other open camera devices.";
      } 
      return "Unknown camera error: " + errorCode;
    }
    
    public void onDisconnected(CameraDevice camera) {
      Camera2Session.this.checkIsOnCameraThread();
      boolean startFailure = (Camera2Session.this.captureSession == null && Camera2Session.this.state != Camera2Session.SessionState.STOPPED);
      Camera2Session.this.state = Camera2Session.SessionState.STOPPED;
      Camera2Session.this.stopInternal();
      if (startFailure) {
        Camera2Session.this.callback.onFailure(CameraSession.FailureType.DISCONNECTED, "Camera disconnected / evicted.");
      } else {
        Camera2Session.this.events.onCameraDisconnected(Camera2Session.this);
      } 
    }
    
    public void onError(CameraDevice camera, int errorCode) {
      Camera2Session.this.checkIsOnCameraThread();
      Camera2Session.this.reportError(getErrorDescription(errorCode));
    }
    
    public void onOpened(CameraDevice camera) {
      Camera2Session.this.checkIsOnCameraThread();
      Logging.d("Camera2Session", "Camera opened.");
      Camera2Session.this.cameraDevice = camera;
      Camera2Session.this.surfaceTextureHelper.setTextureSize(Camera2Session.this.captureFormat.width, Camera2Session.this.captureFormat.height);
      Camera2Session.this.surface = new Surface(Camera2Session.this.surfaceTextureHelper.getSurfaceTexture());
      try {
        camera.createCaptureSession(Arrays.asList(Camera2Session.this.surface), Camera2Session.this.new CaptureSessionCallback(), Camera2Session.this.cameraThreadHandler);
        Camera2Session.this.events.onCameraOpening();
      } catch (CameraAccessException e) {
        Camera2Session.this.reportError("Failed to create capture session. " + e);
        return;
      } 
    }
    
    public void onClosed(CameraDevice camera) {
      Camera2Session.this.checkIsOnCameraThread();
      Logging.d("Camera2Session", "Camera device closed.");
      Camera2Session.this.events.onCameraClosed(Camera2Session.this, camera.getId());
    }
  }
  
  private class CaptureSessionCallback extends CameraCaptureSession.StateCallback {
    public void onConfigureFailed(CameraCaptureSession session) {
      Camera2Session.this.checkIsOnCameraThread();
      session.close();
      Camera2Session.this.reportError("Failed to configure capture session.");
    }
    
    public void onConfigured(CameraCaptureSession session) {
      Camera2Session.this.checkIsOnCameraThread();
      Logging.d("Camera2Session", "Camera capture session configured.");
      Camera2Session.this.captureSession = session;
      try {
        captureRequestBuilder = Camera2Session.this.cameraDevice.createCaptureRequest(3);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range(
              Integer.valueOf(Camera2Session.this.captureFormat.framerate.min / Camera2Session.this.fpsUnitFactor), 
              Integer.valueOf(Camera2Session.this.captureFormat.framerate.max / Camera2Session.this.fpsUnitFactor)));
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, 
            Integer.valueOf(1));
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.valueOf(false));
        chooseStabilizationMode(captureRequestBuilder);
        chooseFocusMode(captureRequestBuilder);
        captureRequestBuilder.addTarget(Camera2Session.this.surface);
        session.setRepeatingRequest(captureRequestBuilder
            .build(), new Camera2Session.CameraCaptureCallback(), Camera2Session.this.cameraThreadHandler);
      } catch (CameraAccessException e) {
        Camera2Session.this.reportError("Failed to start capture request. " + e);
        return;
      } 
      Camera2Session.this.surfaceTextureHelper.startListening(frame -> {
            Camera2Session.this.checkIsOnCameraThread();
            if (Camera2Session.this.state != Camera2Session.SessionState.RUNNING) {
              Logging.d("Camera2Session", "Texture frame captured but camera is no longer running.");
              return;
            } 
            if (!Camera2Session.this.firstFrameReported) {
              Camera2Session.this.firstFrameReported = true;
              int startTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - Camera2Session.this.constructionTimeNs);
              Camera2Session.camera2StartTimeMsHistogram.addSample(startTimeMs);
            } 
            VideoFrame modifiedFrame = new VideoFrame(CameraSession.createTextureBufferWithModifiedTransformMatrix((TextureBufferImpl)frame.getBuffer(), Camera2Session.this.isCameraFrontFacing, -Camera2Session.this.cameraOrientation), Camera2Session.this.getFrameOrientation(), frame.getTimestampNs());
            Camera2Session.this.events.onFrameCaptured(Camera2Session.this, modifiedFrame);
            modifiedFrame.release();
          });
      Logging.d("Camera2Session", "Camera device successfully started.");
      Camera2Session.this.callback.onDone(Camera2Session.this);
    }
    
    private void chooseStabilizationMode(CaptureRequest.Builder captureRequestBuilder) {
      int[] availableOpticalStabilization = (int[])Camera2Session.this.cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
      if (availableOpticalStabilization != null)
        for (int mode : availableOpticalStabilization) {
          if (mode == 1) {
            captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, 
                Integer.valueOf(1));
            captureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 
                Integer.valueOf(0));
            Logging.d("Camera2Session", "Using optical stabilization.");
            return;
          } 
        }  
      int[] availableVideoStabilization = (int[])Camera2Session.this.cameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
      if (availableVideoStabilization != null)
        for (int mode : availableVideoStabilization) {
          if (mode == 1) {
            captureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 
                Integer.valueOf(1));
            captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, 
                Integer.valueOf(0));
            Logging.d("Camera2Session", "Using video stabilization.");
            return;
          } 
        }  
      Logging.d("Camera2Session", "Stabilization not available.");
    }
    
    private void chooseFocusMode(CaptureRequest.Builder captureRequestBuilder) {
      int[] availableFocusModes = (int[])Camera2Session.this.cameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
      for (int mode : availableFocusModes) {
        if (mode == 3) {
          captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, 
              Integer.valueOf(3));
          Logging.d("Camera2Session", "Using continuous video auto-focus.");
          return;
        } 
      } 
      Logging.d("Camera2Session", "Auto-focus is not available.");
    }
  }
  
  private static class CameraCaptureCallback extends CameraCaptureSession.CaptureCallback {
    public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
      Logging.d("Camera2Session", "Capture failed: " + failure);
    }
  }
  
  public static void create(CameraSession.CreateSessionCallback callback, CameraSession.Events events, Context applicationContext, CameraManager cameraManager, SurfaceTextureHelper surfaceTextureHelper, String cameraId, int width, int height, int framerate) {
    new Camera2Session(callback, events, applicationContext, cameraManager, surfaceTextureHelper, cameraId, width, height, framerate);
  }
  
  private Camera2Session(CameraSession.CreateSessionCallback callback, CameraSession.Events events, Context applicationContext, CameraManager cameraManager, SurfaceTextureHelper surfaceTextureHelper, String cameraId, int width, int height, int framerate) {
    Logging.d("Camera2Session", "Create new camera2 session on camera " + cameraId);
    this.constructionTimeNs = System.nanoTime();
    this.cameraThreadHandler = new Handler();
    this.callback = callback;
    this.events = events;
    this.applicationContext = applicationContext;
    this.cameraManager = cameraManager;
    this.surfaceTextureHelper = surfaceTextureHelper;
    this.cameraId = cameraId;
    this.width = width;
    this.height = height;
    this.framerate = framerate;
    start();
  }
  
  private void start() {
    checkIsOnCameraThread();
    Logging.d("Camera2Session", "start");
    try {
      this.cameraCharacteristics = this.cameraManager.getCameraCharacteristics(this.cameraId);
    } catch (CameraAccessException|IllegalArgumentException e) {
      reportError("getCameraCharacteristics(): " + e.getMessage());
      return;
    } 
    this.cameraOrientation = ((Integer)this.cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)).intValue();
    this.isCameraFrontFacing = (((Integer)this.cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)).intValue() == 0);
    findCaptureFormat();
    if (this.captureFormat == null)
      return; 
    openCamera();
  }
  
  private void findCaptureFormat() {
    checkIsOnCameraThread();
    Range[] arrayOfRange = (Range[])this.cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
    this.fpsUnitFactor = Camera2Enumerator.getFpsUnitFactor((Range<Integer>[])arrayOfRange);
    List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> framerateRanges = Camera2Enumerator.convertFramerates((Range<Integer>[])arrayOfRange, this.fpsUnitFactor);
    List<Size> sizes = Camera2Enumerator.getSupportedSizes(this.cameraCharacteristics);
    Logging.d("Camera2Session", "Available preview sizes: " + sizes);
    Logging.d("Camera2Session", "Available fps ranges: " + framerateRanges);
    if (framerateRanges.isEmpty() || sizes.isEmpty()) {
      reportError("No supported capture formats.");
      return;
    } 
    CameraEnumerationAndroid.CaptureFormat.FramerateRange bestFpsRange = CameraEnumerationAndroid.getClosestSupportedFramerateRange(framerateRanges, this.framerate);
    Size bestSize = CameraEnumerationAndroid.getClosestSupportedSize(sizes, this.width, this.height);
    CameraEnumerationAndroid.reportCameraResolution(camera2ResolutionHistogram, bestSize);
    this.captureFormat = new CameraEnumerationAndroid.CaptureFormat(bestSize.width, bestSize.height, bestFpsRange);
    Logging.d("Camera2Session", "Using capture format: " + this.captureFormat);
  }
  
  private void openCamera() {
    checkIsOnCameraThread();
    Logging.d("Camera2Session", "Opening camera " + this.cameraId);
//    this.events.onCameraOpening();
    try {
      initZoomParameter();
      this.cameraManager.openCamera(this.cameraId, new CameraStateCallback(), this.cameraThreadHandler);
    } catch (CameraAccessException|IllegalArgumentException|SecurityException e) {
      reportError("Failed to open camera: " + e);
      return;
    } 
  }
  
  public void stop() {
    Logging.d("Camera2Session", "Stop camera2 session on camera " + this.cameraId);
    checkIsOnCameraThread();
    if (this.state != SessionState.STOPPED) {
      long stopStartTime = System.nanoTime();
      this.state = SessionState.STOPPED;
      stopInternal();
      int stopTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartTime);
      camera2StopTimeMsHistogram.addSample(stopTimeMs);
    } 
  }

  @Override
  public boolean isZoomSupported() {
    float max_digital_zoom = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    return max_digital_zoom > 0;
  }

  @Override
  public int getMaxZoom(){
    float max_digital_zoom = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    return (int) max_digital_zoom;
  }

  @Override
  public int getZoom(){
    if (cameraDevice == null || cameraCharacteristics == null) {
      return 0;
    }
    return mZoom;
  }

  @Override
  public void setZoom(int zoom){
    if (cameraDevice == null || cameraCharacteristics == null || captureRequestBuilder == null) {
      return;
    }
    mZoom = zoom;
    int maxZoom = getMaxZoom();
    if (zoom > maxZoom) { // 放大
      mZoom = maxZoom;
    } else if (zoom < 0) { // 缩小
      mZoom = 0;
    }

    Rect rect = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
    int cropW = (int) (mStepWidth * mZoom);
    int cropH = (int) (mStepHeight * mZoom);
    Rect zoomRect = new Rect(rect.left + cropW, rect.top + cropH, rect.right - cropW, rect.bottom - cropH);
    Logging.d(TAG, "zoomRect: " + zoomRect);
    captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
    startPreview(); // 需要重新 start preview 才能生效
  }

  private void initZoomParameter() {
    Rect rect = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
    Logging.d(TAG, "sensor_info_active_array_size: " + rect);
    // max_digital_zoom 表示 active_rect 除以 crop_rect 的最大值
    float max_digital_zoom = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    Logging.d(TAG, "max_digital_zoom: " + max_digital_zoom);
    // crop_rect的最小宽高
    float minWidth = rect.width() / max_digital_zoom;
    float minHeight = rect.height() / max_digital_zoom;
    // 因为缩放时两边都要变化，所以要除以2
    mStepWidth = (rect.width() - minWidth) / max_digital_zoom / 2;
    mStepHeight = (rect.height() - minHeight) / max_digital_zoom / 2;
  }

  public void startPreview() {
    Logging.v(TAG, "startPreview");
    if (captureSession == null || captureRequestBuilder == null) {
      Logging.w(TAG, "startPreview: mCaptureSession or mPreviewRequestBuilder is null");
      return;
    }
    try {
      captureSession.setRepeatingRequest(captureRequestBuilder.build(), new Camera2Session.CameraCaptureCallback(), Camera2Session.this.cameraThreadHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }
  
  private void stopInternal() {
    Logging.d("Camera2Session", "Stop internal");
    checkIsOnCameraThread();
    this.surfaceTextureHelper.stopListening();
    if (this.captureSession != null) {
      this.captureSession.close();
      this.captureSession = null;
    } 
    if (this.surface != null) {
      this.surface.release();
      this.surface = null;
    } 
    if (this.cameraDevice != null) {
      this.cameraDevice.close();
      this.cameraDevice = null;
    } 
    Logging.d("Camera2Session", "Stop done");
  }
  
  private void reportError(String error) {
    checkIsOnCameraThread();
    Logging.e("Camera2Session", "Error: " + error);
    boolean startFailure = (this.captureSession == null && this.state != SessionState.STOPPED);
    this.state = SessionState.STOPPED;
    stopInternal();
    if (startFailure) {
      this.callback.onFailure(CameraSession.FailureType.ERROR, error);
    } else {
      this.events.onCameraError(this, error);
    } 
  }
  
  private int getFrameOrientation() {
    int rotation = CameraSession.getDeviceOrientation(this.applicationContext);
    if (!this.isCameraFrontFacing)
      rotation = 360 - rotation; 
    return (this.cameraOrientation + rotation) % 360;
  }
  
  private void checkIsOnCameraThread() {
    if (Thread.currentThread() != this.cameraThreadHandler.getLooper().getThread())
      throw new IllegalStateException("Wrong thread"); 
  }
}

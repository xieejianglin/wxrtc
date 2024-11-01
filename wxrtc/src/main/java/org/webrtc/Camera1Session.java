package org.webrtc;

import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

class Camera1Session implements CameraSession {
  private static final String TAG = "Camera1Session";
  
  private static final int NUMBER_OF_CAPTURE_BUFFERS = 3;
  
  private static final Histogram camera1StartTimeMsHistogram = Histogram.createCounts("WebRTC.Android.Camera1.StartTimeMs", 1, 10000, 50);
  
  private static final Histogram camera1StopTimeMsHistogram = Histogram.createCounts("WebRTC.Android.Camera1.StopTimeMs", 1, 10000, 50);
  
  private static final Histogram camera1ResolutionHistogram = Histogram.createEnumeration("WebRTC.Android.Camera1.Resolution", CameraEnumerationAndroid.COMMON_RESOLUTIONS.size());
  
  private final Handler cameraThreadHandler;
  
  private final CameraSession.Events events;
  
  private final boolean captureToTexture;
  
  private final Context applicationContext;
  
  private final SurfaceTextureHelper surfaceTextureHelper;
  
  private final int cameraId;
  
  private final Camera camera;
  
  private final Camera.CameraInfo info;
  
  private final CameraEnumerationAndroid.CaptureFormat captureFormat;
  
  private final long constructionTimeNs;
  
  private SessionState state;
  
  private boolean firstFrameReported;
  
  private enum SessionState {
    RUNNING, STOPPED;
  }
  
  public static void create(CameraSession.CreateSessionCallback callback, CameraSession.Events events, boolean captureToTexture, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, String cameraName, int width, int height, int framerate) {
    int cameraId;
    Camera camera;
    CameraEnumerationAndroid.CaptureFormat captureFormat;
    long constructionTimeNs = System.nanoTime();
    Logging.d(TAG, "Open camera " + cameraName);
    events.onCameraOpening();
    try {
      cameraId = Camera1Enumerator.getCameraIndex(cameraName);
    } catch (IllegalArgumentException e) {
      callback.onFailure(CameraSession.FailureType.ERROR, e.getMessage());
      return;
    } 
    try {
      camera = Camera.open(cameraId);
    } catch (RuntimeException e) {
      callback.onFailure(CameraSession.FailureType.ERROR, e.getMessage());
      return;
    } 
    if (camera == null) {
      callback.onFailure(CameraSession.FailureType.ERROR, "Camera.open returned null for camera id = " + cameraId);
      return;
    } 
    try {
      camera.setPreviewTexture(surfaceTextureHelper.getSurfaceTexture());
    } catch (IOException|RuntimeException e) {
      camera.release();
      callback.onFailure(CameraSession.FailureType.ERROR, e.getMessage());
      return;
    } 
    Camera.CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(cameraId, info);
    try {
      Camera.Parameters parameters = camera.getParameters();
      captureFormat = findClosestCaptureFormat(parameters, width, height, framerate);
      Size pictureSize = findClosestPictureSize(parameters, width, height);
      updateCameraParameters(camera, parameters, captureFormat, pictureSize, captureToTexture);
    } catch (RuntimeException e) {
      camera.release();
      callback.onFailure(CameraSession.FailureType.ERROR, e.getMessage());
      return;
    } 
    if (!captureToTexture) {
      int frameSize = captureFormat.frameSize();
      for (int i = 0; i < NUMBER_OF_CAPTURE_BUFFERS; i++) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(frameSize);
        camera.addCallbackBuffer(buffer.array());
      } 
    } 
    try {
      camera.setDisplayOrientation(0);
    } catch (RuntimeException e) {
      camera.release();
      callback.onFailure(CameraSession.FailureType.ERROR, e.getMessage());
      return;
    } 
    callback.onDone(new Camera1Session(events, captureToTexture, applicationContext, surfaceTextureHelper, cameraId, camera, info, captureFormat, constructionTimeNs));
  }
  
  private static void updateCameraParameters(Camera camera, Camera.Parameters parameters, CameraEnumerationAndroid.CaptureFormat captureFormat, Size pictureSize, boolean captureToTexture) {
    List<String> focusModes = parameters.getSupportedFocusModes();
    parameters.setPreviewFpsRange(captureFormat.framerate.min, captureFormat.framerate.max);
    parameters.setPreviewSize(captureFormat.width, captureFormat.height);
    parameters.setPictureSize(pictureSize.width, pictureSize.height);
    if (!captureToTexture) {
      Objects.requireNonNull(captureFormat);
      parameters.setPreviewFormat(17);
    } 
    if (parameters.isVideoStabilizationSupported())
      parameters.setVideoStabilization(true); 
    if (focusModes != null && focusModes.contains("continuous-video"))
      parameters.setFocusMode("continuous-video"); 
    camera.setParameters(parameters);
  }
  
  private static CameraEnumerationAndroid.CaptureFormat findClosestCaptureFormat(Camera.Parameters parameters, int width, int height, int framerate) {
    List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> supportedFramerates = Camera1Enumerator.convertFramerates(parameters.getSupportedPreviewFpsRange());
    Logging.d(TAG, "Available fps ranges: " + supportedFramerates);
    CameraEnumerationAndroid.CaptureFormat.FramerateRange fpsRange = CameraEnumerationAndroid.getClosestSupportedFramerateRange(supportedFramerates, framerate);
    Size previewSize = CameraEnumerationAndroid.getClosestSupportedSize(
        Camera1Enumerator.convertSizes(parameters.getSupportedPreviewSizes()), width, height);
    CameraEnumerationAndroid.reportCameraResolution(camera1ResolutionHistogram, previewSize);
    return new CameraEnumerationAndroid.CaptureFormat(previewSize.width, previewSize.height, fpsRange);
  }
  
  private static Size findClosestPictureSize(Camera.Parameters parameters, int width, int height) {
    return CameraEnumerationAndroid.getClosestSupportedSize(
        Camera1Enumerator.convertSizes(parameters.getSupportedPictureSizes()), width, height);
  }
  
  private Camera1Session(CameraSession.Events events, boolean captureToTexture, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, int cameraId, Camera camera, Camera.CameraInfo info, CameraEnumerationAndroid.CaptureFormat captureFormat, long constructionTimeNs) {
    Logging.d(TAG, "Create new camera1 session on camera " + cameraId);
    this.cameraThreadHandler = new Handler();
    this.events = events;
    this.captureToTexture = captureToTexture;
    this.applicationContext = applicationContext;
    this.surfaceTextureHelper = surfaceTextureHelper;
    this.cameraId = cameraId;
    this.camera = camera;
    this.info = info;
    this.captureFormat = captureFormat;
    this.constructionTimeNs = constructionTimeNs;
    surfaceTextureHelper.setTextureSize(captureFormat.width, captureFormat.height);
    startCapturing();
  }
  
  public void stop() {
    Logging.d(TAG, "Stop camera1 session on camera " + this.cameraId);
    checkIsOnCameraThread();
    if (this.state != SessionState.STOPPED) {
      long stopStartTime = System.nanoTime();
      stopInternal();
      int stopTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartTime);
      camera1StopTimeMsHistogram.addSample(stopTimeMs);
    } 
  }

  @Override
  public boolean isZoomSupported() {
    if (camera != null) {
      return camera.getParameters().isZoomSupported();
    }
    return false;
  }

  @Override
  public int getMaxZoom(){
    if (camera != null) {
      return camera.getParameters().getMaxZoom();
    }
    return 0;
  }

  @Override
  public int getZoom(){
    if (camera != null) {
      return camera.getParameters().getZoom();
    }
    return 0;
  }

  @Override
  public void setZoom(int zoom){
    if (!isZoomSupported()) {
      return;
    }
    int mZoom = zoom;
    int maxZoom = getMaxZoom();
    if (zoom > maxZoom) { // 放大
      mZoom = maxZoom;
    } else if (zoom < 0) { // 缩小
      mZoom = 0;
    }

    Camera.Parameters parameters = camera.getParameters();
    parameters.setZoom(mZoom);
    camera.setParameters(parameters);
  }
  
  private void startCapturing() {
    Logging.d(TAG, "Start capturing");
    checkIsOnCameraThread();
    this.state = SessionState.RUNNING;
    this.camera.setErrorCallback(new Camera.ErrorCallback() {
          public void onError(int error, Camera camera) {
            String errorMessage;
            if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
              errorMessage = "Camera server died!";
            } else {
              errorMessage = "Camera error: " + error;
            } 
            Logging.e(TAG, errorMessage);
            Camera1Session.this.stopInternal();
            if (error == Camera.CAMERA_ERROR_EVICTED) {
              Camera1Session.this.events.onCameraDisconnected(Camera1Session.this);
            } else {
              Camera1Session.this.events.onCameraError(Camera1Session.this, errorMessage);
            } 
          }
        });
    if (this.captureToTexture) {
      listenForTextureFrames();
    } else {
      listenForBytebufferFrames();
    } 
    try {
      this.camera.startPreview();
    } catch (RuntimeException e) {
      stopInternal();
      this.events.onCameraError(this, e.getMessage());
    } 
  }
  
  private void stopInternal() {
    Logging.d(TAG, "Stop internal");
    checkIsOnCameraThread();
    if (this.state == SessionState.STOPPED) {
      Logging.d(TAG, "Camera is already stopped");
      return;
    } 
    this.state = SessionState.STOPPED;
    this.surfaceTextureHelper.stopListening();
    this.camera.stopPreview();
    this.camera.release();
    this.events.onCameraClosed(this, String.valueOf(cameraId));
    Logging.d(TAG, "Stop done");
  }
  
  private void listenForTextureFrames() {
    this.surfaceTextureHelper.startListening(frame -> {
          checkIsOnCameraThread();
          if (this.state != SessionState.RUNNING) {
            Logging.d(TAG, "Texture frame captured but camera is no longer running.");
            return;
          } 
          if (!this.firstFrameReported) {
            int startTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.constructionTimeNs);
            camera1StartTimeMsHistogram.addSample(startTimeMs);
            this.firstFrameReported = true;
          } 
          VideoFrame modifiedFrame = new VideoFrame(CameraSession.createTextureBufferWithModifiedTransformMatrix((TextureBufferImpl)frame.getBuffer(), (this.info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT), Surface.ROTATION_0), getFrameOrientation(), frame.getTimestampNs());
          this.events.onFrameCaptured(this, modifiedFrame);
          modifiedFrame.release();
        });
  }
  
  private void listenForBytebufferFrames() {
    this.camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
          public void onPreviewFrame(byte[] data, Camera callbackCamera) {
            Camera1Session.this.checkIsOnCameraThread();
            if (callbackCamera != Camera1Session.this.camera) {
              Logging.e(TAG, "Callback from a different camera. This should never happen.");
              return;
            } 
            if (Camera1Session.this.state != Camera1Session.SessionState.RUNNING) {
              Logging.d(TAG, "Bytebuffer frame captured but camera is no longer running.");
              return;
            } 
            long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
            if (!Camera1Session.this.firstFrameReported) {
              int startTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - Camera1Session.this.constructionTimeNs);
              Camera1Session.camera1StartTimeMsHistogram.addSample(startTimeMs);
              Camera1Session.this.firstFrameReported = true;
            } 
            VideoFrame.Buffer frameBuffer = new NV21Buffer(data, Camera1Session.this.captureFormat.width, Camera1Session.this.captureFormat.height, () -> {
              Camera1Session.this.cameraThreadHandler.post(() -> {
                if (Camera1Session.this.state == Camera1Session.SessionState.RUNNING) {
                  Camera1Session.this.camera.addCallbackBuffer(data);
                }
              });
            });
            VideoFrame frame = new VideoFrame(frameBuffer, Camera1Session.this.getFrameOrientation(), captureTimeNs);
            Camera1Session.this.events.onFrameCaptured(Camera1Session.this, frame);
            frame.release();
          }
        });
  }
  
  private int getFrameOrientation() {
    int rotation = CameraSession.getDeviceOrientation(this.applicationContext);
    if (this.info.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
      rotation = 360 - rotation; 
    return (this.info.orientation + rotation) % 360;
  }
  
  private void checkIsOnCameraThread() {
    if (Thread.currentThread() != this.cameraThreadHandler.getLooper().getThread())
      throw new IllegalStateException("Wrong thread"); 
  }
}

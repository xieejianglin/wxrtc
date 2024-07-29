package org.webrtc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

abstract class CameraCapturer implements CameraVideoCapturer {
  private static final String TAG = "CameraCapturer";
  
  private static final int MAX_OPEN_CAMERA_ATTEMPTS = 3;
  
  private static final int OPEN_CAMERA_DELAY_MS = 500;
  
  private static final int OPEN_CAMERA_TIMEOUT = 10000;
  
  private final CameraEnumerator cameraEnumerator;
  
  private final CameraVideoCapturer.CameraEventsHandler eventsHandler;
  
  private final Handler uiThreadHandler;
  
  enum SwitchState {
    IDLE, PENDING, IN_PROGRESS;
  }
  
  @Nullable
  private final CameraSession.CreateSessionCallback createSessionCallback = new CameraSession.CreateSessionCallback() {
      public void onDone(CameraSession session) {
        CameraCapturer.this.checkIsOnCameraThread();
        Logging.d("CameraCapturer", "Create session done. Switch state: " + CameraCapturer.this.switchState);
        CameraCapturer.this.uiThreadHandler.removeCallbacks(CameraCapturer.this.openCameraTimeoutRunnable);
        synchronized (CameraCapturer.this.stateLock) {
          CameraCapturer.this.capturerObserver.onCapturerStarted(true);
          CameraCapturer.this.sessionOpening = false;
          CameraCapturer.this.currentSession = session;
          CameraCapturer.this.cameraStatistics = new CameraVideoCapturer.CameraStatistics(CameraCapturer.this.surfaceHelper, CameraCapturer.this.eventsHandler);
          CameraCapturer.this.firstFrameObserved = false;
          CameraCapturer.this.stateLock.notifyAll();
          if (CameraCapturer.this.switchState == CameraCapturer.SwitchState.IN_PROGRESS) {
            CameraCapturer.this.switchState = CameraCapturer.SwitchState.IDLE;
            if (CameraCapturer.this.switchEventsHandler != null) {
              CameraCapturer.this.switchEventsHandler.onCameraSwitchDone(CameraCapturer.this.cameraEnumerator.isFrontFacing(CameraCapturer.this.cameraName));
              CameraCapturer.this.switchEventsHandler = null;
            } 
          } else if (CameraCapturer.this.switchState == CameraCapturer.SwitchState.PENDING) {
            String selectedCameraName = CameraCapturer.this.pendingCameraName;
            CameraCapturer.this.pendingCameraName = null;
            CameraCapturer.this.switchState = CameraCapturer.SwitchState.IDLE;
            CameraCapturer.this.switchCameraInternal(CameraCapturer.this.switchEventsHandler, selectedCameraName);
          } 
        } 
      }
      
      public void onFailure(CameraSession.FailureType failureType, String error) {
        CameraCapturer.this.checkIsOnCameraThread();
        CameraCapturer.this.uiThreadHandler.removeCallbacks(CameraCapturer.this.openCameraTimeoutRunnable);
        synchronized (CameraCapturer.this.stateLock) {
          CameraCapturer.this.capturerObserver.onCapturerStarted(false);
          CameraCapturer.this.openAttemptsRemaining--;
          if (CameraCapturer.this.openAttemptsRemaining <= 0) {
            Logging.w("CameraCapturer", "Opening camera failed, passing: " + error);
            CameraCapturer.this.sessionOpening = false;
            CameraCapturer.this.stateLock.notifyAll();
            if (CameraCapturer.this.switchState != CameraCapturer.SwitchState.IDLE) {
              if (CameraCapturer.this.switchEventsHandler != null) {
                CameraCapturer.this.switchEventsHandler.onCameraSwitchError(error);
                CameraCapturer.this.switchEventsHandler = null;
              } 
              CameraCapturer.this.switchState = CameraCapturer.SwitchState.IDLE;
            } 
            if (failureType == CameraSession.FailureType.DISCONNECTED) {
              CameraCapturer.this.eventsHandler.onCameraDisconnected();
            } else {
              CameraCapturer.this.eventsHandler.onCameraError(error);
            } 
          } else {
            Logging.w("CameraCapturer", "Opening camera failed, retry: " + error);
            CameraCapturer.this.createSessionInternal(500);
          } 
        } 
      }
    };
  
  @Nullable
  private final CameraSession.Events cameraSessionEventsHandler = new CameraSession.Events() {
      public void onCameraOpening() {
        CameraCapturer.this.checkIsOnCameraThread();
        synchronized (CameraCapturer.this.stateLock) {
          if (CameraCapturer.this.currentSession != null) {
            Logging.w("CameraCapturer", "onCameraOpening while session was open.");
            return;
          } 
          CameraCapturer.this.eventsHandler.onCameraOpening(CameraCapturer.this.cameraName);
        } 
      }
      
      public void onCameraError(CameraSession session, String error) {
        CameraCapturer.this.checkIsOnCameraThread();
        synchronized (CameraCapturer.this.stateLock) {
          if (session != CameraCapturer.this.currentSession) {
            Logging.w("CameraCapturer", "onCameraError from another session: " + error);
            return;
          } 
          CameraCapturer.this.eventsHandler.onCameraError(error);
          CameraCapturer.this.stopCapture();
        } 
      }
      
      public void onCameraDisconnected(CameraSession session) {
        CameraCapturer.this.checkIsOnCameraThread();
        synchronized (CameraCapturer.this.stateLock) {
          if (session != CameraCapturer.this.currentSession) {
            Logging.w("CameraCapturer", "onCameraDisconnected from another session.");
            return;
          } 
          CameraCapturer.this.eventsHandler.onCameraDisconnected();
          CameraCapturer.this.stopCapture();
        } 
      }
      
      public void onCameraClosed(CameraSession session) {
        CameraCapturer.this.checkIsOnCameraThread();
        synchronized (CameraCapturer.this.stateLock) {
          if (session != CameraCapturer.this.currentSession && CameraCapturer.this.currentSession != null) {
            Logging.d("CameraCapturer", "onCameraClosed from another session.");
            return;
          } 
          CameraCapturer.this.eventsHandler.onCameraClosed();
        } 
      }
      
      public void onFrameCaptured(CameraSession session, VideoFrame frame) {
        CameraCapturer.this.checkIsOnCameraThread();
        synchronized (CameraCapturer.this.stateLock) {
          if (session != CameraCapturer.this.currentSession) {
            Logging.w("CameraCapturer", "onFrameCaptured from another session.");
            return;
          } 
          if (!CameraCapturer.this.firstFrameObserved) {
            CameraCapturer.this.eventsHandler.onFirstFrameAvailable();
            CameraCapturer.this.firstFrameObserved = true;
          } 
          CameraCapturer.this.cameraStatistics.addFrame();
          CameraCapturer.this.capturerObserver.onFrameCaptured(frame);
        } 
      }
    };
  
  private final Runnable openCameraTimeoutRunnable = new Runnable() {
      public void run() {
        CameraCapturer.this.eventsHandler.onCameraError("Camera failed to start within timeout.");
      }
    };
  
  private Handler cameraThreadHandler;
  
  private Context applicationContext;
  
  private CapturerObserver capturerObserver;
  
  private SurfaceTextureHelper surfaceHelper;
  
  private final Object stateLock = new Object();
  
  private boolean sessionOpening;
  
  @Nullable
  private CameraSession currentSession;
  
  private String cameraName;
  
  private String pendingCameraName;
  
  private int width;
  
  private int height;
  
  private int framerate;
  
  private int openAttemptsRemaining;
  
  private SwitchState switchState = SwitchState.IDLE;
  
  @Nullable
  private CameraVideoCapturer.CameraSwitchHandler switchEventsHandler;
  
  @Nullable
  private CameraVideoCapturer.CameraStatistics cameraStatistics;
  
  private boolean firstFrameObserved;
  
  public CameraCapturer(String cameraName, @Nullable CameraVideoCapturer.CameraEventsHandler eventsHandler, CameraEnumerator cameraEnumerator) {
    if (eventsHandler == null)
      eventsHandler = new CameraVideoCapturer.CameraEventsHandler() {
          public void onCameraError(String errorDescription) {}
          
          public void onCameraDisconnected() {}
          
          public void onCameraFreezed(String errorDescription) {}
          
          public void onCameraOpening(String cameraName) {}
          
          public void onFirstFrameAvailable() {}
          
          public void onCameraClosed() {}
        }; 
    this.eventsHandler = eventsHandler;
    this.cameraEnumerator = cameraEnumerator;
    this.cameraName = cameraName;
    List<String> deviceNames = Arrays.asList(cameraEnumerator.getDeviceNames());
    this.uiThreadHandler = new Handler(Looper.getMainLooper());
    if (deviceNames.isEmpty())
      throw new RuntimeException("No cameras attached."); 
    if (!deviceNames.contains(this.cameraName))
      throw new IllegalArgumentException("Camera name " + this.cameraName + " does not match any known camera device."); 
  }
  
  public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver capturerObserver) {
    this.applicationContext = applicationContext;
    this.capturerObserver = capturerObserver;
    this.surfaceHelper = surfaceTextureHelper;
    this.cameraThreadHandler = surfaceTextureHelper.getHandler();
  }
  
  public void startCapture(int width, int height, int framerate) {
    Logging.d("CameraCapturer", "startCapture: " + width + "x" + height + "@" + framerate);
    if (this.applicationContext == null)
      throw new RuntimeException("CameraCapturer must be initialized before calling startCapture."); 
    synchronized (this.stateLock) {
      if (this.sessionOpening || this.currentSession != null) {
        Logging.w("CameraCapturer", "Session already open");
        return;
      } 
      this.width = width;
      this.height = height;
      this.framerate = framerate;
      this.sessionOpening = true;
      this.openAttemptsRemaining = 3;
      createSessionInternal(0);
    } 
  }
  
  private void createSessionInternal(int delayMs) {
    this.uiThreadHandler.postDelayed(this.openCameraTimeoutRunnable, (delayMs + 10000));
    this.cameraThreadHandler.postDelayed(new Runnable() {
      public void run() {
          CameraCapturer.this.createCameraSession(CameraCapturer.this.createSessionCallback, CameraCapturer.this.cameraSessionEventsHandler, CameraCapturer.this.applicationContext, CameraCapturer.this.surfaceHelper, CameraCapturer.this.cameraName, CameraCapturer.this.width, CameraCapturer.this.height, CameraCapturer.this.framerate);
        }
    }, delayMs);
  }
  
  public void stopCapture() {
    Logging.d("CameraCapturer", "Stop capture");
    synchronized (this.stateLock) {
      while (this.sessionOpening) {
        Logging.d("CameraCapturer", "Stop capture: Waiting for session to open");
        try {
          this.stateLock.wait();
        } catch (InterruptedException e) {
          Logging.w("CameraCapturer", "Stop capture interrupted while waiting for the session to open.");
          Thread.currentThread().interrupt();
          return;
        } 
      } 
      if (this.currentSession != null) {
        Logging.d("CameraCapturer", "Stop capture: Nulling session");
        this.cameraStatistics.release();
        this.cameraStatistics = null;
        final CameraSession oldSession = this.currentSession;
        this.cameraThreadHandler.post(new Runnable() {
              public void run() {
                oldSession.stop();
              }
            });
        this.currentSession = null;
        this.capturerObserver.onCapturerStopped();
      } else {
        Logging.d("CameraCapturer", "Stop capture: No session open");
      } 
    } 
    Logging.d("CameraCapturer", "Stop capture done");
  }
  
  public void changeCaptureFormat(int width, int height, int framerate) {
    Logging.d("CameraCapturer", "changeCaptureFormat: " + width + "x" + height + "@" + framerate);
    synchronized (this.stateLock) {
      stopCapture();
      startCapture(width, height, framerate);
    } 
  }
  
  public void dispose() {
    Logging.d("CameraCapturer", "dispose");
    stopCapture();
  }
  
  public void switchCamera(final CameraVideoCapturer.CameraSwitchHandler switchEventsHandler) {
    Logging.d("CameraCapturer", "switchCamera");
    this.cameraThreadHandler.post(new Runnable() {
          public void run() {
            List<String> deviceNames = Arrays.asList(CameraCapturer.this.cameraEnumerator.getDeviceNames());
            if (deviceNames.size() < 2) {
              CameraCapturer.this.reportCameraSwitchError("No camera to switch to.", switchEventsHandler);
              return;
            } 
            int cameraNameIndex = deviceNames.indexOf(CameraCapturer.this.cameraName);
            String cameraName = deviceNames.get((cameraNameIndex + 1) % deviceNames.size());
            CameraCapturer.this.switchCameraInternal(switchEventsHandler, cameraName);
          }
        });
  }
  
  public void switchCamera(final CameraVideoCapturer.CameraSwitchHandler switchEventsHandler, final String cameraName) {
    Logging.d("CameraCapturer", "switchCamera");
    this.cameraThreadHandler.post(new Runnable() {
          public void run() {
            CameraCapturer.this.switchCameraInternal(switchEventsHandler, cameraName);
          }
        });
  }
  
  public boolean isScreencast() {
    return false;
  }
  
  public void printStackTrace() {
    Thread cameraThread = null;
    if (this.cameraThreadHandler != null)
      cameraThread = this.cameraThreadHandler.getLooper().getThread(); 
    if (cameraThread != null) {
      StackTraceElement[] cameraStackTrace = cameraThread.getStackTrace();
      if (cameraStackTrace.length > 0) {
        Logging.d("CameraCapturer", "CameraCapturer stack trace:");
        for (StackTraceElement traceElem : cameraStackTrace)
          Logging.d("CameraCapturer", traceElem.toString()); 
      } 
    } 
  }
  
  private void reportCameraSwitchError(String error, @Nullable CameraVideoCapturer.CameraSwitchHandler switchEventsHandler) {
    Logging.e("CameraCapturer", error);
    if (switchEventsHandler != null)
      switchEventsHandler.onCameraSwitchError(error); 
  }
  
  private void switchCameraInternal(@Nullable CameraVideoCapturer.CameraSwitchHandler switchEventsHandler, String selectedCameraName) {
    Logging.d("CameraCapturer", "switchCamera internal");
    List<String> deviceNames = Arrays.asList(this.cameraEnumerator.getDeviceNames());
    if (!deviceNames.contains(selectedCameraName)) {
      reportCameraSwitchError("Attempted to switch to unknown camera device " + selectedCameraName, switchEventsHandler);
      return;
    } 
    synchronized (this.stateLock) {
      if (this.switchState != SwitchState.IDLE) {
        reportCameraSwitchError("Camera switch already in progress.", switchEventsHandler);
        return;
      } 
      if (!this.sessionOpening && this.currentSession == null) {
        reportCameraSwitchError("switchCamera: camera is not running.", switchEventsHandler);
        return;
      } 
      this.switchEventsHandler = switchEventsHandler;
      if (this.sessionOpening) {
        this.switchState = SwitchState.PENDING;
        this.pendingCameraName = selectedCameraName;
        return;
      } 
      this.switchState = SwitchState.IN_PROGRESS;
      Logging.d("CameraCapturer", "switchCamera: Stopping session");
      this.cameraStatistics.release();
      this.cameraStatistics = null;
      final CameraSession oldSession = this.currentSession;
      this.cameraThreadHandler.post(new Runnable() {
            public void run() {
              oldSession.stop();
            }
          });
      this.currentSession = null;
      this.cameraName = selectedCameraName;
      this.sessionOpening = true;
      this.openAttemptsRemaining = 1;
      createSessionInternal(0);
    } 
    Logging.d("CameraCapturer", "switchCamera done");
  }
  
  private void checkIsOnCameraThread() {
    if (Thread.currentThread() != this.cameraThreadHandler.getLooper().getThread()) {
      Logging.e("CameraCapturer", "Check is on camera thread failed.");
      throw new RuntimeException("Not on camera thread.");
    } 
  }
  
  protected String getCameraName() {
    synchronized (this.stateLock) {
      return this.cameraName;
    } 
  }
  
  protected abstract void createCameraSession(CameraSession.CreateSessionCallback paramCreateSessionCallback, CameraSession.Events paramEvents, Context paramContext, SurfaceTextureHelper paramSurfaceTextureHelper, String paramString, int paramInt1, int paramInt2, int paramInt3);
}

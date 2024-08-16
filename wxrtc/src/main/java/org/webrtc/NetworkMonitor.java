package org.webrtc;

import android.content.Context;
import android.os.Build;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class NetworkMonitor {
  private static final String TAG = "NetworkMonitor";
  
  private static class InstanceHolder {
    static final NetworkMonitor instance = new NetworkMonitor();
  }
  
  private NetworkChangeDetectorFactory networkChangeDetectorFactory = new NetworkChangeDetectorFactory() {
      public NetworkChangeDetector create(NetworkChangeDetector.Observer observer, Context context) {
        return new NetworkMonitorAutoDetect(observer, context);
      }
    };
  
  private final ArrayList<Long> nativeNetworkObservers;
  
  private final ArrayList<NetworkObserver> networkObservers;
  
  private final Object networkChangeDetectorLock = new Object();
  
  @Nullable
  private NetworkChangeDetector networkChangeDetector;
  
  private int numObservers;
  
  private volatile NetworkChangeDetector.ConnectionType currentConnectionType;
  
  private NetworkMonitor() {
    this.nativeNetworkObservers = new ArrayList<>();
    this.networkObservers = new ArrayList<>();
    this.numObservers = 0;
    this.currentConnectionType = NetworkChangeDetector.ConnectionType.CONNECTION_UNKNOWN;
  }
  
  public void setNetworkChangeDetectorFactory(NetworkChangeDetectorFactory factory) {
    assertIsTrue((this.numObservers == 0));
    this.networkChangeDetectorFactory = factory;
  }
  
  @Deprecated
  public static void init(Context context) {}
  
  @CalledByNative
  public static NetworkMonitor getInstance() {
    return InstanceHolder.instance;
  }
  
  private static void assertIsTrue(boolean condition) {
    if (!condition)
      throw new AssertionError("Expected to be true"); 
  }
  
  public void startMonitoring(Context applicationContext, String fieldTrialsString) {
    synchronized (this.networkChangeDetectorLock) {
      this.numObservers++;
      if (this.networkChangeDetector == null)
        this.networkChangeDetector = createNetworkChangeDetector(applicationContext, fieldTrialsString); 
      this.currentConnectionType = this.networkChangeDetector.getCurrentConnectionType();
    } 
  }
  
  @Deprecated
  public void startMonitoring(Context applicationContext) {
    startMonitoring(applicationContext, "");
  }
  
  @Deprecated
  public void startMonitoring() {
    startMonitoring(ContextUtils.getApplicationContext(), "");
  }
  
  @CalledByNative
  private void startMonitoring(@Nullable Context applicationContext, long nativeObserver, String fieldTrialsString) {
    Logging.d(TAG, "Start monitoring with native observer " + nativeObserver + " fieldTrialsString: " + fieldTrialsString);
    startMonitoring(
        (applicationContext != null) ? applicationContext : ContextUtils.getApplicationContext(), fieldTrialsString);
    synchronized (this.nativeNetworkObservers) {
      this.nativeNetworkObservers.add(Long.valueOf(nativeObserver));
    } 
    updateObserverActiveNetworkList(nativeObserver);
    notifyObserversOfConnectionTypeChange(this.currentConnectionType);
  }
  
  public void stopMonitoring() {
    synchronized (this.networkChangeDetectorLock) {
      if (--this.numObservers == 0) {
        this.networkChangeDetector.destroy();
        this.networkChangeDetector = null;
      } 
    } 
  }
  
  @CalledByNative
  private void stopMonitoring(long nativeObserver) {
    Logging.d(TAG, "Stop monitoring with native observer " + nativeObserver);
    stopMonitoring();
    synchronized (this.nativeNetworkObservers) {
      this.nativeNetworkObservers.remove(Long.valueOf(nativeObserver));
    } 
  }
  
  @CalledByNative
  private boolean networkBindingSupported() {
    synchronized (this.networkChangeDetectorLock) {
      return (this.networkChangeDetector != null && this.networkChangeDetector.supportNetworkCallback());
    } 
  }
  
  @CalledByNative
  private static int androidSdkInt() {
    return Build.VERSION.SDK_INT;
  }
  
  private NetworkChangeDetector.ConnectionType getCurrentConnectionType() {
    return this.currentConnectionType;
  }
  
  private NetworkChangeDetector createNetworkChangeDetector(Context appContext, final String fieldTrialsString) {
    return this.networkChangeDetectorFactory.create(new NetworkChangeDetector.Observer() {
          public void onConnectionTypeChanged(NetworkChangeDetector.ConnectionType newConnectionType) {
            NetworkMonitor.this.updateCurrentConnectionType(newConnectionType);
          }
          
          public void onNetworkConnect(NetworkChangeDetector.NetworkInformation networkInfo) {
            NetworkMonitor.this.notifyObserversOfNetworkConnect(networkInfo);
          }
          
          public void onNetworkDisconnect(long networkHandle) {
            NetworkMonitor.this.notifyObserversOfNetworkDisconnect(networkHandle);
          }
          
          public void onNetworkPreference(List<NetworkChangeDetector.ConnectionType> types, int preference) {
            NetworkMonitor.this.notifyObserversOfNetworkPreference(types, preference);
          }
          
          public String getFieldTrialsString() {
            return fieldTrialsString;
          }
        }, appContext);
  }
  
  private void updateCurrentConnectionType(NetworkChangeDetector.ConnectionType newConnectionType) {
    this.currentConnectionType = newConnectionType;
    notifyObserversOfConnectionTypeChange(newConnectionType);
  }
  
  private void notifyObserversOfConnectionTypeChange(NetworkChangeDetector.ConnectionType newConnectionType) {
    List<NetworkObserver> javaObservers;
    List<Long> nativeObservers = getNativeNetworkObserversSync();
    for (Long nativeObserver : nativeObservers)
      nativeNotifyConnectionTypeChanged(nativeObserver.longValue()); 
    synchronized (this.networkObservers) {
      javaObservers = new ArrayList<>(this.networkObservers);
    } 
    for (NetworkObserver observer : javaObservers)
      observer.onConnectionTypeChanged(newConnectionType); 
  }
  
  private void notifyObserversOfNetworkConnect(NetworkChangeDetector.NetworkInformation networkInfo) {
    List<Long> nativeObservers = getNativeNetworkObserversSync();
    for (Long nativeObserver : nativeObservers)
      nativeNotifyOfNetworkConnect(nativeObserver.longValue(), networkInfo); 
  }
  
  private void notifyObserversOfNetworkDisconnect(long networkHandle) {
    List<Long> nativeObservers = getNativeNetworkObserversSync();
    for (Long nativeObserver : nativeObservers)
      nativeNotifyOfNetworkDisconnect(nativeObserver.longValue(), networkHandle); 
  }
  
  private void notifyObserversOfNetworkPreference(List<NetworkChangeDetector.ConnectionType> types, int preference) {
    List<Long> nativeObservers = getNativeNetworkObserversSync();
    for (NetworkChangeDetector.ConnectionType type : types) {
      for (Long nativeObserver : nativeObservers)
        nativeNotifyOfNetworkPreference(nativeObserver.longValue(), type, preference); 
    } 
  }
  
  private void updateObserverActiveNetworkList(long nativeObserver) {
    List<NetworkChangeDetector.NetworkInformation> networkInfoList;
    synchronized (this.networkChangeDetectorLock) {
      networkInfoList = (this.networkChangeDetector == null) ? null : this.networkChangeDetector.getActiveNetworkList();
    } 
    if (networkInfoList == null)
      return; 
    NetworkChangeDetector.NetworkInformation[] networkInfos = new NetworkChangeDetector.NetworkInformation[networkInfoList.size()];
    networkInfos = networkInfoList.<NetworkChangeDetector.NetworkInformation>toArray(networkInfos);
    nativeNotifyOfActiveNetworkList(nativeObserver, networkInfos);
  }
  
  private List<Long> getNativeNetworkObserversSync() {
    synchronized (this.nativeNetworkObservers) {
      return new ArrayList<>(this.nativeNetworkObservers);
    } 
  }
  
  @Deprecated
  public static void addNetworkObserver(NetworkObserver observer) {
    getInstance().addObserver(observer);
  }
  
  public void addObserver(NetworkObserver observer) {
    synchronized (this.networkObservers) {
      this.networkObservers.add(observer);
    } 
  }
  
  @Deprecated
  public static void removeNetworkObserver(NetworkObserver observer) {
    getInstance().removeObserver(observer);
  }
  
  public void removeObserver(NetworkObserver observer) {
    synchronized (this.networkObservers) {
      this.networkObservers.remove(observer);
    } 
  }
  
  public static boolean isOnline() {
    NetworkChangeDetector.ConnectionType connectionType = getInstance().getCurrentConnectionType();
    return (connectionType != NetworkChangeDetector.ConnectionType.CONNECTION_NONE);
  }
  
  private native void nativeNotifyConnectionTypeChanged(long paramLong);
  
  private native void nativeNotifyOfNetworkConnect(long paramLong, NetworkChangeDetector.NetworkInformation paramNetworkInformation);
  
  private native void nativeNotifyOfNetworkDisconnect(long paramLong1, long paramLong2);
  
  private native void nativeNotifyOfActiveNetworkList(long paramLong, NetworkChangeDetector.NetworkInformation[] paramArrayOfNetworkInformation);
  
  private native void nativeNotifyOfNetworkPreference(long paramLong, NetworkChangeDetector.ConnectionType paramConnectionType, int paramInt);
  
  @Nullable
  NetworkChangeDetector getNetworkChangeDetector() {
    synchronized (this.networkChangeDetectorLock) {
      return this.networkChangeDetector;
    } 
  }
  
  int getNumObservers() {
    synchronized (this.networkChangeDetectorLock) {
      return this.numObservers;
    } 
  }
  
  static NetworkMonitorAutoDetect createAndSetAutoDetectForTest(Context context, String fieldTrialsString) {
    NetworkMonitor networkMonitor = getInstance();
    NetworkChangeDetector networkChangeDetector = networkMonitor.createNetworkChangeDetector(context, fieldTrialsString);
    networkMonitor.networkChangeDetector = networkChangeDetector;
    return (NetworkMonitorAutoDetect)networkChangeDetector;
  }
  
  public static interface NetworkObserver {
    void onConnectionTypeChanged(NetworkChangeDetector.ConnectionType param1ConnectionType);
  }
}

package org.webrtc;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NetworkMonitorAutoDetect extends BroadcastReceiver implements NetworkChangeDetector {
  private static final long INVALID_NET_ID = -1L;
  
  private static final String TAG = "NetworkMonitorAutoDetect";
  
  private final NetworkChangeDetector.Observer observer;
  
  private final IntentFilter intentFilter;
  
  private final Context context;
  
  @Nullable
  private final ConnectivityManager.NetworkCallback mobileNetworkCallback;
  
  @Nullable
  private final ConnectivityManager.NetworkCallback allNetworkCallback;
  
  private ConnectivityManagerDelegate connectivityManagerDelegate;
  
  private WifiManagerDelegate wifiManagerDelegate;
  
  private WifiDirectManagerDelegate wifiDirectManagerDelegate;
  
  private static boolean includeWifiDirect;
  
  static class NetworkState {
    private final boolean connected;
    
    private final int type;
    
    private final int subtype;
    
    private final int underlyingNetworkTypeForVpn;
    
    private final int underlyingNetworkSubtypeForVpn;
    
    public NetworkState(boolean connected, int type, int subtype, int underlyingNetworkTypeForVpn, int underlyingNetworkSubtypeForVpn) {
      this.connected = connected;
      this.type = type;
      this.subtype = subtype;
      this.underlyingNetworkTypeForVpn = underlyingNetworkTypeForVpn;
      this.underlyingNetworkSubtypeForVpn = underlyingNetworkSubtypeForVpn;
    }
    
    public boolean isConnected() {
      return this.connected;
    }
    
    public int getNetworkType() {
      return this.type;
    }
    
    public int getNetworkSubType() {
      return this.subtype;
    }
    
    public int getUnderlyingNetworkTypeForVpn() {
      return this.underlyingNetworkTypeForVpn;
    }
    
    public int getUnderlyingNetworkSubtypeForVpn() {
      return this.underlyingNetworkSubtypeForVpn;
    }
  }
  
  @SuppressLint({"NewApi"})
  @VisibleForTesting
  class SimpleNetworkCallback extends ConnectivityManager.NetworkCallback {
    @GuardedBy("availableNetworks")
    final Set<Network> availableNetworks;
    
    SimpleNetworkCallback(Set<Network> availableNetworks) {
      this.availableNetworks = availableNetworks;
    }
    
    public void onAvailable(Network network) {
      Logging.d("NetworkMonitorAutoDetect", "Network handle: " + 
          
          NetworkMonitorAutoDetect.networkToNetId(network) + " becomes available: " + network
          .toString());
      synchronized (this.availableNetworks) {
        this.availableNetworks.add(network);
      } 
      onNetworkChanged(network);
    }
    
    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
      Logging.d("NetworkMonitorAutoDetect", "handle: " + 
          NetworkMonitorAutoDetect.networkToNetId(network) + " capabilities changed: " + networkCapabilities
          .toString());
      onNetworkChanged(network);
    }
    
    public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
      Logging.d("NetworkMonitorAutoDetect", "handle: " + NetworkMonitorAutoDetect.networkToNetId(network) + " link properties changed");
      onNetworkChanged(network);
    }
    
    public void onLosing(Network network, int maxMsToLive) {
      Logging.d("NetworkMonitorAutoDetect", "Network handle: " + 
          
          NetworkMonitorAutoDetect.networkToNetId(network) + ", " + network.toString() + " is about to lose in " + maxMsToLive + "ms");
    }
    
    public void onLost(Network network) {
      Logging.d("NetworkMonitorAutoDetect", "Network handle: " + 
          
          NetworkMonitorAutoDetect.networkToNetId(network) + ", " + network.toString() + " is disconnected");
      synchronized (this.availableNetworks) {
        this.availableNetworks.remove(network);
      } 
      NetworkMonitorAutoDetect.this.observer.onNetworkDisconnect(NetworkMonitorAutoDetect.networkToNetId(network));
    }
    
    private void onNetworkChanged(Network network) {
      NetworkChangeDetector.NetworkInformation networkInformation = NetworkMonitorAutoDetect.this.connectivityManagerDelegate.networkToInfo(network);
      if (networkInformation != null)
        NetworkMonitorAutoDetect.this.observer.onNetworkConnect(networkInformation); 
    }
  }
  
  static class ConnectivityManagerDelegate {
    @Nullable
    private final ConnectivityManager connectivityManager;
    
    @NonNull
    @GuardedBy("availableNetworks")
    private final Set<Network> availableNetworks;
    
    private final boolean getAllNetworksFromCache;
    
    private final boolean requestVPN;
    
    private final boolean includeOtherUidNetworks;
    
    ConnectivityManagerDelegate(Context context, Set<Network> availableNetworks, String fieldTrialsString) {
      this((ConnectivityManager)context.getSystemService("connectivity"), availableNetworks, fieldTrialsString);
    }
    
    @VisibleForTesting
    ConnectivityManagerDelegate(ConnectivityManager connectivityManager, Set<Network> availableNetworks, String fieldTrialsString) {
      this.connectivityManager = connectivityManager;
      this.availableNetworks = availableNetworks;
      this
        .getAllNetworksFromCache = checkFieldTrial(fieldTrialsString, "getAllNetworksFromCache", false);
      this.requestVPN = checkFieldTrial(fieldTrialsString, "requestVPN", false);
      this
        .includeOtherUidNetworks = checkFieldTrial(fieldTrialsString, "includeOtherUidNetworks", false);
    }
    
    private static boolean checkFieldTrial(String fieldTrialsString, String key, boolean defaultValue) {
      if (fieldTrialsString.contains(key + ":true"))
        return true; 
      if (fieldTrialsString.contains(key + ":false"))
        return false; 
      return defaultValue;
    }
    
    NetworkMonitorAutoDetect.NetworkState getNetworkState() {
      if (this.connectivityManager == null)
        return new NetworkMonitorAutoDetect.NetworkState(false, -1, -1, -1, -1); 
      return getNetworkState(this.connectivityManager.getActiveNetworkInfo());
    }
    
    @SuppressLint({"NewApi"})
    NetworkMonitorAutoDetect.NetworkState getNetworkState(@Nullable Network network) {
      if (network == null || this.connectivityManager == null)
        return new NetworkMonitorAutoDetect.NetworkState(false, -1, -1, -1, -1); 
      NetworkInfo networkInfo = this.connectivityManager.getNetworkInfo(network);
      if (networkInfo == null) {
        Logging.w("NetworkMonitorAutoDetect", "Couldn't retrieve information from network " + network.toString());
        return new NetworkMonitorAutoDetect.NetworkState(false, -1, -1, -1, -1);
      } 
      if (networkInfo.getType() != 17) {
        NetworkCapabilities networkCapabilities = this.connectivityManager.getNetworkCapabilities(network);
        if (networkCapabilities == null || 
          !networkCapabilities.hasTransport(4))
          return getNetworkState(networkInfo); 
        return new NetworkMonitorAutoDetect.NetworkState(networkInfo.isConnected(), 17, -1, networkInfo
            .getType(), networkInfo.getSubtype());
      } 
      if (networkInfo.getType() == 17) {
        if (Build.VERSION.SDK_INT >= 23 && network
          .equals(this.connectivityManager.getActiveNetwork())) {
          NetworkInfo underlyingActiveNetworkInfo = this.connectivityManager.getActiveNetworkInfo();
          if (underlyingActiveNetworkInfo != null && underlyingActiveNetworkInfo
            .getType() != 17)
            return new NetworkMonitorAutoDetect.NetworkState(networkInfo.isConnected(), 17, -1, underlyingActiveNetworkInfo
                .getType(), underlyingActiveNetworkInfo.getSubtype()); 
        } 
        return new NetworkMonitorAutoDetect.NetworkState(networkInfo
            .isConnected(), 17, -1, -1, -1);
      } 
      return getNetworkState(networkInfo);
    }
    
    private NetworkMonitorAutoDetect.NetworkState getNetworkState(@Nullable NetworkInfo networkInfo) {
      if (networkInfo == null || !networkInfo.isConnected())
        return new NetworkMonitorAutoDetect.NetworkState(false, -1, -1, -1, -1); 
      return new NetworkMonitorAutoDetect.NetworkState(true, networkInfo.getType(), networkInfo.getSubtype(), -1, -1);
    }
    
    @SuppressLint({"NewApi"})
    Network[] getAllNetworks() {
      if (this.connectivityManager == null)
        return new Network[0]; 
      if (supportNetworkCallback() && this.getAllNetworksFromCache)
        synchronized (this.availableNetworks) {
          return this.availableNetworks.<Network>toArray(new Network[0]);
        }  
      return this.connectivityManager.getAllNetworks();
    }
    
    @Nullable
    List<NetworkChangeDetector.NetworkInformation> getActiveNetworkList() {
      if (!supportNetworkCallback())
        return null; 
      ArrayList<NetworkChangeDetector.NetworkInformation> netInfoList = new ArrayList<>();
      for (Network network : getAllNetworks()) {
        NetworkChangeDetector.NetworkInformation info = networkToInfo(network);
        if (info != null)
          netInfoList.add(info); 
      } 
      return netInfoList;
    }
    
    @SuppressLint({"NewApi"})
    long getDefaultNetId() {
      if (!supportNetworkCallback())
        return -1L; 
      NetworkInfo defaultNetworkInfo = this.connectivityManager.getActiveNetworkInfo();
      if (defaultNetworkInfo == null)
        return -1L; 
      Network[] networks = getAllNetworks();
      long defaultNetId = -1L;
      for (Network network : networks) {
        if (hasInternetCapability(network)) {
          NetworkInfo networkInfo = this.connectivityManager.getNetworkInfo(network);
          if (networkInfo != null && networkInfo.getType() == defaultNetworkInfo.getType()) {
            if (defaultNetId != -1L)
              throw new RuntimeException("Multiple connected networks of same type are not supported."); 
            defaultNetId = NetworkMonitorAutoDetect.networkToNetId(network);
          } 
        } 
      } 
      return defaultNetId;
    }
    
    @SuppressLint({"NewApi"})
    @Nullable
    private NetworkChangeDetector.NetworkInformation networkToInfo(@Nullable Network network) {
      if (network == null || this.connectivityManager == null)
        return null; 
      LinkProperties linkProperties = this.connectivityManager.getLinkProperties(network);
      if (linkProperties == null) {
        Logging.w("NetworkMonitorAutoDetect", "Detected unknown network: " + network.toString());
        return null;
      } 
      if (linkProperties.getInterfaceName() == null) {
        Logging.w("NetworkMonitorAutoDetect", "Null interface name for network " + network.toString());
        return null;
      } 
      NetworkMonitorAutoDetect.NetworkState networkState = getNetworkState(network);
      NetworkChangeDetector.ConnectionType connectionType = NetworkMonitorAutoDetect.getConnectionType(networkState);
      if (connectionType == NetworkChangeDetector.ConnectionType.CONNECTION_NONE) {
        Logging.d("NetworkMonitorAutoDetect", "Network " + network.toString() + " is disconnected");
        return null;
      } 
      if (connectionType == NetworkChangeDetector.ConnectionType.CONNECTION_UNKNOWN || connectionType == NetworkChangeDetector.ConnectionType.CONNECTION_UNKNOWN_CELLULAR)
        Logging.d("NetworkMonitorAutoDetect", "Network " + network.toString() + " connection type is " + connectionType + " because it has type " + networkState
            .getNetworkType() + " and subtype " + networkState
            .getNetworkSubType()); 
      NetworkChangeDetector.ConnectionType underlyingConnectionTypeForVpn = NetworkMonitorAutoDetect.getUnderlyingConnectionTypeForVpn(networkState);
      NetworkChangeDetector.NetworkInformation networkInformation = new NetworkChangeDetector.NetworkInformation(linkProperties.getInterfaceName(), connectionType, underlyingConnectionTypeForVpn, NetworkMonitorAutoDetect.networkToNetId(network), getIPAddresses(linkProperties));
      return networkInformation;
    }
    
    @SuppressLint({"NewApi"})
    boolean hasInternetCapability(Network network) {
      if (this.connectivityManager == null)
        return false; 
      NetworkCapabilities capabilities = this.connectivityManager.getNetworkCapabilities(network);
      return (capabilities != null && capabilities
        .hasCapability(12));
    }
    
    @SuppressLint({"NewApi"})
    @VisibleForTesting
    NetworkRequest createNetworkRequest() {
      NetworkRequest.Builder builder = (new NetworkRequest.Builder()).addCapability(12);
      if (this.requestVPN)
        builder.removeCapability(15); 
      if (Build.VERSION.SDK_INT >= 31 && this.includeOtherUidNetworks)
        builder.setIncludeOtherUidNetworks(true); 
      return builder.build();
    }
    
    @SuppressLint({"NewApi"})
    public void registerNetworkCallback(ConnectivityManager.NetworkCallback networkCallback) {
      this.connectivityManager.registerNetworkCallback(createNetworkRequest(), networkCallback);
    }
    
    @SuppressLint({"NewApi"})
    public void requestMobileNetwork(ConnectivityManager.NetworkCallback networkCallback) {
      NetworkRequest.Builder builder = new NetworkRequest.Builder();
      builder.addCapability(12)
        .addTransportType(0);
      this.connectivityManager.requestNetwork(builder.build(), networkCallback);
    }
    
    @SuppressLint({"NewApi"})
    NetworkChangeDetector.IPAddress[] getIPAddresses(LinkProperties linkProperties) {
      NetworkChangeDetector.IPAddress[] ipAddresses = new NetworkChangeDetector.IPAddress[linkProperties.getLinkAddresses().size()];
      int i = 0;
      for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
        ipAddresses[i] = new NetworkChangeDetector.IPAddress(linkAddress.getAddress().getAddress());
        i++;
      } 
      return ipAddresses;
    }
    
    @SuppressLint({"NewApi"})
    public void releaseCallback(ConnectivityManager.NetworkCallback networkCallback) {
      if (supportNetworkCallback()) {
        Logging.d("NetworkMonitorAutoDetect", "Unregister network callback");
        this.connectivityManager.unregisterNetworkCallback(networkCallback);
      } 
    }
    
    public boolean supportNetworkCallback() {
      return (this.connectivityManager != null);
    }
  }
  
  static class WifiManagerDelegate {
    @Nullable
    private final Context context;
    
    WifiManagerDelegate(Context context) {
      this.context = context;
    }
    
    WifiManagerDelegate() {
      this.context = null;
    }
    
    String getWifiSSID() {
      Intent intent = this.context.registerReceiver(null, new IntentFilter("android.net.wifi.STATE_CHANGE"));
      if (intent != null) {
        WifiInfo wifiInfo = (WifiInfo)intent.getParcelableExtra("wifiInfo");
        if (wifiInfo != null) {
          String ssid = wifiInfo.getSSID();
          if (ssid != null)
            return ssid; 
        } 
      } 
      return "";
    }
  }
  
  static class WifiDirectManagerDelegate extends BroadcastReceiver {
    private static final int WIFI_P2P_NETWORK_HANDLE = 0;
    
    private final Context context;
    
    private final NetworkChangeDetector.Observer observer;
    
    @Nullable
    private NetworkChangeDetector.NetworkInformation wifiP2pNetworkInfo;
    
    WifiDirectManagerDelegate(NetworkChangeDetector.Observer observer, Context context) {
      this.context = context;
      this.observer = observer;
      IntentFilter intentFilter = new IntentFilter();
      intentFilter.addAction("android.net.wifi.p2p.STATE_CHANGED");
      intentFilter.addAction("android.net.wifi.p2p.CONNECTION_STATE_CHANGE");
      context.registerReceiver(this, intentFilter);
      if (Build.VERSION.SDK_INT > 28) {
        WifiP2pManager manager = (WifiP2pManager)context.getSystemService("wifip2p");
        WifiP2pManager.Channel channel = manager.initialize(context, context.getMainLooper(), null);
        manager.requestGroupInfo(channel, wifiP2pGroup -> onWifiP2pGroupChange(wifiP2pGroup));
      } 
    }
    
    @SuppressLint({"InlinedApi"})
    public void onReceive(Context context, Intent intent) {
      if ("android.net.wifi.p2p.CONNECTION_STATE_CHANGE".equals(intent.getAction())) {
        WifiP2pGroup wifiP2pGroup = (WifiP2pGroup)intent.getParcelableExtra("p2pGroupInfo");
        onWifiP2pGroupChange(wifiP2pGroup);
      } else if ("android.net.wifi.p2p.STATE_CHANGED".equals(intent.getAction())) {
        int state = intent.getIntExtra("wifi_p2p_state", 0);
        onWifiP2pStateChange(state);
      } 
    }
    
    public void release() {
      this.context.unregisterReceiver(this);
    }
    
    public List<NetworkChangeDetector.NetworkInformation> getActiveNetworkList() {
      if (this.wifiP2pNetworkInfo != null)
        return Collections.singletonList(this.wifiP2pNetworkInfo); 
      return Collections.emptyList();
    }
    
    private void onWifiP2pGroupChange(@Nullable WifiP2pGroup wifiP2pGroup) {
      NetworkInterface wifiP2pInterface;
      if (wifiP2pGroup == null || wifiP2pGroup.getInterface() == null)
        return; 
      try {
        wifiP2pInterface = NetworkInterface.getByName(wifiP2pGroup.getInterface());
      } catch (SocketException e) {
        Logging.e("NetworkMonitorAutoDetect", "Unable to get WifiP2p network interface", e);
        return;
      } 
      List<InetAddress> interfaceAddresses = Collections.list(wifiP2pInterface.getInetAddresses());
      NetworkChangeDetector.IPAddress[] ipAddresses = new NetworkChangeDetector.IPAddress[interfaceAddresses.size()];
      for (int i = 0; i < interfaceAddresses.size(); i++)
        ipAddresses[i] = new NetworkChangeDetector.IPAddress(((InetAddress)interfaceAddresses.get(i)).getAddress()); 
      this.wifiP2pNetworkInfo = new NetworkChangeDetector.NetworkInformation(wifiP2pGroup.getInterface(), NetworkChangeDetector.ConnectionType.CONNECTION_WIFI, NetworkChangeDetector.ConnectionType.CONNECTION_NONE, 0L, ipAddresses);
      this.observer.onNetworkConnect(this.wifiP2pNetworkInfo);
    }
    
    private void onWifiP2pStateChange(int state) {
      if (state == 1) {
        this.wifiP2pNetworkInfo = null;
        this.observer.onNetworkDisconnect(0L);
      } 
    }
  }
  
  @GuardedBy("availableNetworks")
  final Set<Network> availableNetworks = new HashSet<>();
  
  private boolean isRegistered;
  
  private NetworkChangeDetector.ConnectionType connectionType;
  
  private String wifiSSID;
  
  @SuppressLint({"NewApi"})
  public NetworkMonitorAutoDetect(NetworkChangeDetector.Observer observer, Context context) {
    this.observer = observer;
    this.context = context;
    String fieldTrialsString = observer.getFieldTrialsString();
    this.connectivityManagerDelegate = new ConnectivityManagerDelegate(context, this.availableNetworks, fieldTrialsString);
    this.wifiManagerDelegate = new WifiManagerDelegate(context);
    NetworkState networkState = this.connectivityManagerDelegate.getNetworkState();
    this.connectionType = getConnectionType(networkState);
    this.wifiSSID = getWifiSSID(networkState);
    this.intentFilter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
    if (includeWifiDirect)
      this.wifiDirectManagerDelegate = new WifiDirectManagerDelegate(observer, context); 
    registerReceiver();
    if (this.connectivityManagerDelegate.supportNetworkCallback()) {
      ConnectivityManager.NetworkCallback tempNetworkCallback = new ConnectivityManager.NetworkCallback();
      try {
        this.connectivityManagerDelegate.requestMobileNetwork(tempNetworkCallback);
      } catch (SecurityException e) {
        Logging.w("NetworkMonitorAutoDetect", "Unable to obtain permission to request a cellular network.");
        tempNetworkCallback = null;
      } 
      this.mobileNetworkCallback = tempNetworkCallback;
      this.allNetworkCallback = new SimpleNetworkCallback(this.availableNetworks);
      this.connectivityManagerDelegate.registerNetworkCallback(this.allNetworkCallback);
    } else {
      this.mobileNetworkCallback = null;
      this.allNetworkCallback = null;
    } 
  }
  
  public static void setIncludeWifiDirect(boolean enable) {
    includeWifiDirect = enable;
  }
  
  public boolean supportNetworkCallback() {
    return this.connectivityManagerDelegate.supportNetworkCallback();
  }
  
  void setConnectivityManagerDelegateForTests(ConnectivityManagerDelegate delegate) {
    this.connectivityManagerDelegate = delegate;
  }
  
  void setWifiManagerDelegateForTests(WifiManagerDelegate delegate) {
    this.wifiManagerDelegate = delegate;
  }
  
  boolean isReceiverRegisteredForTesting() {
    return this.isRegistered;
  }
  
  @Nullable
  public List<NetworkChangeDetector.NetworkInformation> getActiveNetworkList() {
    List<NetworkChangeDetector.NetworkInformation> connectivityManagerList = this.connectivityManagerDelegate.getActiveNetworkList();
    if (connectivityManagerList == null)
      return null; 
    ArrayList<NetworkChangeDetector.NetworkInformation> result = new ArrayList<>(connectivityManagerList);
    if (this.wifiDirectManagerDelegate != null)
      result.addAll(this.wifiDirectManagerDelegate.getActiveNetworkList()); 
    return result;
  }
  
  public void destroy() {
    if (this.allNetworkCallback != null)
      this.connectivityManagerDelegate.releaseCallback(this.allNetworkCallback); 
    if (this.mobileNetworkCallback != null)
      this.connectivityManagerDelegate.releaseCallback(this.mobileNetworkCallback); 
    if (this.wifiDirectManagerDelegate != null)
      this.wifiDirectManagerDelegate.release(); 
    unregisterReceiver();
  }
  
  private void registerReceiver() {
    if (this.isRegistered)
      return; 
    this.isRegistered = true;
    this.context.registerReceiver(this, this.intentFilter);
  }
  
  private void unregisterReceiver() {
    if (!this.isRegistered)
      return; 
    this.isRegistered = false;
    this.context.unregisterReceiver(this);
  }
  
  public NetworkState getCurrentNetworkState() {
    return this.connectivityManagerDelegate.getNetworkState();
  }
  
  public long getDefaultNetId() {
    return this.connectivityManagerDelegate.getDefaultNetId();
  }
  
  private static NetworkChangeDetector.ConnectionType getConnectionType(boolean isConnected, int networkType, int networkSubtype) {
    if (!isConnected)
      return NetworkChangeDetector.ConnectionType.CONNECTION_NONE; 
    switch (networkType) {
      case 9:
        return NetworkChangeDetector.ConnectionType.CONNECTION_ETHERNET;
      case 1:
        return NetworkChangeDetector.ConnectionType.CONNECTION_WIFI;
      case 6:
        return NetworkChangeDetector.ConnectionType.CONNECTION_4G;
      case 7:
        return NetworkChangeDetector.ConnectionType.CONNECTION_BLUETOOTH;
      case 0:
      case 4:
      case 5:
        switch (networkSubtype) {
          case 1:
          case 2:
          case 4:
          case 7:
          case 11:
          case 16:
            return NetworkChangeDetector.ConnectionType.CONNECTION_2G;
          case 3:
          case 5:
          case 6:
          case 8:
          case 9:
          case 10:
          case 12:
          case 14:
          case 15:
          case 17:
            return NetworkChangeDetector.ConnectionType.CONNECTION_3G;
          case 13:
          case 18:
            return NetworkChangeDetector.ConnectionType.CONNECTION_4G;
          case 20:
            return NetworkChangeDetector.ConnectionType.CONNECTION_5G;
        } 
        return NetworkChangeDetector.ConnectionType.CONNECTION_UNKNOWN_CELLULAR;
      case 17:
        return NetworkChangeDetector.ConnectionType.CONNECTION_VPN;
    } 
    return NetworkChangeDetector.ConnectionType.CONNECTION_UNKNOWN;
  }
  
  public static NetworkChangeDetector.ConnectionType getConnectionType(NetworkState networkState) {
    return getConnectionType(networkState.isConnected(), networkState.getNetworkType(), networkState
        .getNetworkSubType());
  }
  
  public NetworkChangeDetector.ConnectionType getCurrentConnectionType() {
    return getConnectionType(getCurrentNetworkState());
  }
  
  private static NetworkChangeDetector.ConnectionType getUnderlyingConnectionTypeForVpn(NetworkState networkState) {
    if (networkState.getNetworkType() != 17)
      return NetworkChangeDetector.ConnectionType.CONNECTION_NONE; 
    return getConnectionType(networkState.isConnected(), networkState
        .getUnderlyingNetworkTypeForVpn(), networkState
        .getUnderlyingNetworkSubtypeForVpn());
  }
  
  private String getWifiSSID(NetworkState networkState) {
    if (getConnectionType(networkState) != NetworkChangeDetector.ConnectionType.CONNECTION_WIFI)
      return ""; 
    return this.wifiManagerDelegate.getWifiSSID();
  }
  
  public void onReceive(Context context, Intent intent) {
    NetworkState networkState = getCurrentNetworkState();
    if ("android.net.conn.CONNECTIVITY_CHANGE".equals(intent.getAction()))
      connectionTypeChanged(networkState); 
  }
  
  private void connectionTypeChanged(NetworkState networkState) {
    NetworkChangeDetector.ConnectionType newConnectionType = getConnectionType(networkState);
    String newWifiSSID = getWifiSSID(networkState);
    if (newConnectionType == this.connectionType && newWifiSSID.equals(this.wifiSSID))
      return; 
    this.connectionType = newConnectionType;
    this.wifiSSID = newWifiSSID;
    Logging.d("NetworkMonitorAutoDetect", "Network connectivity changed, type is: " + this.connectionType);
    this.observer.onConnectionTypeChanged(newConnectionType);
  }
  
  @SuppressLint({"NewApi"})
  private static long networkToNetId(Network network) {
    if (Build.VERSION.SDK_INT >= 23)
      return network.getNetworkHandle(); 
    return Integer.parseInt(network.toString());
  }
}

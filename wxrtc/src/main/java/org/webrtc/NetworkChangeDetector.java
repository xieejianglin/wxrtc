package org.webrtc;

import androidx.annotation.Nullable;
import java.util.List;

public interface NetworkChangeDetector {
  ConnectionType getCurrentConnectionType();
  
  boolean supportNetworkCallback();
  
  @Nullable
  List<NetworkInformation> getActiveNetworkList();
  
  void destroy();
  
  public enum ConnectionType {
    CONNECTION_UNKNOWN, CONNECTION_ETHERNET, CONNECTION_WIFI, CONNECTION_5G, CONNECTION_4G, CONNECTION_3G, CONNECTION_2G, CONNECTION_UNKNOWN_CELLULAR, CONNECTION_BLUETOOTH, CONNECTION_VPN, CONNECTION_NONE;
  }
  
  public static class IPAddress {
    public final byte[] address;
    
    public IPAddress(byte[] address) {
      this.address = address;
    }
    
    @CalledByNative("IPAddress")
    private byte[] getAddress() {
      return this.address;
    }
  }
  
  public static class NetworkInformation {
    public final String name;
    
    public final NetworkChangeDetector.ConnectionType type;
    
    public final NetworkChangeDetector.ConnectionType underlyingTypeForVpn;
    
    public final long handle;
    
    public final NetworkChangeDetector.IPAddress[] ipAddresses;
    
    public NetworkInformation(String name, NetworkChangeDetector.ConnectionType type, NetworkChangeDetector.ConnectionType underlyingTypeForVpn, long handle, NetworkChangeDetector.IPAddress[] addresses) {
      this.name = name;
      this.type = type;
      this.underlyingTypeForVpn = underlyingTypeForVpn;
      this.handle = handle;
      this.ipAddresses = addresses;
    }
    
    @CalledByNative("NetworkInformation")
    private NetworkChangeDetector.IPAddress[] getIpAddresses() {
      return this.ipAddresses;
    }
    
    @CalledByNative("NetworkInformation")
    private NetworkChangeDetector.ConnectionType getConnectionType() {
      return this.type;
    }
    
    @CalledByNative("NetworkInformation")
    private NetworkChangeDetector.ConnectionType getUnderlyingConnectionTypeForVpn() {
      return this.underlyingTypeForVpn;
    }
    
    @CalledByNative("NetworkInformation")
    private long getHandle() {
      return this.handle;
    }
    
    @CalledByNative("NetworkInformation")
    private String getName() {
      return this.name;
    }
  }
  
  public static abstract class Observer {
    public abstract void onConnectionTypeChanged(NetworkChangeDetector.ConnectionType param1ConnectionType);
    
    public abstract void onNetworkConnect(NetworkChangeDetector.NetworkInformation param1NetworkInformation);
    
    public abstract void onNetworkDisconnect(long param1Long);
    
    public abstract void onNetworkPreference(List<NetworkChangeDetector.ConnectionType> param1List, int param1Int);
    
    public String getFieldTrialsString() {
      return "";
    }
  }
}

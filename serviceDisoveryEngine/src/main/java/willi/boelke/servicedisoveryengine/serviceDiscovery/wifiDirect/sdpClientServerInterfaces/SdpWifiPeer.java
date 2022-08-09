package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.sdpClientServerInterfaces;


import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.SdpWifiConnection;


public interface SdpWifiPeer
{
    void onServiceDiscovered(String address, UUID serviceUUID);

    void onBecameGroupOwner();

    void onBecameGroupClient();

    void onConnectionEstablished(SdpWifiConnection connection);

    boolean shouldConnectTo(String address, UUID serviceUUID);
}

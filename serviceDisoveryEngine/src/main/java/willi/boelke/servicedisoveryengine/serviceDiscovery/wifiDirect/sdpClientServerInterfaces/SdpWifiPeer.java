package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.sdpClientServerInterfaces;


import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;
import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.sdpWifiEngine.SdpWifiConnection;


public interface SdpWifiPeer
{
    void onServiceDiscovered(String address, ServiceDescription description);

    void onBecameGroupOwner();

    void onBecameGroupClient();

    void onConnectionEstablished(SdpWifiConnection connection);

    boolean shouldConnectTo(String address, ServiceDescription description);
}

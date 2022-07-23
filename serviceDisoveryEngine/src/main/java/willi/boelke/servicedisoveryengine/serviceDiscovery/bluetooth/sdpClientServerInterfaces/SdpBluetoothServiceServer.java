package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces;


import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;

public interface SdpBluetoothServiceServer
{
    void onClientConnected(SdpBluetoothConnection connection);
}

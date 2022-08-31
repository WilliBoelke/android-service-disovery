package willi.boelke.serviceDiscovery.bluetooth.sdpBluetoothEngine;

import android.bluetooth.BluetoothDevice;

import willi.boelke.serviceDiscovery.serviceDescription.ServiceDescription;

public interface SdpBluetoothServiceClient
{
    /**
     * this will be called whenever a service has been discovered that 
     * is looked for through {@link SdpBluetoothEngine#startSDPDiscoveryForService(ServiceDescription, SdpBluetoothServiceClient)}
     * However this does not mean that a connection was established yet. 
     * 
     * The connection will be established if {@link #shouldConnectTo(String, ServiceDescription)}
     * returns true for the given service. 
     * 
     * Listeners will get the established connection through implementing {@link #onConnectedToService(SdpBluetoothConnection)}
     *
     * @param address
     * The address of the remote bluetooth device
     * @param description
     * the Service description as resolved through the UUID
     */
    void onServiceDiscovered(String address, ServiceDescription description);

    void onPeerDiscovered(BluetoothDevice peer);

    void onConnectedToService(SdpBluetoothConnection connection);

    boolean shouldConnectTo(String address, ServiceDescription description);
}

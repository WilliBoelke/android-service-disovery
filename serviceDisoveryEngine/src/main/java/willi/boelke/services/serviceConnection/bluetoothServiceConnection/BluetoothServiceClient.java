package willi.boelke.services.serviceConnection.bluetoothServiceConnection;


import android.bluetooth.BluetoothDevice;

import willi.boelke.services.serviceDiscovery.ServiceDescription;

public interface BluetoothServiceClient
{
    /**
     * this will be called whenever a service has been discovered that 
     * is looked for through {@link BluetoothServiceConnectionEngine#startSDPDiscoveryForService(ServiceDescription, BluetoothServiceClient)}
     * However this does not mean that a connection was established yet. 
     * 
     * The connection will be established if {@link #shouldConnectTo(String, ServiceDescription)}
     * returns true for the given service. 
     * 
     * Listeners will get the established connection through implementing {@link #onConnectedToService(BluetoothConnection)}
     *
     * @param address
     * The address of the remote bluetooth device
     * @param description
     * the Service description as resolved through the UUID
     */
    void onServiceDiscovered(String address, ServiceDescription description);

    void onPeerDiscovered(BluetoothDevice peer);

    void onConnectedToService(BluetoothConnection connection);

    boolean shouldConnectTo(String address, ServiceDescription description);
}

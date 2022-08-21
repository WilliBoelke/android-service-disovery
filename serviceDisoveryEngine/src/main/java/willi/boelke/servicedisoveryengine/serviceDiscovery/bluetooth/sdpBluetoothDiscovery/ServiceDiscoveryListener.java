package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothDiscovery;

import android.bluetooth.BluetoothDevice;

import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;

/**
 * Listener interface to get notified when
 * the {@link SdpBluetoothDiscoveryEngine} found a
 * new device or service on a device.
 *
 * @author WilliBolke
 */
public interface ServiceDiscoveryListener
{
    /**
     * Called whenever a service was discovered which has been looked for
     * through {@link SdpBluetoothDiscoveryEngine#startSDPDiscoveryForService(ServiceDescription)}
     *
     * Provides the remote host device and the service description to identify the service
     *
     * @param host
     *  The BluetoothDevice hosting / advertising the service
     * @param description
     *  The service description as provided in {@link SdpBluetoothDiscoveryEngine#startSDPDiscoveryForService(ServiceDescription)}
     *  which was resolved through matching UUIDs
     */
    void onServiceDiscovered(BluetoothDevice host, ServiceDescription description);

    /**
     * Called whenever a remote bluetooth device was discovered.
     * this device does not have to have a desired service available
     * @param device
     *   A bluetooth device in range
     */
    void onPeerDiscovered(BluetoothDevice device);
}
package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery;

import android.bluetooth.BluetoothDevice;

import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * Listener interface to get notified when
 * the {@link BluetoothDiscoveryVOne} found a
 * new device or service on a device.
 *
 * @author WilliBolke
 */
public interface BluetoothServiceDiscoveryListener
{
    /**
     * Called whenever a service was discovered which has been looked for
     * through {@link BluetoothDiscoveryVOne#startDiscoveryForService(ServiceDescription)}
     * <p>
     * Provides the remote host device and the service description to identify the service
     *
     * @param host
     *         The BluetoothDevice hosting / advertising the service
     * @param description
     *         The service description as provided in {@link BluetoothDiscoveryVOne#startDiscoveryForService(ServiceDescription)}
     *         which was resolved through matching UUIDs
     */
    void onServiceDiscovered(BluetoothDevice host, ServiceDescription description);

    /**
     * Called whenever a remote bluetooth device was discovered.
     * this device does not have to have a desired service available
     *
     * @param device
     *         A bluetooth device in range
     */
    void onPeerDiscovered(BluetoothDevice device);
}
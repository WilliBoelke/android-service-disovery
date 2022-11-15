package willi.boelke.services.serviceConnection.bluetoothServiceConnection;


import android.bluetooth.BluetoothDevice;

import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * A BluetoothServiceClient is the client side of a bluetooth connection
 * a client can be registered at the {@link BluetoothServiceConnectionEngine}
 * to get notified about events.
 *
 * <p>
 * <h2>Registering and Unregistering</h2>
 * A client always needs to be registered along side a {@link ServiceDescription}.
 * A client can be registered several times, though with different {@link ServiceDescription}s.
 * See : {@link BluetoothServiceConnectionEngine#startSDPDiscoveryForService(ServiceDescription, BluetoothServiceClient)}
 * <p>
 * To stop the service discovery and unregister the client only the ServiceDescription is necessary.
 * {@link BluetoothServiceConnectionEngine#stopSDPDiscoveryForService(ServiceDescription)}
 *
 * <p>
 * <h2>Events</h2>
 * A client will be provided with information about a number of
 * events.#onServiceDiscovered(BluetoothDevice, ServiceDescription)
 * {@link BluetoothServiceClient#onPeerDiscovered(BluetoothDevice)}
 * {@link BluetoothServiceClient#onServiceDiscovered(BluetoothDevice, ServiceDescription)}
 *
 * <p>
 * <h2>Connections</h2>
 * If a connection to a service host/server has been established
 * {@link BluetoothServiceClient#onConnectedToService(BluetoothConnection)} will be called,
 * providing the client with all necessary means to communicate with the peer/server.
 * <P>
 * Before a connection will be attempted {@link BluetoothServiceClient#shouldConnectTo(BluetoothDevice, ServiceDescription)}
 * will be called, allowing the client to prevent (return false) or allow (return true) a connection attempt
 *
 * <p>
 * <h2>Missing</h2>
 * Currently missing from the client are notifications regarding some (actually not unimportant)
 * events like "onConnectionFailed" and other error handling callbacks.
 * These are planned for the future.
 *
 * @author WilliBoelke
 */
public interface BluetoothServiceClient
{
    /**
     * This will be called whenever a service has been discovered that
     * is looked for through
     * {@link BluetoothServiceConnectionEngine#startSDPDiscoveryForService(ServiceDescription, BluetoothServiceClient)}
     * However this does not mean that a connection was established yet.
     * <p>
     * The connection will be established if {@link #shouldConnectTo(BluetoothDevice, ServiceDescription)}
     * returns true for the given service.
     * <p>
     * Listeners will get the established connection through implementing {@link #onConnectedToService(BluetoothConnection)}
     *
     * @param host
     *         The address of the remote bluetooth device
     * @param description
     *         the Service description as resolved through the UUID
     */
    void onServiceDiscovered(BluetoothDevice host, ServiceDescription description);

    /**
     * Will be called when a new bluetooth peer is discovered.
     * At this time no information about the available services
     * on this peer are given.
     * @param peer
     * The discovered bluetooth peer
     */
    void onPeerDiscovered(BluetoothDevice peer);

    /**
     * Called when a connection has been established
     * @param connection
     * The {@link BluetoothConnection}, which contains
     * the service description and the socket
     */
    void onConnectedToService(BluetoothConnection connection);

    /**
     * Called before a connection attempt is made.
     * Will prevent or allow a connection attempt.
     * @param host
     *  The host device of the service.
     * @param description
     *  The service description of the service discovered.
     * @return
     * return true if a connection should be attempted, else return false.
     */
    boolean shouldConnectTo(BluetoothDevice host, ServiceDescription description);
}

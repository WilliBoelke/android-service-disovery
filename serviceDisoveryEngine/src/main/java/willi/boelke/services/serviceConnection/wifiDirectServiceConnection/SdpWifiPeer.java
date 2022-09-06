package willi.boelke.services.serviceConnection.wifiDirectServiceConnection;


import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * Interface for a sdp wifi peer
 * A peer can be registered as listener when starting a service / service discovery
 * through {@link WifiDirectConnectionEngine#registerService(ServiceDescription, SdpWifiPeer)}
 * <p>
 * A SdpWifiPeer wil get notified about a range of events through the here
 * defined callback methods.
 *
 * @author WilliBoelke
 */
public interface SdpWifiPeer
{
    /**
     * This will be called when a instance of the searched service was discovered
     * on a remote device.
     * At this point a connection has not been established.
     * A connection will be established after calling {@link #shouldConnectTo(String, ServiceDescription)}
     * to decide whether a connection should be established or not.
     *
     * @param address
     *         The address of the host device
     * @param description
     *         the description of the service
     */
    void onServiceDiscovered(String address, ServiceDescription description);

    /**
     * Will be called when the local peer became the group owner
     * of the wifi direct group
     */
    void onBecameGroupOwner();

    /**
     * Will be called when teh local peer became a client
     * in a wifi direct group
     */
    void onBecameGroupClient();

    /**
     * Will be called when a connection was established
     * and the sockets connected.
     * Providing a SdpWifiConnection
     *
     * @param connection
     *         SdpWifiConnection instance which contains information about
     *         service, peer and the socket.
     */
    void onConnectionEstablished(SdpWifiConnection connection);

    /**
     * Will  be called after a instance of the searched service was discovered
     * decides whether a connection should be established.
     * Needs to return true if connection should be made.
     * else needs to return false.
     *
     * @param address
     *         the address of the remote device
     * @param description
     *         the description of the service
     *
     * @return true or false, depending on if a connection should be made or not
     */
    boolean shouldConnectTo(String address, ServiceDescription description);
}

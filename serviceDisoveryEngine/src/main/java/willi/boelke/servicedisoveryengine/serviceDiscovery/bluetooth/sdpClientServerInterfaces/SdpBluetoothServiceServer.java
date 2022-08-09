package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces;

import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;

/**
 * Describes a services server implementation as
 * required by {@link willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.SdpBluetoothEngine}.
 *
 * @author  Willi Boelke
 */
public interface SdpBluetoothServiceServer
{
    /**
     * Will be calle whenever a new connection was established,
     * providing the service with the new connection
     *
     * @param connection
     * the {@link SdpBluetoothConnection}, containing all means to communicate with the
     * connected peer (client)
     */
    void onClientConnected(SdpBluetoothConnection connection);
}

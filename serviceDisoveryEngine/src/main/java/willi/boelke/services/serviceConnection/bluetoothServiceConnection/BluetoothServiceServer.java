package willi.boelke.services.serviceConnection.bluetoothServiceConnection;


/**
 * Describes a services server implementation as
 * required by {@link BluetoothServiceConnectionEngine}.
 *
 * @author Willi Boelke
 */
public interface BluetoothServiceServer
{
    /**
     * Will be calle whenever a new connection was established,
     * providing the service with the new connection
     *
     * @param connection
     *         the {@link BluetoothConnection}, containing all means to communicate with the
     *         connected peer (client)
     */
    void onClientConnected(BluetoothConnection connection);
}

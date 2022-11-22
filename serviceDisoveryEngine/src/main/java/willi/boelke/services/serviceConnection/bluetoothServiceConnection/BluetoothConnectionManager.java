package willi.boelke.services.serviceConnection.bluetoothServiceConnection;

import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * Holds a list of {@link BluetoothConnection}s
 * which is used by the  {@link BluetoothServiceConnectionEngine}
 * <p>
 * <h2>Usage in the BluetoothServiceConnectionEngine</h2>
 * There are several use cases for this in the connect5ion engine:
 * <ul>
 *    <li>Preventing doubling connections / connection attempts</li>
 *    <li>Closing connections when he socket
 *    (for whatever reason) died/closed/disconnected</li>
 *    <li>Closing all open connections when the
 *     application ends / The engine stopped was stopped down.</li>
 *    <li>Closing all connections from or to a specific service.</li>
 * </ul>
 * This is utilized in {@link BluetoothServiceConnectionEngine}
 */
class BluetoothConnectionManager
{

    //
    //  ----------  instance variables ----------
    //

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    /**
     * A thread safe array list
     * holing all open {@link BluetoothConnection}s
     */
    private final CopyOnWriteArrayList<BluetoothConnection> openConnections;


    //
    //  ----------  constructor and initialisation ----------
    //

    protected BluetoothConnectionManager()
    {
        this.openConnections = new CopyOnWriteArrayList<>();
    }

    //
    //  ---------- public methods ----------
    //

    /**
     * Add a connection
     * this wil b keep until the connection dies
     * either by manually closing it or by losing the connection
     *
     * @param connection
     *         the connection to be stored
     */
    protected void addConnection(BluetoothConnection connection)
    {
        Log.d(TAG, "store connections :" + openConnections);
        this.openConnections.add(connection);
    }

    /**
     * Checks if a connection (defined by device address and service description) already exists
     * For to be sure all sockets will be checked if they are sill connected , and else closed before
     * looking the connection up.
     *
     * @param address
     *         the MAC address of the remote BluetoothDevice
     * @param description
     *         the Service description
     *
     * @return true when there is an equal connection - else returns false
     */
    protected boolean isAlreadyConnected(String address, ServiceDescription description)
    {
        Log.d(TAG, "isAlreadyConnected: looking for a equal connection that already is established");
        logConnectionTable();
        this.closeAndRemoveZombieConnections();
        Log.d(TAG, "isAlreadyConnected: there are " + this.openConnections.size() + " open connections");
        for (BluetoothConnection connection : this.openConnections)
        {
            Log.d(TAG, "isAlreadyConnected:  Comparing connection " + connection.toString());
            if (connection.getServiceDescription().equals(description) && connection.getRemoteDeviceAddress().equals(address))
            {
                Log.e(TAG, "isAlreadyConnected: found a equal connection, don't connect");
                return true;
            }
        }
        Log.d(TAG, "isAlreadyConnected: no equal connection found, cam be connected");
        return false;
    }

    /**
     * This closes all running connections in {@link #openConnections}
     * it should be called when stopping the BluetoothEngine to ensure
     * that all BluetoothSockets are closed.
     */
    protected void closeAllConnections()
    {
        logConnectionTable();
        for (BluetoothConnection connection : this.openConnections)
        {
            connection.close();
        }
        this.openConnections.clear();
    }

    /**
     * Closes all connections (sockets) which are clients to the service defined to
     * the given service description,
     *
     * @param description
     *         The description of the service
     */
    protected void closeAllClientConnectionsToService(ServiceDescription description)
    {
        Log.d(TAG, "closeAllClientConnectionsToService: closing connections to servers with " + description);
        closeConnectionsWithDescription(description, false);
    }

    /**
     * Closes and removes all connections service servers
     *
     * @param description
     *         The description of the service
     */
    protected void closeServerConnectionsToService(ServiceDescription description)
    {
        closeConnectionsWithDescription(description, true);
    }

    /**
     * Closes all sockets of connections with the given service description and removes them
     *
     * @param description
     *         The description of the connections to close
     * @param serverOnly
     *         if only server side connections should be closed true
     *
     * @see #closeServerConnectionsToService(ServiceDescription)
     * @see #closeAllClientConnectionsToService(ServiceDescription)
     */
    private void closeConnectionsWithDescription(ServiceDescription description, boolean serverOnly)
    {
        Log.d(TAG, "closeConnectionsWithDescription: " + description);
        logConnectionTable();
        ArrayList<BluetoothConnection> connectionsToClose = new ArrayList<>();
        for (BluetoothConnection connection : this.openConnections)
        {
            if (connection.getServiceDescription().equals(description) && connection.isServerPeer() == serverOnly)
            {
                connectionsToClose.add(connection);
            }
        }

        for (BluetoothConnection connectionToClose : connectionsToClose)
        {
            this.openConnections.remove(connectionToClose);
            connectionToClose.close();
            Log.d(TAG, "closeServiceConnectionsToServiceWithUUID: closed client connections");
        }
    }

    //
    //  ----------  private methods ----------
    //

    /**
     * Iterates over {@link #openConnections} as checks if they
     * are still open {@link BluetoothConnection#isConnected()} equals true.
     * <p>
     * If they are not connected anymore the convention will be closed and removed from the
     * list.
     * <p>
     * This should be executed before checking if a equal connection already exist
     * to prevent blocking a new (rebuild) disconnected by an old, disconnected one.
     *
     * @see #isAlreadyConnected(String, ServiceDescription)
     */
    private void closeAndRemoveZombieConnections()
    {
        Log.d(TAG, "closeAndRemoveZombieConnections: closing zombie sockets");
        logConnectionTable();
        ArrayList<BluetoothConnection> zombies = new ArrayList<>();
        for (BluetoothConnection connection : this.openConnections)
        {
            if (!connection.isConnected() || connection.isClosed())
            {
                zombies.add(connection);
            }
        }

        for (BluetoothConnection zombie : zombies)
        {
            zombie.close();
            Log.d(TAG, "closeAndRemoveZombieConnections: Zombie socket closed " + zombie);

            this.openConnections.remove(zombie);
            Log.d(TAG, "closeAndRemoveZombieConnections: closed zombie connections");
        }
    }

    /**
     * Logs the current
     * {@link #openConnections} list.
     */
    private void logConnectionTable()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("---------------------------------\n");
        for (BluetoothConnection connection : openConnections)
        {
            sb.append(connection);
            sb.append("\n");
        }
        sb.append("---------------------------------\n");
        Log.d(TAG, "currently open connections: \n"
                + sb
        );
    }
}

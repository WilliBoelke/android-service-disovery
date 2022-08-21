package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine;

import android.util.Log;

import java.util.ArrayList;

import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;

/**
 * Holds a list of {@link SdpBluetoothConnection}
 * This helps with:
 * * Checking if a connection already is is established
 * * Closing connections when he socket (for whatever reason) died/closed
 * * Closing all open connections when the Application ends /The SdpBluetoothEngine was shu down.
 * * Closing all connections from or to a specific service.
 * <p>
 * This is utilized in the SdpBluetoothEngine
 */
class SdpBluetoothConnectionManager
{

    //
    //  ----------  instance variables ----------
    //

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private ArrayList<SdpBluetoothConnection> openConnections;


    //
    //  ----------  constructor and initialisation ----------
    //

    protected SdpBluetoothConnectionManager()
    {
        this.openConnections = new ArrayList<>();
    }


    //
    //  ---------- public methods ----------
    //


    protected void addConnection(SdpBluetoothConnection connection)
    {
        this.openConnections.add(connection);
    }

    /**
     * Checks if a connection (defined by device address and service description) already exists
     * For to be sure all sockets will be checked if they are sill connected , and else closed before
     * looking the connection up.
     *
     * @param address
     * the MAC address of the remote BluetoothDevice
     * @param description
     * the Service description
     * @return
     * true when there is an equal connection - else returns false
     */
    protected boolean isAlreadyConnected(String address, ServiceDescription description)
    {
        Log.d(TAG, "isAlreadyConnected: looking for a equal connection that already is established");
        this.closeAndRemoveZombieConnections();
        Log.d(TAG, "isAlreadyConnected: there are " + this.openConnections.size() + " open connections");
        for (SdpBluetoothConnection connection : this.openConnections)
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
        for (SdpBluetoothConnection connection : this.openConnections)
        {
            connection.close();
        }
        this.openConnections = new ArrayList<>();
    }

    /**
     * Closes all connections (sockets) which are clients to the service defined to
     * the given service description,
     * @param description
     * The description of the service
     */
    protected synchronized void closeAllClientConnectionsToService(ServiceDescription description)
    {
        closeConnectionsWithUUID(description, false);
    }


    protected void closeServiceConnectionsToServiceWithUUID(ServiceDescription description)
    {
        closeConnectionsWithUUID(description, true);
    }

    private void closeConnectionsWithUUID(ServiceDescription description, boolean serverOnly)
    {
        ArrayList<SdpBluetoothConnection> connectionsToClose = new ArrayList();
        for (SdpBluetoothConnection connection : this.openConnections)
        {
            if (connection.getServiceDescription().equals(description) && connection.isServerPeer() == serverOnly)
            {
                connectionsToClose.add(connection);
            }
        }

        for (SdpBluetoothConnection connectionToClose : connectionsToClose)
        {
            connectionToClose.close();
            this.openConnections.remove(connectionToClose);
            Log.d(TAG, "closeServiceConnectionsToServiceWithUUID: closed client connections");
        }
    }

    //
    //  ----------  private methods ----------
    //

    /**
     * Iterates over {@link #openConnections} as checks if they
     * are still open {@link SdpBluetoothConnection#isConnected()} equals true.
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
        ArrayList<SdpBluetoothConnection> zombies = new ArrayList();
        for (SdpBluetoothConnection connection : this.openConnections)
        {
            if (!connection.isConnected())
            {
                zombies.add(connection);
            }
        }

        for (SdpBluetoothConnection zombie : zombies)
        {
            zombie.close();
            Log.d(TAG, "closeAndRemoveZombieConnections: Zombie socket closed " + zombie);

            this.openConnections.remove(zombie);
            Log.d(TAG, "closeAndRemoveZombieConnections: closed zombie connections");
        }
    }
}

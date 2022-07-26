package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth;

import android.util.Log;

import java.util.ArrayList;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;

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

    public SdpBluetoothConnectionManager()
    {
        this.openConnections = new ArrayList<>();
    }


    //
    //  ---------- public methods ----------
    //


    public void addConnection(SdpBluetoothConnection connection)
    {
        this.openConnections.add(connection);
    }

    public boolean isAlreadyConnected(String address, UUID serviceUUID)
    {
        Log.d(TAG, "isAlreadyConnected: looking for a equal connection that already is established");
        this.closeAndRemoveZombieConnections();
        Log.d(TAG, "isAlreadyConnected: there are " + this.openConnections.size() + " open connections");
        for (SdpBluetoothConnection connection : this.openConnections)
        {
            Log.d(TAG, "isAlreadyConnected:  Comparing connection " + connection.toString());
            if (connection.getServiceUUID().equals(serviceUUID) && connection.getRemoteDeviceAddress().equals(address))
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
    public void closeAllConnections()
    {
        for (SdpBluetoothConnection connection : this.openConnections)
        {
            connection.close();
        }
        this.openConnections = new ArrayList<>();
    }

    public void closeAllClientConnectionsToServiceWihUUID(UUID serviceUUID)
    {
        closeConnectionsWithUUID(serviceUUID, false);
    }

    public void closeServiceConnectionsToServiceWithUUID(UUID serviceUUID)
    {
        closeConnectionsWithUUID(serviceUUID, true);
    }

    private void closeConnectionsWithUUID(UUID serviceUUID, boolean serverOnly)
    {
        ArrayList<SdpBluetoothConnection> connectionsToClose = new ArrayList();
        for (SdpBluetoothConnection connection : this.openConnections)
        {
            if (connection.getServiceUUID().equals(serviceUUID) && connection.isServerPeer() == serverOnly)
            {
                connectionsToClose.add(connection);
            }
        }

        for (SdpBluetoothConnection connectionToClose : connectionsToClose)
        {
            connectionToClose.close();
            this.openConnections.remove(connectionToClose);
            Log.d(TAG, "closeServiceConnectionsToServiceWithUUID: closed zombie connections");
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
     * @see #isAlreadyConnected(String, UUID)
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

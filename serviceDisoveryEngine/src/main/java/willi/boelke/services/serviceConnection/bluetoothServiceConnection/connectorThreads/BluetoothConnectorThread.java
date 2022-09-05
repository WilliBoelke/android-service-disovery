package willi.boelke.services.serviceConnection.bluetoothServiceConnection.connectorThreads;

import java.util.UUID;

import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothConnection;
import willi.boelke.services.serviceDiscovery.ServiceDescription;


public abstract class BluetoothConnectorThread extends Thread
{

    /**
     * The running thread
     */
    protected Thread thread;
    /**
     * A generated UUID needed for the BluetoothAdapter
     */
    protected ServiceDescription description;


    public interface ConnectionEventListener
    {
        void onConnectionFailed(UUID uuid, BluetoothConnectorThread failedConnector);

        void inConnectionSuccess(BluetoothConnectorThread connector, BluetoothConnection connection);
    }
}

package willi.boelke.serviceDiscovery.bluetooth.sdpConnectorThreads;
import java.util.UUID;

import willi.boelke.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothConnection;
import willi.boelke.serviceDiscovery.serviceDescription.ServiceDescription;


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

        void inConnectionSuccess(BluetoothConnectorThread connector, SdpBluetoothConnection connection);
    }
}

package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpConnectorThreads;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;


public abstract class BluetoothConnectorThread extends Thread
{

    /**
     * The running thread
     */
    protected Thread thread;
    /**
     * A generated UUID needed for the BluetoothAdapter
     */
    protected UUID serviceUUID;


    public interface ConnectionEventListener
    {
        void onConnectionFailed(UUID uuid, BluetoothClientConnector failedClientConnector);

        void inConnectionSuccess(SdpBluetoothConnection connection);
    }
}

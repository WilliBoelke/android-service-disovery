package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpConnectorThreads;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;


public abstract class BluetoothConnectorThread extends Thread
{


    public interface ConnectionEventListener
    {
        void onConnectionFailed(UUID uuid, BluetoothClientConnector failedClientConnector);

        void inConnectionSuccess(SdpBluetoothConnection connection);
    }
}

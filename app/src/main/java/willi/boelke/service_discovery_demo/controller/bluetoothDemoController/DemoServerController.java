package willi.boelke.service_discovery_demo.controller.bluetoothDemoController;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

import willi.boelke.service_discovery_demo.controller.ControllerListener;
import willi.boelke.service_discovery_demo.controller.WriteThread;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothConnection;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothServiceConnectionEngine;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothServiceServer;
import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * This is a demo implementation of a bluetooth sdp "server"
 * as described in the {@link BluetoothServiceServer}.
 * <p>
 * It will report opened client connections to the view using LiveData objets.
 * <p>
 * It will start writing periodic messages to open client connections, reporting which service
 * it is and counting up the message number.
 *
 * @author Willi Boelke
 */
public class DemoServerController implements BluetoothServiceServer
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    /**
     * Instance of the WriteThread,
     * will write messages in a periodical interval
     * to all open connections
     */
    private WriteThread<BluetoothConnection, BluetoothDevice> writer;

    /**
     * The UUID of the advertised service
     */
    private final ServiceDescription serviceDescription;

    private ControllerListener<BluetoothConnection, BluetoothDevice> listener;

    private final CopyOnWriteArrayList<BluetoothConnection> connections;

    //
    //  ---------- constructor and initialisation ----------
    //

    /**
     * Public constructor
     *
     * @param serviceDescription
     *         the serviceDescription of the advertised service
     */
    public DemoServerController(ServiceDescription serviceDescription)
    {
        this.serviceDescription = serviceDescription;
        this.connections = new CopyOnWriteArrayList<>();
        this.writer = new WriteThread<>(connections, serviceDescription, listener);
    }

    //
    //  ---------- bluetooth service server ----------
    //

    @Override
    public void onClientConnected(BluetoothConnection connection)
    {
        Log.d(TAG, "onClientConnected: a client connect, adding to connections ");
        listener.onNewConnection(connection);
        connections.add(connection);
        if(connections.size()==1){
            startWriting();
        }
    }

    //
    //  ---------- start / stop service ----------
    //

    /**
     * Starts the WriteThread if i is not
     * already running
     */
    private void startWriting()
    {
        if (!writer.isRunning())
        {
            writer = new WriteThread<>(connections, serviceDescription, listener);
            writer.start();
        }
    }

    /**
     * Stops the WriteThread
     */
    private void stopWriting()
    {
        if (writer.isRunning())
        {
            writer.cancel();
        }
    }

    /**
     * This starts the advertisement of the service
     * using the {@link BluetoothServiceConnectionEngine}.
     * <p>
     * Please not that the SdpBluetooth engine is require to be initialized at this point.
     */
    public void startService()
    {
        BluetoothServiceConnectionEngine.getInstance().startSDPService(this.serviceDescription, this);
    }

    /**
     * Stops the advertisement of the service.
     */
    public void stopService()
    {
        //--- closing all sockets and stopping service advertisement ---//
        BluetoothServiceConnectionEngine.getInstance().disconnectFromClientsOn(this.serviceDescription);
        BluetoothServiceConnectionEngine.getInstance().stopSDPService(this.serviceDescription);
        //--- notify listener that all connections where closed and clear the list ---//
        this.stopWriting();
        for (BluetoothConnection connection : connections)
        {
            listener.onConnectionLost(connection);
        }
        this.connections.clear();
    }

    //
    //  ----------  listener ----------
    //

    public void setListener(ControllerListener<BluetoothConnection, BluetoothDevice> listener)
    {
        this.listener = listener;
    }
}

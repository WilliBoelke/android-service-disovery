package willi.boelke.service_discovery_demo.controller.bluetoothDemoController;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

import willi.boelke.service_discovery_demo.controller.ControllerListener;
import willi.boelke.service_discovery_demo.controller.ReadThread;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothConnection;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothServiceClient;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothServiceConnectionEngine;
import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * This is a demo implementation for a a bluetooth sdp "client"
 * as defined in the interface {@link BluetoothServiceClient}
 * <p>
 * This works as a client for the {@link DemoServerController}.
 * <p>
 * It will report every change in discovered devices and connected services to the view
 * using LiveData.
 * <p>
 * It will start listening on opened connections and report received messages to the view
 * using LiveData objects.
 *
 * @author Willi Boelke
 */
public class DemoClientController implements BluetoothServiceClient
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private final CopyOnWriteArrayList<BluetoothConnection> connections = new CopyOnWriteArrayList<>();

    private ControllerListener<BluetoothConnection, BluetoothDevice> listener;

    private ReadThread<BluetoothConnection, BluetoothDevice> reader;

    private final ServiceDescription serviceDescription;

    public DemoClientController(ServiceDescription serviceDescription)
    {
        this.serviceDescription = serviceDescription;
        this.reader = new ReadThread<>(connections, listener);
    }

    //
    //  ---------- BluetoothServiceClient ----------
    //

    @Override
    public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
    {
        this.listener.onNewNotification("Discovered service " + description.getServiceUuid());
        Log.d(TAG, "onServiceDiscovered: a service of " + description.getServiceType() + " has been discovered");
    }

    @Override
    public void onConnectedToService(BluetoothConnection connection)
    {
        Log.e(TAG, "onConnectedToService: weird null pointer check " +connection );
        this.connections.add(connection);
        if(this.connections.size() == 1){
            this.startReading();
        }
        this.listener.onNewConnection(connection);
    }

    @Override
    public boolean shouldConnectTo(BluetoothDevice host, ServiceDescription description)
    {
        return true;
    }

    @Override
    public void onPeerDiscovered(BluetoothDevice device)
    {
        Log.d(TAG, "onDevicesInRangeChange: called with new device");
        this.listener.onNewDiscovery(device);
    }


    //
    //  ----------  starting and stopping the client ----------
    //

    /**
     * This starts the client.
     * the client will be registered.
     * <p>
     * The discovers process though needs to be started manually through the
     * BluetoothServiceConnectionEngine.
     */
    public void startClient()
    {
        BluetoothServiceConnectionEngine.getInstance().startDiscoveryForService(serviceDescription, this);
    }

    /**
     * Stops the client by unregistering in from the
     * Engine. This will also disconnect all
     * running connections of this client.
     */
    public void stopClient()
    {
        Log.d(TAG, "stopClient: stopping client for service " + serviceDescription);
        BluetoothServiceConnectionEngine.getInstance().stopDiscoveryForService(serviceDescription);
        BluetoothServiceConnectionEngine.getInstance().disconnectFromServicesWith(serviceDescription);
        stopReading();
        this.listener.onNewNotification("Stopped client for service " + this.serviceDescription.getServiceType());
        this.listener.onMessageChange("");
        // okay so simply put : the read thread will
        // remove most of the closed connections
        // though in some cases it some before i can
        // so here lets remove the rest
        for (BluetoothConnection connection: connections)
        {
            this.listener.onConnectionLost(connection);
        }
    }



    //
    //  ----------  listener ----------
    //


    public void setListener(ControllerListener<BluetoothConnection, BluetoothDevice> listener)
    {
        this.listener = listener;
    }


    //
    //  ----------  reading ----------
    //

    /**
     * Starts a new ReadThread and stores it in {@link #reader}.
     * If a reader is already running i will be canceled first.
     */
    private void startReading()
    {
        if (reader.isRunning())
        {
            stopReading();
        }
        reader = new ReadThread<>(connections, listener);
        reader.start();
    }

    /**
     * Cancels the {@link #reader} if it is currently running.
     */
    private void stopReading()
    {
        if (reader.isRunning())
        {
            reader.cancel();
        }
    }
}



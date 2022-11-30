package willi.boelke.service_discovery_demo.view.demoFragments;

import android.bluetooth.BluetoothDevice;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

import willi.boelke.service_discovery_demo.controller.ControllerListener;
import willi.boelke.service_discovery_demo.controller.bluetoothDemoController.DemoClientController;
import willi.boelke.service_discovery_demo.controller.bluetoothDemoController.DemoServerController;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothConnection;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothServiceConnectionEngine;

/**
 * This is the ViewModel for the BluetoothConnection Fragment.
 * It Serves as layer in between the {@link DemoClientController},
 * {@link DemoServerController}, {@link BluetoothServiceConnectionEngine}
 * and the view.
 * <p>
 * It Stores View Data, like the discovered devices amd
 * established connection over livecycle changes of the view
 *
 * @author WilliBoelke
 */
public class BluetoothConnectionViewModel extends ViewModel
{
    private DemoClientController clientControllerOne;
    private DemoServerController serverControllerOne;
    private DemoClientController clientControllerTwo;
    private DemoServerController serverControllerTwo;

    private final BluetoothServiceConnectionEngine engine;
    private final MutableLiveData<ArrayList<BluetoothConnection>> openConnections = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> latestMessage = new MutableLiveData<>("");
    private final MutableLiveData<String> latestNotification = new MutableLiveData<>("");
    private final MutableLiveData<ArrayList<BluetoothDevice>> discoveredPeers = new MutableLiveData<>(new ArrayList<>());

    public BluetoothConnectionViewModel()
    {
        // Should be initialized and started in view bc of context
        engine = BluetoothServiceConnectionEngine.getInstance();
        this.setupController();
    }

    private void setupController()
    {


        clientControllerOne = new DemoClientController(ServiceDescriptionProvider.getServiceDescriptionOne());
        clientControllerTwo = new DemoClientController(ServiceDescriptionProvider.getServiceDescriptionOTwo());
        serverControllerOne = new DemoServerController(ServiceDescriptionProvider.getServiceDescriptionOne());
        serverControllerTwo = new DemoServerController(ServiceDescriptionProvider.getServiceDescriptionOTwo());

        //
        //  ----------  Client Listener ----------
        //

        // Implementing the listener interface and setting the implementation to both
        // Demo Clients, this will update the Livate data and thus notify the view about changes.

        ControllerListener<BluetoothConnection, BluetoothDevice> clientListener
                = new ControllerListener<BluetoothConnection, BluetoothDevice>()
        {
            @Override
            public void onMessageChange(String message)
            {
                latestMessage.postValue(message);
            }

            @Override
            public void onNewNotification(String notification)
            {
                latestNotification.postValue(notification);
            }

            @Override
            public void onConnectionLost(BluetoothConnection connection)
            {
                ArrayList<BluetoothConnection> tmp = openConnections.getValue();
                tmp.remove(connection);
                openConnections.postValue(tmp);
            }

            @Override
            public void onNewConnection(BluetoothConnection connection)
            {
                ArrayList<BluetoothConnection> tmp = openConnections.getValue();
                tmp.add(connection);
                openConnections.postValue(tmp);
            }

            @Override
            public void onNewDiscovery(BluetoothDevice device)
            {
                ArrayList<BluetoothDevice> tmp = discoveredPeers.getValue();
                if (!tmp.contains(device))
                {
                    tmp.add(device);
                    discoveredPeers.postValue(tmp);
                }
            }
        };

        clientControllerOne.setListener(clientListener);
        clientControllerTwo.setListener(clientListener);


        //
        //  ---------- server listeners ----------
        //

        ControllerListener<BluetoothConnection, BluetoothDevice> serverListener
                = new ControllerListener<BluetoothConnection, BluetoothDevice>()
        {

            @Override
            public void onNewNotification(String notification)
            {
                latestNotification.postValue(notification);
            }

            @Override
            public void onConnectionLost(BluetoothConnection connection)
            {
                ArrayList<BluetoothConnection> tmp = openConnections.getValue();
                tmp.remove(connection);
                openConnections.postValue(tmp);
            }

            @Override
            public void onNewConnection(BluetoothConnection connection)
            {
                ArrayList<BluetoothConnection> tmp = openConnections.getValue();
                tmp.add(connection);
                openConnections.postValue(tmp);
            }

            @Override
            public void onNewDiscovery(BluetoothDevice device)
            {
                // not used in Server
            }

            @Override
            public void onMessageChange(String message)
            {
                // not used in Server
            }
        };

        this.serverControllerOne.setListener(serverListener);
        this.serverControllerTwo.setListener(serverListener);
    }

    protected void starDiscovery()
    {
        this.discoveredPeers.postValue(new ArrayList<>());
        engine.startDeviceDiscovery();
    }

    protected void stopDiscovery()
    {
        engine.stopDeviceDiscovery();
    }


    protected void startServiceOne()
    {
        this.serverControllerOne.startService();
    }

    protected void startServiceTwo()
    {
        this.serverControllerTwo.startService();
    }

    protected void stopServiceOne()
    {
        this.serverControllerOne.stopService();
    }

    protected void stopServiceTwo()
    {
        this.serverControllerTwo.stopService();
    }

    protected void startClientOne()
    {
        this.clientControllerOne.startClient();
    }

    protected void startClientTwo()
    {
        this.clientControllerTwo.startClient();
    }

    protected void refreshServices()
    {
        engine.refreshNearbyServices();
    }

    protected void stopClientOne()
    {
        this.clientControllerOne.stopClient();
    }

    protected void stopClientTwo()
    {
        this.clientControllerTwo.stopClient();
    }


    //
    //  ---------- observable getters ----------
    //


    protected LiveData<ArrayList<BluetoothConnection>> getOpenConnections()
    {
        return this.openConnections;
    }

    protected LiveData<ArrayList<BluetoothDevice>> getDiscoveredDevices()
    {
        return this.discoveredPeers;
    }

    protected LiveData<String> getMessage()
    {
        return this.latestMessage;
    }

    protected LiveData<String> getNotification()
    {
        return this.latestNotification;
    }

    protected void makeDiscoverable()
    {
        engine.startDiscoverable();
    }

    protected void goInactive()
    {
        this.stopServiceOne();
        this.stopServiceTwo();
        this.stopClientOne();
        this.stopClientTwo();
        this.stopDiscovery();
    }
}

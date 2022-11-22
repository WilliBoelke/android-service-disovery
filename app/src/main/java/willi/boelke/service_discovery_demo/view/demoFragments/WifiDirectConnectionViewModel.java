package willi.boelke.service_discovery_demo.view.demoFragments;

import android.net.wifi.p2p.WifiP2pDevice;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashMap;

import willi.boelke.service_discovery_demo.controller.ControllerListener;
import willi.boelke.service_discovery_demo.controller.wifiDemoController.WifiDemoController;
import willi.boelke.services.serviceConnection.wifiDirectServiceConnection.WifiConnection;
import willi.boelke.services.serviceConnection.wifiDirectServiceConnection.WifiDirectConnectionEngine;
import willi.boelke.services.serviceDiscovery.ServiceDescription;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryVOne;

/**
 * This is the ViewModel for the BluetoothDiscoveryFragment.
 * It Serves as layer in between the {@link BluetoothServiceDiscoveryVOne},
 * and the View. It will store and keep view data during configuration changes.
 *
 * @author WilliBoelke
 */
public class WifiDirectConnectionViewModel extends ViewModel
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private final WifiDirectConnectionEngine engine;
    private final MutableLiveData<ArrayList<WifiConnection>> openConnections = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> latestNotification = new MutableLiveData<>("");
    private final MutableLiveData<String> latestMessage = new MutableLiveData<>("");

    private WifiDemoController wifiDemoControllerOne;
    private WifiDemoController wifiDemoControllerTwo;


    public WifiDirectConnectionViewModel()
    {
        // Should be initialized and started in view bc of context
        engine = WifiDirectConnectionEngine.getInstance();
        setupController();
    }


    private void setupController()
    {
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        serviceAttributesTwo.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        ServiceDescription descriptionForServiceOne = new ServiceDescription("Counting Service One", serviceAttributesOne);
        ServiceDescription descriptionForServiceTwo = new ServiceDescription("Counting Service Two", serviceAttributesTwo);
        wifiDemoControllerOne = new WifiDemoController(descriptionForServiceOne);
        wifiDemoControllerTwo = new WifiDemoController(descriptionForServiceTwo);

        //
        //  ----------  Client Listener ----------
        //

        // Implementing the listener interface and setting the implementation to both
        // Demo Clients, this will update the LiveData and thus notify the view about changes.

        ControllerListener<WifiConnection, WifiP2pDevice> listener = new ControllerListener<WifiConnection, WifiP2pDevice>()
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
            public void onConnectionLost(WifiConnection connection)
            {
                ArrayList<WifiConnection> tmp = openConnections.getValue();
                tmp.remove(connection);
                openConnections.postValue(tmp);
            }

            @Override
            public void onNewConnection(WifiConnection connection)
            {
                ArrayList<WifiConnection> tmp = openConnections.getValue();
                tmp.add(connection);
                openConnections.postValue(tmp);
            }

            @Override
            public void onNewDiscovery(WifiP2pDevice device)
            {
                // nothing to do here
            }
        };

        wifiDemoControllerOne.setListener(listener);
        wifiDemoControllerTwo.setListener(listener);
    }


    //
    //  ---------- starting/stopping discovery ----------
    //

    protected void startDiscovery()
    {
        engine.startDiscovery();
    }

    protected void stopDiscovery()
    {
        engine.stopDiscovery();
    }

    protected void startServiceOne()
    {
        wifiDemoControllerOne.startService();
    }

    protected void startServiceTwo()
    {
        wifiDemoControllerTwo.startService();
    }

    public void stopServiceOne()
    {
        this.wifiDemoControllerOne.stop();
    }

    public void stopServiceTwo()
    {
        this.wifiDemoControllerTwo.stop();
    }

    //
    //  ---------- LiveData getter ----------
    //

    protected LiveData<String> getLatestNotification()
    {
        return latestNotification;
    }

    protected LiveData<ArrayList<WifiConnection>> getOpenConnections()
    {
        return openConnections;
    }

    public LiveData<String> getLatestMessage()
    {
        return latestMessage;
    }


    public void goInactive()
    {
        this.stopDiscovery();
        this.stopServiceTwo();
        this.stopServiceOne();
    }

    public void goActive()
    {
    }
}

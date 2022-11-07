package willi.boelke.service_discovery_demo.view.demoFragments;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashMap;

import willi.boelke.services.serviceDiscovery.ServiceDescription;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothDiscoveryEngine;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryListener;

/**
 * This is the ViewModel for the BluetoothDiscoveryFragment.
 * It Serves as layer in between the {@link willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothDiscoveryEngine},
 * and the View. It will store and keep view data during configuration changes.
 *
 * @author WilliBoelke
 */
public class BluetoothDiscoveryViewModel extends ViewModel implements BluetoothServiceDiscoveryListener
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private final BluetoothDiscoveryEngine engine;
    private final MutableLiveData<ArrayList<ServiceDescription>> discoveredServices = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> notification = new MutableLiveData<>();
    private final ServiceDescription descriptionForServiceOne;
    private final ServiceDescription descriptionForServiceTwo;


    public BluetoothDiscoveryViewModel()
    {
        // Should be initialized and started in view bc of context
        engine = BluetoothDiscoveryEngine.getInstance();
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        serviceAttributesTwo.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        descriptionForServiceOne = new ServiceDescription("Counting Service One", serviceAttributesOne);
        descriptionForServiceTwo = new ServiceDescription("Counting Service Two", serviceAttributesTwo);
        engine.registerDiscoverListener(this);
    }


    //
    //  ---------- starting/stopping discovery ----------
    //

    protected void startDiscovery()
    {
        this.discoveredServices.postValue(new ArrayList<>());
        engine.startDeviceDiscovery();
    }

    protected void startSearchServiceOne()
    {
        engine.startDiscoveryForService(descriptionForServiceOne);
    }

    protected void startSearchServiceTwo()
    {
        engine.startDiscoveryForService(descriptionForServiceTwo);
    }

    protected void stopSearchServiceOne()
    {
        engine.stopDiscoveryForService(descriptionForServiceOne);
    }

    protected void stopSearchServiceTwo()
    {
        engine.stopDiscoveryForService(descriptionForServiceTwo);
    }

    protected void refreshServices()
    {
        engine.refreshNearbyServices();
    }


    //
    //  ---------- LiveData getter ----------
    //

    protected LiveData<String> getLatestNotification()
    {
        return notification;
    }

    protected LiveData<ArrayList<ServiceDescription>> getDiscoveredDevices()
    {
        return discoveredServices;
    }

    //
    //  ----------  Discovers Listener ----------
    //

    @SuppressLint("MissingPermission")
    @Override
    public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
    {
        this.notification.postValue("New Service discovered " + description.getServiceUuid());
        ArrayList<ServiceDescription> tmp = discoveredServices.getValue();
        tmp.add(description);
        discoveredServices.postValue(tmp);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onPeerDiscovered(BluetoothDevice device)
    {
        this.notification.postValue("New Peer discovered  { " +
                device.getName() + ", " +
                device.getAddress() + " }");
    }
}
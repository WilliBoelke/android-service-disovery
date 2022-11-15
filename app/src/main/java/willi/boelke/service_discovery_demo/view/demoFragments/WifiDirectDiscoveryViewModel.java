package willi.boelke.service_discovery_demo.view.demoFragments;

import android.net.wifi.p2p.WifiP2pDevice;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashMap;

import willi.boelke.services.serviceDiscovery.ServiceDescription;
import willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery.WifiDirectServiceDiscoveryEngine;
import willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery.WifiServiceDiscoveryListener;

/**
 * This is the ViewModel for the WifiDiscoveryFragment.
 * It Serves as layer in between the {@link WifiDirectServiceDiscoveryEngine},
 * and the View. It will store and keep view data during configuration changes.
 * <p>
 * The Engine as a singleton will (and should be)
 * already initialized and started in the Activity/Fragment
 * since it requires a Context to run, which i don't wanna pass here.
 *
 * @author WilliBoelke
 */
public class WifiDirectDiscoveryViewModel extends ViewModel implements WifiServiceDiscoveryListener
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();
    private final MutableLiveData<ArrayList<ServiceDescription>> discoveredServices
            = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> latestNotification = new MutableLiveData<>("");
    private final ServiceDescription descriptionForServiceTwo;
    private final ServiceDescription descriptionForServiceOne;
    private boolean notifyAboutAllServices = false;


    public WifiDirectDiscoveryViewModel()
    {
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        serviceAttributesTwo.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        descriptionForServiceOne = new ServiceDescription("Counting Service One", serviceAttributesOne);
        descriptionForServiceTwo = new ServiceDescription("Counting Service Two", serviceAttributesTwo);
        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(this);
    }


    //
    //  ---------- starting/stopping discovery ----------
    //

    protected void startDiscovery()
    {
        this.discoveredServices.postValue(new ArrayList<>());
        this.latestNotification.postValue("Start Discovery");
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery();
    }

    protected void startSearchServiceOne()
    {
        this.latestNotification.postValue("Start service 1");
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(descriptionForServiceOne);
        WifiDirectServiceDiscoveryEngine.getInstance().startService(descriptionForServiceOne);
    }

    protected void startSearchServiceTwo()
    {
        this.latestNotification.postValue("Start service 2");
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(descriptionForServiceTwo);
        WifiDirectServiceDiscoveryEngine.getInstance().startService(descriptionForServiceTwo);

    }

    protected void stopSearchServiceOne()
    {
        this.latestNotification.postValue("Stop service 1");
        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscoveryForService(descriptionForServiceOne);
        WifiDirectServiceDiscoveryEngine.getInstance().stopService(descriptionForServiceOne);
    }

    protected void stopSearchServiceTwo()
    {
        this.latestNotification.postValue("Stop service 2");
        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscoveryForService(descriptionForServiceTwo);
        WifiDirectServiceDiscoveryEngine.getInstance().stopService(descriptionForServiceTwo);
    }

    protected void notifyAboutAllServices()
    {
        this.notifyAboutAllServices = !this.notifyAboutAllServices;
        String msg = this.notifyAboutAllServices ? "Looking for aöö services" : "Only specified services";
        this.latestNotification.postValue(msg);
        WifiDirectServiceDiscoveryEngine.getInstance().notifyAboutAllServices(this.notifyAboutAllServices);
    }


    protected void stopDiscovery()
    {
        this.latestNotification.postValue("Stopped discovery");
        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscovery();
    }

    //
    //  ---------- LiveData getter ----------
    //

    protected LiveData<String> getLatestNotification()
    {
        return this.latestNotification;
    }

    protected LiveData<ArrayList<ServiceDescription>> getDiscoveredDevices()
    {
        return this.discoveredServices;
    }

    //
    //  ----------  Discovers Listener ----------
    //


    @Override
    public void onServiceDiscovered(WifiP2pDevice host, ServiceDescription description)
    {
        ArrayList<ServiceDescription> tmp = discoveredServices.getValue();
        tmp.add(description);
        discoveredServices.postValue(tmp);
    }
}

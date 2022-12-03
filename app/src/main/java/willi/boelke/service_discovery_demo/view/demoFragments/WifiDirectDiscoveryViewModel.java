package willi.boelke.service_discovery_demo.view.demoFragments;

import android.net.wifi.p2p.WifiP2pDevice;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

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
    private final MutableLiveData<ArrayList<ServiceDescription>> discoveredServices
            = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> latestNotification = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> notifyAboutAllServices = new MutableLiveData<>(false);


    public WifiDirectDiscoveryViewModel()
    {
        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(this);
    }


    //
    //  ---------- starting/stopping discovery ----------
    //

    protected void startDiscovery()
    {
        this.discoveredServices.postValue(new ArrayList<>());
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery();
    }

    protected void startSearchServiceOne()
    {
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(ServiceDescriptionProvider.getServiceDescriptionOne());
        WifiDirectServiceDiscoveryEngine.getInstance().startService(ServiceDescriptionProvider.getServiceDescriptionOne());
    }

    protected void startSearchServiceTwo()
    {
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(ServiceDescriptionProvider.getServiceDescriptionOTwo());
        WifiDirectServiceDiscoveryEngine.getInstance().startService(ServiceDescriptionProvider.getServiceDescriptionOTwo());
    }

    protected void stopSearchServiceOne()
    {
        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscoveryForService(ServiceDescriptionProvider.getServiceDescriptionOne());
        WifiDirectServiceDiscoveryEngine.getInstance().stopService(ServiceDescriptionProvider.getServiceDescriptionOne());
    }

    protected void stopSearchServiceTwo()
    {
        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscoveryForService(ServiceDescriptionProvider.getServiceDescriptionOTwo());
        WifiDirectServiceDiscoveryEngine.getInstance().stopService(ServiceDescriptionProvider.getServiceDescriptionOTwo());
    }

    protected void notifyAboutAllServices()
    {
        boolean discoverAll = Boolean.FALSE.equals(this.notifyAboutAllServices.getValue());
        this.notifyAboutAllServices.postValue(discoverAll);
        WifiDirectServiceDiscoveryEngine.getInstance().notifyAboutAllServices(discoverAll);
    }


    protected void stopDiscovery()
    {
        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscovery();
    }

    //
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

    protected LiveData<Boolean> getDiscoverAllState()
    {
        return this.notifyAboutAllServices;
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

    public void goInactive()
    {
        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscoveryForService(ServiceDescriptionProvider.getServiceDescriptionOne());
        WifiDirectServiceDiscoveryEngine.getInstance().stopService(ServiceDescriptionProvider.getServiceDescriptionOne());
        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscoveryForService(ServiceDescriptionProvider.getServiceDescriptionOTwo());
        WifiDirectServiceDiscoveryEngine.getInstance().stopService(ServiceDescriptionProvider.getServiceDescriptionOTwo());
        WifiDirectServiceDiscoveryEngine.getInstance().notifyAboutAllServices(false);
        WifiDirectServiceDiscoveryEngine.getInstance().unregisterDiscoveryListener(this);
    }

    public void goActive()
    {
        WifiDirectServiceDiscoveryEngine.getInstance().notifyAboutAllServices(Boolean.TRUE.equals(this.notifyAboutAllServices.getValue()));
        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(this);
    }
}

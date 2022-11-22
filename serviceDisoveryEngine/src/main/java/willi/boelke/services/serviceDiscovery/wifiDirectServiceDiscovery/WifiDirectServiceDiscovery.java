package willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery;

import android.Manifest;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pManager;

import androidx.annotation.RequiresPermission;

import willi.boelke.services.serviceDiscovery.IServiceDiscoveryEngine;
import willi.boelke.services.serviceDiscovery.ServiceDescription;

public interface WifiDirectServiceDiscovery extends IServiceDiscoveryEngine
{

    @Override
    boolean start(Context context);

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    boolean start(Context context, WifiP2pManager manager, WifiP2pManager.Channel channel);

    @Override
    void stop();

    void startDiscovery();

    void stopDiscovery();

    @Override
    void startDiscoveryForService(ServiceDescription description);

    @Override
    void stopDiscoveryForService(ServiceDescription description);

    void startService(ServiceDescription description);

    void stopService(ServiceDescription description);

    void registerDiscoverListener(WifiServiceDiscoveryListener listener);

    void unregisterDiscoveryListener(WifiServiceDiscoveryListener listener);

    void setServiceType(String serviceType);

    @Override
    void notifyAboutAllServices(boolean all);
}

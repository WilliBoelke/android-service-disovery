package willi.boelke.service_discovery_demo.view.ui.wifiDirecServiceDiscoveryDemo;

import android.Manifest;

import androidx.annotation.RequiresPermission;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.UUID;

import willi.boelke.service_discovery_demo.controller.wifiDemoController.WifiDemoController;
import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.SdpWifiConnection;

public class WifiDirectViewModel extends ViewModel
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    WifiDemoController wifiDemoControllerOne;

    WifiDemoController wifiDemoControllerTwo;

    public WifiDirectViewModel()
    {
        this.wifiDemoControllerOne = new WifiDemoController(UUID.fromString("1be0643f-1d98-573b-97cd-ca98a65347dd"));
        this.wifiDemoControllerTwo = new WifiDemoController(UUID.fromString("4be0643f-1d98-573b-97cd-ca98a65347dd"));
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void startServiceOne(){
        this.wifiDemoControllerOne.startService();
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void stopServiceOne(){
        this.wifiDemoControllerOne.stopService();
    }


    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void startServiceTwo(){
        this.wifiDemoControllerTwo.startService();
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void stopServiceTwo(){
        this.wifiDemoControllerTwo.stopService();
    }

    public void stopDiscovery(){

    }

    protected LiveData<ArrayList<SdpWifiConnection>> getConnectionsOne(){
        return this.wifiDemoControllerOne.getConnections();
    }

    protected LiveData<ArrayList<SdpWifiConnection>> getConnectionsTwo (){
        return this.wifiDemoControllerTwo.getConnections();
    }


    protected LiveData<String> getCurrentMessageOne(){
        return this.wifiDemoControllerOne.getCurrentMessage();
    }

    protected LiveData<String> getCurrentMessageTwo(){
        return this.wifiDemoControllerTwo.getCurrentMessage();
    }





}
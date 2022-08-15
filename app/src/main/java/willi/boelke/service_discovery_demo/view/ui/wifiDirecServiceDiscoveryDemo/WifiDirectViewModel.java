package willi.boelke.service_discovery_demo.view.ui.wifiDirecServiceDiscoveryDemo;

import android.Manifest;

import androidx.annotation.RequiresPermission;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.UUID;

import willi.boelke.service_discovery_demo.controller.wifiDemoController.WifiDemoController;
import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;
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
        ServiceDescription descriptionForServiceOne = new ServiceDescription("Service Counting One");
        ServiceDescription descriptionForServiceTwo = new ServiceDescription("Service Counting Two");
        descriptionForServiceOne.addAttribute("info", "this service counts upwards and sends it to all clients");
        descriptionForServiceTwo.addAttribute("info", "this service als counts upwards and sends it to all clients");

        this.wifiDemoControllerOne = new WifiDemoController(descriptionForServiceOne);
        this.wifiDemoControllerTwo = new WifiDemoController(descriptionForServiceOne);
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
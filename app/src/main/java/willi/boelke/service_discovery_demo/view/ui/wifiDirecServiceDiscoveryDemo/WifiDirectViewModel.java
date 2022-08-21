package willi.boelke.service_discovery_demo.view.ui.wifiDirecServiceDiscoveryDemo;

import android.Manifest;

import androidx.annotation.RequiresPermission;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashMap;

import willi.boelke.service_discovery_demo.controller.wifiDemoController.WifiDemoController;
import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;
import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.sdpWifiEngine.SdpWifiConnection;

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
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("service-name", "Counting Service One");
        serviceAttributesOne.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        serviceAttributesTwo.put("service-name", "Counting Service Two");
        serviceAttributesTwo.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        ServiceDescription descriptionForServiceOne = new ServiceDescription(serviceAttributesOne);
        ServiceDescription descriptionForServiceTwo = new ServiceDescription(serviceAttributesTwo);

        this.wifiDemoControllerOne = new WifiDemoController(descriptionForServiceOne);
        this.wifiDemoControllerTwo = new WifiDemoController(descriptionForServiceTwo);
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
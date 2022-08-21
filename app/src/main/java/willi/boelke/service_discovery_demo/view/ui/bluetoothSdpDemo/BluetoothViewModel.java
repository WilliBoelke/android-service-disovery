package willi.boelke.service_discovery_demo.view.ui.bluetoothSdpDemo;

import android.bluetooth.BluetoothDevice;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import willi.boelke.service_discovery_demo.controller.bluetoothDemoController.DemoClientController;
import willi.boelke.service_discovery_demo.controller.bluetoothDemoController.DemoServerController;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothConnection;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;


public class BluetoothViewModel extends ViewModel
{

    private DemoClientController clientControllerOne;
    private DemoServerController serverControllerOne;
    private DemoClientController clientControllerTwo;
    private DemoServerController serverControllerTwo;

    public BluetoothViewModel()
    {
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("service-name", "Counting Service One");
        serviceAttributesOne.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        serviceAttributesTwo.put("service-name", "Counting Service Two");
        serviceAttributesTwo.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        ServiceDescription descriptionForServiceOne = new ServiceDescription(serviceAttributesOne);
        ServiceDescription descriptionForServiceTwo = new ServiceDescription(serviceAttributesTwo);


        clientControllerOne = new DemoClientController(descriptionForServiceOne);
        clientControllerTwo = new DemoClientController(descriptionForServiceTwo);
        serverControllerOne = new DemoServerController(descriptionForServiceOne);
        serverControllerTwo = new DemoServerController(descriptionForServiceTwo);
    }

    protected void starDiscovery(){
        SdpBluetoothEngine.getInstance().startDeviceDiscovery();
    }

    protected void stopDiscovery(){
        SdpBluetoothEngine.getInstance().stopDeviceDiscovery();
    }

    protected void enableDiscoverable(){
        SdpBluetoothEngine.getInstance().startDiscoverable();
    }

    protected void startServiceOne()
    {
        this.serverControllerOne.startWriting();
        this.serverControllerOne.startService();
    }

    protected void startServiceTwo(){
        this.serverControllerTwo.startWriting();
        this.serverControllerTwo.startService();
    } 
    
    protected void stopServiceOne()
    {
        this.serverControllerOne.stopWriting();
        this.serverControllerOne.stopService();
    }
    
    protected void stopServiceTwo(){
        this.serverControllerTwo.stopWriting();
        this.serverControllerTwo.stopService();
    } 

    protected void startClientOne()
    {
        this.clientControllerOne.startReading();
        this.clientControllerOne.startClient();
    }
    protected void startClientTwo(){
        this.clientControllerTwo.startReading();
        this.clientControllerTwo.startClient();
    }

    protected void refreshServices(){
        SdpBluetoothEngine.getInstance().refreshNearbyServices();
    }

    protected void endClientOne()
    {
        this.clientControllerOne.endClient();
    }
    
    protected void endClientTwo(){
        this.clientControllerTwo.endClient();
    } 


    protected LiveData<ArrayList<SdpBluetoothConnection>> getClientOneConnections(){
        return clientControllerOne.getConnections();
    }

    protected LiveData<ArrayList<SdpBluetoothConnection>> getClientTwoConnections(){
        return clientControllerTwo.getConnections();
    }

    protected LiveData<ArrayList<SdpBluetoothConnection>> getServerOneConnections(){
        return serverControllerOne.getConnections();
    }

    protected LiveData<ArrayList<SdpBluetoothConnection>> getServerTwoConnections(){
        return serverControllerTwo.getConnections();
    }

    protected LiveData<ArrayList<BluetoothDevice>> getDevicesInRange()
    {
        return clientControllerOne.getDevicesInRange();
    }

    protected LiveData<String> getMessageServiceOne()
    {
        return clientControllerOne.getLatestMessage();
    }

    protected LiveData<String> getMessageServiceTwo()
    {
        return clientControllerTwo.getLatestMessage();
    }

    protected LiveData<String> getNotificationOne()
    {
        return clientControllerOne.getLatestNotification();
    }

    protected LiveData<String> getNotificationTwo()
    {
        return clientControllerTwo.getLatestNotification();
    }


    protected ArrayList<SdpBluetoothConnection> mergeConnectionLists(ArrayList<SdpBluetoothConnection> listOne, ArrayList<SdpBluetoothConnection> listTwo, ArrayList<SdpBluetoothConnection> listThree, ArrayList<SdpBluetoothConnection> listFour)
    {
        Set<SdpBluetoothConnection> fooSet = new LinkedHashSet<>(listOne);
        fooSet.addAll(listTwo);
        fooSet.addAll(listThree);
        fooSet.addAll(listFour);
        return new ArrayList<>(fooSet);
    }
}
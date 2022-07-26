package willi.boelke.service_discovery_demo.view.ui.bluetoothSdpDemo;

import android.bluetooth.BluetoothDevice;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import willi.boelke.service_discovery_demo.controller.DemoClientController;
import willi.boelke.service_discovery_demo.controller.DemoServerController;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.SdpBluetoothEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;


public class BluetoothViewModel extends ViewModel
{

    private DemoClientController clientControllerOne;
    private DemoServerController serverControllerOne;
    private DemoClientController clientControllerTwo;
    private DemoServerController serverControllerTwo;

    public BluetoothViewModel()
    {
        clientControllerOne = new DemoClientController(UUID.fromString( "4be0643f-1d98-573b-97cd-ca98a65347dd"));
        clientControllerTwo = new DemoClientController(UUID.fromString( "1be0643f-1d98-573b-97cd-ca98a65347dd"));
        serverControllerOne = new DemoServerController(UUID.fromString( "4be0643f-1d98-573b-97cd-ca98a65347dd"));
        serverControllerTwo = new DemoServerController(UUID.fromString( "1be0643f-1d98-573b-97cd-ca98a65347dd"));
    }

    protected void starDiscovery(){
        SdpBluetoothEngine.getInstance().startDiscovery();
    }

    protected void stopDiscovery(){
        SdpBluetoothEngine.getInstance().stopDiscovery();
    }

    protected void enableDiscoverable(){
        SdpBluetoothEngine.getInstance().askToMakeDeviceDiscoverable();
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

    protected ArrayList<SdpBluetoothConnection> mergeConnectionLists(ArrayList<SdpBluetoothConnection> listOne, ArrayList<SdpBluetoothConnection> listTwo, ArrayList<SdpBluetoothConnection> listThree, ArrayList<SdpBluetoothConnection> listFour)
    {
        Set<SdpBluetoothConnection> fooSet = new LinkedHashSet<>(listOne);
        fooSet.addAll(listTwo);
        fooSet.addAll(listThree);
        fooSet.addAll(listFour);
        return new ArrayList<>(fooSet);
    }
}
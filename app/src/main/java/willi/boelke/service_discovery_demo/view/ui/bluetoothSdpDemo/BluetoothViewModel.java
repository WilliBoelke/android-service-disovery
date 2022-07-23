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

    public void starDiscovery(){
        SdpBluetoothEngine.getInstance().startDiscovery();
    }

    public void stopDiscovery(){
        SdpBluetoothEngine.getInstance().stopDiscovery();
    }

    public void enableDiscoverable(){
        SdpBluetoothEngine.getInstance().askToMakeDeviceDiscoverable();
    }

    public void startServiceOne()
    {
        this.serverControllerOne.startWriting();
        this.serverControllerOne.startService();
    }

    public void startServiceTwo(){
        this.serverControllerTwo.startWriting();
        this.serverControllerTwo.startService();
    } 
    
    public void stopServiceOne()
    {
        this.serverControllerOne.stopWriting();
        this.serverControllerOne.stopService();
    }
    
    public void stopServiceTwo(){
        this.serverControllerTwo.stopWriting();
        this.serverControllerTwo.stopService();
    } 

    public void startClientOne()
    {
        this.clientControllerOne.startReading();
        this.clientControllerOne.startClient();
    }
    public void startClientTwo(){
        this.clientControllerTwo.startReading();
        this.clientControllerTwo.startClient();
    }

    public void endClientOne()
    {
        this.clientControllerOne.endClient();
    }
    
    public void endClientTwo(){
        this.clientControllerTwo.endClient();
    } 


    public LiveData<ArrayList<SdpBluetoothConnection>> getClientOneConnections(){
        return clientControllerOne.getConnections();
    }

    public LiveData<ArrayList<SdpBluetoothConnection>> getClientTwoConnections(){
        return clientControllerTwo.getConnections();
    }

    public LiveData<ArrayList<SdpBluetoothConnection>> getServerOneConnections(){
        return serverControllerOne.getConnections();
    }

    public LiveData<ArrayList<SdpBluetoothConnection>> getServerTwoConnections(){
        return serverControllerTwo.getConnections();
    }

    public LiveData<ArrayList<BluetoothDevice>> getDevicesInRange()
    {
        return clientControllerOne.getDevicesInRange();
    }

    public LiveData<String> getMessageServiceOne()
    {
        return clientControllerOne.getLatestMessage();
    }
    public LiveData<String> getMessageServiceTwo()
    {
        return clientControllerTwo.getLatestMessage();
    }

    public ArrayList<SdpBluetoothConnection> mergeConnectionLists(ArrayList<SdpBluetoothConnection> listOne, ArrayList<SdpBluetoothConnection> listTwo, ArrayList<SdpBluetoothConnection> listThree, ArrayList<SdpBluetoothConnection> listFour)
    {
        Set<SdpBluetoothConnection> fooSet = new LinkedHashSet<>(listOne);
        fooSet.addAll(listTwo);
        fooSet.addAll(listThree);
        fooSet.addAll(listFour);
        return new ArrayList<>(fooSet);
    }
}
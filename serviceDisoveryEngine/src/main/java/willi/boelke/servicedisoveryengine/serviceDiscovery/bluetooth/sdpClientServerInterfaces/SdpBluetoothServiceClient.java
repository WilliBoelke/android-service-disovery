package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces;

import android.bluetooth.BluetoothDevice;

import java.util.ArrayList;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;

public interface SdpBluetoothServiceClient
{
    void onServiceDiscovered(String address, UUID serviceUUID);

    void onConnectedToService(SdpBluetoothConnection connection);

    boolean shouldConnectTo(String address, UUID serviceUUID);

    void onDevicesInRangeChange(ArrayList<BluetoothDevice> devices);
}

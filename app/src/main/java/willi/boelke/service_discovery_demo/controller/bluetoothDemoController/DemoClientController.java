package willi.boelke.service_discovery_demo.controller.bluetoothDemoController;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.SdpBluetoothEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceClient;

/**
 * This is a demo implementation for a a bluetooth sdp "client"
 * as defined in the interface {@link SdpBluetoothServiceClient}
 * <p>
 * This works as a client for the {@link DemoServerController}.
 * <p>
 * It will report every change in discovered devices and connected services to the view
 * using LiveData.
 * <p>
 * It will start listening on opened connections and report received messages to the view
 * using LiveData objects.
 *
 * @author Willi Boelke
 */
public class DemoClientController implements SdpBluetoothServiceClient
{

    private final String TAG = this.getClass().getSimpleName();

    private final MutableLiveData<ArrayList<SdpBluetoothConnection>> connections;

    private final MutableLiveData<String> currentMessage;

    private final MutableLiveData<ArrayList<BluetoothDevice>> devicesInRange;

    private ReadThread reader;

    private final UUID serviceUUID;

    public DemoClientController(UUID uuid)
    {
        this.serviceUUID = uuid;
        this.reader = new ReadThread();
        this.connections = new MutableLiveData<>();
        this.devicesInRange = new MutableLiveData<>();
        this.currentMessage = new MutableLiveData<>();
        this.connections.setValue(new ArrayList<>());
        this.devicesInRange.setValue(new ArrayList<>());
        this.currentMessage.setValue("");
    }

    @Override
    public void onServiceDiscovered(String address, UUID serviceUUID)
    {
        Log.d(TAG, "onServiceDiscovered: a service with the UUID " + serviceUUID + " has been discovered");
    }

    @Override
    public void onConnectedToService(SdpBluetoothConnection connection)
    {
        ArrayList<SdpBluetoothConnection> tmp = this.connections.getValue();
        tmp.add(connection);
        this.connections.postValue(tmp);
    }

    @Override
    public boolean shouldConnectTo(String address, UUID serviceUUID)
    {
        return true;
    }

    @Override
    public void onDevicesInRangeChange(ArrayList<BluetoothDevice> devices)
    {
        Log.d(TAG, "onDevicesInRangeChange: called with new devices");
        Log.d(TAG, devices.toString());
        this.devicesInRange.postValue(devices);
    }

    public MutableLiveData<ArrayList<SdpBluetoothConnection>> getConnections()
    {
        return this.connections;
    }

    public MutableLiveData<String> getLatestMessage()
    {
        return this.currentMessage;
    }

    public MutableLiveData<ArrayList<BluetoothDevice>> getDevicesInRange()
    {
        return this.devicesInRange;
    }

    public void startClient()
    {
        SdpBluetoothEngine.getInstance().startSDPDiscoveryForServiceWithUUID(serviceUUID, this);
    }

    public void endClient()
    {
        SdpBluetoothEngine.getInstance().stopSDPDiscoveryForServiceWithUUID(serviceUUID);
    }

    public void startReading()
    {
        reader = new ReadThread();
        reader.start();
    }

    private class ReadThread extends Thread
    {

        private boolean running = true;

        private Thread thread;

        public void run()
        {

            this.thread = Thread.currentThread();

            while (running)
            {
                ArrayList<SdpBluetoothConnection> disconnecedConnections = new ArrayList<>();
                ArrayList<SdpBluetoothConnection> tmpConnections = (ArrayList<SdpBluetoothConnection>) connections.getValue().clone();
                for (SdpBluetoothConnection connection : tmpConnections)
                {
                    if (connection.isConnected())
                    {
                        try
                        {
                            byte[] buffer = new byte[2048];
                            int bytes;
                            bytes = connection.getConnectionSocket().getInputStream().read(buffer);
                            String incomingTransmission = new String(buffer, 0, bytes);
                            currentMessage.postValue(incomingTransmission);
                            Log.d(TAG, "run: Received " + incomingTransmission);
                        }
                        catch (IOException e)
                        {
                            disconnecedConnections.add(connection);
                        }
                    }
                    else
                    {
                        disconnecedConnections.add(connection);
                    }
                }

                for (SdpBluetoothConnection connection : disconnecedConnections)
                {
                    connection.close();
                    tmpConnections.remove(connection);
                    connections.postValue(tmpConnections);
                }
            }
        }

        public void cancel()
        {
            this.running = false;
            this.thread.interrupt();
        }
    }
}



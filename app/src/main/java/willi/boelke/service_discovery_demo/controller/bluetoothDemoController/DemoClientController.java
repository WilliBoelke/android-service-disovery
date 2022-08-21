package willi.boelke.service_discovery_demo.controller.bluetoothDemoController;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.ArrayList;

import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothConnection;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothServiceClient;
import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;

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

    private final MutableLiveData<ArrayList<SdpBluetoothConnection>> connections = new MutableLiveData<>();

    private final MutableLiveData<String> currentMessage = new MutableLiveData<>();

    private final MutableLiveData<String> currentNotification = new MutableLiveData<>();

    private final MutableLiveData<ArrayList<BluetoothDevice>> devicesInRange = new MutableLiveData<>();

    private ReadThread reader;

    private final ServiceDescription serviceDescription;

    public DemoClientController(ServiceDescription serviceDescription)
    {
        this.serviceDescription = serviceDescription;
        this.reader = new ReadThread();
        this.connections.setValue(new ArrayList<>());
        this.devicesInRange.setValue(new ArrayList<>());
        this.currentMessage.setValue("");
        this.currentNotification.setValue("");
    }

    @Override
    public void onServiceDiscovered(String address, ServiceDescription description)
    {
        this.currentNotification.postValue("Discovered service " + description.getServiceUuid());
        Log.d(TAG, "onServiceDiscovered: a service with the UUID " + description + " has been discovered");
    }

    @Override
    public void onConnectedToService(SdpBluetoothConnection connection)
    {
        ArrayList<SdpBluetoothConnection> tmp = this.connections.getValue();
        tmp.add(connection);
        this.connections.postValue(tmp);
        this.currentNotification.postValue("New connection established to " + connection.getRemoteDeviceAddress());
    }

    @Override
    public boolean shouldConnectTo(String address, ServiceDescription description)
    {
        return true;
    }

    @Override
    public void onPeerDiscovered(BluetoothDevice device)
    {
        Log.d(TAG, "onDevicesInRangeChange: called with new device");
        if (!this.devicesInRange.getValue().contains(device))
        {
            ArrayList<BluetoothDevice> tmp = devicesInRange.getValue();
            tmp.add(device);
            this.devicesInRange.postValue(tmp);
            this.currentNotification.postValue("new peer discovered " + device.getAddress());
        }
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
        SdpBluetoothEngine.getInstance().startSDPDiscoveryForService(serviceDescription, this);
    }

    public void endClient()
    {
        SdpBluetoothEngine.getInstance().stopSDPDiscoveryForService(serviceDescription);
        SdpBluetoothEngine.getInstance().disconnectFromServicesWith(serviceDescription);
        this.currentNotification.setValue("disconnected client");
        this.currentMessage.setValue("");
    }

    public void startReading()
    {
        reader = new ReadThread();
        reader.start();
    }

    public LiveData<String> getLatestNotification()
    {
        return this.currentNotification;
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



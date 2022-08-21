package willi.boelke.service_discovery_demo.controller.wifiDemoController;

import android.Manifest;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.ArrayList;

import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;
import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.sdpWifiEngine.SdpWifiConnection;
import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.sdpWifiEngine.SdpWifiEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.sdpClientServerInterfaces.SdpWifiPeer;

/**
 * Demo implementation of a SdpWifiPeer.
 *
 * A peer can either be Group Owner or Client in a Wifi Direct Group.
 * In this demo implementation the GO will periodically write messages
 * tho the clients in the group.
 *
 * other implementations may do it the other wa around or implement a
 * different protocol for communications.
 * Or even the GO routing messages between the clients.
 *
 * @author Willi Boelke
 */
public class WifiDemoController implements SdpWifiPeer
{
    private static final String GROUP_OWNER_DEFAULT_MESSAGE = "writing to clients...";

    /**
     * UUID of the service being advertised / looked for using this controller
     */
    private final ServiceDescription description;

    private MutableLiveData<String> currentMessage = new MutableLiveData<>("");
    private MutableLiveData<Boolean> isGroupOwner = new MutableLiveData<>(Boolean.FALSE);
    private MutableLiveData<ArrayList<SdpWifiConnection>> openConnections = new MutableLiveData<>();
    private ReadThread readThread;
    private WriteThread writeThread;
    private boolean gotRoleAssigned = false;

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    //
    //  ----------  constructor and initialisation ----------
    //

    /**
     * Public constructor
     *
     * @param description
     *   A service description to identify the service and prove additional information about it.
     */
    public WifiDemoController(ServiceDescription description)
    {
        this.openConnections.setValue(new ArrayList<>());
        this.description = description;
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void startService()
    {
        this.writeThread = new WriteThread();
        this.readThread = new ReadThread();
        SdpWifiEngine.getInstance().start(this.description, this);
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void stopService()
    {
        SdpWifiEngine.getInstance().stop();
        SdpWifiEngine.getInstance().disconnectFromGroup();
        try{
            this.writeThread.cancel();
        }
        catch (NullPointerException e){
            Log.d(TAG, "stopService: thread was not initialized");
        }
        try{
            this.readThread.cancel();
        }
        catch (NullPointerException e){
            Log.d(TAG, "stopService: thread was not initialized");
        }
        this.readThread = null;
        this.writeThread = null;
        this.openConnections.setValue(new ArrayList<>());
        this.currentMessage.setValue("");
        this.gotRoleAssigned =false;
    }

    public LiveData<String> getCurrentMessage()
    {
        return this.currentMessage;
    }

    public LiveData<ArrayList<SdpWifiConnection>> getConnections(){
        return  this.openConnections;
    }

    @Override
    public void onServiceDiscovered(String address, ServiceDescription description)
    {
        // this is just for information reasons, there is not much to do at this point
    }


    @Override
    public void onBecameGroupOwner()
    {
        if(! this.gotRoleAssigned) // just the first time
        {
            // lets notify subscribers
            this.isGroupOwner.postValue(Boolean.TRUE);
            this.currentMessage.setValue(GROUP_OWNER_DEFAULT_MESSAGE);
            writeThread.start();

            this.gotRoleAssigned = true;
        }
    }

    @Override
    public void onBecameGroupClient()
    {
        if(!this.gotRoleAssigned) // just the first time
        {
            // lets notify subscribers
            this.isGroupOwner.postValue(Boolean.FALSE);
            readThread.start();

            this.gotRoleAssigned = true;
        }
    }

    @Override
    public void onConnectionEstablished(SdpWifiConnection connection)
    {
        Log.e(TAG, "onConnectionEstablished: got new connection");
        ArrayList<SdpWifiConnection> tmp = this.openConnections.getValue();
        tmp.add(connection);
        this.openConnections.postValue(tmp);
    }

    @Override
    public boolean shouldConnectTo(String address, ServiceDescription description)
    {
        //todo use that actually in the engine
        return true;
    }



    //
    //  ----------  write and read  ----------
    //


    /**
     * The Write thread will iterate through the list of
     * open connections, and write a message to each of them.
     *
     * This will happen periodically with a random waiting time in between.
     *
     * The WriteThread will run indefinitely till it is stopped by calling
     * its `cancel` method.
     *
     * @author Willi Boelke
     */
    private class WriteThread extends Thread
    {
        private final String TAG = this.getClass().getSimpleName();
        private boolean shouldRun = true;
        private boolean isRunning = false;
        private Thread thread;

        public void run()
        {
            this.isRunning = true;
            this.thread = Thread.currentThread();
            int counter = 0;
            while (shouldRun)
            {
                ArrayList<SdpWifiConnection> disconnectedConnections = new ArrayList<>();
                ArrayList<SdpWifiConnection> tmpConnections = (ArrayList<SdpWifiConnection>) openConnections.getValue().clone();
                for (SdpWifiConnection connection : tmpConnections)
                {
                    if (connection.isConnected())
                    {
                        try
                        {
                            String msg = "Test message number " + counter + " from service: " + description;
                            connection.getConnectionSocket().getOutputStream().write(msg.getBytes());
                            connection.getConnectionSocket().getOutputStream().flush();
                        }
                        catch (IOException e)
                        {
                            disconnectedConnections.add(connection);
                        }
                    }
                    else
                    {
                        disconnectedConnections.add(connection);
                    }
                }
                for (SdpWifiConnection closedConnection : disconnectedConnections)
                {
                    closedConnection.close();
                    tmpConnections.remove(closedConnection);
                    openConnections.postValue(tmpConnections);
                }

                try
                {
                    synchronized (this)
                    {
                        int timeOut = 600 + (int) ((Math.random() * (400 - 100)) + 100);
                        this.wait(timeOut);
                    }
                    if (counter < Integer.MAX_VALUE)
                    {
                        counter++;
                    }
                    else
                    {
                        counter = 0;
                    }
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }

        public void cancel()
        {
            this.shouldRun = false;
            this.isRunning = false;
            try
            {
                this.thread.interrupt();
            }
            catch (NullPointerException e){
                Log.e(TAG, "cancel: tried to interrupt thread which was not running");
            }

        }
        public boolean isRunning()
        {
            return this.isRunning;
        }
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
                ArrayList<SdpWifiConnection> disconnecedConnections = new ArrayList<>();
                ArrayList<SdpWifiConnection> tmpConnections = (ArrayList<SdpWifiConnection>) openConnections.getValue().clone();
                for (SdpWifiConnection connection : tmpConnections)
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
                        catch (IOException | IndexOutOfBoundsException e)
                        {
                            disconnecedConnections.add(connection);
                        }
                    }
                    else
                    {
                        disconnecedConnections.add(connection);
                    }
                }

                for (SdpWifiConnection connection : disconnecedConnections)
                {
                    connection.close();
                    tmpConnections.remove(connection);
                    openConnections.postValue(tmpConnections);
                }
            }
        }

        public void cancel()
        {
            this.running = false;
            try
            {
                this.thread.interrupt();
            }
            catch (NullPointerException e){
                Log.e(TAG, "cancel: tried to interrupt thread which was not running");
            }
        }
    }
}

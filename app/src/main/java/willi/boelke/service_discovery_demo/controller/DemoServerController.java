package willi.boelke.service_discovery_demo.controller;

import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.SdpBluetoothEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceServer;

/**
 * This is a Demo Service Server
 * Tt will count up and integer and broadcast it to all connected
 * peers (clients)
 */
public class DemoServerController implements SdpBluetoothServiceServer
{
    private final String TAG = this.getClass().getSimpleName();

    private MutableLiveData<ArrayList<SdpBluetoothConnection>> connections;

    private WriteThread writer;

    private UUID serviceUUID;

    //
    //  ---------- constructor and initialisation ----------
    //

    public DemoServerController(UUID uuid)
    {
        this.serviceUUID = uuid;
        this.writer = new WriteThread();
        this.connections = new MutableLiveData<>();
        this.connections.setValue(new ArrayList<>());
    }


    //
    //  ---------- bluetooth service server ----------
    //

    @Override
    public void onClientConnected(SdpBluetoothConnection connection)
    {
        Log.d(TAG, "onClientConnected: a client connect, adding to connections");
        ArrayList<SdpBluetoothConnection> tempConnections = this.connections.getValue();
        tempConnections.add(connection);
        this.connections.postValue(tempConnections);
    }


    //
    //  ---------- start / stop service ----------
    //

    public void startWriting()
    {
        if(!writer.isRunning()){
            writer = new WriteThread();
            writer.start();
        }
    }

    public void stopWriting(){
        if(writer.isRunning()){
            writer.cancel();
        }
    }

    /**
     * Starts a bluetooth service
     */
    public void startService()
    {
        SdpBluetoothEngine.getInstance().startSDPService("Counting Service", serviceUUID , this);
    }

    public void stopService()
    {
        SdpBluetoothEngine.getInstance().stopSDPService(this.serviceUUID);
    }


    //
    //  ---------- getter and setter ----------
    //

    public MutableLiveData<ArrayList<SdpBluetoothConnection>> getConnections(){
        return this.connections;
    }


    ////
    ////------------  the write thread ---------------
    ////

    private class WriteThread extends Thread {
        private final String TAG = this.getClass().getSimpleName();
      private boolean shouldRun = true;
      private boolean isRunning = false;
      private Thread thread;

        public void run(){
            this.isRunning = true;
            this.thread = Thread.currentThread();
            int counter = 0;
            while(shouldRun)
            {
                ArrayList<SdpBluetoothConnection> disconnectedConnections = new ArrayList<>();
                ArrayList<SdpBluetoothConnection> tmpConnections = (ArrayList<SdpBluetoothConnection>) connections.getValue().clone();
                for (SdpBluetoothConnection connection : tmpConnections)
                {
                    if (connection.isConnected()){
                        try
                        {
                            String msg = "Test message number " + counter + " from service: " + serviceUUID;
                            connection.getConnectionSocket().getOutputStream().write(msg.getBytes());
                            //Log.d(TAG, "run: writing" + counter );
                        }
                        catch (IOException e)
                         {
                             disconnectedConnections.add(connection);

                        }
                    }
                    else{

                        disconnectedConnections.add(connection);
                    }
                }
                for (SdpBluetoothConnection closedConnection : disconnectedConnections)
                {
                    closedConnection.close();
                    tmpConnections.remove(closedConnection);
                    connections.postValue(tmpConnections);
                }

                try
                {
                    synchronized(this){
                        int timeOut = 600 + (int) ((Math.random() * (400 - 100)) + 100);
                         this.wait(timeOut);
                    }
                    if(counter < Integer.MAX_VALUE){
                        counter++;
                    }
                    else{
                        counter = 0;
                    }
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                }
            }

            public void cancel(){
                Log.d(TAG, "cancel: stopping write thread");
                this.shouldRun = false;
                this.isRunning = false;
                this.thread.interrupt();
            }

            public boolean isRunning(){
                return this.isRunning;
            }
    }
}

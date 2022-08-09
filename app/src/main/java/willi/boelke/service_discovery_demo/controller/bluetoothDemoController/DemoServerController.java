package willi.boelke.service_discovery_demo.controller.bluetoothDemoController;

import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.SdpBluetoothEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceServer;

/**
 * This is a demo implementation of a bluetooth sdp "server"
 * as described in the {@link SdpBluetoothServiceServer}.
 * <p>
 * It will report opened client connections to the view using LiveData objets.
 * <p>
 * It will start writing periodic messages to open client connections, reporting which service
 * it is and counting up the message number.
 *
 * @author Willi Boelke
 */
public class DemoServerController implements SdpBluetoothServiceServer
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    /**
     * LiveData ArrayList, this will be updated when new connections
     * are reported or connections closed.
     * <p>
     * It can be obtained using {@link #getConnections()}
     * Other entities may listen to updates on this object to get notified about changes.
     */
    private final MutableLiveData<ArrayList<SdpBluetoothConnection>> connections;

    /**
     * Instance of the WriteThread,
     * will write messages in a periodical interval
     * to all open connections
     */
    private WriteThread writer;

    /**
     * The UUID of the advertised service
     */
    private final UUID serviceUUID;


    //
    //  ---------- constructor and initialisation ----------
    //

    /**
     * Public constructor
     *
     * @param uuid
     *         the UUID of the advertised service
     */
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

    /**
     * Starts the WriteThread if i is not
     * already running
     */
    public void startWriting()
    {
        if (!writer.isRunning())
        {
            writer = new WriteThread();
            writer.start();
        }
    }

    /**
     * Stops the WriteThread
     */
    public void stopWriting()
    {
        if (writer.isRunning())
        {
            writer.cancel();
        }
    }

    /**
     * This starts the advertisement of the service
     * using the {@link SdpBluetoothEngine}.
     *
     * Please not that the SdpBluetooth engine is require to be initialized at this point.
     */
    public void startService()
    {
        SdpBluetoothEngine.getInstance().startSDPService("Counting Service", serviceUUID, this);
    }

    /**
     * Stops the advertisement of the service.
     */
    public void stopService()
    {
        SdpBluetoothEngine.getInstance().stopSDPService(this.serviceUUID);
    }


    //
    //  ---------- getter and setter ----------
    //

    /**
     * Returns a LiveData object containing a
     * up to date list of the currently active connections
     * @return
     * LiveData Array list contracting the connections
     */
    public MutableLiveData<ArrayList<SdpBluetoothConnection>> getConnections()
    {
        return this.connections;
    }


    ////
    ////------------  the write thread ---------------
    ////

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
                ArrayList<SdpBluetoothConnection> disconnectedConnections = new ArrayList<>();
                ArrayList<SdpBluetoothConnection> tmpConnections = (ArrayList<SdpBluetoothConnection>) connections.getValue().clone();
                for (SdpBluetoothConnection connection : tmpConnections)
                {
                    if (connection.isConnected())
                    {
                        try
                        {
                            String msg = "Test message number " + counter + " from service: " + serviceUUID;
                            connection.getConnectionSocket().getOutputStream().write(msg.getBytes());
                            Log.d(TAG, "run: writing" + counter );
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
                for (SdpBluetoothConnection closedConnection : disconnectedConnections)
                {
                    closedConnection.close();
                    tmpConnections.remove(closedConnection);
                    connections.postValue(tmpConnections);
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
            Log.d(TAG, "cancel: stopping write thread");
            this.shouldRun = false;
            this.isRunning = false;
            this.thread.interrupt();
        }

        public boolean isRunning()
        {
            return this.isRunning;
        }
    }
}

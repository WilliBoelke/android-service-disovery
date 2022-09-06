package willi.boelke.service_discovery_demo.controller.bluetoothDemoController;

import android.bluetooth.BluetoothAdapter;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.ArrayList;

import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothConnection;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothServiceConnectionEngine;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothServiceServer;
import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * This is a demo implementation of a bluetooth sdp "server"
 * as described in the {@link BluetoothServiceServer}.
 * <p>
 * It will report opened client connections to the view using LiveData objets.
 * <p>
 * It will start writing periodic messages to open client connections, reporting which service
 * it is and counting up the message number.
 *
 * @author Willi Boelke
 */
public class DemoServerController implements BluetoothServiceServer
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
    private final MutableLiveData<ArrayList<BluetoothConnection>> connections;

    /**
     * Instance of the WriteThread,
     * will write messages in a periodical interval
     * to all open connections
     */
    private WriteThread writer;

    /**
     * The UUID of the advertised service
     */
    private final ServiceDescription serviceDescription;


    //
    //  ---------- constructor and initialisation ----------
    //

    /**
     * Public constructor
     *
     * @param serviceDescription
     *         the serviceDescription of the advertised service
     */
    public DemoServerController(ServiceDescription serviceDescription)
    {
        this.serviceDescription = serviceDescription;
        this.writer = new WriteThread();
        this.connections = new MutableLiveData<>();
        this.connections.setValue(new ArrayList<>());
    }


    //
    //  ---------- bluetooth service server ----------
    //

    @Override
    public void onClientConnected(BluetoothConnection connection)
    {
        Log.d(TAG, "onClientConnected: a client connect, adding to connections");
        ArrayList<BluetoothConnection> tempConnections = this.connections.getValue();
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
     * using the {@link BluetoothServiceConnectionEngine}.
     *
     * Please not that the SdpBluetooth engine is require to be initialized at this point.
     */
    public void startService()
    {
        BluetoothServiceConnectionEngine.getInstance().startSDPService(this.serviceDescription, this);
    }

    /**
     * Stops the advertisement of the service.
     */
    public void stopService()
    {
        BluetoothServiceConnectionEngine.getInstance().disconnectFromClientsWithUUID(this.serviceDescription);
        BluetoothServiceConnectionEngine.getInstance().stopSDPService(this.serviceDescription);
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
    public MutableLiveData<ArrayList<BluetoothConnection>> getConnections()
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
                ArrayList<BluetoothConnection> disconnectedConnections = new ArrayList<>();
                ArrayList<BluetoothConnection> tmpConnections = (ArrayList<BluetoothConnection>) connections.getValue().clone();
                for (BluetoothConnection connection : tmpConnections)
                {
                    if (connection.isConnected())
                    {
                        try
                        {
                            String msg = "Test message number " + counter + " from : " + serviceDescription.getServiceName() + BluetoothAdapter.getDefaultAdapter().getName();
                            connection.getConnectionSocket().getOutputStream().write(msg.getBytes());
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
                for (BluetoothConnection closedConnection : disconnectedConnections)
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

package willi.boelke.services.serviceConnection.bluetoothServiceConnection;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import willi.boelke.services.serviceDiscovery.ServiceDescription;


/**
 * This thread creates a RFCOMM Bluetooth Socket and tries to start an
 * outgoing connection to the remote service
 * server socket with the given serve UUID (through the {@link ServiceDescription})
 * <h2>Usage</h2>
 * This service will run exactly once and report back asynchronous
 * through the callback methods specified in the {@link ConnectionEventListener}
 * with either the created socket or to report failure.
 *
 * @author WilliBoelke
 */
public class BluetoothClientConnector extends BluetoothConnectorThread
{
    //------------Instance Variables------------

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    /**
     * The Bluetooth device to connect with
     */
    private final BluetoothDevice server;

    /**
     * On Receive callback can be implemented somewhere else and set
     * through the setter, will execute when the ConnectedThread receives something
     */
    private final ConnectionEventListener connectionStateChangeListener;

    /**
     * TThe Bluetooth Socket
     */
    private BluetoothSocket mmSocket;

    /**
     * set to false when canceled
     */
    private boolean running = true;


    //------------Constructors------------

    public BluetoothClientConnector(ServiceDescription description, BluetoothDevice server, ConnectionEventListener connectionStateChangeListener)
    {
        this.connectionStateChangeListener = connectionStateChangeListener;
        this.server = server;
        this.description = description;
    }

    //------------Network Connection Methods ------------

    public void run()
    {
        this.thread = currentThread();
        BluetoothSocket tmp = null;
        Log.d(TAG, "run: ----ConnectThread is running---- \n trying to connect to " + this.server.getName() + " | " + this.server.getAddress());

        try
        {
            Log.d(TAG, "run: trying to create a Rfcomm Socket ");
            tmp = server.createRfcommSocketToServiceRecord(this.description.getServiceUuid());
        }
        catch (IOException e)
        {
            Log.d(TAG, "run: could not create a Rfcomm Socket");
            e.printStackTrace();
        }
        mmSocket = tmp;
        try
        {
            Log.d(TAG, "run: random wait in case we found several services...");
            synchronized (this)
            {
                wait(new Random().nextInt(400));
            }
        }
        catch (InterruptedException e)
        {
            // just go on and try to connect if this is interrupted
        }
        Log.d(TAG, "run: socket created - tyring to connect");
        try
        {

            // Blocking call:
            // only return on successful connection or exception
            mmSocket.connect();
        }
        catch (IOException e)
        {
            Log.e(TAG, "run: could not make connection, socket closed ", e);
            try
            {
                mmSocket.close();
                this.connectionStateChangeListener.onConnectionFailed(this.description.getServiceUuid(), this);
                Log.e(TAG, "run: socked closed ");
                return;
            }
            catch (IOException ioException)
            {
                Log.e(TAG, "run: could no close socket   " + Arrays.toString(ioException.getStackTrace()));
                return;
            }
        }
        if(!running){
            Log.d(TAG, "run: Thread was canceled");
            return;
        }
        Log.d(TAG, "run: connection established ");
        this.connectionStateChangeListener.onConnectionSuccess(this, new BluetoothConnection(this.description, mmSocket, false));
        Log.d(TAG, "run: Thread ended");
    }


    @Override
    public void cancel()
    {
        this.running = false;
        this.thread.interrupt();
        try
        {
            this.mmSocket.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (NullPointerException e)
        {
            Log.e(TAG, "cancel: socket was not yet initialized");
        }
    }
}




package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpConnectorThreads;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;


/**
 * his connects a bluetooth sdp client to
 * an bluetooth SDP Server.
 */
public class BluetoothClientConnector extends BluetoothConnectorThread
{
    //------------Instance Variables------------

    /**
     * Log Tag
     */
    private final String TAG = this.getClass().getSimpleName();

    /**
     * The BluetoothAdapter
     */
    private BluetoothAdapter mBluetoothAdapter;

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



    //------------Constructors------------

    public BluetoothClientConnector(BluetoothAdapter mBluetoothAdapter, UUID serviceUUID, BluetoothDevice server, ConnectionEventListener connectionStateChangeListener)
    {
        this.connectionStateChangeListener = connectionStateChangeListener;
        this.server = server;
        this.serviceUUID = serviceUUID;
        this.mBluetoothAdapter = mBluetoothAdapter;
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
            tmp = server.createRfcommSocketToServiceRecord(serviceUUID);
        }
        catch (IOException e)
        {
            Log.d(TAG, "run: could not create a Rfcomm Socket");
            e.printStackTrace();
        }
        mmSocket = tmp;
        // Blocking call:
        // only return on successful connection or exception
        Log.d(TAG, "run: socket created - tyring to connect");
        try
        {
            mmSocket.connect();
        }
        catch (IOException e)
        {
            Log.e(TAG, "run: could not make connection, socket closed  " + Arrays.toString(e.getStackTrace()));
            try
            {
                this.connectionStateChangeListener.onConnectionFailed(this.serviceUUID, this);
                mmSocket.close();
                Log.e(TAG, "run: socked closed ");
                return;
            }
            catch (IOException ioException)
            {
                Log.e(TAG, "run: could no close socket   " + Arrays.toString(ioException.getStackTrace()));
                return;
            }
        }
        Log.d(TAG, "run: connection established ");
        this.connectionStateChangeListener.inConnectionSuccess(new SdpBluetoothConnection(this.serviceUUID, mmSocket, false));
        Log.d(TAG, "run: Thread ended");
    }

    public UUID getServiceUUID()
    {
        return this.serviceUUID;
    }

    public void cancel(){
        this.thread.interrupt();
        try
        {
            this.mmSocket.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}




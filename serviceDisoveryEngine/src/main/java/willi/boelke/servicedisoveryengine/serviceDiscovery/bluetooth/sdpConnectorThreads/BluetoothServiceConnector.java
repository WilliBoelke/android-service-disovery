package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpConnectorThreads;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceServer;


/**
 * This thread registers a Bluetooth Service to the devices service records
 * and accepts incoming connection attempts
 */
public class BluetoothServiceConnector  extends BluetoothConnectorThread
{

    /**
     * Log Tag
     */
    private final String TAG = this.getClass().getSimpleName();

    /**
     * A generated UUID needed for the BluetoothAdapter
     */
    private final UUID serviceUUID;

    /**
     * Name of he Service
     */
    private final String serviceName;

    /**
     * The BluetoothAdapter
     */
    private final BluetoothAdapter mBluetoothAdapter;

    /**
     * Implementation of the BluetoothServiceServer interface, which
     * allows to  get notified about connections trough callbacks
     */
    private final ConnectionEventListener connectionEvenListener;

    /**
     * Bluetooth server socket o accept incoming connections
     */
    private BluetoothServerSocket serverSocket;

    private boolean running;

    private Thread acceptThread;

    //
    //  ----------  constructor and initialisation ----------
    //

    /**
     * Constructor
     *
     * @param bluetoothAdapter
     *         The BluetoothAdapter tto use (usually the defaultAdapter)
     * @param serviceName
     *         The name of he service to be registered to the service records
     * @param serviceUUID
     *         The UUID of the service to be registered in the service records
     * @param connectionEvenListener
     *         Implementation of the {@link ConnectionEventListener} interface
     */
    public BluetoothServiceConnector(BluetoothAdapter bluetoothAdapter, String serviceName, UUID serviceUUID, ConnectionEventListener connectionEvenListener)
    {
        this.mBluetoothAdapter = bluetoothAdapter;
        this.serviceName = serviceName;
        this.serviceUUID = serviceUUID;
        this.connectionEvenListener = connectionEvenListener;
        this.running = true;
    }

    //
    //  ----------  start  ----------
    //

    public synchronized void startService() throws IOException
    {
        Log.d(TAG, "startService : starting Bluetooth Service");
        openServerSocket();
        this.start();
    }


    private void openServerSocket() throws IOException
    {
        Log.d(TAG, "openServerSocket: opening server socket with UUID : " + serviceUUID.toString() + " and name " + serviceName);
        this.serverSocket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(serviceName, serviceUUID);
    }


    //
    //  ----------  run ----------
    //

    public void run()
    {
        this.acceptThread = currentThread();
        while (this.running){
            acceptConnections();
        }
        Log.d(TAG, "run: Accept thread ended final");
    }

    /**
     * Accepts one incoming connection through the {@link #serverSocket}
     * If a connection was established  {@link SdpBluetoothServiceServer#onClientConnected(SdpBluetoothConnection connection)}
     * will be called.
     * <p>
     * This method contains a blocking call  and  will only return after a connection was established !
     */
    private void acceptConnections()
    {
        Log.d(TAG, "run:  Thread started");
        BluetoothSocket socket = null;
        //Blocking Call : Accept thread waits here till another device connects (or canceled)
        Log.d(TAG, "run: RFCOM server socket started, waiting for connections ...");
        try
        {
            socket = this.serverSocket.accept();
            Log.d(TAG, "run: RFCOM server socked accepted client connection");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        if(socket == null)
        {
            Log.d(TAG, "run: Thread was interrupted");
            return;
       }
        Log.d(TAG, "run:  service accepted client connection, opening streams");
        this.connectionEvenListener.inConnectionSuccess(new SdpBluetoothConnection(this.serviceUUID, socket, true));
    }


    //
    //  ----------  end ----------
    //

    public void cancel()
    {
        Log.d(TAG, "cancel: cancelling accept thread");
        this.running = false;
        if(this.acceptThread != null)
        {
            this.acceptThread.interrupt();
            Log.d(TAG, "cancel:  accept thread interrupted");
        }
        try
        {
            this.serverSocket.close();
            Log.d(TAG, "closeConnection: closed AcceptThread");
        }
        catch (NullPointerException | IOException e)
        {
            Log.e(TAG, "closeService:  socket was null  ", e);
        }
    }


    //
    //  ----------  getter and setter ----------
    //

    /**
     * Returns he UUID of this service
     * @return
     */
    public UUID getServiceUUID()
    {
        return this.serviceUUID;
    }

}



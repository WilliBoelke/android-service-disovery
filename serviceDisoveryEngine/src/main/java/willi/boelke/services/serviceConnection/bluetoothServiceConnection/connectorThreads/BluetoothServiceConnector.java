package willi.boelke.services.serviceConnection.bluetoothServiceConnection.connectorThreads;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;

import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothConnection;
import willi.boelke.services.serviceDiscovery.ServiceDescription;


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

    //
    //  ----------  constructor and initialisation ----------
    //

    /**
     * Constructor
     *
     * @param bluetoothAdapter
     *         The BluetoothAdapter tto use (usually the defaultAdapter)
     * @param description
     *         The description of the service, providing a UUId and a service name
     * @param connectionEvenListener
     *         Implementation of the {@link ConnectionEventListener} interface
     */
    public BluetoothServiceConnector(BluetoothAdapter bluetoothAdapter, ServiceDescription description, ConnectionEventListener connectionEvenListener)
    {
        this.mBluetoothAdapter = bluetoothAdapter;
        this.description = description;
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
            Log.d(TAG, "openServerSocket: opening server socket with UUID : " + description.getServiceUuid());
            this.serverSocket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(description.getServiceName().toString(), description.getServiceUuid());

    }

    //
    //  ----------  run ----------
    //

    public void run()
    {
        this.thread = currentThread();
        while (this.running){
            acceptConnections();
        }
        Log.d(TAG, "run: Accept thread ended final");
    }

    /**
     * Accepts one incoming connection through the {@link #serverSocket}
     * If a connection was established  {@link SdpBluetoothServiceServer#onClientConnected(BluetoothConnection connection)}
     * will be called.
     * <p>
     * This method contains a blocking call  and  will only return after a connection was established !
     */
    private void acceptConnections()
    {
            Log.d(TAG, "run:  Thread started");
            BluetoothSocket socket = null;
            //Blocking Call : Accept thread waits here till another device connects (or canceled)
            Log.d(TAG, "run: RFCOMM server socket started, waiting for connections ...");
            try
            {
                socket = this.serverSocket.accept();
                Log.d(TAG, "run: RFCOMM server socked accepted client connection");
            }
            catch (IOException e)
            {
                Log.e(TAG, "acceptConnections: an IOException occurred, trying to fix");
                try
                {
                    Log.e(TAG, "acceptConnections: trying to close socket");
                    this.serverSocket.close();
                }
                catch (IOException e1){
                    Log.e(TAG, "acceptConnections: could not close the socket");
                }
                try
                {
                    Log.e(TAG, "acceptConnections: trying to open new server socket");
                    this.openServerSocket();
                }
                catch (IOException e2){
                    Log.e(TAG, "acceptConnections: failed to open new server socked...shutting down");
                    // todo notify engine / shutdown
                }

            }
            catch (Exception ie)
            {
                // there is an exception throw here, when interrupting in test cases
                // it does not say which one...or why, so i go with a general catch.
                // I guess that it is somehow related to interrupting the thread while it waits for an async
                // response from a mocked method, which also runs a thread, but i am not really sure in the end
                Log.d(TAG, "acceptConnections: an unexpected exception occurred, this maybe is because thread was interrupted");
            }

            if (socket == null)
            {
                Log.d(TAG, "run: Thread was interrupted");
                return;
            }
            Log.d(TAG, "run:  service accepted client connection, opening streams");
            this.connectionEvenListener.inConnectionSuccess(this , new BluetoothConnection(this.description, socket, true));
    }


    //
    //  ----------  end ----------
    //

    public void cancel()
    {
        Log.d(TAG, "cancel: cancelling accept thread");
        this.running = false;
        if(this.thread != null)
        {
            this.thread.interrupt();
            Log.d(TAG, "cancel: accept thread interrupted");
        }
        try
        {
            this.serverSocket.close();
            Log.d(TAG, "cancel: closed AcceptThread");
        }
        catch (NullPointerException | IOException e)
        {
            Log.e(TAG, "cancel: socket was null", e);
        }
    }




    //
    //  ----------  getter and setter ----------
    //

    /**
     * Returns he UUID of this service
     * @return
     */
    public ServiceDescription getService()
    {
        return this.description;
    }

}



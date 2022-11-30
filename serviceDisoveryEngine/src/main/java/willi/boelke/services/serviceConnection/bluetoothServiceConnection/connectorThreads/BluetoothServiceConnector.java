package willi.boelke.services.serviceConnection.bluetoothServiceConnection.connectorThreads;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.util.Random;
import java.util.UUID;

import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothConnection;
import willi.boelke.services.serviceDiscovery.ServiceDescription;


/**
 * This thread registers a SDP service record to the local SDP server
 * and opens a RFCOMM server socket.
 * The bluetooth service record can be found by sdp queries on remote devices
 * and a connections attempts will be accepted here.
 * <h2>Usage</h2>
 * This thread will register the service with the local service
 * on {@link #start()}.
 * A service socket will be opened and connections accepted.
 * This threads runs until ts stopped by calling {@link #cancel()}.
 * While it is running it can accept multiple connections.
 * Established connections will be reported by calling the
 * {@link ConnectionEventListener#onConnectionSuccess(BluetoothConnectorThread, BluetoothConnection)}
 * callback, providing the connected socket and the {@link ServiceDescription}.
 * <p>
 * When a connection fails {@link ConnectionEventListener#onConnectionFailed(UUID, BluetoothConnectorThread)}
 * will be called, though the thread will keep running until canceled.
 *
 * @author WilliBoelke
 */
public class BluetoothServiceConnector extends BluetoothConnectorThread
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    /**
     * The BluetoothAdapter
     */
    private final BluetoothAdapter mBluetoothAdapter;

    /**
     * Implementation of the BluetoothServiceServer interface, which
     * allows to get notified about connections / failures
     */
    private final ConnectionEventListener connectionEvenListener;

    /**
     * Bluetooth server socket o accept incoming connections
     */
    private BluetoothServerSocket serverSocket;

    /**
     * Determines whether the tread is in a running state
     * and should keep on with accepting connections
     * or stop the loop.
     */
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
    //  ----------  run ----------
    //

    public void run()
    {
        this.thread = currentThread();
        Log.d(TAG, "startService : starting Bluetooth Service");
        try
        {
            openServerSocket();
        }
        catch (IOException e)
        {
            connectionEvenListener.onConnectionFailed(this.description.getServiceUuid(), this);
        }

        acceptConnections();
    }

    private void openServerSocket() throws IOException
    {
        Log.d(TAG, "openServerSocket: opening server socket with UUID : " + description.getServiceUuid());
        this.serverSocket =
                mBluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        description.getInstanceName(),
                        description.getServiceUuid()
                );
    }

    /**
     * Accepts one incoming connection through the {@link #serverSocket}
     * If a connection was established  {@link ConnectionEventListener#onConnectionSuccess(BluetoothConnectorThread, BluetoothConnection)}
     * will be called.
     * <p>
     * This method contains a blocking call  and  will only return after a connection was established !
     */
    private void acceptConnections()
    {
        while (this.running)
        {
            Log.d(TAG, "run:  Thread started");
            BluetoothSocket socket = null;
            //Blocking Call : Accept thread waits here till another device connects (or canceled)
            Log.d(TAG, "run: RFCOMM server socket started, waiting for connections ...");
            try
            {
                socket = this.serverSocket.accept();
                Log.d(TAG, "run: RFCOMM server socked accepted client connection");
                synchronized (this)
                {
                    // well just as in the client connector
                    // making it a little random. In case on client connects to
                    // the same time
                    Log.d(TAG, "acceptConnections: giving it some randomness.. ");
                    wait(new Random().nextInt(200));
                }
            }
            catch (IOException e)
            {
                Log.e(TAG, "acceptConnections: an IOException occurred, trying to fix");
                try
                {
                    Log.e(TAG, "acceptConnections: trying to close socket");
                    this.serverSocket.close();
                }
                catch (IOException e1)
                {
                    Log.e(TAG, "acceptConnections: could not close the socket");
                }
                if (this.running)
                {
                    /// notify that failed -- only if it should run
                    this.connectionEvenListener.onConnectionFailed(this.description.getServiceUuid(), this);
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
            this.connectionEvenListener.onConnectionSuccess(this, new BluetoothConnection(this.description, socket, true));
        }
    }


    //
    //  ----------  end ----------
    //

    @Override
    public void cancel()
    {
        Log.d(TAG, "cancel: cancelling accept thread");
        this.running = false;
        if (this.thread != null)
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
     *
     * @return
     */
    public ServiceDescription getService()
    {
        return this.description;
    }
}



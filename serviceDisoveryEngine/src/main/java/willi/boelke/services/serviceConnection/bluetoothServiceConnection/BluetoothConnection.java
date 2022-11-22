package willi.boelke.services.serviceConnection.bluetoothServiceConnection;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import willi.boelke.services.serviceConnection.ServiceConnection;
import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * A point to point connection between two bluetooth enabled devices
 * based on a service.
 * <h2>Description</h2>
 * This combines a {@link BluetoothSocket} with a {@link ServiceDescription}
 * making it easy to handle connection to several services and differentiating
 * between them.
 *
 * @author WilliBoelke
 */
public class BluetoothConnection implements ServiceConnection
{


    //
    //  ----------  instance variables ----------
    //

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    /**
     * This shows if the connection was made as Server or client socket
     * This may be useful for the application
     */
    private final Boolean serverPeer;

    /**
     * The socket to the remote device
     */
    private final BluetoothSocket connectionSocket;

    /**
     * The description of teh service which this
     * connection is based on.
     */
    private final ServiceDescription description;


    //
    //  ----------  constructor and init ----------
    //

    public BluetoothConnection(ServiceDescription description, BluetoothSocket socket, boolean serverPeer)
    {
        this.description = description;
        this.connectionSocket = socket;
        this.serverPeer = serverPeer;
    }


    //
    //  ----------  public methods ----------
    //


    @Override
    public void close()
    {
        Log.d(TAG, "close: closing connection " + this);
        try
        {
            this.connectionSocket.getInputStream().close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        try
        {
            this.connectionSocket.getOutputStream().close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        try
        {
            this.connectionSocket.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isConnected()
    {
        return this.connectionSocket.isConnected();
    }

    /**
     * This just returns the same as {@link #isConnected()} but in reverse
     * it is just here to fulfill the interface requirement.
     *
     * @return true if the socket is not connected else returns false
     */
    @Override
    public boolean isClosed()
    {
        return !this.connectionSocket.isConnected();
    }

    @Override
    public InputStream getInputStream() throws IOException
    {
        return connectionSocket.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException
    {
        return connectionSocket.getOutputStream();
    }


    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        BluetoothConnection that = (BluetoothConnection) o;
        // A SDP Connection is equal o another, when it goes to the same service (UUID) on the same device (Address)
        return Objects.equals(connectionSocket.getRemoteDevice().getAddress(), that.connectionSocket.getRemoteDevice().getAddress()) && this.description.equals(that.description);
    }

    //
    //  ---------- getter and setter  ----------
    //

    public BluetoothDevice getRemoteDevice()
    {
        return this.connectionSocket.getRemoteDevice();
    }

    @Override
    public String getRemoteDeviceAddress()
    {
        return this.getRemoteDevice().getAddress();
    }

    public BluetoothSocket getConnectionSocket()
    {
        return connectionSocket;
    }

    @Override
    public ServiceDescription getServiceDescription()
    {
        return description;
    }

    public boolean isServerPeer()
    {
        return serverPeer;
    }

    @Override
    public String toString()
    {
        return String.format("{|Peer: %-20s|Server: %-5s|UUID: %-36s|}", this.connectionSocket.getRemoteDevice().getName(), this.isServerPeer(), this.description.getServiceUuid());
    }
}

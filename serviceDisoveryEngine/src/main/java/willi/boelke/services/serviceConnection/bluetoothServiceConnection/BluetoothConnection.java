package willi.boelke.services.serviceConnection.bluetoothServiceConnection;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.util.Objects;

import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * A SdpBluetoothConnection is a Point-o-Point o point connection between two service records
 * on bluetooth enabled devices.
 *
 * It is a thin wrapper around a BluetoothSocket to extends it with information about the service
 * it is connected too.
 *
 */
public class BluetoothConnection
{


    //
    //  ----------  instance variables ----------
    //

    private final String TAG = this.getClass().getSimpleName();
    /**
     * This shows if the connection was made as Server or client socket
     * This may be useful for the application
     */
    private Boolean serverPeer;
    private BluetoothSocket connectionSocket;
    private ServiceDescription description;


    //
    //  ----------  constructor and init ----------
    //

    public BluetoothConnection(ServiceDescription description, BluetoothSocket socket, boolean serverPeer){
        this.description = description;
        this.connectionSocket = socket;
        this.serverPeer = serverPeer;
    }


    //
    //  ----------  public methods ----------
    //


    public void close()
    {
        Log.d(TAG, "close: closing connection " + this.toString());
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

    public boolean isConnected()
    {
        return this.connectionSocket.isConnected();
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

    public String getRemoteDeviceAddress(){
        return this.getRemoteDevice().getAddress();
    }

    public BluetoothSocket getConnectionSocket()
    {
        return connectionSocket;
    }

    public ServiceDescription getServiceDescription()
    {
        return description;
    }

    public boolean isServerPeer(){
        return serverPeer;
    }

    @Override
    public String toString(){
        return String.format("{|%-20s|%-20s|%-5s|}", this.connectionSocket.getRemoteDevice().getName(),  this.isServerPeer(), this.description.getServiceUuid());
    }

}

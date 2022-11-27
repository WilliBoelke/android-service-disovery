package willi.boelke.services.serviceConnection.wifiDirectServiceConnection;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import willi.boelke.services.serviceConnection.ServiceConnection;
import willi.boelke.services.serviceDiscovery.ServiceDescription;


public class WifiConnection implements ServiceConnection
{
    private final Socket connectionSocket;

    private ServiceDescription serviceDescription;

    public WifiConnection(Socket socket, ServiceDescription description)
    {
        this.connectionSocket = socket;
        this.serviceDescription = description;
    }

    @Override
    public String getRemoteDeviceAddress()
    {
        return connectionSocket.getRemoteSocketAddress().toString();
    }

    public Socket getConnectionSocket()
    {
        return connectionSocket;
    }

    @Override
    public ServiceDescription getServiceDescription()
    {
        return this.serviceDescription;
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

    /**
     * Returns the connection state of the socket.
     * Note: Closing a socket doesn't clear its connection state, which
     * means this method will return true for a closed socket (see isClosed())
     * if it was successfuly connected prior to being closed.
     */
    @Override
    public boolean isConnected()
    {
        return connectionSocket.isConnected();
    }

    /**
     * Returns the closed state of the socket.
     *
     * @return true if the socket has been closed
     */
    @Override
    public boolean isClosed()
    {
        return connectionSocket.isClosed();
    }

    @NonNull
    @Override
    public String toString()
    {
        return "SdpWifiConnection{" +
                "connectedPeerAddress=" + connectionSocket.getRemoteSocketAddress().toString() +
                ", serviceUUID=" + serviceDescription +
                '}';
    }

    /**
     * Closes the sockets Input- and OutputStream
     * as well as the socket itself.
     */
    @Override
    public void close()
    {
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
}

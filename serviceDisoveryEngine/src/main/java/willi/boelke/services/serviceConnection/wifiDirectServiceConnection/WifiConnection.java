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

    private final ServiceDescription serviceDescription;

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

    @Override
    public boolean isConnected()
    {
        return connectionSocket.isConnected();
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

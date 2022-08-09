package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect;

import android.net.wifi.p2p.WifiP2pDevice;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.UUID;

public class SdpWifiConnection
{
    private Socket connectionSocket;

    private UUID serviceUUID;

    public SdpWifiConnection ( Socket socket, UUID serviceUUID){
        this.connectionSocket = socket;
        this.serviceUUID = serviceUUID;
    }

    public String getRemoteDeviceAddress()
    {
        return connectionSocket.getRemoteSocketAddress().toString();
    }

    public Socket getConnectionSocket()
    {
        return connectionSocket;
    }

    public UUID getServiceUUID(){
        return this.serviceUUID;
    }

    public InputStream getInputStream() throws IOException
    {
        return connectionSocket.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException
    {
        return connectionSocket.getOutputStream();
    }

    public boolean isConnected(){
        return connectionSocket.isConnected();
    }

    @Override
    public String toString()
    {
        return "SdpWifiConnection{" +
                "connectedPeerAddress=" + connectionSocket.getRemoteSocketAddress().toString() +
                ", serviceUUID=" + serviceUUID +
                '}';
    }

    public void close(){
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

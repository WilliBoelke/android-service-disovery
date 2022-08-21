package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.sdpWifiEngine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;

public class SdpWifiConnection
{
    private Socket connectionSocket;

    private ServiceDescription serviceDescription;

    public SdpWifiConnection ( Socket socket, ServiceDescription description){
        this.connectionSocket = socket;
        this.serviceDescription = description;
    }

    public String getRemoteDeviceAddress()
    {
        return connectionSocket.getRemoteSocketAddress().toString();
    }

    public Socket getConnectionSocket()
    {
        return connectionSocket;
    }

    public ServiceDescription getServiceDescription(){
        return this.serviceDescription;
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
                ", serviceUUID=" + serviceDescription +
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

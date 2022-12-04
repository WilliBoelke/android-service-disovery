package willi.boelke.services.serviceConnection.wifiDirectServiceConnection;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;

import willi.boelke.services.serviceDiscovery.ServiceDescription;


public class WifiConnectionTest
{
    private WifiConnection testConnectionOne;
    private WifiConnection testConnectionTwo;
    private Socket testSocketOne;
    private Socket testSocketTwo;


    @Before
    public void setUp()
    {
        testSocketOne = Mockito.mock(Socket.class);
        testSocketTwo = Mockito.mock(Socket.class);

        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("service-name", "Test Service One");
        serviceAttributesOne.put("service-info", "This is a test service description");
        serviceAttributesTwo.put("service-name", "Counting Service Two");
        serviceAttributesTwo.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        ServiceDescription descriptionForServiceOne = new ServiceDescription("Test Service One",
                serviceAttributesOne,
                "_testOne._tcp");
        ServiceDescription descriptionForServiceTwo = new ServiceDescription(
                "Test Service One",
                serviceAttributesTwo,
                "_testTwo._tcp");

        testConnectionOne = new WifiConnection(testSocketOne,descriptionForServiceOne);
        testConnectionTwo = new WifiConnection(testSocketTwo, descriptionForServiceTwo);
    }

    @Test
    public void givesCorrectConnectionInfo()
    {
        when(testSocketOne.isConnected()).thenReturn(true);
        when(testSocketTwo.isConnected()).thenReturn(false);

        assertTrue(testConnectionOne.isConnected());
        assertFalse(testConnectionTwo.isConnected());
    }


    @Test
    public void givesTheCorrectRemoteDeviceAddress()
    {
        when(testSocketOne.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("device",415));
        assertEquals(testConnectionOne.getRemoteDeviceAddress(), "device/<unresolved>:415");
    }

    @Test
    public void closesSocketCorrectly() throws IOException
    {
        InputStream testInputStream = Mockito.mock(InputStream.class);
        OutputStream testOutputStream = Mockito.mock(OutputStream.class);
        when(testSocketOne.getInputStream()).thenReturn(testInputStream);
        when(testSocketOne.getOutputStream()).thenReturn(testOutputStream);

        testConnectionOne.close();

        Mockito.verify(testInputStream, times(1)).close();
        Mockito.verify(testOutputStream, times(1)).close();
        Mockito.verify(testSocketOne, times(1)).close();
    }

}
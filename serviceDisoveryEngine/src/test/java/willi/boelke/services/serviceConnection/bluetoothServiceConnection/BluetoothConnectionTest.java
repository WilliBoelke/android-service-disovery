package willi.boelke.services.serviceConnection.bluetoothServiceConnection;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import willi.boelke.services.serviceDiscovery.ServiceDescription;

@RunWith(MockitoJUnitRunner.class)
public class BluetoothConnectionTest
{
    private BluetoothConnection testConnectionOne;
    private BluetoothConnection testConnectionTwo;
    private BluetoothSocket testSocketOne;
    private BluetoothSocket testSocketTwo;


    @Before
    public void setUp()
    {
        testSocketOne = Mockito.mock(BluetoothSocket.class);
        testSocketTwo = Mockito.mock(BluetoothSocket.class);

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

        testConnectionOne = new BluetoothConnection(descriptionForServiceOne, testSocketOne, false);
        testConnectionTwo = new BluetoothConnection(descriptionForServiceTwo, testSocketTwo, true);
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
    public void correctlyGivesPeerStatus()
    {
        assertTrue(testConnectionTwo.isServerPeer());
        assertFalse(testConnectionOne.isServerPeer());
    }

    @Test
    public void givesTheCorrectRemoteDeviceAddress()
    {
        String testAddress = "This is not a real address";
        BluetoothDevice testRemoteDevice = Mockito.mock(BluetoothDevice.class);
        when(testRemoteDevice.getAddress()).thenReturn(testAddress);
        when(testSocketOne.getRemoteDevice()).thenReturn(testRemoteDevice);

        assertEquals(testConnectionOne.getRemoteDeviceAddress(), testAddress);
    }

    @Test
    public void givesTTheCorrectRemoteDevice()
    {
        BluetoothDevice testRemoteDevice = Mockito.mock(BluetoothDevice.class);
        when(testSocketOne.getRemoteDevice()).thenReturn(testRemoteDevice);

        assertEquals(testConnectionOne.getRemoteDevice(), testRemoteDevice);
    }


    @Test
    public void closesSocketCorrectly() throws IOException
    {
        InputStream testInputStream = Mockito.mock(InputStream.class);
        OutputStream testOutputStream = Mockito.mock(OutputStream.class);
        when(testSocketOne.getInputStream()).thenReturn(testInputStream);
        when(testSocketOne.getOutputStream()).thenReturn(testOutputStream);

        //Just for the lo in that method
        BluetoothDevice testRemoteDevice = Mockito.mock(BluetoothDevice.class);
        when(testRemoteDevice.getName()).thenReturn("testRemoteDevice");
        when(testSocketOne.getRemoteDevice()).thenReturn(testRemoteDevice);

        testConnectionOne.close();

        Mockito.verify(testInputStream, times(1)).close();
        Mockito.verify(testOutputStream, times(1)).close();
        Mockito.verify(testSocketOne, times(1)).close();
    }

    @Test
    public void equalConnections()
    {
        //----------------------------------
        // NOTE : As stated in the requirements
        // a connection equal when {address, uuid}
        // of both connections are equal
        //---------------------------------
        // Different Addresses and UUID
        BluetoothDevice testRemoteDeviceOne = Mockito.mock(BluetoothDevice.class);
        when(testRemoteDeviceOne.getAddress()).thenReturn("no real address one");
        when(testSocketOne.getRemoteDevice()).thenReturn(testRemoteDeviceOne);
        BluetoothDevice testRemoteDeviceTwo = Mockito.mock(BluetoothDevice.class);
        when(testRemoteDeviceTwo.getAddress()).thenReturn("no real address two");
        when(testSocketTwo.getRemoteDevice()).thenReturn(testRemoteDeviceTwo);
        assertNotEquals(testConnectionOne, testConnectionTwo);

        // different UUID
        when(testRemoteDeviceTwo.getAddress()).thenReturn("no real address one");
        when(testSocketTwo.getRemoteDevice()).thenReturn(testRemoteDeviceTwo);
        assertNotEquals(testConnectionOne, testConnectionTwo);
    }
}
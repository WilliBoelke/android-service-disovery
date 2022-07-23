package willi.boelke.servicedisoveryengine.serviceDiscovery.sdpBluetoothConnection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;

@RunWith(MockitoJUnitRunner.class)
public class SdpBluetoothConnectionTest
{
    private final UUID testUUIDOne = UUID.fromString("12345fff-f49a-11ec-b939-0242ac120002");
    private final UUID testUUIDTwo = UUID.fromString("24245fff-f49a-11ec-b939-0425bc220002");

    SdpBluetoothConnection testConnectionOne;
    SdpBluetoothConnection testConnectionTwo;

    BluetoothSocket testSocketOne;
    BluetoothSocket testSocketTwo;
    @Before
    public void setUp() throws Exception
    {
        testSocketOne = Mockito.mock(BluetoothSocket.class);
        testSocketTwo = Mockito.mock(BluetoothSocket.class);

        testConnectionOne = new SdpBluetoothConnection(testUUIDOne, testSocketOne, false);
        testConnectionTwo = new SdpBluetoothConnection(testUUIDTwo, testSocketTwo, true);
    }

    @Test
    public void givesCorrectConnectionInfo(){
        when(testSocketOne.isConnected()).thenReturn(true);
        when(testSocketTwo.isConnected()).thenReturn(false);

        assertTrue(testConnectionOne.isConnected());
        assertFalse(testConnectionTwo.isConnected());
    }

    @Test
    public void correctlyGivesPeerStatus(){
        assertTrue(testConnectionTwo.isServerPeer());
        assertFalse(testConnectionOne.isServerPeer());
    }

    @Test
    public void givesTheCorrectRemoteDeviceAddress(){
        String testAddress = "This is not a real address";
        BluetoothDevice testRemoteDevice = Mockito.mock(BluetoothDevice.class);
        when(testRemoteDevice.getAddress()).thenReturn(testAddress);
        when(testSocketOne.getRemoteDevice()).thenReturn(testRemoteDevice);

        assertEquals(testConnectionOne.getRemoteDeviceAddress(), testAddress);
    }

    @Test
    public void givesTTheCorrectRemoteDevice(){
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
    public void equalConnections(){
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
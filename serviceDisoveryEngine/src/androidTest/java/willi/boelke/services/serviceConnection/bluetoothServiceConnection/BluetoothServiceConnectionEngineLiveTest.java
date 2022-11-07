package willi.boelke.services.serviceConnection.bluetoothServiceConnection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.DEVICE_A;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.DEVICE_B;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.DEVICE_C;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.MAC_A_BT;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.MAC_B_BT;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.MAC_C_BT;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.determineTestRunner;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.getCurrentDeviceName;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.getTestRunner;

import android.Manifest;
import android.arch.core.executor.testing.CountingTaskExecutorRule;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothDiscoveryEngine;
import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * The tests aim to test {@link BluetoothServiceConnectionEngine} and
 * {@link BluetoothDiscoveryEngine}
 * on actual hardware.
 * <p>---------------------------------------------<p>
 * This is more experimental, and i aim to improve on
 * that in the future, but i could find another good way
 * to make this kind of tests, though...i cant be the only one needing this.
 * <p>
 * The tests can fail, they are performed on actual hardware
 * it comes to timing issues between the devices.
 * Sometimes a discovery just does not find the service or device in
 * the specified amount of time.
 * <p>
 * If all test fail check the following :
 * * device names specified ? <br>
 * * wifi / bluetooth available and on ?<br>
 * * in case of bluetooth i observed that UUIDs arent reliably
 * exchanged when a device is low on battery<br>
 * * in case of bluetooth make sure to press the alter dialogue
 * to allow discoverability (sorry cant change that apparently)<br>
 * * check if the tests get out of sync, maybe one adb connection is
 * much slower then the others ?<br>
 * <p>
 * <p>
 * The tests need to run in sync on 3
 * different devices, depending on the adb connection to each
 * and the speed of the devices themself it can fall out of sync.
 * I will try to find a better solution in the future.
 * <p>
 * Also regarding the timing issues- it helps to not run all the tests
 * sequentially, because then delays add up.- maybe run tests one at a time
 * i know that's not really automated (but since the alter dialogue pops up always
 * there need to be someone managing it either way).
 * For that also keep an eye on this:
 * https://stackoverflow.com/questions/73418555/disable-system-alertdialog-in-android-instrumented-tests
 * maybe there will be an answer.
 * <p>---------------------------------------------<p>
 * These tests are to be performed on 3
 * physical devices.
 * The devices are required to have Wifi Direct  set to on.
 * The devices all need to run the tests simultaneously.
 * <p>
 * To run the test a few configurations are needed, to differentiate
 * their names need to be specified beforehand. Same goes for
 * their bluetooth and wifi mac addresses.
 * For that refer to the {@link willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager}
 * ad specify them there.
 * <p>---------------------------------------------<p>
 * General premise - each test will be split into 3 different roles
 * which will execute a different code. Those are defined in additional methods
 * right below the test method itself, following the naming pattern
 * "testCaseName_roleSpecificName"
 *
 * @author WilliBoelke
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class BluetoothServiceConnectionEngineLiveTest
{
    ServiceDescription descriptionForServiceOne;
    ServiceDescription descriptionForServiceTwo;

    @Rule
    public GrantPermissionRule fineLocationPermissionRule = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION);
    @Rule
    public CountingTaskExecutorRule executionerRule = new CountingTaskExecutorRule();

    @BeforeClass
    public static void classSetup()
    {
        determineTestRunner();
    }


    @Before
    public void setup()
    {
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("service-name", "Test Service One");
        serviceAttributesOne.put("service-info", "This is a test service description");
        serviceAttributesTwo.put("service-name", "Counting Service Two");
        serviceAttributesTwo.put("service-info", "This is another test service description");
        descriptionForServiceOne = new ServiceDescription("test service one", serviceAttributesOne);
        descriptionForServiceTwo = new ServiceDescription("test service two", serviceAttributesTwo);
        BluetoothServiceConnectionEngine.getInstance().start(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @After
    public void teardown() throws NullPointerException, InvocationTargetException, IllegalAccessException, NoSuchMethodException
    {
        BluetoothServiceConnectionEngine.getInstance().teardownEngine();
        // tearing down discovery engine with reflections
        Method teardown = BluetoothDiscoveryEngine.getInstance().getClass().getDeclaredMethod("teardownEngine");
        teardown.setAccessible(true);
        teardown.invoke(BluetoothDiscoveryEngine.getInstance());
    }


    //
    //  ----------  connecting to one service ----------
    //

    /**
     * Testing if a connection to one discovered service can be established
     */
    @Test
    public void itShouldConnectToOneNearbyService() throws InterruptedException
    {
        switch (getTestRunner())
        {
            case DEVICE_A:
                itShouldConnectToOneNearbyService_discoverAndConnect();
                break;
            case DEVICE_B:
                itShouldConnectToOneNearbyService_advertiseServiceAndAccept();
                break;
            case DEVICE_C:
                synchronized (this)
                {
                    this.wait(40000); // wait for test to finish
                }
            default:
                System.out.println("device not specified " + getCurrentDeviceName());
        }
    }

    public void itShouldConnectToOneNearbyService_advertiseServiceAndAccept() throws InterruptedException
    {
        ArrayList<BluetoothConnection> acceptedConnections = new ArrayList<>();
        BluetoothServiceConnectionEngine.getInstance().startSDPService(descriptionForServiceOne, acceptedConnections::add);
        BluetoothServiceConnectionEngine.getInstance().startDiscoverable();
        synchronized (this)
        {
            this.wait(40000); // wait for test to finish
        }

        assertEquals(acceptedConnections.get(0).getRemoteDeviceAddress(), MAC_A_BT);
        assertEquals(acceptedConnections.get(0).getServiceDescription(), descriptionForServiceOne);
    }

    public void itShouldConnectToOneNearbyService_discoverAndConnect() throws InterruptedException
    {
        ArrayList<BluetoothConnection> connections = new ArrayList<>();

        BluetoothServiceConnectionEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne, new BluetoothServiceClient()
        {
            @Override
            public void onServiceDiscovered(String address, ServiceDescription description)
            {
                // not under test here
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice peer)
            {
                // not under test here
            }

            @Override
            public void onConnectedToService(BluetoothConnection connection)
            {
                connections.add(connection);
            }

            @Override
            public boolean shouldConnectTo(String address, ServiceDescription description)
            {
                // making shure it only connects to device be, in case other devices are advertising the some service here
                return address.equals(MAC_B_BT); // it should connect7
            }
        });
        BluetoothServiceConnectionEngine.getInstance().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(40000); // device discovery takes about 12s
        }
        assertEquals(connections.get(0).getRemoteDeviceAddress(), MAC_B_BT);
        assertEquals(connections.get(0).getServiceDescription(), descriptionForServiceOne);
    }



    //
    //  ----------  two connections to one device ----------
    //

    @Test
    public void itShouldMakeTwoConnectionsToTwoServicesOnTheSameDevice() throws InterruptedException{
        switch (getTestRunner())
        {
            case DEVICE_A:
                itShouldMakeTwoConnectionsToTwoServicesOnTheSameDevice_discoverAndConnect();
                break;
            case DEVICE_B:
                itShouldMakeTwoConnectionsToTwoServicesOnTheSameDevice_advertiseServiceAndAccept();
                break;
            case DEVICE_C:
                synchronized (this)
                {
                    this.wait(40000); // wait for test to finish
                }
            default:
                System.out.println("device not specified " + getCurrentDeviceName());
        }
    }

    public void itShouldMakeTwoConnectionsToTwoServicesOnTheSameDevice_advertiseServiceAndAccept() throws InterruptedException
    {

        ArrayList<BluetoothConnection> acceptedConnections = new ArrayList<>();
        BluetoothServiceConnectionEngine.getInstance().startSDPService(descriptionForServiceOne, acceptedConnections::add);
        BluetoothServiceConnectionEngine.getInstance().startSDPService(descriptionForServiceTwo, acceptedConnections::add);
        BluetoothServiceConnectionEngine.getInstance().startDiscoverable();
        synchronized (this)
        {
            this.wait(40000); // wait for test to finish
        }

        assertEquals(acceptedConnections.get(0).getRemoteDeviceAddress(), MAC_A_BT);
        assertEquals(acceptedConnections.get(0).getServiceDescription(), descriptionForServiceOne);
    }

    public void itShouldMakeTwoConnectionsToTwoServicesOnTheSameDevice_discoverAndConnect() throws InterruptedException
    {


        ArrayList<ServiceDescription> connectedServices = new ArrayList<>();

        BluetoothServiceConnectionEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne, new BluetoothServiceClient()
        {
            @Override
            public void onServiceDiscovered(String address, ServiceDescription description)
            {
                // not under test here
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice peer)
            {
                // not under test here
            }

            @Override
            public void onConnectedToService(BluetoothConnection connection)
            {
                connectedServices.add(connection.getServiceDescription());
            }

            @Override
            public boolean shouldConnectTo(String address, ServiceDescription description)
            {
                // making sure it only connects to device be, in case other devices are advertising the some service here
                return address.equals(MAC_B_BT); // it should connect7
            }
        });

        BluetoothServiceConnectionEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceTwo, new BluetoothServiceClient()
        {
            @Override
            public void onServiceDiscovered(String address, ServiceDescription description)
            {
                // not under test here
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice peer)
            {
                // not under test here
            }

            @Override
            public void onConnectedToService(BluetoothConnection connection)
            {
                connectedServices.add(connection.getServiceDescription());
            }

            @Override
            public boolean shouldConnectTo(String address, ServiceDescription description)
            {
                // making sure it only connects to device be, in case other devices are advertising the some service here
                return address.equals(MAC_B_BT); // it should connect7
            }
        });
        BluetoothServiceConnectionEngine.getInstance().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(40000); // device discovery takes about 12s
        }

        assertEquals(2, connectedServices.size());
        assertTrue(connectedServices.contains(descriptionForServiceTwo));
        assertTrue(connectedServices.contains(descriptionForServiceOne));
    }

    //
    //  ----------  several clients can connect ----------
    //

    @Test
    public void itShouldAcceptConnectionsFromSeveralClients() throws InterruptedException{
        switch (getTestRunner())
        {
            case DEVICE_A:
                itShouldAcceptConnectionsFromSeveralClients_advertiseServiceAndAccept();
                break;
            case DEVICE_B:
            case DEVICE_C:
                itShouldAcceptConnectionsFromSeveralClients_discoverAndConnect();
                break;
            default:
                System.out.println("device not specified " + getCurrentDeviceName());
        }
    }

    public void itShouldAcceptConnectionsFromSeveralClients_advertiseServiceAndAccept() throws InterruptedException
    {

        ArrayList<ServiceDescription> connectedServices = new ArrayList<>();
        ArrayList<String> connectedClients = new ArrayList<>();
        BluetoothServiceConnectionEngine.getInstance().startSDPService(descriptionForServiceOne, connection ->
        {
            connectedServices.add(connection.getServiceDescription());
            connectedClients.add(connection.getRemoteDeviceAddress());
        });
        BluetoothServiceConnectionEngine.getInstance().startDiscoverable();
        synchronized (this)
        {
            this.wait(40000); // wait for test to finish
        }

        assertEquals(2, connectedServices.size());
        assertEquals(2, connectedClients.size());
        assertTrue(connectedClients.contains(MAC_B_BT));
        assertTrue(connectedClients.contains(MAC_C_BT));
    }

    public void itShouldAcceptConnectionsFromSeveralClients_discoverAndConnect() throws InterruptedException
    {

        ArrayList<ServiceDescription> connectedServices = new ArrayList<>();
        BluetoothServiceConnectionEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne, new BluetoothServiceClient()
        {
            @Override
            public void onServiceDiscovered(String address, ServiceDescription description)
            {
                // not under test here
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice peer)
            {
                // not under test here
            }

            @Override
            public void onConnectedToService(BluetoothConnection connection)
            {
                connectedServices.add(connection.getServiceDescription());
            }

            @Override
            public boolean shouldConnectTo(String address, ServiceDescription description)
            {
               return true;
            }
        });
        BluetoothServiceConnectionEngine.getInstance().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(40000); // device discovery takes about 12s
        }
        assertTrue(true);
    }
}
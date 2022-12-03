package willi.boelke.services.serviceConnection.bluetoothServiceConnection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static willi.boelke.services.testUtils.DeviceRoleManager.DEVICE_A;
import static willi.boelke.services.testUtils.DeviceRoleManager.DEVICE_B;
import static willi.boelke.services.testUtils.DeviceRoleManager.DEVICE_C;
import static willi.boelke.services.testUtils.DeviceRoleManager.MAC_A_BT;
import static willi.boelke.services.testUtils.DeviceRoleManager.MAC_B_BT;
import static willi.boelke.services.testUtils.DeviceRoleManager.MAC_C_BT;
import static willi.boelke.services.testUtils.DeviceRoleManager.determineTestRunner;
import static willi.boelke.services.testUtils.DeviceRoleManager.getCurrentDeviceName;
import static willi.boelke.services.testUtils.DeviceRoleManager.getTestRunner;

import android.Manifest;
import android.arch.core.executor.testing.CountingTaskExecutorRule;
import android.bluetooth.BluetoothDevice;

import androidx.test.ext.junit.runners.AndroidJUnit4;
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
import java.util.ArrayList;
import java.util.HashMap;

import willi.boelke.services.serviceDiscovery.ServiceDescription;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryEngine;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryVTwo;

/**
 * These tests {@link BluetoothServiceConnectionEngine}
 * on actual hardware and with real connections.
 * As a discovery engine {@link BluetoothServiceDiscoveryVTwo}
 * wil be used.
 * <p>
 * <h2>General idea of the test </h2>
 * This test set is build to run on 3 different android and bluetooth
 * enabled devices. During the test run the devices will take different
 * roles - executing different code and expecting different results.
 * For each device to know what to do at runtime they need to be
 * specified beforehand.
 * <p>
 * A device will either advertise one or more services or perform a
 * service discovery.
 * <p>
 * <h2>Limitations and problems</h2>
 * The most severe problem of this test set is a timing issue.
 * The tests need to run in sync on all three devices, though the only
 * moment to establish synchronicity is when the tests are started.
 * After that they can easily fall out of sync though, depending on the devices
 * performance and especially due do the quality of the ADB connection (usb cable etc).
 * <p>
 * To minimize the impact of that the test rely on waiting a few seconds.
 * This though elongates the runtime of the tests.
 * <p>
 * Another issue is that to make a device (bluetooth) discoverable a user
 * input on the device is required and it seems like there
 * is no way to circumvent that even for testing reasons.
 * This means the tests can only work if they are monitored and a user input
 * allows the discoverability each time.
 * <p>
 * I experimented with different lengths of discoverability. The maximum is
 * 300 seconds <a href="https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#EXTRA_DISCOVERABLE_DURATION">
 * Android Documentation</a> this isn't long enough to run all tests at once.
 * <p>
 * Several sources pointed out that setting the discoverable time to 0 will
 * give it an indefinite discoverable time alas my test devices defaulted to 120
 * seconds when using a discoverable time of 0. (This probably worked on older android versions)
 * <p>
 * <h2>Troubleshooting</h2>
 * If all test fail check the following :
 * * device names specified ? <br>
 * * wifi / bluetooth available and on ?<br>
 * * in case of bluetooth i observed that UUIDs arent reliably
 * exchanged when a device is low on battery<br>
 * * in case of bluetooth make sure to press the alter dialogue
 * to allow discoverability (sorry cant change that apparently)<br>
 * * check if the tests get out of sync, maybe one ADB connection is
 * much slower then the others ?<br>
 * <p>
 * Also regarding the timing issues- it helps to not run all the tests
 * sequentially, because then delays add up. maybe run tests one at a time
 * i know that's not really automated (but since the alert dialogue pops up always
 * there needs to be someone managing it either way).
 * For that also keep an eye on this
 * <a href="https://stackoverflow.com/questions/73418555/disable-system-alertdialog-in-android-instrumented-tests">
 * Stack Overflow Question</a>
 * maybe there will be an answer.
 *
 * <p>
 * <h2>Usage</h2>
 * These tests are to be performed on 3
 * physical devices.
 * The devices are required to have Wifi Direct  set to on.
 * The devices all need to run the tests simultaneously.
 * Android Studio allows to select several devices to run
 * the tests.
 * <p>
 * To run the test a few configurations are needed, to differentiate
 * their names need to be specified beforehand. Same goes for
 * their bluetooth and wifi mac addresses. For that refer to the
 * {@link willi.boelke.services.testUtils.DeviceRoleManager}:
 *
 * @author WilliBoelke
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class IntegrationBluetoothServiceConnectionEngine
{
    private ServiceDescription descriptionForServiceOne;
    private ServiceDescription descriptionForServiceTwo;

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
        descriptionForServiceOne = new ServiceDescription("test service one", serviceAttributesOne, "_testOne._tcp");
        descriptionForServiceTwo = new ServiceDescription("test service two", serviceAttributesTwo, "_testTwo._tcp");
    }

    /**
     * To be implement in subclasses to provide a connection engine with
     * a {@link BluetoothServiceDiscoveryEngine}
     *
     * @return the {@link BluetoothServiceConnectionEngine} under test.
     */
    protected abstract BluetoothServiceConnectionEngine getConnectionEngine();


    @After
    public void teardown() throws NullPointerException, InvocationTargetException, IllegalAccessException, NoSuchMethodException
    {
        getConnectionEngine().teardownEngine();
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
        getConnectionEngine().startSDPService(descriptionForServiceOne, acceptedConnections::add);
        getConnectionEngine().startDiscoverable();
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

        getConnectionEngine().startDiscoveryForService(descriptionForServiceOne, new BluetoothServiceClient()
        {
            @Override
            public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
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
            public boolean shouldConnectTo(BluetoothDevice host, ServiceDescription description)
            {
                // making sure it only connects to device be, in case other devices are advertising the some service here
                return host.getAddress().equals(MAC_B_BT); // it should connect7
            }
        });
        getConnectionEngine().startDeviceDiscovery();

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
    public void itShouldMakeTwoConnectionsToTwoServicesOnTheSameDevice() throws InterruptedException
    {
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
        getConnectionEngine().startSDPService(descriptionForServiceOne, acceptedConnections::add);
        getConnectionEngine().startSDPService(descriptionForServiceTwo, acceptedConnections::add);
        getConnectionEngine().startDiscoverable();
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

        getConnectionEngine().startDiscoveryForService(descriptionForServiceOne, new BluetoothServiceClient()
        {
            @Override
            public void onServiceDiscovered(BluetoothDevice address, ServiceDescription description)
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
            public boolean shouldConnectTo(BluetoothDevice host, ServiceDescription description)
            {
                // making sure it only connects to device be, in case other devices are advertising the some service here
                return host.getAddress().equals(MAC_B_BT); // it should connect7
            }
        });

        getConnectionEngine().startDiscoveryForService(descriptionForServiceTwo, new BluetoothServiceClient()
        {
            @Override
            public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
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
            public boolean shouldConnectTo(BluetoothDevice host, ServiceDescription description)
            {
                // making sure it only connects to device be, in case other devices are advertising the some service here
                return host.getAddress().equals(MAC_B_BT); // it should connect7
            }
        });
        getConnectionEngine().startDeviceDiscovery();

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
    public void itShouldAcceptConnectionsFromSeveralClients() throws InterruptedException
    {
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
        getConnectionEngine().startSDPService(descriptionForServiceOne, connection ->
        {
            connectedServices.add(connection.getServiceDescription());
            connectedClients.add(connection.getRemoteDeviceAddress());
        });
        getConnectionEngine().startDiscoverable();
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
        getConnectionEngine().startDiscoveryForService(descriptionForServiceOne, new BluetoothServiceClient()
        {
            @Override
            public void onServiceDiscovered(BluetoothDevice address, ServiceDescription description)
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
            public boolean shouldConnectTo(BluetoothDevice host, ServiceDescription description)
            {
                return true;
            }
        });
        getConnectionEngine().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(40000); // device discovery takes about 12s
        }
        assertTrue(true);
    }
}
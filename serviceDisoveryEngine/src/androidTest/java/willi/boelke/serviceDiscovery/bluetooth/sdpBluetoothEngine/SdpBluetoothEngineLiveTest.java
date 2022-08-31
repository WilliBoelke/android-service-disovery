package willi.boelke.serviceDiscovery.bluetooth.sdpBluetoothEngine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.DEVICE_A;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.DEVICE_B;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.DEVICE_C;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.MAC_A_BT;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.MAC_B_BT;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.MAC_C_BT;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.determineTestRunner;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.getCurrentDeviceName;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.getTestRunner;

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

import willi.boelke.serviceDiscovery.bluetooth.sdpBluetoothDiscovery.SdpBluetoothDiscoveryEngine;
import willi.boelke.serviceDiscovery.serviceDescription.ServiceDescription;

/**
 * The tests aim to test {@link SdpBluetoothEngine} and
 * {@link SdpBluetoothDiscoveryEngine}
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
 * For that refer to the {@link willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager}
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
public class SdpBluetoothEngineLiveTest
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
        serviceAttributesTwo.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        descriptionForServiceOne = new ServiceDescription(serviceAttributesOne);
        descriptionForServiceTwo = new ServiceDescription(serviceAttributesTwo);
        SdpBluetoothEngine.getInstance().start(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @After
    public void teardown() throws NullPointerException, InvocationTargetException, IllegalAccessException, NoSuchMethodException
    {
        SdpBluetoothEngine.getInstance().teardownEngine();
        Method teardown = SdpBluetoothDiscoveryEngine.getInstance().getClass().getDeclaredMethod("teardownEngine");
        teardown.invoke(SdpBluetoothDiscoveryEngine.getInstance());
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
        ArrayList<SdpBluetoothConnection> acceptedConnections = new ArrayList<>();
        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceOne, new SdpBluetoothServiceServer()
        {
            @Override
            public void onClientConnected(SdpBluetoothConnection connection)
            {
                acceptedConnections.add(connection);
            }
        });
        SdpBluetoothEngine.getInstance().startDiscoverable();
        synchronized (this)
        {
            this.wait(40000); // wait for test to finish
        }

        assertEquals(acceptedConnections.get(0).getRemoteDeviceAddress(), MAC_A_BT);
        assertEquals(acceptedConnections.get(0).getServiceDescription(), descriptionForServiceOne);
    }

    public void itShouldConnectToOneNearbyService_discoverAndConnect() throws InterruptedException
    {
        ArrayList<SdpBluetoothConnection> connections = new ArrayList<>();

        SdpBluetoothEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne, new SdpBluetoothServiceClient()
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
            public void onConnectedToService(SdpBluetoothConnection connection)
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
        SdpBluetoothEngine.getInstance().startDeviceDiscovery();

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

        ArrayList<SdpBluetoothConnection> acceptedConnections = new ArrayList<>();
        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceOne, new SdpBluetoothServiceServer()
        {
            @Override
            public void onClientConnected(SdpBluetoothConnection connection)
            {
                acceptedConnections.add(connection);
            }
        });
        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceTwo, new SdpBluetoothServiceServer()
        {
            @Override
            public void onClientConnected(SdpBluetoothConnection connection)
            {
                acceptedConnections.add(connection);
            }
        });
        SdpBluetoothEngine.getInstance().startDiscoverable();
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

        SdpBluetoothEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne, new SdpBluetoothServiceClient()
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
            public void onConnectedToService(SdpBluetoothConnection connection)
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

        SdpBluetoothEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceTwo, new SdpBluetoothServiceClient()
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
            public void onConnectedToService(SdpBluetoothConnection connection)
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
        SdpBluetoothEngine.getInstance().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(40000); // device discovery takes about 12s
        }

        assertTrue(connectedServices.size() == 2);
        assertTrue(connectedServices.contains(descriptionForServiceTwo));
        assertTrue(connectedServices.contains(descriptionForServiceOne));
    }



    //
    //  ----------  if two devices advertise the same service and look for it, only one connection should be established ----------
    //

    @Test
    public void itShouldEstablishOnlyOneConnectionOverOneService() throws InterruptedException{
        switch (getTestRunner())
        {
            case DEVICE_A:
            case DEVICE_B:
                itShouldEstablishOnlyOneConnectionOverOneService_test();
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

    public void itShouldEstablishOnlyOneConnectionOverOneService_test() throws InterruptedException
    {


        ArrayList<ServiceDescription> establishedConnections = new ArrayList<>();
        ArrayList<ServiceDescription> discoveredServices = new ArrayList<>();

        //--- advertising service ---//

        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceOne, new SdpBluetoothServiceServer()
        {
            @Override
            public void onClientConnected(SdpBluetoothConnection connection)
            {
                establishedConnections.add(connection.getServiceDescription());
            }
        });

        SdpBluetoothEngine.getInstance().startDiscoverable();
        SdpBluetoothEngine.getInstance().startDeviceDiscovery();

        //--- looking for service ---//

        SdpBluetoothEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne, new SdpBluetoothServiceClient()
        {
            @Override
            public void onServiceDiscovered(String address, ServiceDescription description)
            {
                discoveredServices.add(description);
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice peer)
            {
                // not under test here
            }

            @Override
            public void onConnectedToService(SdpBluetoothConnection connection)
            {
                establishedConnections.add(connection.getServiceDescription());
            }

            @Override
            public boolean shouldConnectTo(String address, ServiceDescription description)
            {
               return true;
            }
        });



        synchronized (this)
        {
            this.wait(40000); // wait for test to finish
        }

        // both should have only one connection but also one discovery, but one
        // discovery doesn't lead to connections because only one connection is allowed
        assertEquals(1, establishedConnections.size());
        assertTrue(establishedConnections.contains(descriptionForServiceOne));
        assertTrue(discoveredServices.contains(descriptionForServiceOne));
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
        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceOne, new SdpBluetoothServiceServer()
        {
            @Override
            public void onClientConnected(SdpBluetoothConnection connection)
            {
                connectedServices.add(connection.getServiceDescription());
                connectedClients.add(connection.getRemoteDeviceAddress());
            }
        });
        SdpBluetoothEngine.getInstance().startDiscoverable();
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

        SdpBluetoothEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne, new SdpBluetoothServiceClient()
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
            public void onConnectedToService(SdpBluetoothConnection connection)
            {
                connectedServices.add(connection.getServiceDescription());
            }

            @Override
            public boolean shouldConnectTo(String address, ServiceDescription description)
            {
               return true;
            }
        });
        SdpBluetoothEngine.getInstance().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(40000); // device discovery takes about 12s
        }
        assertTrue(true);
    }










    ////
    ////------------  repeat SdpBluetoothDiscoveryEngine test , but wrapped inside the SdpBluetoothEngine ---------------
    ////


    //
    //  ----------  discovers nearby devices ----------
    //

    @Test
    public void itShouldFindNearbyDevice() throws InterruptedException
    {
        switch (getTestRunner())
        {
            case DEVICE_A:
                itShouldFindNearbyDevice_discovery();
                break;
            case DEVICE_B:

            case DEVICE_C:
                itShouldFindNearbyDevice_discoverable();
            default:
                System.out.println("device not specified " + getCurrentDeviceName());
        }
    }

    /**
     * Starts the device discoverability
     */
    private void itShouldFindNearbyDevice_discoverable() throws InterruptedException
    {

        SdpBluetoothEngine.getInstance().startDiscoverable();
        assertTrue(true); // test shouldn't fail on this device
        synchronized (this)
        {
            this.wait(13000); // device discovery takes about 12s
        }
    }

    /**
     * Starts the device discovery and checks if the other two devices where found by
     * looking for their mac addresses
     *
     */
    private void itShouldFindNearbyDevice_discovery() throws InterruptedException
    {


        ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();

        SdpBluetoothEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne, new SdpBluetoothServiceClient()
        {
            @Override
            public void onServiceDiscovered(String address, ServiceDescription description)
            {
                // not under test here
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice peer)
            {
                discoveredDevices.add(peer);
            }

            @Override
            public void onConnectedToService(SdpBluetoothConnection connection)
            {
                // not under test here
            }

            @Override
            public boolean shouldConnectTo(String address, ServiceDescription description)
            {
                return false; // do not connect for now
            }
        });
        SdpBluetoothEngine.getInstance().startDeviceDiscovery();
        synchronized (this)
        {
            this.wait(13000); // device discovery takes about 12s
        }
        assertTrue(discoveredDevices.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_B_BT)));
        assertTrue(discoveredDevices.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_C_BT)));
    }





    //
    //  ----------  discovers one nearby service ----------
    //

    @Test
    public void itShouldFindOneNearbyAvailableService() throws InterruptedException
    {
        switch (getTestRunner())
        {
            case DEVICE_A:
                itShouldFindOneNearbyAvailableServices_serviceDiscovery();
                break;
            case DEVICE_B:
                itShouldFindOneNearbyAvailableService_serviceAdvertisement();
                break;
            case DEVICE_C:
                synchronized (this)
                {
                    this.wait(31000); // wait for test to finish
                }
                assertTrue(true); // test shouldn't fail on this device
            default:
                System.out.println("device not specified " + getCurrentDeviceName());
        }
    }

    /**
     * Starts the device discoverability and a service {@link #descriptionForServiceOne}
     */
    private void itShouldFindOneNearbyAvailableService_serviceAdvertisement() throws InterruptedException
    {

        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceOne, new SdpBluetoothServiceServer()
        {
            @Override
            public void onClientConnected(SdpBluetoothConnection connection)
            {
                // do nothing here, we wont connect ..just advertisement
            }
        });
        SdpBluetoothEngine.getInstance().startDiscoverable();
        synchronized (this)
        {
            this.wait(30000); // wait for test to finish
        }
        assertTrue(true); // test shouldn't fail on this device
    }

    /**
     * Starts the service discovery and checks if the service was found
     */
    private void itShouldFindOneNearbyAvailableServices_serviceDiscovery() throws InterruptedException
    {


        ArrayList<String> serviceHosts = new ArrayList<>();
        ArrayList<ServiceDescription> services = new ArrayList<>();
        SdpBluetoothEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne, new SdpBluetoothServiceClient()
        {
            @Override
            public void onServiceDiscovered(String address, ServiceDescription description)
            {
                serviceHosts.add(address);
                services.add(description);
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice peer)
            {
                // not under test here
            }

            @Override
            public void onConnectedToService(SdpBluetoothConnection connection)
            {
                // not under test here
            }

            @Override
            public boolean shouldConnectTo(String address, ServiceDescription description)
            {
                return false; // do not connect for now
            }
        });

        SdpBluetoothEngine.getInstance().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(30000); // this is the maximum time i give it to find the service (device discovery + fetching uuids)
        }

        System.out.println(serviceHosts);
        assertTrue(serviceHosts.contains(MAC_B_BT));
        assertTrue(services.contains(descriptionForServiceOne));
    }





    //
    //  ----------  discovers 2 services on the same device  ----------
    //

    @Test
    public void itShouldFindTwoNearbyAvailableServices() throws InterruptedException
    {
        switch (getTestRunner())
        {
            case DEVICE_A:
                itShouldFindTwoNearbyAvailableServices_serviceDiscovery();
                break;
            case DEVICE_B:
            case DEVICE_C:
                itShouldFindTwoNearbyAvailableServices_serviceAdvertisement();
                break;
            default:
                System.out.println("device not specified " + getCurrentDeviceName());
        }
    }

    /**
     * Starts the device discoverability and a service {@link #descriptionForServiceOne}
     */
    private void itShouldFindTwoNearbyAvailableServices_serviceAdvertisement() throws InterruptedException
    {

        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceOne, new SdpBluetoothServiceServer()
        {
            @Override
            public void onClientConnected(SdpBluetoothConnection connection)
            {
                // do nothing here, we wont connect ..just advertisement
            }
        });
        SdpBluetoothEngine.getInstance().startDiscoverable();
        synchronized (this)
        {
            this.wait(30000); // wait for test to finish
        }
        assertTrue(true); // test shouldn't fail on this device
    }

    /**
     * Starts the service discovery and checks if the service was found
     */
    private void itShouldFindTwoNearbyAvailableServices_serviceDiscovery() throws InterruptedException
    {


        ArrayList<String> serviceHosts = new ArrayList<>();
        ArrayList<ServiceDescription> services = new ArrayList<>();
        SdpBluetoothEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne, new SdpBluetoothServiceClient()
        {
            @Override
            public void onServiceDiscovered(String address, ServiceDescription description)
            {
                serviceHosts.add(address);
                services.add(description);
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice peer)
            {
                // not under test here
            }

            @Override
            public void onConnectedToService(SdpBluetoothConnection connection)
            {
                // not under test here
            }

            @Override
            public boolean shouldConnectTo(String address, ServiceDescription description)
            {
                return false; // do not connect for now
            }
        });

        SdpBluetoothEngine.getInstance().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(30000); // this is the maximum time i give it to find the service
        }

        assertTrue(serviceHosts.contains(MAC_B_BT));
        assertTrue(serviceHosts.contains(MAC_C_BT));
        assertTrue(services.contains(descriptionForServiceOne));
    }




    //
    //  ----------  it finds two different services on separate devices ----------
    //

    @Test
    public void itShouldFindTwoDifferentServicesOnSeparateDevice() throws InterruptedException
    {
        switch (getTestRunner())
        {
            case DEVICE_A:
                itShouldFindTwoDifferentServicesOnSeparateDevice_serviceDiscovery();
                break;
            case DEVICE_B:
                itShouldFindTwoDifferentServicesOnSeparateDevice_serviceAdvertisement_A();
                break;
            case DEVICE_C:
                itShouldFindTwoDifferentServicesOnSeparateDevice_serviceAdvertisement_B();
                break;
            default:
                System.out.println("device not specified " + getCurrentDeviceName());
        }
    }

    private void itShouldFindTwoDifferentServicesOnSeparateDevice_serviceAdvertisement_B() throws InterruptedException
    {

        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceOne, new SdpBluetoothServiceServer()
        {
            @Override
            public void onClientConnected(SdpBluetoothConnection connection)
            {
                // do nothing here, we wont connect ..just advertisement
            }
        });
        SdpBluetoothEngine.getInstance().startDiscoverable();
        synchronized (this)
        {
            this.wait(30000); // wait for test to finish
        }
        assertTrue(true); // test shouldn't fail on this device
    }

    private void itShouldFindTwoDifferentServicesOnSeparateDevice_serviceAdvertisement_A() throws InterruptedException
    {

        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceTwo, new SdpBluetoothServiceServer()
        {
            @Override
            public void onClientConnected(SdpBluetoothConnection connection)
            {
                // do nothing here, we wont connect ..just advertisement
            }
        });
        SdpBluetoothEngine.getInstance().startDiscoverable();
        synchronized (this)
        {
            this.wait(30000); // wait for test to finish
        }
        assertTrue(true); // test shouldn't fail on this device
    }

    private void itShouldFindTwoDifferentServicesOnSeparateDevice_serviceDiscovery() throws InterruptedException
    {


        ArrayList<String> serviceHosts = new ArrayList<>();
        ArrayList<ServiceDescription> services = new ArrayList<>();
        SdpBluetoothEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne, new SdpBluetoothServiceClient()
        {
            @Override
            public void onServiceDiscovered(String address, ServiceDescription description)
            {
                serviceHosts.add(address);
                services.add(description);
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice peer)
            {
                // not under test here
            }

            @Override
            public void onConnectedToService(SdpBluetoothConnection connection)
            {
                // not under test here
            }

            @Override
            public boolean shouldConnectTo(String address, ServiceDescription description)
            {
                return false; // do not connect for now
            }
        });

        SdpBluetoothEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceTwo, new SdpBluetoothServiceClient()
        {
            @Override
            public void onServiceDiscovered(String address, ServiceDescription description)
            {
                serviceHosts.add(address);
                services.add(description);
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice peer)
            {
                // not under test here
            }

            @Override
            public void onConnectedToService(SdpBluetoothConnection connection)
            {
                // not under test here
            }

            @Override
            public boolean shouldConnectTo(String address, ServiceDescription description)
            {
                return false; // do not connect for now
            }
        });


        SdpBluetoothEngine.getInstance().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(30000); // this is the maximum time i give it to find the service
        }

        assertTrue(serviceHosts.contains(MAC_B_BT));
        assertTrue(serviceHosts.contains(MAC_C_BT));
        assertTrue(services.contains(descriptionForServiceOne));
    }
}
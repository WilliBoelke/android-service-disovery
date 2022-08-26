package willi.boelke.servicedisoveryengine.wifiDirect.sdpWifiDirectDiscovery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

import java.util.ArrayList;
import java.util.HashMap;

import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothDiscovery.SdpBluetoothDiscoveryEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothConnection;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothServiceClient;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothServiceServer;
import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;

/**
 * These test should run on 3 actual devices
 * which need to be bluetooth enabled.
 * Emulators cant be used.
 * <p>
 * Thew tests are integration tests for the whole
 * 'bluetooth.sdpServiceDiscovery' package.
 * <p>
 * `bluetooth.sdpBluetoothEngine` will also be used,
 * though that's not the focus here
 * <p>
 * The tests are required to run on all 3 devices simultaneously
 * (in android studio you can choose several devices at once)
 * <p>
 * The tests are expected to run under laboratory conditions,
 * though an interference should not happen.
 * <p>
 * These tests needs a little setup.
 * names and mac addresses of the 3 devices need to be specified beforehand
 * to differentiate them at runtime, and execute the supposed code at each of them
 * <p>
 * The tests require a person to click on allowing the devices to become discoverable
 * There doesn't seem a way to bypass this.
 * <p>
 * There is another tests set, where the android api is mocked
 * these will run quicker and without someone having to stick around ;)
 * <p>
 * I noticed that the test may not work when devices are low (<10%) of battery
 * so they should be charged well too
 * <p>
 * <p>
 * Premise :
 * Each method annotated with `@test` is the "entrance" into a test scenario
 * on all the devices. It has a set of methods which contain the code depending on the devices
 * role.
 * The role dependant methods are named like the test case, with an additional identifier in the end.
 * <p>
 * Since all test run independently on different hardware there is no way to keep them synchronized
 * perfectly, so each device- depending on its role has to wait for the other devices to finish.
 * This is solved using wait()
 *
 * ------
 * These test CAN FAIL, that happens as well as sometimes a service wont be found
 * on the actual device, this can be case by timing issues (these test have them, depending on the devices
 * and the abd connection from the PC to them, this can cause serious delays on one device right at the start)
 * Also as mentioned above the battery can influence the discoverability of services.
 *
 * Also the amount of bluetooth devices can influence the timing, when there are many, then there
 * are many SDP request, this may cause a test to run out of time.
 *
 * So to make it short again - they can fail, that's okay, it just shouldn't happen to often.
 * Then something is seriously wrong (check the points above first and rule them out)
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class WifiDirectDiscoveryEngineLiveTest
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();
    private final int TEST_CASE_TIMEOUT = 35000;

    private final int ROLE_A = 1;
    private final int ROLE_B = 2;
    private final int ROLE_C = 3;

    private final String DEVICE_A = "samsungSM-T580";
    private final String DEVICE_B = "LENOVOLenovo TB-X304L";
    private final String DEVICE_C = "DOOGEEY8";

    private final String MAC_A = "D0:7F:A0:D6:1C:9A";
    private final String MAC_B = "D0:F8:8C:2F:19:9F";
    private final String MAC_C = "20:19:08:15:56:13";

    private int role;

    ServiceDescription descriptionForServiceOne;
    ServiceDescription descriptionForServiceTwo;

    @Rule
    public GrantPermissionRule fineLocationPermissionRule = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION);
    @Rule
    public CountingTaskExecutorRule executionerRule = new CountingTaskExecutorRule();


    public String getCurrentDeviceName()
    {
        //----------------------------------
        // NOTE : the actual mac address of
        // the local device would have been better
        // since then they could have been compared
        // more easily
        //----------------------------------
        return android.os.Build.MANUFACTURER + android.os.Build.MODEL;
    }

    @BeforeClass
    public static void classSetup()
    {

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

        switch (getCurrentDeviceName())
        {
            case DEVICE_A:
                role = ROLE_A;
                break;
            case DEVICE_B:
                role = ROLE_B;
                break;
            case DEVICE_C:
                role = ROLE_C;
                break;
            default:
                System.out.println("device not specified " + getCurrentDeviceName());
        }
        SdpBluetoothEngine.initialize(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @After
    public void teardown() throws NullPointerException
    {
        SdpBluetoothEngine.getInstance().teardownEngine();
        SdpBluetoothDiscoveryEngine.getInstance().teardownEngine();
    }


    
    

    ////
    ////------------  connecting to services ---------------
    ////


    //
    //  ----------  connecting to one service ----------
    //

    @Test
    public void itShouldConnectToOneNearbyService() throws InterruptedException{
        switch (role)
        {
            case ROLE_A:
                itShouldConnectToOneNearbyService_discoverAndConnect();
                break;
            case ROLE_B:
                itShouldConnectToOneNearbyService_advertiseServiceAndAccept();
                break;
            case ROLE_C:
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
        SdpBluetoothEngine.getInstance().start();
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

        assertEquals(acceptedConnections.get(0).getRemoteDeviceAddress(), MAC_A);
        assertEquals(acceptedConnections.get(0).getServiceDescription(), descriptionForServiceOne);
    }

    public void itShouldConnectToOneNearbyService_discoverAndConnect() throws InterruptedException
    {
        SdpBluetoothEngine.getInstance().start();

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
                if(address.equals(MAC_B)){
                return true; // it should connect7
                }
                return false;
            }
        });
        SdpBluetoothEngine.getInstance().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(40000); // device discovery takes about 12s
        }
        assertEquals(connections.get(0).getRemoteDeviceAddress(), MAC_B);
        assertEquals(connections.get(0).getServiceDescription(), descriptionForServiceOne);
    }



    //
    //  ----------  two connections to one device ----------
    //

    @Test
    public void itShouldMakeTwoConnectionsToTwoServicesOnTheSameDevice() throws InterruptedException{
        switch (role)
        {
            case ROLE_A:
                itShouldMakeTwoConnectionsToTwoServicesOnTheSameDevice_discoverAndConnect();
                break;
            case ROLE_B:
                itShouldMakeTwoConnectionsToTwoServicesOnTheSameDevice_advertiseServiceAndAccept();
                break;
            case ROLE_C:
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
        SdpBluetoothEngine.getInstance().start();
        ArrayList<SdpBluetoothConnection> acceptedConnections = new ArrayList<>();
        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceOne, connection -> acceptedConnections.add(connection));
        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceTwo, connection -> acceptedConnections.add(connection));
        SdpBluetoothEngine.getInstance().startDiscoverable();
        synchronized (this)
        {
            this.wait(40000); // wait for test to finish
        }

        assertEquals(acceptedConnections.get(0).getRemoteDeviceAddress(), MAC_A);
        assertEquals(acceptedConnections.get(0).getServiceDescription(), descriptionForServiceOne);
    }

    public void itShouldMakeTwoConnectionsToTwoServicesOnTheSameDevice_discoverAndConnect() throws InterruptedException
    {
        SdpBluetoothEngine.getInstance().start();

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
                if(address.equals(MAC_B)){
                    return true; // it should connect7
                }
                return false;
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
                if(address.equals(MAC_B)){
                    return true; // it should connect7
                }
                return false;
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
        switch (role)
        {
            case ROLE_A:
            case ROLE_B:
                itShouldEstablishOnlyOneConnectionOverOneService_test();
                break;
            case ROLE_C:
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

        SdpBluetoothEngine.getInstance().start();
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
        switch (role)
        {
            case ROLE_A:
                itShouldAcceptConnectionsFromSeveralClients_advertiseServiceAndAccept();
                break;
            case ROLE_B:
            case ROLE_C:
                itShouldAcceptConnectionsFromSeveralClients_discoverAndConnect();
                break;
            default:
                System.out.println("device not specified " + getCurrentDeviceName());
        }
    }

    public void itShouldAcceptConnectionsFromSeveralClients_advertiseServiceAndAccept() throws InterruptedException
    {
        SdpBluetoothEngine.getInstance().start();
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
        assertTrue(connectedClients.contains(MAC_B));
        assertTrue(connectedClients.contains(MAC_C));
    }

    public void itShouldAcceptConnectionsFromSeveralClients_discoverAndConnect() throws InterruptedException
    {
        SdpBluetoothEngine.getInstance().start();

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
        switch (role)
        {
            case ROLE_A:
                itShouldFindNearbyDevice_discovery();
                break;
            case ROLE_B:
                itShouldFindNearbyDevice_discoverable();
                break;
            case ROLE_C:
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
        SdpBluetoothEngine.getInstance().start();
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

        SdpBluetoothEngine.getInstance().start();

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
        assertTrue(discoveredDevices.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_B)));
        assertTrue(discoveredDevices.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_C)));
    }





    //
    //  ----------  discovers one nearby service ----------
    //

    @Test
    public void itShouldFindOneNearbyAvailableService() throws InterruptedException
    {
        switch (role)
        {
            case ROLE_A:
                itShouldFindOneNearbyAvailableServices_serviceDiscovery();
                break;
            case ROLE_B:
                itShouldFindOneNearbyAvailableService_serviceAdvertisement();
                break;
            case ROLE_C:
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
        SdpBluetoothEngine.getInstance().start();
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

        SdpBluetoothEngine.getInstance().start();
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
        assertTrue(serviceHosts.contains(MAC_B));
        assertTrue(services.contains(descriptionForServiceOne));
    }





    //
    //  ----------  discovers 2 services on the same device  ----------
    //

    @Test
    public void itShouldFindTwoNearbyAvailableServices() throws InterruptedException
    {
        switch (role)
        {
            case ROLE_A:
                itShouldFindTwoNearbyAvailableServices_serviceDiscovery();
                break;
            case ROLE_B:
            case ROLE_C:
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
        SdpBluetoothEngine.getInstance().start();
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

        SdpBluetoothEngine.getInstance().start();
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

        assertTrue(serviceHosts.contains(MAC_B));
        assertTrue(serviceHosts.contains(MAC_C));
        assertTrue(services.contains(descriptionForServiceOne));
    }




    //
    //  ----------  it finds two different services on separate devices ----------
    //

    @Test
    public void itShouldFindTwoDifferentServicesOnSeparateDevice() throws InterruptedException
    {
        switch (role)
        {
            case ROLE_A:
                itShouldFindTwoDifferentServicesOnSeparateDevice_serviceDiscovery();
                break;
            case ROLE_B:
                itShouldFindTwoDifferentServicesOnSeparateDevice_serviceAdvertisement_A();
                break;
            case ROLE_C:
                itShouldFindTwoDifferentServicesOnSeparateDevice_serviceAdvertisement_B();
                break;
            default:
                System.out.println("device not specified " + getCurrentDeviceName());
        }
    }

    private void itShouldFindTwoDifferentServicesOnSeparateDevice_serviceAdvertisement_B() throws InterruptedException
    {
        SdpBluetoothEngine.getInstance().start();
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
        SdpBluetoothEngine.getInstance().start();
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

        SdpBluetoothEngine.getInstance().start();
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

        assertTrue(serviceHosts.contains(MAC_B));
        assertTrue(serviceHosts.contains(MAC_C));
        assertTrue(services.contains(descriptionForServiceOne));
    }
}
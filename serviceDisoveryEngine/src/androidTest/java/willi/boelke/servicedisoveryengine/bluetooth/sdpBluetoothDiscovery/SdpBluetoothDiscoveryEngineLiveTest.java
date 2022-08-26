package willi.boelke.servicedisoveryengine.bluetooth.sdpBluetoothDiscovery;

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
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothDiscovery.BluetoothServiceDiscoveryListener;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothConnection;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothServiceServer;
import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;

/**
 * These test should run on 3 actual devices
 * which need to be bluetooth enabled.
 * Emulators cant be used.
 *
 * Thew tests are integration tests for the whole
 * 'bluetooth.sdpServiceDiscovery' package.
 *
 * `bluetooth.sdpBluetoothEngine` will also be used,
 *  though that's not the focus here
 *
 * The tests are required to run on all 3 devices simultaneously
 * (in android studio you can choose several devices at once)
 *
 * The tests are expected to run under laboratory conditions,
 * though an interference should not happen.
 *
 * These tests needs a little setup.
 * names and mac addresses of the 3 devices need to be specified beforehand
 * to differentiate them at runtime, and execute the supposed code at each of them
 *
 * The tests require a person to click on allowing the devices to become discoverable
 * There doesn't seem a way to bypass this.
 *
 * There is another tests set, where the android api is mocked
 * these will run quicker and without someone having to stick around ;)
 *
 * I noticed that the test may not work when devices are low (<10%) of battery
 * so they should be charged well too
 *
 *
 * Premise :
 * Each method annotated with `@test` is the "entrance" into a test scenario
 * on all the devices. It has a set of methods which contain the code depending on the devices
 * role.
 * The role dependant methods are named like the test case, with an additional identifier in the end.
 *
 * Since all test run independently on different hardware there is no way to keep them synchronized
 * perfectly, so each device- depending on its role has to wait for the other devices to finish.
 * This is solved using wait()
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SdpBluetoothDiscoveryEngineLiveTest {

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


    public String getCurrentDeviceName(){
        //----------------------------------
        // NOTE : the actual mac address of
        // the local device would have been better
        // since then they could have been compared
        // more easily
        //----------------------------------
        return android.os.Build.MANUFACTURER + android.os.Build.MODEL;
    }

    @BeforeClass
    public static void classSetup(){

    }

    @Before
    public void setup(){
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("service-name", "Test Service One");
        serviceAttributesOne.put("service-info", "This is a test service description");
        serviceAttributesTwo.put("service-name", "Counting Service Two");
        serviceAttributesTwo.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        descriptionForServiceOne = new ServiceDescription(serviceAttributesOne);
        descriptionForServiceTwo = new ServiceDescription(serviceAttributesTwo);

        System.out.println(getCurrentDeviceName());
        switch(getCurrentDeviceName()){
            case  DEVICE_A :
                role = ROLE_A;
                break;
            case DEVICE_B:
                role = ROLE_B;
                break;
            case DEVICE_C:
                role = ROLE_C;
                break;
            default:
                System.out.println("device not specified " +getCurrentDeviceName());
        }
        SdpBluetoothEngine.initialize(InstrumentationRegistry.getInstrumentation().getTargetContext());
        SdpBluetoothDiscoveryEngine.initialize(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @After
    public void teardown() throws NullPointerException{
        SdpBluetoothEngine.getInstance().teardownEngine();
        SdpBluetoothDiscoveryEngine.getInstance().teardownEngine();
    }



    
    //
    //  ----------  discovers nearby devices ----------
    //
 
    @Test
    public void itShouldFindNearbyDevice() throws InterruptedException
    {
        switch(role){
            case ROLE_A :
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
        synchronized (this){
            this.wait(13000); // device discovery takes about 12s
        }
    }

    /**
     * Starts the device discovery and checks if the other two devices where found by
     * looking for their mac addresses
     * @throws InterruptedException
     */
    private void itShouldFindNearbyDevice_discovery() throws InterruptedException
    {

        SdpBluetoothDiscoveryEngine.getInstance().start();

        ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();

        SdpBluetoothDiscoveryEngine.getInstance().registerDiscoverListener(new BluetoothServiceDiscoveryListener()
        {

            @Override
            public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
            {

            }

            @Override
            public void onPeerDiscovered(BluetoothDevice device)
            {
                discoveredDevices.add(device);
            }
        });
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery();
        synchronized (this){
            this.wait(13000); // device discovery takes about 12s
        }        assertTrue(discoveredDevices.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_B)));
        assertTrue(discoveredDevices.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_C)));
    }

    

    //
    //  ----------  discovers one nearby service ----------
    //

    @Test
    public void itShouldFindOneNearbyAvailableService() throws InterruptedException
    {
        switch(role){
            case ROLE_A :
                itShouldFindOneNearbyAvailableServices_serviceDiscovery();
                break;
            case ROLE_B:
                itShouldFindOneNearbyAvailableService_serviceAdvertisement();
                break;
            case ROLE_C:
                synchronized (this){
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
        synchronized (this){
            this.wait(30000); // wait for test to finish
        }
        assertTrue(true); // test shouldn't fail on this device

    }

    /**
     * Starts the service discovery and checks if the service was found
     */
    private void   itShouldFindOneNearbyAvailableServices_serviceDiscovery() throws InterruptedException
    {

        SdpBluetoothDiscoveryEngine.getInstance().start();
        ArrayList<BluetoothDevice> serviceHosts = new ArrayList<>();
        ArrayList<ServiceDescription> services = new ArrayList<>();

        SdpBluetoothDiscoveryEngine.getInstance().registerDiscoverListener(new BluetoothServiceDiscoveryListener()
        {
            @Override
            public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
            {
                serviceHosts.add(host);
                services.add(description);
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice device)
            {
              // not to test here
            }
        });

        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne);
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery();

        synchronized (this){
            this.wait(30000); // this is the maximum time i give it to find the service
        }

        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_B)));
        assertTrue(services.contains(descriptionForServiceOne));

    }

    
    //
    //  ----------  discovers 2 services on the same device  ----------
    //


    @Test
    public void itShouldFindTwoNearbyAvailableService() throws InterruptedException
    {
        switch(role){
            case ROLE_A :
                itShouldFindTwoNearbyAvailableService_serviceDiscovery();
                break;
            case ROLE_B:
            case ROLE_C:
                itShouldFindTwoNearbyAvailableService_serviceAdvertisement();
                break;
            default:
                System.out.println("device not specified " + getCurrentDeviceName());

        }

    }

    /**
     * Starts the device discoverability and a service {@link #descriptionForServiceOne}
     */
    private void itShouldFindTwoNearbyAvailableService_serviceAdvertisement() throws InterruptedException
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
        synchronized (this){
            this.wait(30000); // wait for test to finish
        }
        assertTrue(true); // test shouldn't fail on this device

    }

    /**
     * Starts the service discovery and checks if the service was found
     */
    private void itShouldFindTwoNearbyAvailableService_serviceDiscovery() throws InterruptedException
    {

        SdpBluetoothDiscoveryEngine.getInstance().start();

        ArrayList<BluetoothDevice> serviceHosts = new ArrayList<>();
        ArrayList<ServiceDescription> services = new ArrayList<>();

        SdpBluetoothDiscoveryEngine.getInstance().registerDiscoverListener(new BluetoothServiceDiscoveryListener()
        {
            @Override
            public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
            {
                serviceHosts.add(host);
                services.add(description);
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice device)
            {
                // not to test here
            }
        });

        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne);
        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceTwo);
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery();

        synchronized (this){
            this.wait(30000); // this is the maximum time i give it to find the service
        }

        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_B)));
        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_C)));
        assertTrue(services.contains(descriptionForServiceOne));

    }


    //
    //  ----------  it can find two different services on a single device ----------
    //


    @Test
    public void itShouldFindTwoDifferentServicesOnOneDevice() throws InterruptedException
    {
        switch(role){
            case ROLE_A :
                itShouldFindTwoDifferentServices_serviceDiscovery();
                break;
            case ROLE_B:
                synchronized (this){
                    this.wait(30000); // wait for test to finish
                }
                break;
            case ROLE_C:
                itShouldFindTwoDifferentServices_serviceAdvertisement();
                break;
            default:
                System.out.println("device not specified " + getCurrentDeviceName());

        }

    }

    /**
     * Starts the device discoverability and a service {@link #descriptionForServiceOne}
     */
    private void itShouldFindTwoDifferentServices_serviceAdvertisement() throws InterruptedException
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
        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceTwo, new SdpBluetoothServiceServer()
        {
            @Override
            public void onClientConnected(SdpBluetoothConnection connection)
            {
                // do nothing here, we wont connect ..just advertisement
            }
        });
        SdpBluetoothEngine.getInstance().startDiscoverable();
        synchronized (this){
            this.wait(30000); // wait for test to finish
        }
        assertTrue(true); // test shouldn't fail on this device

    }

    /**
     * Starts the service discovery and checks if the service was found
     */
    private void itShouldFindTwoDifferentServices_serviceDiscovery() throws InterruptedException
    {

        SdpBluetoothDiscoveryEngine.getInstance().start();

        ArrayList<BluetoothDevice> serviceHosts = new ArrayList<>();
        ArrayList<ServiceDescription> services = new ArrayList<>();

        SdpBluetoothDiscoveryEngine.getInstance().registerDiscoverListener(new BluetoothServiceDiscoveryListener()
        {
            @Override
            public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
            {
                serviceHosts.add(host);
                services.add(description);
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice device)
            {
                // not to test here
            }
        });

        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne);
        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceTwo);
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery();

        synchronized (this){
            this.wait(30000); // this is the maximum time i give it to find the service
        }

        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_C)));
        assertTrue(services.contains(descriptionForServiceOne));
        assertTrue(services.contains(descriptionForServiceTwo));

    }


    //
    //  ----------  it finds two different services on separate devices ----------
    //


    @Test
    public void itShouldFindTwoDifferentServicesOnSeparateDevice() throws InterruptedException
    {
        switch(role){
            case ROLE_A :
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
        synchronized (this){
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
        synchronized (this){
            this.wait(30000); // wait for test to finish
        }
        assertTrue(true); // test shouldn't fail on this device

    }


    private void itShouldFindTwoDifferentServicesOnSeparateDevice_serviceDiscovery() throws InterruptedException
    {
        SdpBluetoothDiscoveryEngine.getInstance().start();

        ArrayList<BluetoothDevice> serviceHosts = new ArrayList<>();
        ArrayList<ServiceDescription> services = new ArrayList<>();

        SdpBluetoothDiscoveryEngine.getInstance().registerDiscoverListener(new BluetoothServiceDiscoveryListener()
        {
            @Override
            public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
            {
                serviceHosts.add(host);
                services.add(description);
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice device)
            {
                // not to test here
            }
        });

        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne);
        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceTwo);
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery();

        synchronized (this){
            this.wait(30000); // this is the maximum time i give it to find the service
        }

        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_C)));
        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_B)));
        assertTrue(services.contains(descriptionForServiceOne));
        assertTrue(services.contains(descriptionForServiceTwo));
    }




    @Test
    public void itShouldNotifiedAboutMatchingServicesAlreadyDiscovered() throws InterruptedException
    {
        switch(role){
            case ROLE_A :
                itShouldNotifiedAboutMatchingServicesAlreadyDiscovered_serviceDiscovery();
                break;
            case ROLE_B:
                itShouldNotifiedAboutMatchingServicesAlreadyDiscovered_serviceAdvertisement();
                break;
            case ROLE_C:
                itShouldNotifiedAboutMatchingServicesAlreadyDiscovered_serviceAdvertisement();
                break;
            default:
                System.out.println("device not specified " + getCurrentDeviceName());

        }

    }


    private void itShouldNotifiedAboutMatchingServicesAlreadyDiscovered_serviceAdvertisement() throws InterruptedException
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
        synchronized (this){
            this.wait(30000); // wait for test to finish
        }
        assertTrue(true); // test shouldn't fail on this device

    }

    private void itShouldNotifiedAboutMatchingServicesAlreadyDiscovered_serviceDiscovery() throws InterruptedException
    {
        SdpBluetoothDiscoveryEngine.getInstance().start();

        ArrayList<BluetoothDevice> serviceHosts = new ArrayList<>();
        ArrayList<ServiceDescription> services = new ArrayList<>();

        SdpBluetoothDiscoveryEngine.getInstance().registerDiscoverListener(new BluetoothServiceDiscoveryListener()
        {
            @Override
            public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
            {
                serviceHosts.add(host);
                services.add(description);
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice device)
            {
                // not to test here
            }
        });

        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery();

        synchronized (this){
            this.wait(25000); // this is the maximum time i give it to find the service
        }

        assertEquals(0, serviceHosts.size());

        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(descriptionForServiceOne);
        synchronized (this){
            this.wait(5000); // this is the maximum time i give it to find the service
        }
        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_C)));
        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_B)));
        assertTrue(services.contains(descriptionForServiceOne));
    }


}
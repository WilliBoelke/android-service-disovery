package willi.boelke.serviceDiscovery.bluetooth.sdpBluetoothDiscovery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.DEVICE_A;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.DEVICE_B;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.DEVICE_C;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.MAC_B_BT;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.MAC_C_BT;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.determineTestRunner;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.getCurrentDeviceName;
import static willi.boelke.serviceDiscovery.testUtils.DeviceRoleManager.getTestRunner;

import android.Manifest;
import android.arch.core.executor.testing.CountingTaskExecutorRule;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

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

import willi.boelke.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothEngine;
import willi.boelke.serviceDiscovery.serviceDescription.ServiceDescription;

/**
 * The tests aim to test {@link SdpBluetoothDiscoveryEngine}
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
public class SdpBluetoothDiscoveryEngineLiveTest {

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

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
        SdpBluetoothEngine.getInstance().start(InstrumentationRegistry.getInstrumentation().getTargetContext());
        SdpBluetoothDiscoveryEngine.getInstance().start(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @After
    public void teardown() throws NullPointerException
    {
        SdpBluetoothDiscoveryEngine.getInstance().teardownEngine();
    }



    
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
                itShouldFindNearbyDevice_discoverable();
                break;
            case DEVICE_C:
                itShouldFindNearbyDevice_discoverable();
            default:
              Log.e(TAG, "device not specified " + getCurrentDeviceName());
        }
    }

    /**
     * Starts the device discoverability
     */
    private void itShouldFindNearbyDevice_discoverable() throws InterruptedException
    {
        SdpBluetoothEngine.getInstance().startDiscoverable();
        assertTrue(true); // test shouldn't fail on this device
        synchronized (this){
            this.wait(13000); // device discovery takes about 12s
        }
    }

    /**
     * Starts the device discovery and checks if the other two devices where found by
     * looking for their mac addresses
     */
    private void itShouldFindNearbyDevice_discovery() throws InterruptedException
    {


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
              Log.e(TAG, "device not specified " + getCurrentDeviceName());
        }

    }


    /**
     * Starts the device discoverability and a service {@link #descriptionForServiceOne}
     */
    private void itShouldFindOneNearbyAvailableService_serviceAdvertisement() throws InterruptedException
    {
        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceOne, connection ->
        {
            // do nothing here, we wont connect ..just advertisement
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
    private void   itShouldFindOneNearbyAvailableServices_serviceDiscovery() throws InterruptedException
    {
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

        SdpBluetoothDiscoveryEngine.getInstance().startSdpDiscoveryForService(descriptionForServiceOne);
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery();

        synchronized (this){
            this.wait(30000); // this is the maximum time i give it to find the service
        }

        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_B_BT)));
        assertTrue(services.contains(descriptionForServiceOne));

    }

    
    //
    //  ----------  discovers 2 services on the same device  ----------
    //


    @Test
    public void itShouldFindTwoNearbyAvailableService() throws InterruptedException
    {
        switch (getTestRunner())
        {
            case DEVICE_A:
                itShouldFindTwoNearbyAvailableService_serviceDiscovery();
                break;
            case DEVICE_B:
            case DEVICE_C:
                itShouldFindTwoNearbyAvailableService_serviceAdvertisement();
                break;
            default:
              Log.e(TAG, "device not specified " + getCurrentDeviceName());
        }

    }

    /**
     * Starts the device discoverability and a service {@link #descriptionForServiceOne}
     */
    private void itShouldFindTwoNearbyAvailableService_serviceAdvertisement() throws InterruptedException
    {
        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceOne, connection ->
        {
            // do nothing here, we wont connect ..just advertisement
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
    private void itShouldFindTwoNearbyAvailableService_serviceDiscovery() throws InterruptedException
    {
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

        SdpBluetoothDiscoveryEngine.getInstance().startSdpDiscoveryForService(descriptionForServiceOne);
        SdpBluetoothDiscoveryEngine.getInstance().startSdpDiscoveryForService(descriptionForServiceTwo);
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(30000); // this is the maximum time i give it to find the service
        }

        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_B_BT)));
        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_C_BT)));
        assertTrue(services.contains(descriptionForServiceOne));

    }


    //
    //  ----------  it can find two different services on a single device ----------
    //


    @Test
    public void itShouldFindTwoDifferentServicesOnOneDevice() throws InterruptedException
    {
        switch (getTestRunner())
        {
            case DEVICE_A:
                itShouldFindTwoDifferentServices_serviceDiscovery();
                break;
            case DEVICE_B:
                synchronized (this)
                {
                    this.wait(31000); // wait for test to finish
                }
                break;
            case DEVICE_C:
                itShouldFindTwoDifferentServices_serviceAdvertisement();
                break;
            default:
              Log.e(TAG, "device not specified " + getCurrentDeviceName());

        }

    }

    /**
     * Starts the device discoverability and a service {@link #descriptionForServiceOne}
     */
    private void itShouldFindTwoDifferentServices_serviceAdvertisement() throws InterruptedException
    {
        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceOne, connection ->
        {
            // do nothing here, we wont connect ..just advertisement
        });
        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceTwo, connection ->
        {
            // do nothing here, we wont connect ..just advertisement
        });
        SdpBluetoothEngine.getInstance().startDiscoverable();
        synchronized (this)
        {
            this.wait(31000); // wait for test to finish
        }
        assertTrue(true); // test shouldn't fail on this device

    }

    /**
     * Starts the service discovery and checks if the service was found
     */
    private void itShouldFindTwoDifferentServices_serviceDiscovery() throws InterruptedException
    {
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

        SdpBluetoothDiscoveryEngine.getInstance().startSdpDiscoveryForService(descriptionForServiceOne);
        SdpBluetoothDiscoveryEngine.getInstance().startSdpDiscoveryForService(descriptionForServiceTwo);
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery();

        synchronized (this){
            this.wait(30000); // this is the maximum time i give it to find the service
        }

        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_C_BT)));
        assertTrue(services.contains(descriptionForServiceOne));
        assertTrue(services.contains(descriptionForServiceTwo));
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
              Log.e(TAG, "device not specified " + getCurrentDeviceName());

        }

    }


    private void itShouldFindTwoDifferentServicesOnSeparateDevice_serviceAdvertisement_B() throws InterruptedException
    {
        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceOne, connection ->
        {
            // do nothing here, we wont connect ..just advertisement
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
        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceTwo, connection ->
        {
            // do nothing here, we wont connect ..just advertisement
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

        SdpBluetoothDiscoveryEngine.getInstance().startSdpDiscoveryForService(descriptionForServiceOne);
        SdpBluetoothDiscoveryEngine.getInstance().startSdpDiscoveryForService(descriptionForServiceTwo);
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(30000); // this is the maximum time i give it to find the service
        }

        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_C_BT)));
        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_B_BT)));
        assertTrue(services.contains(descriptionForServiceOne));
        assertTrue(services.contains(descriptionForServiceTwo));
    }


    @Test
    public void itShouldNotifiedAboutMatchingServicesAlreadyDiscovered() throws InterruptedException
    {
        switch (getTestRunner())
        {
            case DEVICE_A:
                itShouldNotifiedAboutMatchingServicesAlreadyDiscovered_serviceDiscovery();
                break;
            case DEVICE_B:
            case DEVICE_C:
                itShouldNotifiedAboutMatchingServicesAlreadyDiscovered_serviceAdvertisement();
                break;
            default:
              Log.e(TAG, "device not specified " + getCurrentDeviceName());
        }

    }


    private void itShouldNotifiedAboutMatchingServicesAlreadyDiscovered_serviceAdvertisement() throws InterruptedException
    {
        SdpBluetoothEngine.getInstance().startSDPService(descriptionForServiceOne, connection ->
        {
            // do nothing here, we wont connect ..just advertisement
        });
        SdpBluetoothEngine.getInstance().startDiscoverable();
        synchronized (this)
        {
            this.wait(30000); // wait for test to finish
        }
        assertTrue(true); // test shouldn't fail on this device

    }

    private void itShouldNotifiedAboutMatchingServicesAlreadyDiscovered_serviceDiscovery() throws InterruptedException
    {
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

        synchronized (this)
        {
            this.wait(25000); // this is the maximum time i give it to find the service
        }

        assertEquals(0, serviceHosts.size());

        SdpBluetoothDiscoveryEngine.getInstance().startSdpDiscoveryForService(descriptionForServiceOne);
        synchronized (this)
        {
            this.wait(5000); // this is the maximum time i give it to find the service
        }
        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_C_BT)));
        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_B_BT)));
        assertTrue(services.contains(descriptionForServiceOne));
    }


}
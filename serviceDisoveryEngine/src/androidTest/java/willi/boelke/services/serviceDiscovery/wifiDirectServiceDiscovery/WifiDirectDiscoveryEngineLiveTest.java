package willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.DEVICE_A;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.DEVICE_B;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.DEVICE_C;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.MAC_B_WIFI;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.MAC_C_WIFI;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.determineTestRunner;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.getTestRunner;
import static willi.boelke.services.serviceDiscovery.testUtils.DeviceRoleManager.printDeviceInfo;

import android.Manifest;
import android.arch.core.executor.testing.CountingTaskExecutorRule;
import android.net.wifi.p2p.WifiP2pDevice;
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

import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothDiscoveryVOne;

import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * The tests aim to test the
 * {@link BluetoothDiscoveryVOne}
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
 * sequentially, but each test alone - because when running all tests
 * delays add up.
 * I know that's not really automated (but since the alter dialogue pops up always
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
public class WifiDirectDiscoveryEngineLiveTest
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private ServiceDescription descriptionForServiceOne;
    private ServiceDescription descriptionForServiceTwo;

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
        printDeviceInfo(InstrumentationRegistry.getInstrumentation().getTargetContext());
        WifiDirectDiscoveryEngine.getInstance();
        WifiDirectDiscoveryEngine.getInstance().start(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @After
    public void teardown() throws NullPointerException
    {
        WifiDirectDiscoveryEngine.getInstance().teardownEngine();
    }


    //
    //  ----------  service s ----------
    //

    /**
     * Advertises one service with he service Description
     * {@link #descriptionForServiceOne}
     */
    public void advertise_ServiceOne() throws InterruptedException
    {
        WifiDirectDiscoveryEngine.getInstance().startService(descriptionForServiceOne);
        WifiDirectDiscoveryEngine.getInstance().startDiscovery();
        synchronized (this)
        {
            this.wait(20000);
        }
    }

    /**
     * Advertises two services with the descriptions
     * {@link #descriptionForServiceOne} and {@link #descriptionForServiceTwo}
     */
    public void advertise_twoServices() throws InterruptedException
    {
        WifiDirectDiscoveryEngine.getInstance().startService(descriptionForServiceOne);
        WifiDirectDiscoveryEngine.getInstance().startService(descriptionForServiceTwo);
        WifiDirectDiscoveryEngine.getInstance().startDiscovery();
        synchronized (this)
        {
            this.wait(20000); // wait for test to finish
        }
    }


    //
    //  ----------  discover one nearby service ----------
    //

    /**
     * Discovers one service, advertised by one remote device
     */
    @Test
    public void itShouldFindOneNearbyService() throws InterruptedException
    {
        switch (getTestRunner())
        {
            case DEVICE_A:
                itShouldFindOneNearbyService_discover();
                break;
            case DEVICE_B:
                advertise_ServiceOne();
                break;
            case DEVICE_C:
                synchronized (this)
                {
                    this.wait(20000); // wait for test to finish
                }
            default:
                System.out.println("device not specified " + getCurrentDeviceName());
        }
    }
    
    public void itShouldFindOneNearbyService_discover() throws InterruptedException
    {

        final ServiceDescription[] foundServiceDescription = new ServiceDescription[1];
        WifiDirectDiscoveryEngine.getInstance().registerDiscoverListener((host, description) -> foundServiceDescription[0] = description);
        WifiDirectDiscoveryEngine.getInstance().startDiscoveryForService(descriptionForServiceOne);
        WifiDirectDiscoveryEngine.getInstance().startDiscovery();

        synchronized (this)
        {
            this.wait(20000); // wait for test to finish
        }

        assertEquals(descriptionForServiceOne, foundServiceDescription[0]);
    }


    //
    //  ----------  two services on the same device ----------
    //

    /**
     * Discovers two services advertised by one remote device
     */
    @Test
    public void itShouldFindTwoNearbyService() throws InterruptedException
    {
        switch (getTestRunner())
        {
            case DEVICE_A:
                itShouldFindTwoNearbyService_discover();
                break;
            case DEVICE_B:
                advertise_twoServices();
                break;
            case DEVICE_C:
                synchronized (this)
                {
                    this.wait(20000); // wait for test to finish
                }
            default:
                System.out.println("device not specified " + getCurrentDeviceName());
        }
    }

    public void itShouldFindTwoNearbyService_discover() throws InterruptedException
    {
        synchronized (this)
        {
            this.wait(3000); // wait for services to start
        }

        ArrayList<ServiceDescription> foundServiceDescriptions = new ArrayList<>();
        WifiDirectDiscoveryEngine.getInstance().registerDiscoverListener((host, description) ->
        {
            Log.e(TAG, "Discovered service");
            if ((description.equals(descriptionForServiceOne) || description.equals(descriptionForServiceTwo))
                    && host.deviceAddress.equals(MAC_B_WIFI))
            {
                foundServiceDescriptions.add(description);
            }
        });
        WifiDirectDiscoveryEngine.getInstance().startDiscoveryForService(descriptionForServiceOne);
        WifiDirectDiscoveryEngine.getInstance().startDiscoveryForService(descriptionForServiceTwo);
        WifiDirectDiscoveryEngine.getInstance().startDiscovery();

        synchronized (this)
        {
            this.wait(15000);
        }

        assertEquals(2, foundServiceDescriptions.size());
        assertTrue(foundServiceDescriptions.contains(descriptionForServiceTwo));
        assertTrue(foundServiceDescriptions.contains(descriptionForServiceOne));
    }


    //
    //  ----------  two services on the separate device ----------
    //

    /**
     * Discovers two services advertised by two different remote devices
     */
    @Test
    public void itShouldFindTwoNearbyServiceOnTwoDevices() throws InterruptedException
    {
        switch (getTestRunner())
        {
            case DEVICE_A:
                itShouldFindTwoNearbyServiceOnTwoDevices_discover();
                break;
            case DEVICE_B:
            case DEVICE_C:
                advertise_ServiceOne();
                break;
            default:
                System.out.println("device not specified " + getCurrentDeviceName());
        }
    }

    public void itShouldFindTwoNearbyServiceOnTwoDevices_discover() throws InterruptedException
    {
        synchronized (this)
        {
            this.wait(3000);
        }
        ArrayList<ServiceDescription> foundServiceDescriptions = new ArrayList<>();
        ArrayList<WifiP2pDevice> foundHosts = new ArrayList<>();
        WifiDirectDiscoveryEngine.getInstance().registerDiscoverListener((host, description) ->
        {
            Log.e(TAG, "itShouldFindTwoNearbyServiceOnTwoDevices_discover: --- " + host.deviceAddress);
            if (description.equals(descriptionForServiceOne) &&
                    (host.deviceAddress.equals(MAC_C_WIFI) || host.deviceAddress.equals(MAC_B_WIFI)))
            {
                foundServiceDescriptions.add(description);
                foundHosts.add(host);
            }
        });

        WifiDirectDiscoveryEngine.getInstance().startDiscoveryForService(descriptionForServiceOne);
        WifiDirectDiscoveryEngine.getInstance().startDiscovery();

        synchronized (this)
        {
            this.wait(20000); // wait for test to finish
        }

        assertEquals(2, foundServiceDescriptions.size());
        assertEquals(2, foundHosts.size());
        assertTrue(foundServiceDescriptions.contains(descriptionForServiceOne));
    }


    //
    //  ----------  notify about all services enabled ----------
    //

    /**
     * Tests if the engine notifies about every discovered service
     * when the option is enabled
     */
    @Test
    public void itShouldNotifyAboutAllServices() throws InterruptedException
    {
        switch (getTestRunner())
        {
            case DEVICE_A:
                itShouldFindTwoNearbyServiceOnTwoDevices_discover();
                break;
            case DEVICE_B:
            case DEVICE_C:
                advertise_twoServices();
                break;
            default:
                System.out.println("device not specified " + getCurrentDeviceName());
        }
    }

    public void itShouldNotifyAboutAllServices_discover() throws InterruptedException
    {
        synchronized (this)
        {
            this.wait(3000);
        }

        ArrayList<ServiceDescription> foundServiceDescriptions = new ArrayList<>();
        ArrayList<WifiP2pDevice> foundHosts = new ArrayList<>();
        WifiDirectDiscoveryEngine.getInstance().registerDiscoverListener((host, description) ->
        {
            Log.e(TAG, "itShouldFindTwoNearbyServiceOnTwoDevices_discover: --- " + host.deviceAddress);
            if ( (description.equals(descriptionForServiceOne) || description.equals(descriptionForServiceTwo)) &&
                    (host.deviceAddress.equals(MAC_C_WIFI) || host.deviceAddress.equals(MAC_B_WIFI)))
            {
                foundServiceDescriptions.add(description);
                foundHosts.add(host);
            }
        });

        WifiDirectDiscoveryEngine.getInstance().notifyAboutAllServices(true);
        WifiDirectDiscoveryEngine.getInstance().startDiscovery();

        synchronized (this)
        {
            this.wait(20000); // wait for test to finish
        }

        assertEquals(4, foundServiceDescriptions.size());
        assertEquals(2, foundHosts.size());
        assertTrue(foundServiceDescriptions.contains(descriptionForServiceOne));
        assertTrue(foundServiceDescriptions.contains(descriptionForServiceTwo));
    }

}
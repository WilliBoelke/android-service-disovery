package willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static willi.boelke.services.testUtils.DeviceRoleManager.DEVICE_A;
import static willi.boelke.services.testUtils.DeviceRoleManager.DEVICE_B;
import static willi.boelke.services.testUtils.DeviceRoleManager.DEVICE_C;
import static willi.boelke.services.testUtils.DeviceRoleManager.MAC_B_WIFI;
import static willi.boelke.services.testUtils.DeviceRoleManager.MAC_C_WIFI;
import static willi.boelke.services.testUtils.DeviceRoleManager.determineTestRunner;
import static willi.boelke.services.testUtils.DeviceRoleManager.getTestRunner;
import static willi.boelke.services.testUtils.DeviceRoleManager.printDeviceInfo;

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

import willi.boelke.services.serviceDiscovery.ServiceDescription;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryVOne;

/**
 * The tests aim to test the
 * {@link BluetoothServiceDiscoveryVOne}
 * on actual hardware.
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
public class IntegrationWifiDirectServiceDiscovery
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
        WifiDirectServiceDiscoveryEngine.getInstance();
        WifiDirectServiceDiscoveryEngine.getInstance().start(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @After
    public void teardown() throws NullPointerException
    {
        WifiDirectServiceDiscoveryEngine.getInstance().teardownEngine();
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
        WifiDirectServiceDiscoveryEngine.getInstance().startService(descriptionForServiceOne);
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery();
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
        WifiDirectServiceDiscoveryEngine.getInstance().startService(descriptionForServiceOne);
        WifiDirectServiceDiscoveryEngine.getInstance().startService(descriptionForServiceTwo);
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery();
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
        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener((host, description) -> foundServiceDescription[0] = description);
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(descriptionForServiceOne);
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery();

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
    public void itShouldFindTwoNearbyServices() throws InterruptedException
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
        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener((host, description) ->
        {
            Log.e(TAG, "Discovered service");
            if ((description.equals(descriptionForServiceOne) || description.equals(descriptionForServiceTwo))
                    && host.deviceAddress.equals(MAC_B_WIFI))
            {
                foundServiceDescriptions.add(description);
            }
        });
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(descriptionForServiceOne);
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(descriptionForServiceTwo);
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery();

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
        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener((host, description) ->
        {
            Log.e(TAG, "itShouldFindTwoNearbyServiceOnTwoDevices_discover: --- " + host.deviceAddress);
            if (description.equals(descriptionForServiceOne) &&
                    (host.deviceAddress.equals(MAC_C_WIFI) || host.deviceAddress.equals(MAC_B_WIFI)))
            {
                foundServiceDescriptions.add(description);
                foundHosts.add(host);
            }
        });

        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(descriptionForServiceOne);
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery();

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
        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener((host, description) ->
        {
            Log.e(TAG, "itShouldFindTwoNearbyServiceOnTwoDevices_discover: --- " + host.deviceAddress);
            if ((description.equals(descriptionForServiceOne) || description.equals(descriptionForServiceTwo)) &&
                    (host.deviceAddress.equals(MAC_C_WIFI) || host.deviceAddress.equals(MAC_B_WIFI)))
            {
                foundServiceDescriptions.add(description);
                foundHosts.add(host);
            }
        });

        WifiDirectServiceDiscoveryEngine.getInstance().notifyAboutAllServices(true);
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery();

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
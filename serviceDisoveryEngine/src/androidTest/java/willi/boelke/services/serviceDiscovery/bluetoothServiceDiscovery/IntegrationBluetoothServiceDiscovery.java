package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static willi.boelke.services.testUtils.DeviceRoleManager.DEVICE_A;
import static willi.boelke.services.testUtils.DeviceRoleManager.DEVICE_B;
import static willi.boelke.services.testUtils.DeviceRoleManager.DEVICE_C;
import static willi.boelke.services.testUtils.DeviceRoleManager.MAC_B_BT;
import static willi.boelke.services.testUtils.DeviceRoleManager.MAC_C_BT;
import static willi.boelke.services.testUtils.DeviceRoleManager.determineTestRunner;
import static willi.boelke.services.testUtils.DeviceRoleManager.getCurrentDeviceName;
import static willi.boelke.services.testUtils.DeviceRoleManager.getTestRunner;

import android.Manifest;
import android.arch.core.executor.testing.CountingTaskExecutorRule;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * These tests {@link BluetoothServiceDiscoveryEngine}
 * on actual hardware and with real connections.
 * The Subclasses {@link IntegrationBluetoothServiceDiscoveryVOne}
 * and {@link BluetoothServiceDiscoveryVTwo} provide the actual
 * implementations of the abstract class.
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
public abstract class IntegrationBluetoothServiceDiscovery
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private ServiceDescription descriptionForServiceOne;
    private ServiceDescription descriptionForServiceTwo;
    protected BluetoothAdapter adapter;

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
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    @After
    public void teardown() throws NullPointerException
    {

    }

    protected abstract BluetoothServiceDiscoveryEngine getBluetoothDiscoveryImplementation();


    //
    //  ----------  discovers nearby devices ----------
    //

    /**
     * Tests if the engine can find nearby devices
     * through start discovery
     */
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
                break;
            default:
                Log.e(TAG, "device not specified " + getCurrentDeviceName());
        }
    }

    /**
     * Starts the device discoverability
     */
    private void itShouldFindNearbyDevice_discoverable() throws InterruptedException
    {

        this.startDiscoverable();
        synchronized (this){
            this.wait(14000); // wait for discovery
        }
    }

    /**
     * Starts the device discovery and checks if the other two devices where found by
     * looking for their mac addresses
     */
    private void itShouldFindNearbyDevice_discovery() throws InterruptedException
    {
        synchronized (this){
            wait(1000);
        }

        ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();
        getBluetoothDiscoveryImplementation().registerDiscoverListener(new BluetoothServiceDiscoveryListener()
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
        getBluetoothDiscoveryImplementation().startDeviceDiscovery();
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

    /**
     * Tests if the engine can find one service
     * advertised by one nearby device
     */
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
        ServiceAdvertisementThread thread = new ServiceAdvertisementThread(adapter, descriptionForServiceOne);
        thread.startService();
        startDiscoverable();
        synchronized (this)
        {
            this.wait(31000); // wait for test to finish
        }
        thread.cancel();
    }

    /**
     * Starts the service discovery and checks if the service was found
     */
    private void   itShouldFindOneNearbyAvailableServices_serviceDiscovery() throws InterruptedException
    {
        synchronized (this){
            wait(1000);
        }
        ArrayList<BluetoothDevice> serviceHosts = new ArrayList<>();
        ArrayList<ServiceDescription> services = new ArrayList<>();

        getBluetoothDiscoveryImplementation().registerDiscoverListener(new BluetoothServiceDiscoveryListener()
        {
            @Override
            public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
            {
                if (host.getAddress().equals(MAC_B_BT))
                {
                    serviceHosts.add(host);
                    services.add(description);
                }
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice device)
            {
                // not to test here
            }
        });

        getBluetoothDiscoveryImplementation().startDiscoveryForService(descriptionForServiceOne);
        getBluetoothDiscoveryImplementation().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(30000); // give it a maximum of 30 seconds
        }

        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_B_BT)));
        assertTrue(services.contains(descriptionForServiceOne));
    }


    //
    //  ----------  discovers 2 services on the same device  ----------
    //


    /**
     * It should be able to find the same
     * service on two nearby devices
     */
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
        ServiceAdvertisementThread thread = new ServiceAdvertisementThread(adapter, descriptionForServiceOne);
        thread.startService();
        startDiscoverable();
        synchronized (this)
        {
            this.wait(31000); // wait for test to finish
        }
        thread.cancel();
    }

    /**
     * Starts the service discovery and checks if the service was found
     */
    private void itShouldFindTwoNearbyAvailableService_serviceDiscovery() throws InterruptedException
    {
        synchronized (this)
        {
            this.wait(1000);
        }
        ArrayList<BluetoothDevice> serviceHosts = new ArrayList<>();
        ArrayList<ServiceDescription> services = new ArrayList<>();

        getBluetoothDiscoveryImplementation().registerDiscoverListener(new BluetoothServiceDiscoveryListener()
        {
            @Override
            public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
            {
                if (host.getAddress().equals(MAC_B_BT) || host.getAddress().equals(MAC_C_BT))
                {
                    serviceHosts.add(host);
                    services.add(description);
                }
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice device)
            {
                // not to test here
            }
        });

        getBluetoothDiscoveryImplementation().startDiscoveryForService(descriptionForServiceOne);
        getBluetoothDiscoveryImplementation().startDiscoveryForService(descriptionForServiceTwo);
        getBluetoothDiscoveryImplementation().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(30000); // performing discovery
        }

        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_B_BT)));
        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_C_BT)));
        assertTrue(services.contains(descriptionForServiceOne));
    }


    //
    //  ----------  it can find two different services on a single device ----------
    //

    /**
     * Tests if several services running on one device can e found
     */
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
     * Starts the device discoverability and two services
     */
    private void itShouldFindTwoDifferentServices_serviceAdvertisement() throws InterruptedException
    {
        ServiceAdvertisementThread threadOne = new ServiceAdvertisementThread(adapter, descriptionForServiceOne);
        threadOne.startService();
        ServiceAdvertisementThread threadTwo = new ServiceAdvertisementThread(adapter, descriptionForServiceTwo);
        threadTwo.startService();
        startDiscoverable();
        synchronized (this)
        {
            this.wait(31500); // wait for test to finish
        }
        threadOne.cancel();
        threadTwo.cancel();
    }

    /**
     * Starts the service discovery and checks if the service was found
     */
    private void itShouldFindTwoDifferentServices_serviceDiscovery() throws InterruptedException
    {
        synchronized (this){
            this.wait(1000);
        }

        ArrayList<BluetoothDevice> serviceHosts = new ArrayList<>();
        ArrayList<ServiceDescription> services = new ArrayList<>();

        getBluetoothDiscoveryImplementation().registerDiscoverListener(new BluetoothServiceDiscoveryListener()
        {
            @Override
            public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
            {
                if (host.getAddress().equals(MAC_C_BT))
                {
                    serviceHosts.add(host);
                    services.add(description);
                }
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice device)
            {
                // not to test here
            }
        });

        getBluetoothDiscoveryImplementation().startDiscoveryForService(descriptionForServiceOne);
        getBluetoothDiscoveryImplementation().startDiscoveryForService(descriptionForServiceTwo);
        getBluetoothDiscoveryImplementation().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(30000);
        }

        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_C_BT)));
        assertTrue(services.contains(descriptionForServiceOne));
        assertTrue(services.contains(descriptionForServiceTwo));
    }


    //
    //  ----------  it finds two different services on separate devices ----------
    //

    /**
     * A device and service discovery is performed before
     * a service is registered to be discovered
     * devices and services should be cashed
     * and the listener should be notified immediately
     */
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
        ServiceAdvertisementThread thread = new ServiceAdvertisementThread(adapter, descriptionForServiceOne);
        thread.startService();
        startDiscoverable();
        synchronized (this)
        {
            this.wait(31500); // wait for test to finish
        }
        thread.cancel();
    }

    private void itShouldNotifiedAboutMatchingServicesAlreadyDiscovered_serviceDiscovery() throws InterruptedException
    {
        synchronized (this){
            this.wait(1000);
        }
        ArrayList<BluetoothDevice> serviceHosts = new ArrayList<>();
        ArrayList<ServiceDescription> services = new ArrayList<>();

        getBluetoothDiscoveryImplementation().registerDiscoverListener(new BluetoothServiceDiscoveryListener()
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

        getBluetoothDiscoveryImplementation().startDeviceDiscovery();

        synchronized (this)
        {
            this.wait(30000); // device and service discovery
        }

        assertEquals(0, serviceHosts.size()); // should not notify yet
        // registering service for search
        getBluetoothDiscoveryImplementation().startDiscoveryForService(descriptionForServiceOne);
        synchronized (this)
        {
            this.wait(1000); // this should be pretty fast, one second is to much actually
        }
        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_C_BT)));
        assertTrue(serviceHosts.contains(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_B_BT)));
        assertTrue(services.contains(descriptionForServiceOne));
    }

    /**
     * This thread is just for starting a bluetooth Server socket
     * and advertise a service through that
     */
    private static class ServiceAdvertisementThread extends Thread
    {

        /**
         * Log Tag
         */
        private final String TAG = this.getClass().getSimpleName();

        /**
         * The BluetoothAdapter
         */
        private final BluetoothAdapter mBluetoothAdapter;

        private final ServiceDescription description;

        /**
         * Bluetooth server socket to accept incoming connections
         */
        private BluetoothServerSocket serverSocket;

        private boolean running;

        private Thread thread;


        //
        //  ----------  constructor and initialisation ----------
        //

        /**
         * Constructor
         *
         * @param bluetoothAdapter
         *         The BluetoothAdapter tto use (usually the defaultAdapter)
         */
        public ServiceAdvertisementThread(BluetoothAdapter bluetoothAdapter, ServiceDescription description)
        {
            this.mBluetoothAdapter = bluetoothAdapter;
            this.description = description;
            this.running = true;
        }

        //
        //  ----------  start  ----------
        //

        public synchronized void startService()
        {
            Log.d(TAG, "startService : starting Bluetooth Service");
            openServerSocket();
            this.start();
        }

        private void openServerSocket()
        {
            Log.d(TAG, "openServerSocket: opening server socket with UUID : " + description.getServiceUuid());
            try
            {
                this.serverSocket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(description.getServiceUuid().toString(), description.getServiceUuid());
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        //
        //  ----------  run ----------
        //

        public void run()
        {
            this.thread = currentThread();
            while (this.running)
            {
                acceptConnections();
            }
            Log.d(TAG, "run: Accept thread ended final");
        }

        /**
         * Advertises the service with the uuid given through {@link #description}
         */
        private void acceptConnections()
        {
            Log.d(TAG, "run:  Thread started");
            BluetoothSocket socket = null;
            //Blocking Call : Accept thread waits here till another device connects (or canceled)
            Log.d(TAG, "run: RFCOMM server socket started, waiting for connections ...");
            try
            {
                socket = this.serverSocket.accept();
                Log.d(TAG, "run: RFCOMM server socked accepted client connection");
            }
            catch (IOException e)
            {
                Log.e(TAG, "acceptConnections: an IOException occurred, trying to fix");
                try
                {
                    Log.e(TAG, "acceptConnections: trying to close socket");
                    this.serverSocket.close();
                }
                catch (IOException e1)
                {
                    Log.e(TAG, "acceptConnections: could not close the socket");
                }
                Log.e(TAG, "acceptConnections: trying to open new server socket");
                this.openServerSocket();
            }
            if (socket == null)
            {
                Log.d(TAG, "run: Thread was interrupted");
                return;
            }
            Log.d(TAG, "run:  service accepted client connection, opening streams");
        }

        //
        //  ----------  end ----------
        //

        public void cancel()
        {
            Log.d(TAG, "cancel: cancelling accept thread");
            this.running = false;
            if (this.thread != null)
            {
                this.thread.interrupt();
                Log.d(TAG, "cancel: accept thread interrupted");
            }
            try
            {
                this.serverSocket.close();
                Log.d(TAG, "cancel: closed AcceptThread");
            }
            catch (NullPointerException | IOException e)
            {
                Log.e(TAG, "cancel: socket was null", e);
            }
        }

        //
        //  ----------  getter and setter ----------
        //
    }

    /**
     * Asks user (the tester in this case) to make device discoverable
     */
    public void startDiscoverable()
    {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
        discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // tests  wand we do add this
        InstrumentationRegistry.getInstrumentation().getTargetContext().startActivity(discoverableIntent);
    }
}
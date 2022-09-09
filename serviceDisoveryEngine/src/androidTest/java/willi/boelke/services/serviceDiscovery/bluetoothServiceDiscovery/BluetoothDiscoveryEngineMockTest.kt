package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery

import android.Manifest
import android.arch.core.executor.testing.CountingTaskExecutorRule
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.os.ParcelUuid
import android.os.Parcelable
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import willi.boelke.services.serviceDiscovery.ServiceDescription
import willi.boelke.services.serviceDiscovery.testUtils.*
import willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery.WifiDirectDiscoveryEngine


/**
 *
 *  These are integration tests for the SdpBluetoothEngine and
 *  other subsequently used systems like the
 *  ServiceDescription and the SdpBluetoothDiscoveryEngine.
 *  <p>---------------------------------------------<p>
 *  For the tests the android bluetooth api is mocked.
 *  This makes it possible for the tests to run faster and
 *  test some more cases then letting them run on the actual api.
 *  <p>
 *  For Mocking the android api the mocking framework `Mockk`
 *  turned out to be a very good alternative to mockito, which
 *  has problems with mocking final and static classes and methods
 *  which is required to mock the android bt api and the context.
 *  Mockk is a Kotlin library thus Kotlin will be used for the test.
 *
 *  Also Mockk REQUIRES AT LEAST ANDROID P for some features,
 *  so to run the tests an emulator or device with android p is recommended.
 *  else some tests may fail
 *  <p>---------------------------------------------<p>
 *  Sine the complete bluetooth api is mocked, and the
 *  BroadcastReceivers are not actually functioning, test could be run
 *  ignoring the actual flow of events.
 *  for example `onUuidsFetched()`, could be called without
 *  prior `onDeviceDiscoveryFinished()`, this also would work.
 *  This would not happen normally, sine the fetching press
 *  will only be started after a device was discovered
 *  For the tests i wont do that, but emulate the Bluetooth API
 *  and behavior as close to the actual thing as possible.
 *  <p>---------------------------------------------<p>
 *  To test the engine we need to monitor its output and emulate its
 *  input:
 *  <p>
 *  -Output:
 *  The results of the SdpBluetoothEngines work can be monitored
 *  through implementing the client and server interfaces.
 *  In some cases method calls on mock objets need to be verified to
 *  see results. Especially on the BluetoothAdapter, which can be
 *  mocked and injected, method calls are a essential part of testing
 *  the engines work.
 *  <p>
 *  -Input:
 *  The SdpBluetoothEngines input comes from BroadcastReceivers or
 *  from the "user". The user input an be easily emulated using the
 *  public interface.
 *  <p>
 *  -Verify API usage:
 *  Since the api is mocked correct and expected method calls can be verified
 *  <p>---------------------------------------------<p>
 *  The BroadcastReceivers are separated form the engine and use
 *  protected methods (`onDeviceDiscovered` , `onUuidsFetched` and `onDeviceDiscoveryFinished`)
 *  to notify the engine.
 *  As stated those methods are protected and not part of the public interface.
 *  Java allows access to protected methods to other classes in the same directory
 *  (which includes tests, as long as they use the same package structure).
 *  Kotlin does not allow that.
 *  Since those methods should not be public at all, and they are still required for
 *  testing, reflections will be used to access them. {@see Utils.kt#callPrivateFunc}
 *
 * @author WilliBoelke
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class BluetoothDiscoveryEngineMockTest {


    /**
     * Executing sequentially
     */
    @get:Rule
    val instantTaskExecutorRule = CountingTaskExecutorRule()

    @get:Rule
    var fineLocationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION)

    lateinit var mockedBtAdapter: BluetoothAdapter
    lateinit var mockedContext: Context

    @Before
    fun setup() {
        //Setup
        mockedContext = mockk(relaxed = true)
        mockedBtAdapter = mockk()

        every { mockedBtAdapter.isEnabled } returns true
        every { mockedBtAdapter.isDiscovering } returns false
        every { mockedBtAdapter.startDiscovery() } returns true
        every { mockedBtAdapter.cancelDiscovery() } returns true
        every { mockedBtAdapter.startDiscovery() } returns true

        //Run
        BluetoothDiscoveryEngine.getInstance().start(mockedContext, mockedBtAdapter)
        BluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        initTestMocks()
    }

    @After
    fun teardown() {
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("teardownEngine")
    }

    @Test
    fun itShouldInitialize() {
        //Check
        assertTrue(BluetoothDiscoveryEngine.getInstance() != null)
    }


    /**
     * It Should notify listeners about discovered peers (BluetoothDevice)
     * as soon as they are found
     */
    @Test
    fun itNotifiesAboutEveryDiscoveredPeer() {


        //--- setting up listener and start discovery ---//

        val foundDevices: ArrayList<BluetoothDevice> = ArrayList()
        BluetoothDiscoveryEngine.getInstance().registerDiscoverListener( object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(host: BluetoothDevice?, description: ServiceDescription?) {
                // not under test
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                foundDevices.add(device)
            }
        })
        BluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()

        //--- checking if listeners get notified about peers ---//
        
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", getTestDeviceOne())
        assertTrue(foundDevices.size == 1)
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", getTestDeviceTwo())
        assertTrue(foundDevices.size == 2)
    }


    /**
     * Verifies that uuids of peers will be fetched after the
     * device discovery finished 
     */
    @Test
    fun itFetchesUuidsOfAllDiscoveredDevicesAfterDeviceDiscoveryFinished() {
        
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        
        BluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceTwo)
        
        //--- should not be fetched before device discovery stopped ---//
        verify(exactly = 0) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 0) {testDeviceTwo.fetchUuidsWithSdp()}

        //--- finish discovery and check if uuids will be fetched ---//
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")
        verify(exactly = 1) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 1) {testDeviceTwo.fetchUuidsWithSdp()}
    }

    /**
     * Testing `refreshNearbyDevices`
     * it should stop the device discovery (if needed)
     * and fetch the uuids on all discovered devices
     */
    @Test
    fun itShouldFetchUuidsWhenRefreshStarted()
    {
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()

        //--- starting discovery and supply test devices ---//

        BluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceTwo)

        //--- should not be fetched ---//
        verify(exactly = 0) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 0) {testDeviceTwo.fetchUuidsWithSdp()}

        //--- end discovery -first fetch ---//
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")
        verify(exactly = 1) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 1) {testDeviceTwo.fetchUuidsWithSdp()}

        //--- refresh -second fetch ---//
        BluetoothDiscoveryEngine.getInstance().refreshNearbyServices()
        verify(exactly = 2) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 2) {testDeviceTwo.fetchUuidsWithSdp()}
    }


    /**
     * It identifies services when they are given trough
     * `onUuidsFetched`, it notifies about the services looked
     * for but not about others
     */
    @Test
    fun itFindsServices() {
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        var foundDevices: BluetoothDevice? = null
        var foundService: ServiceDescription? = null
        BluetoothDiscoveryEngine.getInstance().registerDiscoverListener(object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(host: BluetoothDevice, description: ServiceDescription) {
                foundDevices = host
                foundService = description
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }
        })

        //--- faking the devices discovery ---//
        BluetoothDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionTwo)
        BluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceTwo)

        //--- end device discovery ---//
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")

        //--- faking the service discovery responses and checking output ---//
        BluetoothDiscoveryEngine.getInstance()
            .callPrivateFunc("onUuidsFetched", testDeviceOne, getTestUuidArrayOne())
        assertTrue(foundDevices == testDeviceOne)
        assertTrue(foundService == testDescriptionTwo)

        //--- uuid array two does not contain the uuid looked for - should not notify ---//
        BluetoothDiscoveryEngine.getInstance()
            .callPrivateFunc("onUuidsFetched", testDeviceTwo, getTestUuidArrayTwo())
        assertTrue(foundDevices == testDeviceOne)
        assertTrue(foundService == testDescriptionTwo)
    }

    /**
     * The engine can look for a number of
     * services. If several services are found
     * on different devices it will notify
     * about each with the matching device
     */
    @Test
    fun itShouldBeAbleToSearchForSeveralServicesAtATime() {


        val foundDevices: ArrayList<BluetoothDevice> = ArrayList()
        val foundServices: ArrayList<ServiceDescription> = ArrayList()
        BluetoothDiscoveryEngine.getInstance().registerDiscoverListener(object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(
                host: BluetoothDevice,
                description: ServiceDescription
            ) {
                foundDevices.add(host)
                foundServices.add(description)
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }
        })

        //--- starting discovery for two services ---//
        BluetoothDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFive)
        BluetoothDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionTwo)
        BluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()

        //--- supplying devices ---//
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceTwo)
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")

        //--- faking sdp responses ---//
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceTwo, getTestUuidArrayTwo())
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceOne, getTestUuidArrayOne())

        //--- services on two devices where discovered ---//
        assertTrue(foundDevices.size == 2)
        assertTrue(foundDevices.contains(testDeviceOne) && foundDevices.contains(testDeviceTwo))
        assertTrue(foundServices.size == 2)
        assertTrue(foundServices.size == 2)
    }


    @Test
    fun itShouldPauseTheDiscoveryWhenRefreshingServices(){
        // discovered device
        BluetoothDiscoveryEngine.getInstance().refreshNearbyServices()
        verify(exactly = 1) { mockedBtAdapter.cancelDiscovery() }
    }

    /**
     * services and devices should be cached, listeners should be
     * notified immediately if the start
     * a search an the service was already discovered
     */
    @Test
    fun itShouldNotifyAboutServicesThatWhereDiscoveredBefore() {
        //--- setting up listeners ---//
        val foundDevices: ArrayList<BluetoothDevice> = ArrayList()
        val foundServices: ArrayList<ServiceDescription> = ArrayList()
        BluetoothDiscoveryEngine.getInstance().registerDiscoverListener(object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(
                host: BluetoothDevice,
                description: ServiceDescription
            ) {
                foundDevices.add(host)
                foundServices.add(description)
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }

        })

        //--- starting the device discovery and faking device and service discovery ---//
        BluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceTwo)
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceTwo, getTestUuidArrayTwo())
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceOne, getTestUuidArrayOne())

        //--- starting service discovery ---//
        BluetoothDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFive)
        BluetoothDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionTwo)

        assertTrue(foundDevices.size == 2)
        assertTrue(foundDevices.contains(testDeviceOne) && foundDevices.contains(testDeviceTwo))
        assertTrue(foundServices.size == 2)
        assertTrue(foundServices.contains(testDescriptionTwo) && foundServices.contains(testDescriptionFive))
    }


    /**
     * In some instances he UUID_EXTRA will be `null`
     * the engine needs to handle that without an (unexpected) exception
     *
     * This should also be caught in the BroadcastReceiver before but still
     * it should be checked
     */
    @Test
    fun itShouldHandleUuidArraysBeingNull() {
        val testDeviceOne = getTestDeviceOne()

        //Start client looking for uuid four, which is part of test array two
        val foundDevices: ArrayList<BluetoothDevice> = ArrayList()
        val foundServices: ArrayList<ServiceDescription> = ArrayList()
        BluetoothDiscoveryEngine.getInstance().registerDiscoverListener(object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(
                host: BluetoothDevice,
                description: ServiceDescription
            ) {
                foundDevices.add(host)
                foundServices.add(description)
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }

        })

        BluetoothDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionTwo)
        BluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")
        verify(exactly = 1) {testDeviceOne.fetchUuidsWithSdp()}

        // fetches null array uuids
        BluetoothDiscoveryEngine.getInstance().callOnUuidsFetchedWithNullParam(testDeviceOne, null)
        // nothing should happen , and no NullPointerException
        assertTrue(foundDevices.size == 0)
        assertTrue(foundServices.size == 0)
    }

    /**
     * If the engine is not started, no context -and adapter is provided
     * the engine should not crash when calling an methods
     */
    @Test
    fun itShouldNotCrashIfNotStarted(){
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("teardownEngine")
        BluetoothDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionTwo)
        BluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        BluetoothDiscoveryEngine.getInstance().stopDeviceDiscovery()
        BluetoothDiscoveryEngine.getInstance().stopDiscoveryForService(testDescriptionTwo)
        BluetoothDiscoveryEngine.getInstance().refreshNearbyServices()
        BluetoothDiscoveryEngine.getInstance().registerDiscoverListener(object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(host: BluetoothDevice, description: ServiceDescription) {
                // not under test here
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }

        })
    }

    /**
     * If bluetooth is not available on a given device
     * the bt adapter obtained BluetoothAdapter.getDefaultAdapter()
     * will be null.
     * This should not lead to the engine crash.
     * Also the engine should not start
     */
    @Test
    fun theEngineShouldNotCrashIfTheBluetoothAdapterIsNull(){

        //--- supplying null adapter ---//
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("teardownEngine")
        BluetoothDiscoveryEngine.getInstance().start(mockedContext, null)

        //--- testing methods calls ---//
        BluetoothDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionTwo)
        BluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        BluetoothDiscoveryEngine.getInstance().stopDeviceDiscovery()
        BluetoothDiscoveryEngine.getInstance().stopDiscoveryForService(testDescriptionTwo)
        BluetoothDiscoveryEngine.getInstance().refreshNearbyServices()
        BluetoothDiscoveryEngine.getInstance().registerDiscoverListener(object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(host: BluetoothDevice, description: ServiceDescription) {
                // not under test here
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }

        })
    }

    /**
     * On some devices a bug occurred leading
     * to fetched uuids being bytewise reversed
     * The engine should still recognize them
     */
    @Test
    fun itShouldCheckLittleEndianUuids(){
        val testDeviceOne = getTestDeviceOne()
        var foundDevices: BluetoothDevice? = null
        var foundService: ServiceDescription? = null
        BluetoothDiscoveryEngine.getInstance().registerDiscoverListener(object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(host: BluetoothDevice, description: ServiceDescription) {
                foundDevices = host
                foundService = description
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }
        })
        
        //--- creating bytewise reversed uuid array ---//

        val arrayReversed :Array<Parcelable> =  arrayOf<Parcelable>(ParcelUuid(testDescriptionTwo.bytewiseReverseUuid), ParcelUuid(testDescriptionOne.bytewiseReverseUuid))
        every { testDeviceOne.uuids } returns  arrayOf(
            ParcelUuid(testDescriptionTwo.bytewiseReverseUuid),
            ParcelUuid(testDescriptionOne.bytewiseReverseUuid)
        )

        //--- faking the devices discovery ---//
        BluetoothDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionTwo)
        BluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)

        //--- end device discovery ---//
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")

        //--- faking the service discovery responses and checking output ---//
        BluetoothDiscoveryEngine.getInstance()
            .callPrivateFunc("onUuidsFetched", testDeviceOne, arrayReversed)
        assertTrue(foundDevices == testDeviceOne)
        assertTrue(foundService == testDescriptionTwo)
    }

    /**
     * Several listeners can subscribe to the engine
     * each of them should be notified about discoveries
     */
    @Test
    fun itShouldNotifyAllListener(){

        //--- registering listeners ---//

        var receivedDeviceListenerOne: BluetoothDevice? = null
        var receivedDescriptionListenerOne: ServiceDescription? = null
        var receivedDeviceListenerTwo: BluetoothDevice? = null
        var receivedDescriptionListenerTwo: ServiceDescription? = null
        var receivedDeviceListenerThree: BluetoothDevice? = null
        var receivedDescriptionListenerThree: ServiceDescription? = null
        BluetoothDiscoveryEngine.getInstance().registerDiscoverListener(object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(
                host: BluetoothDevice,
                description: ServiceDescription
            ) {
                receivedDeviceListenerOne = host
                receivedDescriptionListenerOne = description
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }

        })

        BluetoothDiscoveryEngine.getInstance().registerDiscoverListener(object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(
                host: BluetoothDevice,
                description: ServiceDescription
            ) {
                receivedDeviceListenerTwo = host
                receivedDescriptionListenerTwo = description
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }

        })

        BluetoothDiscoveryEngine.getInstance().registerDiscoverListener(object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(
                host: BluetoothDevice,
                description: ServiceDescription
            ) {
                receivedDeviceListenerThree = host
                receivedDescriptionListenerThree = description
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }

        })
        val testDeviceOne = getTestDeviceOne()

        //--- start discovery and discover services ---//
        BluetoothDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionTwo)
        BluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")
        BluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", getTestDeviceOne(), getTestUuidArrayOne())

        //--- all listeners should be notified ---//
        TestCase.assertEquals(testDescriptionTwo, receivedDescriptionListenerOne)
        TestCase.assertEquals(testDescriptionTwo, receivedDescriptionListenerTwo)
        TestCase.assertEquals(testDescriptionTwo, receivedDescriptionListenerThree)
        TestCase.assertEquals(testDeviceOne.address, receivedDeviceListenerOne?.address)
        TestCase.assertEquals(testDeviceOne.address, receivedDeviceListenerTwo?.address)
        TestCase.assertEquals(testDeviceOne.address, receivedDeviceListenerThree?.address)

    }
}

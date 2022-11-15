package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery

import android.Manifest
import android.arch.core.executor.testing.CountingTaskExecutorRule
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
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
import willi.boelke.services.testUtils.*


/**
 * These are unit tests for a [BluetoothServiceDiscoveryEngine]
 * While other internal classes like [ServiceDescription]s
 * are used, Android classes like Context and BluetoothAdapter
 * will be mocked.
 *
 * ## Mocking
 * As mentioned the android API will be mocked, to achieve that
 * the Android Kotlin testing framework `Mockk` is used. `Mockk`
 * turned out to be a very good alternative to ``Mockito``, which
 * has problems with mocking final and static classes and methods
 * which is required to mock the Android Bluetooth API and the Context.
 * ``Mockk`` is a Kotlin library thus Kotlin will be used for the test.
 *
 * Alas also Mockk **REQUIRES AT LEAST ANDROID P** for some features,
 * so to run the tests an emulator or device with Android >= P is recommended.
 *
 * ## How to Test
 * Sine the complete bluetooth api is mocked, and the
 * BroadcastReceivers are not actually functioning, test could be run
 * ignoring the actual flow of events. For example `onUuidsFetched()`,
 * could be called without prior `onDeviceDiscoveryFinished()`, this also
 * would work.
 *
 * This would not happen normally, since the fetching process will only be
 * started after a device was discovered. For these test a adequate use
 * of the API will be emulated and critical method calls
 * on the will be verified.
 *
 * Though the correct usage of the Android API will also be
 * tested withing the integration tests.
 *
 * ## Protected methods
 * The BroadcastReceivers are separated form the engine and use protected methods
 * (`onDeviceDiscovered`, `onUuidsFetched` and `onDeviceDiscoveryFinished`)
 * to notify the engine. As stated those methods are protected and not part
 * of the public interface. Java allows access to protected methods to other
 * classes in the same directory (which includes tests, as long as they use
 * the same package structure). Kotlin does not allow that.
 * Since those methods should not be public at all, and they are still required for
 * testing, reflections will be used to access them see: [callPrivateFunc].
 *
 * @author WilliBoelke
 */
@RunWith(AndroidJUnit4ClassRunner::class)
abstract class UnitBluetoothServiceDiscovery {


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
        mockedContext = mockk<Context>(relaxed = true)
        mockedBtAdapter = mockk<BluetoothAdapter>()

        every { mockedBtAdapter.isEnabled } returns true
        every { mockedBtAdapter.isDiscovering } returns false
        every { mockedBtAdapter.startDiscovery() } returns true
        every { mockedBtAdapter.cancelDiscovery() } returns true
        every { mockedBtAdapter.startDiscovery() } returns true

        specificSetup()

        initTestMocks()
    }

    @After
    abstract fun teardown()

    /**
     * Perform specific setup operations
     * in the subclasses
     * called in the [setup] method.
     */
    abstract fun specificSetup()

    /**
     * To e implemented in subclasses
     * to provide the discovery engine implementation
     * under test.
     */
    abstract fun getDiscoveryEngine(): BluetoothServiceDiscoveryEngine


    /**
     * It Should notify listeners about discovered peers (BluetoothDevice)
     * as soon as they are found
     */
    @Test
    fun itNotifiesAboutEveryDiscoveredPeer() {

        //--- setting up listener and start discovery ---//

        val foundDevices: ArrayList<BluetoothDevice> = ArrayList()
        getDiscoveryEngine()
            .registerDiscoverListener(object : BluetoothServiceDiscoveryListener {
                override fun onServiceDiscovered(
                    host: BluetoothDevice?,
                    description: ServiceDescription?
                ) {
                    // not under test
                }

                override fun onPeerDiscovered(device: BluetoothDevice) {
                    foundDevices.add(device)
                }
            })
        getDiscoveryEngine().startDeviceDiscovery()

        //--- checking if listeners get notified about peers ---//

        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", getTestDeviceOne())
        assertTrue(foundDevices.size == 1)
        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", getTestDeviceTwo())
        assertTrue(foundDevices.size == 2)
    }


    /**
     * Testing `refreshNearbyDevices`
     * it should stop the device discovery (if needed)
     * and fetch the uuids on all discovered devices
     */
    @Test
    fun itShouldFetchUuidsWhenRefreshStarted() {
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()

        //--- starting discovery and supply test devices ---//

        getDiscoveryEngine().startDeviceDiscovery()
        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", testDeviceTwo)

        //--- should not be fetched ---//
        verify(exactly = 0) { testDeviceOne.fetchUuidsWithSdp() }
        verify(exactly = 0) { testDeviceTwo.fetchUuidsWithSdp() }

        //--- end discovery -first fetch ---//
        getDiscoveryEngine().callPrivateFunc("onDeviceDiscoveryFinished")
        verify(exactly = 1) { testDeviceOne.fetchUuidsWithSdp() }
        verify(exactly = 1) { testDeviceTwo.fetchUuidsWithSdp() }

        //--- refresh -second fetch ---//
        getDiscoveryEngine().refreshNearbyServices()
        verify(exactly = 2) { testDeviceOne.fetchUuidsWithSdp() }
        verify(exactly = 2) { testDeviceTwo.fetchUuidsWithSdp() }
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
        getDiscoveryEngine()
            .registerDiscoverListener(object : BluetoothServiceDiscoveryListener {
                override fun onServiceDiscovered(
                    host: BluetoothDevice,
                    description: ServiceDescription
                ) {
                    foundDevices = host
                    foundService = description
                }

                override fun onPeerDiscovered(device: BluetoothDevice) {
                    // not under test here
                }
            })

        //--- faking the devices discovery ---//
        getDiscoveryEngine().startDiscoveryForService(testDescriptionTwo)
        getDiscoveryEngine().startDeviceDiscovery()
        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", testDeviceTwo)

        //--- end device discovery ---//
        getDiscoveryEngine().callPrivateFunc("onDeviceDiscoveryFinished")

        //--- faking the service discovery responses and checking output ---//
        getDiscoveryEngine()
            .callPrivateFunc("onUuidsFetched", testDeviceOne, getTestUuidArrayOne())
        assertTrue(foundDevices == testDeviceOne)
        assertTrue(foundService == testDescriptionTwo)

        //--- uuid array two does not contain the uuid looked for - should not notify ---//
        getDiscoveryEngine()
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
        getDiscoveryEngine()
            .registerDiscoverListener(object : BluetoothServiceDiscoveryListener {
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
        getDiscoveryEngine().startDiscoveryForService(testDescriptionFive)
        getDiscoveryEngine().startDiscoveryForService(testDescriptionTwo)
        getDiscoveryEngine().startDeviceDiscovery()

        //--- supplying devices ---//
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", testDeviceTwo)
        getDiscoveryEngine().callPrivateFunc("onDeviceDiscoveryFinished")

        //--- faking sdp responses ---//
        getDiscoveryEngine()
            .callPrivateFunc("onUuidsFetched", testDeviceTwo, getTestUuidArrayTwo())
        getDiscoveryEngine()
            .callPrivateFunc("onUuidsFetched", testDeviceOne, getTestUuidArrayOne())

        //--- services on two devices where discovered ---//
        assertTrue(foundDevices.size == 2)
        assertTrue(foundDevices.contains(testDeviceOne) && foundDevices.contains(testDeviceTwo))
        assertTrue(foundServices.size == 2)
        assertTrue(foundServices.size == 2)
    }


    @Test
    fun itShouldPauseTheDiscoveryWhenRefreshingServices() {
        // discovered device
        getDiscoveryEngine().refreshNearbyServices()
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
        getDiscoveryEngine()
            .registerDiscoverListener(object : BluetoothServiceDiscoveryListener {
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
        getDiscoveryEngine().startDeviceDiscovery()
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", testDeviceTwo)
        getDiscoveryEngine().callPrivateFunc("onDeviceDiscoveryFinished")
        getDiscoveryEngine()
            .callPrivateFunc("onUuidsFetched", testDeviceTwo, getTestUuidArrayTwo())
        getDiscoveryEngine()
            .callPrivateFunc("onUuidsFetched", testDeviceOne, getTestUuidArrayOne())

        //--- starting service discovery ---//
        getDiscoveryEngine().startDiscoveryForService(testDescriptionFive)
        getDiscoveryEngine().startDiscoveryForService(testDescriptionTwo)

        assertTrue(foundDevices.size == 2)
        assertTrue(foundDevices.contains(testDeviceOne) && foundDevices.contains(testDeviceTwo))
        assertTrue(foundServices.size == 2)
        assertTrue(
            foundServices.contains(testDescriptionTwo) && foundServices.contains(
                testDescriptionFive
            )
        )
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
        getDiscoveryEngine()
            .registerDiscoverListener(object : BluetoothServiceDiscoveryListener {
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

        getDiscoveryEngine().startDiscoveryForService(testDescriptionTwo)
        getDiscoveryEngine().startDeviceDiscovery()
        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        getDiscoveryEngine().callPrivateFunc("onDeviceDiscoveryFinished")
        verify(exactly = 1) { testDeviceOne.fetchUuidsWithSdp() }

        // fetches null array uuids
        getDiscoveryEngine()
            .callOnUuidsFetchedWithNullParam(testDeviceOne, null)
        // nothing should happen , and no NullPointerException
        assertTrue(foundDevices.size == 0)
        assertTrue(foundServices.size == 0)
    }

    /**
     * If the engine is not started, no context -and adapter is provided
     * the engine should not crash when calling an methods
     */
    @Test
    fun itShouldNotCrashIfNotStarted() {
        getDiscoveryEngine().callPrivateFunc("teardownEngine")
        getDiscoveryEngine().startDiscoveryForService(testDescriptionTwo)
        getDiscoveryEngine().startDeviceDiscovery()
        getDiscoveryEngine().stopDeviceDiscovery()
        getDiscoveryEngine().stopDiscoveryForService(testDescriptionTwo)
        getDiscoveryEngine().refreshNearbyServices()
        getDiscoveryEngine()
            .registerDiscoverListener(object : BluetoothServiceDiscoveryListener {
                override fun onServiceDiscovered(
                    host: BluetoothDevice,
                    description: ServiceDescription
                ) {
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
    fun theEngineShouldNotCrashIfTheBluetoothAdapterIsNull() {

        //--- supplying null adapter ---//
        getDiscoveryEngine().callPrivateFunc("teardownEngine")
        getDiscoveryEngine().start(mockedContext, null)

        //--- testing methods calls ---//
        getDiscoveryEngine().startDiscoveryForService(testDescriptionTwo)
        getDiscoveryEngine().startDeviceDiscovery()
        getDiscoveryEngine().stopDeviceDiscovery()
        getDiscoveryEngine().stopDiscoveryForService(testDescriptionTwo)
        getDiscoveryEngine().refreshNearbyServices()
        getDiscoveryEngine()
            .registerDiscoverListener(object : BluetoothServiceDiscoveryListener {
                override fun onServiceDiscovered(
                    host: BluetoothDevice,
                    description: ServiceDescription
                ) {
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
    fun itShouldCheckLittleEndianUuids() {
        val testDeviceOne = getTestDeviceOne()
        var foundDevices: BluetoothDevice? = null
        var foundService: ServiceDescription? = null
        getDiscoveryEngine()
            .registerDiscoverListener(object : BluetoothServiceDiscoveryListener {
                override fun onServiceDiscovered(
                    host: BluetoothDevice,
                    description: ServiceDescription
                ) {
                    foundDevices = host
                    foundService = description
                }

                override fun onPeerDiscovered(device: BluetoothDevice) {
                    // not under test here
                }
            })

        //--- creating bytewise reversed uuid array ---//

        val arrayReversed: Array<Parcelable> = arrayOf<Parcelable>(
            ParcelUuid(testDescriptionTwo.bytewiseReverseUuid),
            ParcelUuid(testDescriptionOne.bytewiseReverseUuid)
        )
        every { testDeviceOne.uuids } returns arrayOf(
            ParcelUuid(testDescriptionTwo.bytewiseReverseUuid),
            ParcelUuid(testDescriptionOne.bytewiseReverseUuid)
        )

        //--- faking the devices discovery ---//
        getDiscoveryEngine().startDiscoveryForService(testDescriptionTwo)
        getDiscoveryEngine().startDeviceDiscovery()
        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", testDeviceOne)

        //--- end device discovery ---//
        getDiscoveryEngine().callPrivateFunc("onDeviceDiscoveryFinished")

        //--- faking the service discovery responses and checking output ---//
        getDiscoveryEngine()
            .callPrivateFunc("onUuidsFetched", testDeviceOne, arrayReversed)
        assertTrue(foundDevices == testDeviceOne)
        assertTrue(foundService == testDescriptionTwo)
    }

    /**
     * Several listeners can subscribe to the engine
     * each of them should be notified about discoveries
     */
    @Test
    fun itShouldNotifyAllListener() {

        //--- registering listeners ---//

        var receivedDeviceListenerOne: BluetoothDevice? = null
        var receivedDescriptionListenerOne: ServiceDescription? = null
        var receivedDeviceListenerTwo: BluetoothDevice? = null
        var receivedDescriptionListenerTwo: ServiceDescription? = null
        var receivedDeviceListenerThree: BluetoothDevice? = null
        var receivedDescriptionListenerThree: ServiceDescription? = null
        getDiscoveryEngine()
            .registerDiscoverListener(object : BluetoothServiceDiscoveryListener {
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

        getDiscoveryEngine()
            .registerDiscoverListener(object : BluetoothServiceDiscoveryListener {
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

        getDiscoveryEngine()
            .registerDiscoverListener(object : BluetoothServiceDiscoveryListener {
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
        getDiscoveryEngine().startDiscoveryForService(testDescriptionTwo)
        getDiscoveryEngine().startDeviceDiscovery()
        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        getDiscoveryEngine().callPrivateFunc("onDeviceDiscoveryFinished")
        getDiscoveryEngine()
            .callPrivateFunc("onUuidsFetched", getTestDeviceOne(), getTestUuidArrayOne())

        //--- all listeners should be notified ---//
        TestCase.assertEquals(testDescriptionTwo, receivedDescriptionListenerOne)
        TestCase.assertEquals(testDescriptionTwo, receivedDescriptionListenerTwo)
        TestCase.assertEquals(testDescriptionTwo, receivedDescriptionListenerThree)
        TestCase.assertEquals(testDeviceOne.address, receivedDeviceListenerOne?.address)
        TestCase.assertEquals(testDeviceOne.address, receivedDeviceListenerTwo?.address)
        TestCase.assertEquals(testDeviceOne.address, receivedDeviceListenerThree?.address)

    }


    /**
     * Verifies that uuids of peers will be fetched after the
     * device discovery finished
     */
    @Test
    fun itFetchesUuidsOfAllDiscoveredDevicesAfterDeviceDiscoveryFinished() {

        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()

        getDiscoveryEngine().startDeviceDiscovery()
        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        getDiscoveryEngine()
            .callPrivateFunc("onDeviceDiscovered", testDeviceTwo)

        //--- should not be fetched before device discovery stopped ---//
        verify(exactly = 0) { testDeviceOne.fetchUuidsWithSdp() }
        verify(exactly = 0) { testDeviceTwo.fetchUuidsWithSdp() }

        //--- finish discovery and check if uuids will be fetched ---//
        getDiscoveryEngine().callPrivateFunc("onDeviceDiscoveryFinished")
        verify(exactly = 1) { testDeviceOne.fetchUuidsWithSdp() }
        verify(exactly = 1) { testDeviceTwo.fetchUuidsWithSdp() }
    }
}

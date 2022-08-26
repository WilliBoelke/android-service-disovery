package willi.boelke.servicedisoveryengine.bluetooth.sdpBluetoothDiscovery

import android.Manifest
import android.arch.core.executor.testing.CountingTaskExecutorRule
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import willi.boelke.servicedisoveryengine.testUtils.*
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothDiscovery.SdpBluetoothDiscoveryEngine
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothDiscovery.BluetoothServiceDiscoveryListener
import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription
import java.util.*


/**
 * These tests need to run on a connected android device or emulator
 * running **android p or higher** The tests need to run sequentially
 * since they are executed on a singleton object in addition to that
 * they sometimes need some waiting ime, i kept them short- still
 * the tests take some time to finish
 * -----------------------------------------------------------------
 * To test the engine we need to monitor its output and emulate its
 * input:
 *
 * * Output:
 *  The results of the SdpBluetoothEngines work can be monitored
 *  through implementing the client and server interfaces.
 *  In some cases method calls on mok objets need to be verified to
 *  see results. Especially on the BluetoothAdapter, which can be
 *  mocked and injected, method calls are a essential part of testing
 *  the engines work.
 *
 * * Input:
 * The SdpBluetoothEngines input comes from BroadcastReceivers or
 * from the "user". The user input an be easily emulated using the
 * public interface.
 *
 * The BroadcastReceivers are separated form the engine and use
 * protected methods to notify he engine, they an be accessit in the
 * test directory as well (Well that is for Java only, and does not
 * work in kotlin), hough in kotlin reflections need o be used to
 * access them.
 *
 * Another point where information comes into the engine is the
 * BluetoothAdapter, this can be faked using a mocked adapter
 *
 * -------------------------------------------------------------
 * Sine the complete bluetooth api is mocked, and the
 * BroadcastReceivers are not actually there, test could be done
 * ignoring the actual flow of events.
 * for example `onUuidsFetched()`, could be called without a
 * prior `onDeviceDiscovered()`, this also would work.
 * This would not happen normally, sine the fetching press
 * will only be started after a device was discovered
 *
 * For the tests i wont do that, but emulate the Bluetooth API
 * and behavior as lose to the actual thing as possible.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class SdpBluetoothDiscoveryEngineMockTest {



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

        //Run
        SdpBluetoothDiscoveryEngine.initialize(mockedContext, mockedBtAdapter)
        SdpBluetoothDiscoveryEngine.getInstance().start()
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        initTestMocks()
    }

    @After
    fun teardown() {
        SdpBluetoothDiscoveryEngine.getInstance().teardownEngine();
    }

    @Test
    fun itShouldInitialize() {
        //Check
        assertTrue(SdpBluetoothDiscoveryEngine.getInstance() != null)
    }


    /**
     * It Should notify listeners about discovered peers (BluetoothDevice)
     * as soon as they are found
     */
    @Test
    fun itNotifiesAboutEveryDiscoveredPeer() {

        val foundDevices: ArrayList<BluetoothDevice>? = ArrayList();
        SdpBluetoothDiscoveryEngine.getInstance().registerDiscoverListener( object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(host: BluetoothDevice?, description: ServiceDescription?) {
                // not under test
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                foundDevices?.add(device)

            }

        })

        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery();
        Thread.sleep(500)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", getTestDeviceOne())
        Thread.sleep(200)
        assertTrue(foundDevices?.size == 1)
        Thread.sleep(200)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", getTestDeviceTwo())
        Thread.sleep(200)
        assertTrue(foundDevices?.size == 2)
    }


    /**
     *
     */
    @Test
    fun itFetchesUuidsOfAllDiscoveredDevicesAfterDeviceDiscoveryFinished() {
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        SdpBluetoothDiscoveryEngine.getInstance().registerDiscoverListener( object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(host: BluetoothDevice?, description: ServiceDescription?) {
                // not under test
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }

        })
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceTwo)
        verify(exactly = 0) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 0) {testDeviceTwo.fetchUuidsWithSdp()}
        verify(exactly = 0) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 0) {testDeviceTwo.fetchUuidsWithSdp()}

        // discovery finished:
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")
        verify(exactly = 1) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 1) {testDeviceTwo.fetchUuidsWithSdp()}
    }


    @Test
    fun itShouldFetchUuidsWhenRefreshStarted() {
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()

        SdpBluetoothDiscoveryEngine.getInstance().registerDiscoverListener( object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(host: BluetoothDevice?, description: ServiceDescription?) {
                // not under test
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }

        })

        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceTwo)
        verify(exactly = 0) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 0) {testDeviceTwo.fetchUuidsWithSdp()}
        verify(exactly = 0) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 0) {testDeviceTwo.fetchUuidsWithSdp()}
        // discovery finished:
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")
        verify(exactly = 1) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 1) {testDeviceTwo.fetchUuidsWithSdp()}

        SdpBluetoothDiscoveryEngine.getInstance().refreshNearbyServices()
        verify(exactly = 2) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 2) {testDeviceTwo.fetchUuidsWithSdp()}
    }


    @Test
    fun itFindsServices() {
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        var foundDevices: ArrayList<BluetoothDevice> = ArrayList()
        var foundServices: ArrayList<ServiceDescription> = ArrayList()
        SdpBluetoothDiscoveryEngine.getInstance().registerDiscoverListener( object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(host: BluetoothDevice, description: ServiceDescription) {
                foundDevices.add(host)
                foundServices.add(description)
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }

        })

        // testDescription one has the testUuidTwo
        // which is part of testDeviceOne
        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(testDescriptionTwo)
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceTwo)

        assertTrue(foundDevices.isEmpty())
        assertTrue(foundServices.isEmpty())
        // discovery finished:
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")

        verify(exactly = 1) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 1) {testDeviceTwo.fetchUuidsWithSdp()}

        // finds the service
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceOne, getTestUuidArrayOne())
        assertTrue(foundDevices.get(0).equals(testDeviceOne))
        assertTrue(foundServices.get(0).equals(testDescriptionTwo))

        // finds services it does not look for
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceTwo, getTestUuidArrayTwo())
        assertTrue(foundDevices.size == 1)
        assertTrue(foundServices.size == 1)
        assertTrue(foundDevices.get(0).equals(testDeviceOne))
        assertTrue(foundServices.get(0).equals(testDescriptionTwo))
    }

    /**
     * The Engine can search for several services at a time
     */
    @Test
    fun itShouldBeAbleToSearchForSeveralServicesAtATime() {


        var foundDevices: ArrayList<BluetoothDevice> = ArrayList()
        var foundServices: ArrayList<ServiceDescription> = ArrayList()
        SdpBluetoothDiscoveryEngine.getInstance().registerDiscoverListener( object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(host: BluetoothDevice, description: ServiceDescription) {
                foundDevices.add(host)
                foundServices.add(description)
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }

        })
        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(testDescriptionFive)
        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(testDescriptionTwo)
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()

        // discovered device
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceTwo)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")

        verify(exactly = 1) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 1) {testDeviceTwo.fetchUuidsWithSdp()}

        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceTwo, getTestUuidArrayTwo())
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceOne, getTestUuidArrayOne())

        assertTrue(foundDevices.size == 2)
        assertTrue(foundDevices.contains(testDeviceOne) && foundDevices.contains(testDeviceTwo))
        assertTrue(foundServices.size == 2)
        assertTrue(foundServices.size == 2)
    }


    @Test
    fun itShouldPauseTheDiscoveryWhenRefreshingServices(){
        // discovered device
        SdpBluetoothDiscoveryEngine.getInstance().refreshNearbyServices()
        verify(exactly = 1) { mockedBtAdapter.cancelDiscovery() }
    }


    @Test
    fun itShouldNotifyAboutServicesThatWhereDiscoveredBefore(){

        var foundDevices: ArrayList<BluetoothDevice> = ArrayList()
        var foundServices: ArrayList<ServiceDescription> = ArrayList()
        SdpBluetoothDiscoveryEngine.getInstance().registerDiscoverListener( object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(host: BluetoothDevice, description: ServiceDescription) {
                foundDevices.add(host)
                foundServices.add(description)
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }

        })
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()

        // discovered device
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceTwo)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")

        verify(exactly = 1) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 1) {testDeviceTwo.fetchUuidsWithSdp()}

        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceTwo, getTestUuidArrayTwo())
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceOne, getTestUuidArrayOne())

        // looking for the services at a later point
        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(testDescriptionFive)
        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(testDescriptionTwo)

        assertTrue(foundDevices.size == 2)
        assertTrue(foundDevices.contains(testDeviceOne) && foundDevices.contains(testDeviceTwo))
        assertTrue(foundServices.size == 2)
        assertTrue(foundServices.size == 2)
    }

    //
    //  ----------  frequent NullPointerException reasons ----------
    //


    /**
     * In some instances he UUID_EXTRA will be `null`
     * the engine needs to handle that without an (unexpected) exception
     *
     * This should also be caught in the BroadcastReceiver before but still
     * it should be checked
     */
    @Test
    fun itShouldHandleUuidArraysBeingNull(){
        val testDeviceOne = getTestDeviceOne()

        //Start client looking for uuid four, which is part of test array two
        var foundDevices: ArrayList<BluetoothDevice> = ArrayList()
        var foundServices: ArrayList<ServiceDescription> = ArrayList()
        SdpBluetoothDiscoveryEngine.getInstance().registerDiscoverListener( object :
            BluetoothServiceDiscoveryListener {
            override fun onServiceDiscovered(host: BluetoothDevice, description: ServiceDescription) {
                foundDevices.add(host)
                foundServices.add(description)
            }

            override fun onPeerDiscovered(device: BluetoothDevice) {
                // not under test here
            }

        })

        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(testDescriptionTwo)
        SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery()
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")
        verify(exactly = 1) {testDeviceOne.fetchUuidsWithSdp()}

        // fetches null array uuids
        SdpBluetoothDiscoveryEngine.getInstance().callOnUuidsFetchedWithNullParam(testDeviceOne, null)
        // nothing should happen , and no NullPointerException
        assertTrue(foundDevices.size == 0)
        assertTrue(foundServices.size == 0)
    }

}

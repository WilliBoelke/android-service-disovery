package willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery

import android.arch.core.executor.testing.CountingTaskExecutorRule
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import io.mockk.*
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import willi.boelke.services.serviceConnection.wifiDirectServiceConnection.WifiDirectConnectionEngine
import willi.boelke.services.serviceDiscovery.ServiceDescription
import willi.boelke.services.testUtils.*

@RunWith(AndroidJUnit4ClassRunner::class)
class UnitWifiDirectServiceDiscovery {

    /**
     * Executing sequentially
     */
    @get:Rule
    val instantTaskExecutorRule = CountingTaskExecutorRule()

    private lateinit var mockedManager: WifiP2pManager
    private lateinit var mockedChannel: WifiP2pManager.Channel
    private lateinit var mockedContext: Context
    private lateinit var mockedWifiManager: WifiManager

    // The catered callbacks
    private var servListenerCapture = CapturingSlot<WifiP2pManager.DnsSdServiceResponseListener>()
    private var txtListenerCapture = CapturingSlot<WifiP2pManager.DnsSdTxtRecordListener>()
    private var clearServiceRequestsCallback = CapturingSlot<WifiP2pManager.ActionListener>()
    private var addServiceRequestsCallback = CapturingSlot<WifiP2pManager.ActionListener>()
    private var discoverServicesCallback = CapturingSlot<WifiP2pManager.ActionListener>()

    @Before
    fun setup() {
        //Setup
        mockedManager = mockk<WifiP2pManager>()
        mockedChannel = mockk<WifiP2pManager.Channel>()
        mockedWifiManager = mockk<WifiManager>()
        mockedContext = mockk<Context>(relaxed = true)
        justRun { mockedManager.clearLocalServices(any(), any()) }
        justRun { mockedChannel.close() }

        every { mockedContext.applicationContext.getSystemService(Context.WIFI_SERVICE) } returns mockedWifiManager
        every { mockedContext.getSystemService(Context.WIFI_P2P_SERVICE) } returns mockedManager
        every { mockedContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) } returns true
        every { mockedManager.initialize(mockedContext, any(), any()) } returns mockedChannel
        every { mockedWifiManager.isP2pSupported } returns true


        // capturing the callbacks
        // they are needed to mock the api
        // this though also means that when the code
        // changed (order of callbacks in the discovery thread) this can cause issues with the test

        justRun {
            mockedManager.setDnsSdResponseListeners(
                mockedChannel,
                capture(servListenerCapture),
                capture(txtListenerCapture)
            )
        }

        justRun {
            mockedManager.clearServiceRequests(
                mockedChannel,
                capture(clearServiceRequestsCallback)
            )
        }

        justRun {
            mockedManager.addServiceRequest(
                mockedChannel,
                any(),
                capture(addServiceRequestsCallback)
            )
        }

        justRun {
            mockedManager.discoverServices(
                mockedChannel,
                capture(discoverServicesCallback)
            )
        }

        //Run
        WifiDirectServiceDiscoveryEngine.getInstance()
            .start(mockedContext)
        initTestMocks()
    }


    @After
    fun teardown() {
        WifiDirectServiceDiscoveryEngine.getInstance().callPrivateFunc("teardownEngine")
    }


    //
    // ------------ utils ---------------
    //

    private class TestDiscoveryListener : WifiServiceDiscoveryListener {
        private var discoveredHosts: ArrayList<WifiP2pDevice> = ArrayList()
        private var discoveredServices: ArrayList<ServiceDescription> = ArrayList()
        private var discoveryCounter = 0

        override fun onServiceDiscovered(host: WifiP2pDevice, description: ServiceDescription) {
            discoveryCounter++
            discoveredHosts.add(host)
            discoveredServices.add(description)
        }

        fun hasServiceDiscovered(description: ServiceDescription): Boolean {
            return discoveredServices.contains(description)
        }

        fun hasHostDiscovered(device: WifiP2pDevice): Boolean {
            return discoveredHosts.contains(device)
        }

        fun hasDiscoveries(discoveries: Int): Boolean {
            return discoveryCounter == discoveries
        }
    }

    private fun simulateDiscoveryOf(device: WifiP2pDevice, serviceDescription: ServiceDescription) {
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "", serviceDescription.txtRecord, device
        )

        servListenerCapture.captured.onDnsSdServiceAvailable(
            serviceDescription.instanceName,
            serviceDescription.serviceType + ".local.",
            device
        )
    }


    //
    // ------------ test ---------------
    //

    /**
     * The engine should only start if
     * the environment is fitting
     */
    @Test
    fun itShouldNotStart() {

        WifiDirectServiceDiscoveryEngine.getInstance().teardownEngine()
        every { mockedWifiManager.isP2pSupported } returns false

        assertFalse(
            WifiDirectServiceDiscoveryEngine.getInstance()
                .start(mockedContext)
        )

        every { mockedWifiManager.isP2pSupported } returns true
        every { mockedContext.applicationContext.getSystemService(Context.WIFI_SERVICE) } returns null

        assertFalse(
            WifiDirectServiceDiscoveryEngine.getInstance()
                .start(mockedContext)
        )

        every { mockedContext.applicationContext.getSystemService(Context.WIFI_SERVICE) } returns mockedWifiManager
        every { mockedContext.getSystemService(Context.WIFI_P2P_SERVICE) } returns null

        assertFalse(
            WifiDirectServiceDiscoveryEngine.getInstance()
                .start(mockedContext)
        )
        every { mockedContext.getSystemService(Context.WIFI_P2P_SERVICE) } returns mockedManager
        every { mockedManager.initialize(mockedContext, any(), any()) } returns null

        assertFalse(
            WifiDirectServiceDiscoveryEngine.getInstance()
                .start(mockedContext)
        )

        every { mockedManager.initialize(mockedContext, any(), any()) } returns mockedChannel

        assertTrue(
            WifiDirectServiceDiscoveryEngine.getInstance()
                .start(mockedContext)
        )

    }

    @Test
    fun verifyDiscoveryApiUsage() {
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        clearServiceRequestsCallback.captured.onSuccess()
        addServiceRequestsCallback.captured.onSuccess()

        verify(atLeast = 1) { mockedManager.discoverServices(mockedChannel, any()) }
        verify(exactly = 2) { mockedManager.clearServiceRequests(mockedChannel, any()) }
        verify(exactly = 1) { mockedManager.addServiceRequest(mockedChannel, any(), any()) }
        verify(exactly = 1) { mockedManager.setDnsSdResponseListeners(mockedChannel, any(), any()) }
    }

    /**
     * When the discovery starts or the engine stops the service requests
     * should be cleared.
     */
    @Test
    fun theServiceRequestsShouldBeCleared() {
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        clearServiceRequestsCallback.captured.onSuccess()
        addServiceRequestsCallback.captured.onSuccess()

        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscovery()
        verify(exactly = 3) { mockedManager.clearServiceRequests(mockedChannel, any()) }
    }


    /**
     * When a service is found through the registered callbacks
     * the engine should notify registered listeners
     */
    @Test
    fun itShouldNotifyWhenServiceFound() {
        val listener = TestDiscoveryListener()

        val testDescription = testDescriptionFour
        val testHost = getTestDeviceOne_Wifi()

        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listener)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescription)

        simulateDiscoveryOf(testHost, testDescription)

        assertTrue(listener.hasDiscoveries(1))
        assertTrue(listener.hasServiceDiscovered(testDescription))
        assertTrue(listener.hasHostDiscovered(testHost))
    }

    /**
     * When several services on different devices
     * are found in one discovery,
     * the engine should notify about all of them
     */
    @Test
    fun itShouldNotifyAboutSeveralServiceDiscoveries() {
        val listener = TestDiscoveryListener()

        val testDescription = testDescriptionFour
        val testHostOne = getTestDeviceOne_Wifi()
        val testHostTwo = getTestDeviceTwo_Wifi()

        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listener)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescription)

        simulateDiscoveryOf(testHostOne, testDescription)
        simulateDiscoveryOf(testHostTwo, testDescription)

        assertTrue(listener.hasDiscoveries(2))
        assertTrue(listener.hasServiceDiscovered(testDescriptionFour))
        assertTrue(listener.hasHostDiscovered(testHostOne))
        assertTrue(listener.hasHostDiscovered(testHostTwo))
    }

    /**
     * Services need to be registered for search
     * or the option to notify about every service needs to ben enabled.
     * this checks if the engine wont notify when nothing is registered.
     */
    @Test
    fun itShouldNotNotifyWhenServiceIsNotSearched() {
        val listener = TestDiscoveryListener()

        val testDescription = testDescriptionFour
        val testHost = getTestDeviceOne_Wifi()

        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listener)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()

        simulateDiscoveryOf(testHost, testDescription)

        assertTrue(listener.hasDiscoveries(0))
    }

    /**
     * The discovery thread may discover the same service (and host) several
     * times, the engine should filter those and only notify once
     */
    @Test
    fun itShouldNotNotifyTwiceAboutAboutTheSameServiceAndHost() {
        val listener = TestDiscoveryListener()

        val testDescription = testDescriptionFour
        val testHost = getTestDeviceOne_Wifi()

        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listener)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescription)

        simulateDiscoveryOf(testHost, testDescription)
        simulateDiscoveryOf(testHost, testDescription)

        assertTrue(listener.hasDiscoveries(1))
    }

    /**
     * Discovered service cache should be cleared with each new
     * discovery run.
     * This means each discovery run should notify about every
     * discovered service even though it was found in earlier discovery
     * runs
     */
    @Test
    fun itShouldNotifyAgainInNewDiscoveryRun() {
        val listener = TestDiscoveryListener()

        val testDescription = testDescriptionFour
        val testHost = getTestDeviceOne_Wifi()

        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listener)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescription)

        simulateDiscoveryOf(testHost, testDescription)
        assertTrue(listener.hasDiscoveries(1))

        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()

        simulateDiscoveryOf(testHost, testDescription)
        assertTrue(listener.hasDiscoveries(2))
    }

    /**
     * The engine should allow the search for several services at the same time
     * and notify when they are found.
     */
    @Test
    fun itShouldSearchForSeveralServices() {
        val listener = TestDiscoveryListener()
        val testDescriptionOne = testDescriptionFour
        val testDescriptionTwo = testDescriptionFive
        val testHostOne = getTestDeviceOne_Wifi()
        val testHostTwo = getTestDeviceTwo_Wifi()

        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listener)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionOne)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionTwo)

        simulateDiscoveryOf(testHostOne, testDescriptionOne)

        assertTrue(listener.hasDiscoveries(1))

        simulateDiscoveryOf(testHostTwo, testDescriptionTwo)

        assertTrue(listener.hasDiscoveries(2))

        assertTrue(listener.hasServiceDiscovered(testDescriptionOne))
        assertTrue(listener.hasServiceDiscovered(testDescriptionTwo))
        assertTrue(listener.hasHostDiscovered(testHostOne))
        assertTrue(listener.hasHostDiscovered(testHostTwo))
    }

    /**
     * A service discovery for a single service can be stopped
     * it should not notify anymore when the service is
     * is discovered again
     */
    @Test
    fun itShouldStopNotifyingWhenDiscoveryForServiceStopped() {
        val listener = TestDiscoveryListener()

        val testDescription = testDescriptionFour
        val testHostOne = getTestDeviceOne_Wifi()
        val testHostTwo = getTestDeviceTwo_Wifi()

        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listener)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescription)

        //----------------------------------
        // NOTE : taking different hosts here to prevent double discovery prevention from kicking in
        //----------------------------------
        simulateDiscoveryOf(testHostOne, testDescription)

        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscoveryForService(testDescription)

        simulateDiscoveryOf(testHostTwo, testDescription)

        assertTrue(listener.hasDiscoveries(1))
        assertTrue(listener.hasServiceDiscovered(testDescription))
        assertTrue(listener.hasHostDiscovered(testHostOne))
        assertFalse(listener.hasHostDiscovered(testHostTwo))
    }

    /**
     * If a discovery is stopped other services
     * stay registered, it should still
     * notify about their discovery
     */
    @Test
    fun itShouldOnlyStopTheCorrectServiceDiscovery() {
        val listener = TestDiscoveryListener()
        val testDescriptionOne = testDescriptionFour
        val testDescriptionTwo = testDescriptionFive
        val testHostOne = getTestDeviceOne_Wifi()
        val testHostTwo = getTestDeviceTwo_Wifi()

        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listener)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionOne)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionTwo)

        simulateDiscoveryOf(testHostOne, testDescriptionOne)
        simulateDiscoveryOf(testHostOne, testDescriptionTwo)

        assertTrue(listener.hasDiscoveries(2))

        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscoveryForService(testDescriptionOne)

        simulateDiscoveryOf(testHostTwo, testDescriptionOne) // this one wont be notified
        simulateDiscoveryOf(testHostTwo, testDescriptionTwo)

        assertTrue(listener.hasDiscoveries(3))
        assertTrue(listener.hasServiceDiscovered(testDescriptionOne))
        assertTrue(listener.hasServiceDiscovered(testDescriptionTwo))
    }

    /**
     * Several listeners can subscribe to the engine
     * each of them should be notified about discoveries
     */
    @Test
    fun itShouldNotifyAllListener() {
        val listenerOne = TestDiscoveryListener()
        val listenerTwo = TestDiscoveryListener()
        val listenerThree = TestDiscoveryListener()
        val testDescription = testDescriptionFive
        val testHost = getTestDeviceOne_Wifi()

        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listenerOne)
        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listenerTwo)
        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listenerThree)

        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescription)
        simulateDiscoveryOf(testHost, testDescription)

        assertTrue(
            listenerOne.hasDiscoveries(1) &&
                    listenerTwo.hasDiscoveries(1) && listenerThree.hasDiscoveries(1)
        )

        assertTrue(
            listenerOne.hasServiceDiscovered(testDescription) &&
                    listenerTwo.hasServiceDiscovered(testDescription) && listenerThree.hasServiceDiscovered(
                testDescription
            )
        )
    }


    /**
     * Method calls on the engine will be ignored before
     * it has been started ot if it is stopped.
     */
    @Test
    fun itShouldIgnoreIfNotStarted() {
        WifiDirectServiceDiscoveryEngine.getInstance().callPrivateFunc("teardownEngine")
        clearMocks(mockedContext, mockedWifiManager, mockedManager, mockedChannel)

        //--- testing methods calls ---//
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionTwo)
        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscoveryForService(testDescriptionTwo)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        verify(exactly = 0) { mockedManager.setDnsSdResponseListeners(mockedChannel, any(), any()) }
        verify(exactly = 0) { mockedManager.clearServiceRequests(mockedChannel, any()) }
        verify(exactly = 0) { mockedManager.addServiceRequest(mockedChannel, any(), any()) }
        verify(exactly = 0) { mockedManager.addServiceRequest(mockedChannel, any(), any()) }
        verify(exactly = 0) { mockedManager.discoverServices(mockedChannel, any()) }
        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscovery()
        verify(exactly = 0) { mockedManager.clearServiceRequests(mockedChannel, any()) }
        WifiDirectServiceDiscoveryEngine.getInstance().startService(testDescriptionTwo)
        verify(exactly = 0) { mockedManager.addLocalService(mockedChannel, any(), any()) }
        WifiDirectServiceDiscoveryEngine.getInstance().stopService(testDescriptionTwo)
        verify(exactly = 0) { mockedManager.removeLocalService(mockedChannel, any(), any()) }
    }


    /**
     * Listeners can unregister and wont be notified anymore
     */
    @Test
    fun itShouldAllowListenersToUnregister() {
        val listenerOne = TestDiscoveryListener()
        val listenerTwo = TestDiscoveryListener()
        val listenerThree = TestDiscoveryListener()
        val testDescription = testDescriptionFive
        val testHostOne = getTestDeviceOne_Wifi()
        val testHostTwo = getTestDeviceTwo_Wifi()

        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listenerOne)
        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listenerTwo)
        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listenerThree)

        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescription)
        simulateDiscoveryOf(testHostOne, testDescription)

        assertTrue(
            listenerOne.hasDiscoveries(1) &&
                    listenerTwo.hasDiscoveries(1) && listenerThree.hasDiscoveries(1)
        )

        assertTrue(
            listenerOne.hasHostDiscovered(testHostOne) &&
                    listenerTwo.hasHostDiscovered(testHostOne) && listenerThree.hasHostDiscovered(
                testHostOne
            )
        )


        WifiDirectServiceDiscoveryEngine.getInstance().unregisterDiscoveryListener(listenerOne)
        WifiDirectServiceDiscoveryEngine.getInstance().unregisterDiscoveryListener(listenerTwo)

        simulateDiscoveryOf(testHostTwo, testDescription)

        assertTrue(
            listenerOne.hasDiscoveries(1) &&
                    listenerTwo.hasDiscoveries(1) && listenerThree.hasDiscoveries(2)
        )

        assertTrue(
            !listenerOne.hasHostDiscovered(testHostTwo) &&
                    !listenerTwo.hasHostDiscovered(testHostTwo) && listenerThree.hasHostDiscovered(
                testHostTwo
            )
        )
    }


    /**
     * There is an option not search for "all"
     * service / get notified about every service found.
     * this should work
     */
    @Test
    fun itShouldNotifyAboutAllServices() {
        val listener = TestDiscoveryListener()
        val testDescriptionOne = testDescriptionFive
        val testDescriptionTwo = testDescriptionFour
        val testHost = getTestDeviceOne_Wifi()

        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listener)

        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        // registering only description five
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionOne)
        WifiDirectServiceDiscoveryEngine.getInstance().notifyAboutAllServices(true)
        simulateDiscoveryOf(testHost, testDescriptionOne)
        simulateDiscoveryOf(testHost, testDescriptionTwo)

        assertTrue(listener.hasDiscoveries(2))
        assertTrue(listener.hasServiceDiscovered(testDescriptionOne))
        assertTrue(listener.hasServiceDiscovered(testDescriptionTwo))
    }


    /**
     * It should discover instances with a different instance names
     * (amd still return the correct data)
     */
    @Test
    fun itShouldDiscoverDifferentInstances() {
        val listener = TestDiscoveryListener()
        val testDescriptionOne = testDescriptionFive
        val testDescriptionTwo = testDescriptionFour
        val testHost = getTestDeviceOne_Wifi()

        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listener)

        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        // registering only description five
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionOne)
        WifiDirectServiceDiscoveryEngine.getInstance().notifyAboutAllServices(true)
        simulateDiscoveryOf(testHost, testDescriptionOne)
        simulateDiscoveryOf(testHost, testDescriptionTwo)

        assertTrue(listener.hasDiscoveries(2))
        assertTrue(listener.hasServiceDiscovered(testDescriptionOne))
        assertTrue(listener.hasServiceDiscovered(testDescriptionTwo))
    }
}
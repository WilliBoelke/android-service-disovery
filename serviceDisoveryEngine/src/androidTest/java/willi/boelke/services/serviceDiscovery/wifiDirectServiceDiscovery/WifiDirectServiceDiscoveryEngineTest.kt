package willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery

import android.arch.core.executor.testing.CountingTaskExecutorRule
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import io.mockk.CapturingSlot
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import willi.boelke.services.serviceDiscovery.ServiceDescription
import willi.boelke.services.testUtils.*

@RunWith(AndroidJUnit4ClassRunner::class)
class WifiDirectServiceDiscoveryEngineTest {

    /**
     * Executing sequentially
     */
    @get:Rule
    val instantTaskExecutorRule = CountingTaskExecutorRule()

    private lateinit var mockedManager: WifiP2pManager
    private lateinit var mockedChannel: WifiP2pManager.Channel

    // The catered callbacks
    private var servListenerCapture = CapturingSlot<WifiP2pManager.DnsSdServiceResponseListener>()
    private var txtListenerCapture =  CapturingSlot<WifiP2pManager.DnsSdTxtRecordListener>()
    private var clearServiceRequestsCallback =  CapturingSlot<WifiP2pManager.ActionListener>()
    private var addServiceRequestsCallback =  CapturingSlot<WifiP2pManager.ActionListener>()
    private var discoverServicesCallback =  CapturingSlot<WifiP2pManager.ActionListener>()

    @Before
    fun setup() {
        //Setup
        mockedManager = mockk<WifiP2pManager>()
        mockedChannel = mockk<WifiP2pManager.Channel>()
        justRun { mockedManager.clearLocalServices(any(), any()) }
        justRun { mockedChannel.close() }

        // capturing the callbacks
        // they are needed to mock the api
        // this though also means that when the code
        // changed (order of callbacks in the discovery thread) this can cause issues with the test

        justRun {
            mockedManager.setDnsSdResponseListeners(mockedChannel,
                 capture(servListenerCapture),
                 capture(txtListenerCapture)
            )
        }

        justRun {
            mockedManager.clearServiceRequests(mockedChannel,
                capture(clearServiceRequestsCallback)
            )
        }

        justRun {
            mockedManager.addServiceRequest(mockedChannel,
                any(),
                capture(addServiceRequestsCallback)
            )
        }

        justRun {
            mockedManager.discoverServices(mockedChannel,
            capture(discoverServicesCallback))
        }

        //Run
        WifiDirectServiceDiscoveryEngine.getInstance().start(mockedManager, mockedChannel)

        initTestMocks()
    }

    @After
    fun teardown() {
        WifiDirectServiceDiscoveryEngine.getInstance().callPrivateFunc("teardownEngine")
    }


    @Test
    fun itShouldStart() {
        assertTrue(WifiDirectServiceDiscoveryEngine.getInstance() != null)
    }

    @Test
    fun verifyDiscoveryApiUsage(){
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        Thread.sleep(100)
        clearServiceRequestsCallback.captured.onSuccess()
        Thread.sleep(100)
        addServiceRequestsCallback.captured.onSuccess()
        Thread.sleep(100)
        discoverServicesCallback.captured.onSuccess()
        Thread.sleep(11000) // wait for thread to run

        verify(exactly = 1) { mockedManager.discoverServices(mockedChannel, any()) }
        verify(exactly = 2) { mockedManager.clearServiceRequests(mockedChannel, any()) }
        verify(exactly = 1) { mockedManager.addServiceRequest(mockedChannel, any(), any()) }
        verify(exactly = 1) { mockedManager.setDnsSdResponseListeners(mockedChannel, any(), any())}
    }

    ////
    ////------------  verify the api usage ---------------
    ////

    /**
     * When the discovery starts or the engine stops the service requests
     * should be cleared.
     */
    @Test
    fun theServiceRequestsShouldBeCleared(){
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        Thread.sleep(100)
        clearServiceRequestsCallback.captured.onSuccess()
        Thread.sleep(100)
        addServiceRequestsCallback.captured.onSuccess()
        Thread.sleep(100)
        discoverServicesCallback.captured.onSuccess()

        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscovery()
        Thread.sleep(100)
        verify(exactly = 2) { mockedManager.clearServiceRequests(mockedChannel, any()) }
    }


    ////
    ////------------  testing engine ---------------
    ////

    /**
     * When a service is found through the registered callbacks
     * the engine should notify registered listeners
     */
    @Test
    fun itShouldNotifyWhenServiceFound() {
        var receivedDevice: WifiP2pDevice? = null
        var receivedDescription: ServiceDescription? = null
        WifiDirectServiceDiscoveryEngine.getInstance()
            .registerDiscoverListener { host, description ->
                receivedDevice = host
                receivedDescription = description

            }
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFour)

        Thread.sleep(1000) //give it a moment to register everything
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFour.serviceRecord,
            getTestDeviceOne_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFour.serviceName,
            "_presence._tcp.local.",
            getTestDeviceOne_Wifi()
        )
        Thread.sleep(2000)
        assertEquals(testDescriptionFour, receivedDescription)
        assertEquals(getTestDeviceOne_Wifi().deviceName, receivedDevice?.deviceName)
        assertEquals(getTestDeviceOne_Wifi().deviceAddress, receivedDevice?.deviceAddress)
    }

    /**
     * When several services are found in one discovery
     * the engine should notify about all of them
     */
    @Test
    fun itShouldNotifyAboutSeveralServiceDiscoveries() {
        var receivedDevice: WifiP2pDevice? = null
        var receivedDescription: ServiceDescription? = null
        WifiDirectServiceDiscoveryEngine.getInstance()
            .registerDiscoverListener { host, description ->
                receivedDevice = host
                receivedDescription = description
            }
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFour)
        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFour.serviceRecord,
            getTestDeviceOne_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFour.serviceName,
            "_presence._tcp.local.",
            getTestDeviceOne_Wifi()
        )

        assertEquals(testDescriptionFour, receivedDescription)
        assertEquals(getTestDeviceOne_Wifi().deviceName, receivedDevice?.deviceName)
        assertEquals(getTestDeviceOne_Wifi().deviceAddress, receivedDevice?.deviceAddress)

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFour.serviceRecord,
            getTestDeviceTwo_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFour.serviceName,
            "_presence._tcp.local.",
            getTestDeviceTwo_Wifi()
        )

        assertEquals(testDescriptionFour, receivedDescription)
        assertEquals(getTestDeviceTwo_Wifi().deviceName, receivedDevice?.deviceName)
        assertEquals(getTestDeviceTwo_Wifi().deviceAddress, receivedDevice?.deviceAddress)
    }

    /**
     * Services need to be registered for search
     * or the option to notify about every service needs to ben enabled.
     * this checks if the engine wont notify when nothing is registered.
     */
    @Test
    fun itShouldNotNotifyWhenServiceIsNotSearched() {
        var wasNotified = false
        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener { _, _ ->
            wasNotified = true
        }
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFour)

        Thread.sleep(1000) //give it a moment to register everything
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFive.serviceRecord,
            getTestDeviceOne_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFive.serviceName,
            "_presence._tcp.local.",
            getTestDeviceOne_Wifi()
        )

        assertFalse(wasNotified)
    }

    /**
     * The discovery thread my discover services several
     * times, the engine should filter those and only notify once
     */
    @Test
    fun itShouldNotNotifyTwiceAboutAboutTheSameServiceAndHost() {
        var notifiedCounter = 0
        WifiDirectServiceDiscoveryEngine.getInstance()
            .registerDiscoverListener { host, description ->
                notifiedCounter++
            }
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFour)
        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFour.serviceRecord,
            getTestDeviceOne_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFour.serviceName,
            "_presence._tcp.local.",
            getTestDeviceOne_Wifi()
        )
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFour.serviceRecord,
            getTestDeviceOne_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFour.serviceName,
            "_presence._tcp.local.",
            getTestDeviceOne_Wifi()
        )

        assertEquals(1, notifiedCounter)
    }

    /**
     * Discovered service cache should eb cleared with each new
     * discovery run.
     * This means each discovery run should notify about every
     * discovered service even though it was found before
     */
    @Test
    fun itShouldNotifyAgainInNewDiscoveryRun() {
        var notifiedCounter = 0
        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener { _, _ ->
            notifiedCounter++
        }
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFour)
        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFour.serviceRecord,
            getTestDeviceOne_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFour.serviceName,
            "_presence._tcp.local.",
            getTestDeviceOne_Wifi()
        )
        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        Thread.sleep(1000) // give the discovery thread a moment to init everything
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFour.serviceRecord,
            getTestDeviceOne_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFour.serviceName,
            "_presence._tcp.local.",
            getTestDeviceOne_Wifi()
        )
        assertEquals(2, notifiedCounter)
    }

    /**
     * The engine should allow the search for several services at the same times
     * and notify when they are found.
     */
    @Test
    fun itShouldSearchForSeveralServices() {
        var receivedDevice: WifiP2pDevice? = null
        var receivedDescription: ServiceDescription? = null
        WifiDirectServiceDiscoveryEngine.getInstance()
            .registerDiscoverListener { host, description ->
                receivedDevice = host
                receivedDescription = description
            }
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFour)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFive)
        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFive.serviceRecord,
            getTestDeviceOne_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFive.serviceName,
            "_presence._tcp.local.",
            getTestDeviceOne_Wifi()
        )

        assertEquals(testDescriptionFive, receivedDescription)
        assertEquals(getTestDeviceOne_Wifi().deviceName, receivedDevice?.deviceName)
        assertEquals(getTestDeviceOne_Wifi().deviceAddress, receivedDevice?.deviceAddress)

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFour.serviceRecord,
            getTestDeviceOne_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFour.serviceName,
            "_presence._tcp.local.",
            getTestDeviceOne_Wifi()
        )
        assertEquals(testDescriptionFour, receivedDescription)
        assertEquals(getTestDeviceOne_Wifi().deviceName, receivedDevice?.deviceName)
        assertEquals(getTestDeviceOne_Wifi().deviceAddress, receivedDevice?.deviceAddress)
    }

    /**
     * A service discovery for a single service can be stopped
     * it should not notify anymore when the service is
     * is discovered again
     */
    @Test
    fun itShouldStopNotifyingWhenDiscoveryForServiceStopped() {
        var notifiedCounter = 0
        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener { _, _ ->
            notifiedCounter++
        }
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFour)
        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFour.serviceRecord,
            getTestDeviceOne_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFour.serviceName,
            "_presence._tcp.local.",
            getTestDeviceOne_Wifi()
        )
        testDescriptionFour
        assertEquals(1, notifiedCounter)


        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscoveryForService(testDescriptionFour)
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFour.serviceRecord,
            getTestDeviceTwo_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFour.serviceName,
            "_presence._tcp.local.",
            getTestDeviceTwo_Wifi()
        )
        assertEquals(1, notifiedCounter)
    }

    /**
     * If a discovery is stopped other services
     * stay registered and it will still be
     * notified about their discovery
     */
    @Test
    fun itShouldOnlyStopTheCorrectServiceDiscovery() {
        var notifiedCounter = 0
        WifiDirectServiceDiscoveryEngine.getInstance()
            .registerDiscoverListener { host, description ->
                notifiedCounter++
            }
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFive)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFour)
        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFour.serviceRecord,
            getTestDeviceOne_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFour.serviceName,
            "_presence._tcp.local.",
            getTestDeviceOne_Wifi()
        )

        assertEquals(1, notifiedCounter)
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFive.serviceRecord,
            getTestDeviceOne_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFive.serviceName,
            "_presence._tcp.local.",
            getTestDeviceOne_Wifi()
        )


        assertEquals(2, notifiedCounter)

        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscoveryForService(testDescriptionFour)
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFour.serviceRecord,
            getTestDeviceTwo_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFour.serviceName,
            "_presence._tcp.local.",
            getTestDeviceTwo_Wifi()
        )
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFive.serviceRecord,
            getTestDeviceTwo_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFive.serviceName,
            "_presence._tcp.local.",
            getTestDeviceTwo_Wifi()
        )

        assertEquals(3, notifiedCounter)
    }

    /**
     * Several listeners can subscribe to the engine
     * each of them should be notified about discoveries
     */
    @Test
    fun itShouldNotifyAllListener() {
        var receivedDeviceListenerOne: WifiP2pDevice? = null
        var receivedDescriptionListenerOne: ServiceDescription? = null
        var receivedDeviceListenerTwo: WifiP2pDevice? = null
        var receivedDescriptionListenerTwo: ServiceDescription? = null
        var receivedDeviceListenerThree: WifiP2pDevice? = null
        var receivedDescriptionListenerThree: ServiceDescription? = null
        WifiDirectServiceDiscoveryEngine.getInstance()
            .registerDiscoverListener { host, description ->
                receivedDeviceListenerOne = host
                receivedDescriptionListenerOne = description
            }

        WifiDirectServiceDiscoveryEngine.getInstance()
            .registerDiscoverListener { host, description ->
                receivedDeviceListenerTwo = host
                receivedDescriptionListenerTwo = description
            }

        WifiDirectServiceDiscoveryEngine.getInstance()
            .registerDiscoverListener { host, description ->
                receivedDeviceListenerThree = host
                receivedDescriptionListenerThree = description
            }
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFive)

        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFive.serviceRecord,
            getTestDeviceOne_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFive.serviceName,
            "_presence._tcp.local.",
            getTestDeviceOne_Wifi()
        )
        assertEquals(getTestDeviceOne_Wifi(), receivedDeviceListenerOne)
        assertEquals(getTestDeviceOne_Wifi(), receivedDeviceListenerTwo)
        assertEquals(getTestDeviceOne_Wifi(), receivedDeviceListenerThree)

        assertEquals(testDescriptionFive, receivedDescriptionListenerOne)
        assertEquals(testDescriptionFive, receivedDescriptionListenerTwo)
        assertEquals(testDescriptionFive, receivedDescriptionListenerThree)
    }


    /**
     * If the engine is not started, no context
     */
    @Test
    fun itShouldNotCrashIfNotStarted() {
        WifiDirectServiceDiscoveryEngine.getInstance().callPrivateFunc("teardownEngine")

        //--- testing methods calls ---//
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionTwo)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startService(testDescriptionTwo)
        WifiDirectServiceDiscoveryEngine.getInstance().stopService(testDescriptionTwo)
        WifiDirectServiceDiscoveryEngine.getInstance().stopDiscoveryForService(testDescriptionTwo)
    }


    /**
     * Listeners can unregister and wont be notified anymore
     */
    @Test
    fun itShouldAllowListenersToUnregister() {
        var receivedDeviceListenerOne: WifiP2pDevice? = null
        var receivedDescriptionListenerOne: ServiceDescription? = null
        var receivedDeviceListenerTwo: WifiP2pDevice? = null
        var receivedDescriptionListenerTwo: ServiceDescription? = null

        WifiDirectServiceDiscoveryEngine.getInstance()
            .registerDiscoverListener { host, description ->
                receivedDeviceListenerOne = host
                receivedDescriptionListenerOne = description
            }
        val listener =
            WifiServiceDiscoveryListener { host, description ->
                receivedDeviceListenerTwo = host
                receivedDescriptionListenerTwo = description
            }

        WifiDirectServiceDiscoveryEngine.getInstance().registerDiscoverListener(listener)


        WifiDirectServiceDiscoveryEngine.getInstance().startDiscovery()
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFive)
        WifiDirectServiceDiscoveryEngine.getInstance().startDiscoveryForService(testDescriptionFour)
        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFive.serviceRecord,
            getTestDeviceOne_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFive.serviceName,
            "_presence._tcp.local.",
            getTestDeviceOne_Wifi()
        )

        assertEquals(getTestDeviceOne_Wifi(), receivedDeviceListenerOne)
        assertEquals(getTestDeviceOne_Wifi(), receivedDeviceListenerTwo)

        assertEquals(testDescriptionFive, receivedDescriptionListenerOne)
        assertEquals(testDescriptionFive, receivedDescriptionListenerTwo)

        WifiDirectServiceDiscoveryEngine.getInstance().unregisterDiscoveryListener(listener)
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable(
            "",
            testDescriptionFour.serviceRecord,
            getTestDeviceTwo_Wifi()
        )
        servListenerCapture.captured.onDnsSdServiceAvailable(
            testDescriptionFour.serviceName,
            "_presence._tcp.local.",
            getTestDeviceTwo_Wifi()
        )

        assertEquals(getTestDeviceTwo_Wifi(), receivedDeviceListenerOne)
        assertEquals(testDescriptionFour, receivedDescriptionListenerOne)

        // did not change - was not notified
        assertEquals(getTestDeviceOne_Wifi(), receivedDeviceListenerTwo)
        assertEquals(testDescriptionFive, receivedDescriptionListenerTwo)

    }

    private fun getTestDeviceOne_Wifi(): WifiP2pDevice {
        val testDevice = WifiP2pDevice()
        testDevice.deviceAddress =  "testDeviceOneAddress"
        testDevice.deviceName =  "testDeviceOne"
        return testDevice
    }

    private fun getTestDeviceTwo_Wifi(): WifiP2pDevice {
        val testDevice = WifiP2pDevice()
        testDevice.deviceAddress =  "testDeviceTwoAddress"
        testDevice.deviceName =  "testDeviceTwo"
        return testDevice
    }


}
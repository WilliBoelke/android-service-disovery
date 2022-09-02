package willi.boelke.serviceDiscovery.wifiDirect.sdpWifiDirectDiscovery

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
import willi.boelke.serviceDiscovery.serviceDescription.ServiceDescription
import willi.boelke.serviceDiscovery.testUtils.callPrivateFunc
import willi.boelke.serviceDiscovery.testUtils.initTestMocks
import willi.boelke.serviceDiscovery.testUtils.testDescriptionFive
import willi.boelke.serviceDiscovery.testUtils.testDescriptionFour

@RunWith(AndroidJUnit4ClassRunner::class)
class WifiDirectDiscoveryEngineTest {

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
        SdpWifiDirectDiscoveryEngine.getInstance().start(mockedManager, mockedChannel)

        initTestMocks()
    }

    @After
    fun teardown() {
        SdpWifiDirectDiscoveryEngine.getInstance().callPrivateFunc("teardownEngine")
    }


    @Test
    fun itShouldStart() {
        assertTrue( SdpWifiDirectDiscoveryEngine.getInstance() != null)
    }

    @Test
    fun verifyDiscoveryApiUsage(){
        SdpWifiDirectDiscoveryEngine.getInstance().startDiscovery()
        Thread.sleep(100)
        clearServiceRequestsCallback.captured.onSuccess()
        Thread.sleep(100)
        addServiceRequestsCallback.captured.onSuccess()
        Thread.sleep(100)
        discoverServicesCallback.captured.onSuccess()
        Thread.sleep(11000)

        verify(exactly = 1) { mockedManager.discoverServices(mockedChannel, any()) }
        verify(exactly = 2) { mockedManager.clearServiceRequests(mockedChannel, any()) }
        verify(exactly = 1) { mockedManager.addServiceRequest(mockedChannel, any(), any()) }
        verify(exactly = 1) { mockedManager.setDnsSdResponseListeners(mockedChannel, any(), any())}
    }

    @Test
    fun theServiceRequestsShouldBeCleared(){
        SdpWifiDirectDiscoveryEngine.getInstance().startDiscovery()
        Thread.sleep(100)
        clearServiceRequestsCallback.captured.onSuccess()
        Thread.sleep(100)
        addServiceRequestsCallback.captured.onSuccess()
        Thread.sleep(100)
        discoverServicesCallback.captured.onSuccess()
        verify(exactly = 1) { mockedManager.clearServiceRequests(mockedChannel, any())}

        SdpWifiDirectDiscoveryEngine.getInstance().stopDiscovery()
        Thread.sleep(100)
        verify(exactly = 3) { mockedManager.clearServiceRequests(mockedChannel, any()) }
    }

    @Test
    fun itShouldNotifyWhenServiceFound(){
        var receivedDevice: WifiP2pDevice? = null
        var receivedDescription: ServiceDescription? = null
        SdpWifiDirectDiscoveryEngine.getInstance().registerDiscoverListener { host, description ->
            receivedDevice = host
            receivedDescription = description

        }
        SdpWifiDirectDiscoveryEngine.getInstance().startDiscovery()
        SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(testDescriptionFour)

        Thread.sleep(1000) //give it a moment to register everything
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFour.serviceRecord, getTestDeviceOne_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFour.serviceName, "_presence._tcp.local." , getTestDeviceOne_Wifi())
        Thread.sleep(2000)
        assertEquals(testDescriptionFour, receivedDescription)
        assertEquals(getTestDeviceOne_Wifi().deviceName, receivedDevice?.deviceName)
        assertEquals(getTestDeviceOne_Wifi().deviceAddress, receivedDevice?.deviceAddress)
    }

    @Test
    fun itShouldNotifyAboutSeveralServiceDiscoveries(){
        var receivedDevice: WifiP2pDevice? = null
        var receivedDescription: ServiceDescription? = null
        SdpWifiDirectDiscoveryEngine.getInstance().registerDiscoverListener { host, description ->
            receivedDevice = host
            receivedDescription = description
        }
        SdpWifiDirectDiscoveryEngine.getInstance().startDiscovery()
        SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(testDescriptionFour)
        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFour.serviceRecord, getTestDeviceOne_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFour.serviceName, "_presence._tcp.local." , getTestDeviceOne_Wifi())

        assertEquals(testDescriptionFour, receivedDescription)
        assertEquals(getTestDeviceOne_Wifi().deviceName, receivedDevice?.deviceName)
        assertEquals(getTestDeviceOne_Wifi().deviceAddress, receivedDevice?.deviceAddress)

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFour.serviceRecord, getTestDeviceTwo_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFour.serviceName, "_presence._tcp.local." , getTestDeviceTwo_Wifi())

        assertEquals(testDescriptionFour, receivedDescription)
        assertEquals(getTestDeviceTwo_Wifi().deviceName, receivedDevice?.deviceName)
        assertEquals(getTestDeviceTwo_Wifi().deviceAddress, receivedDevice?.deviceAddress)
    }


    @Test
    fun itShouldNotNotifyWhenServiceIsNotSearched(){
        var wasNotified = false
        SdpWifiDirectDiscoveryEngine.getInstance().registerDiscoverListener { host, description ->
            wasNotified = true
        }
        SdpWifiDirectDiscoveryEngine.getInstance().startDiscovery()
        SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(testDescriptionFour)

        Thread.sleep(1000) //give it a moment to register everything
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFive.serviceRecord, getTestDeviceOne_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFive.serviceName, "_presence._tcp.local." , getTestDeviceOne_Wifi())

        assertFalse(wasNotified)
    }


    @Test
    fun itShouldNotNotifyTwiceAboutAboutTheSameServiceAndHost(){
        var notifiedCounter = 0
        SdpWifiDirectDiscoveryEngine.getInstance().registerDiscoverListener { host, description ->
            notifiedCounter++
        }
        SdpWifiDirectDiscoveryEngine.getInstance().startDiscovery()
        SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(testDescriptionFour)
        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFour.serviceRecord, getTestDeviceOne_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFour.serviceName, "_presence._tcp.local." , getTestDeviceOne_Wifi())
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFour.serviceRecord, getTestDeviceOne_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFour.serviceName, "_presence._tcp.local." , getTestDeviceOne_Wifi())


        assertEquals(1, notifiedCounter)
    }

    @Test
    fun itShouldNotifyAgainInNewDiscoveryRun()
    {
        var notifiedCounter = 0
        SdpWifiDirectDiscoveryEngine.getInstance().registerDiscoverListener { host, description ->
            notifiedCounter++
        }
        SdpWifiDirectDiscoveryEngine.getInstance().startDiscovery()
        SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(testDescriptionFour)
        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFour.serviceRecord, getTestDeviceOne_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFour.serviceName, "_presence._tcp.local." , getTestDeviceOne_Wifi())
        SdpWifiDirectDiscoveryEngine.getInstance().stopDiscovery()
        SdpWifiDirectDiscoveryEngine.getInstance().startDiscovery()
        Thread.sleep(1000) // give the discovery thread a moment to init everything
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFour.serviceRecord, getTestDeviceOne_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFour.serviceName, "_presence._tcp.local." , getTestDeviceOne_Wifi())
        assertEquals(2, notifiedCounter)
    }


    @Test
    fun itShouldSearchForSeveralServices()
    {
        var receivedDevice: WifiP2pDevice? = null
        var receivedDescription: ServiceDescription? = null
        SdpWifiDirectDiscoveryEngine.getInstance().registerDiscoverListener { host, description ->
            receivedDevice = host
            receivedDescription = description
        }
        SdpWifiDirectDiscoveryEngine.getInstance().startDiscovery()
        SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(testDescriptionFour)
        SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(testDescriptionFive)
        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFive.serviceRecord, getTestDeviceOne_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFive.serviceName, "_presence._tcp.local." , getTestDeviceOne_Wifi())

        assertEquals(testDescriptionFive, receivedDescription)
        assertEquals(getTestDeviceOne_Wifi().deviceName, receivedDevice?.deviceName)
        assertEquals(getTestDeviceOne_Wifi().deviceAddress, receivedDevice?.deviceAddress)

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFour.serviceRecord, getTestDeviceOne_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFour.serviceName, "_presence._tcp.local." , getTestDeviceOne_Wifi())
        assertEquals(testDescriptionFour, receivedDescription)
        assertEquals(getTestDeviceOne_Wifi().deviceName, receivedDevice?.deviceName)
        assertEquals(getTestDeviceOne_Wifi().deviceAddress, receivedDevice?.deviceAddress)
    }

    @Test
    fun itShouldStopNotifyingAfterSdpHasBeenStopped()
    {
        var notifiedCounter = 0
        SdpWifiDirectDiscoveryEngine.getInstance().registerDiscoverListener { host, description ->
            notifiedCounter++
        }
        SdpWifiDirectDiscoveryEngine.getInstance().startDiscovery()
        SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(testDescriptionFour)
        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFour.serviceRecord, getTestDeviceOne_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFour.serviceName, "_presence._tcp.local." , getTestDeviceOne_Wifi())
        testDescriptionFour
        assertEquals(1, notifiedCounter)


        SdpWifiDirectDiscoveryEngine.getInstance().stopSdpDiscovery(testDescriptionFour)
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFour.serviceRecord, getTestDeviceTwo_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFour.serviceName, "_presence._tcp.local." , getTestDeviceTwo_Wifi())
        assertEquals(1, notifiedCounter)
    }

    @Test
    fun itShouldOnlyStopTheCorrectServiceDiscovery(){
        var notifiedCounter = 0
        SdpWifiDirectDiscoveryEngine.getInstance().registerDiscoverListener { host, description ->
            notifiedCounter++
        }
        SdpWifiDirectDiscoveryEngine.getInstance().startDiscovery()
        SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(testDescriptionFive)
        SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(testDescriptionFour)
        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFour.serviceRecord, getTestDeviceOne_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFour.serviceName, "_presence._tcp.local." , getTestDeviceOne_Wifi())

        assertEquals(1, notifiedCounter)
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFive.serviceRecord, getTestDeviceOne_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFive.serviceName, "_presence._tcp.local." , getTestDeviceOne_Wifi())


        assertEquals(2, notifiedCounter)

        SdpWifiDirectDiscoveryEngine.getInstance().stopSdpDiscovery(testDescriptionFour)
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFour.serviceRecord, getTestDeviceTwo_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFour.serviceName, "_presence._tcp.local." , getTestDeviceTwo_Wifi())
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFive.serviceRecord, getTestDeviceTwo_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFive.serviceName, "_presence._tcp.local." , getTestDeviceTwo_Wifi())

        assertEquals(3, notifiedCounter)
    }


    @Test
    fun itShouldNotifyAllListener(){
        var receivedDeviceListenerOne: WifiP2pDevice? = null
        var receivedDescriptionListenerOne: ServiceDescription? = null
        var receivedDeviceListenerTwo: WifiP2pDevice? = null
        var receivedDescriptionListenerTwo: ServiceDescription? = null
        var receivedDeviceListenerThree: WifiP2pDevice? = null
        var receivedDescriptionListenerThree: ServiceDescription? = null
        SdpWifiDirectDiscoveryEngine.getInstance().registerDiscoverListener { host, description ->
            receivedDeviceListenerOne = host
            receivedDescriptionListenerOne = description
        }

        SdpWifiDirectDiscoveryEngine.getInstance().registerDiscoverListener { host, description ->
            receivedDeviceListenerTwo = host
            receivedDescriptionListenerTwo = description
        }

        SdpWifiDirectDiscoveryEngine.getInstance().registerDiscoverListener { host, description ->
            receivedDeviceListenerThree = host
            receivedDescriptionListenerThree = description
        }
        SdpWifiDirectDiscoveryEngine.getInstance().startDiscovery()
        SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(testDescriptionFive)

        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFive.serviceRecord, getTestDeviceOne_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFive.serviceName, "_presence._tcp.local." , getTestDeviceOne_Wifi())
        assertEquals(getTestDeviceOne_Wifi(), receivedDeviceListenerOne)
        assertEquals(getTestDeviceOne_Wifi(), receivedDeviceListenerTwo)
        assertEquals(getTestDeviceOne_Wifi(), receivedDeviceListenerThree)

        assertEquals(testDescriptionFive, receivedDescriptionListenerOne)
        assertEquals(testDescriptionFive, receivedDescriptionListenerTwo)
        assertEquals(testDescriptionFive, receivedDescriptionListenerThree)
    }

    @Test
    fun itShouldAllowListenersToUnregister(){
        var receivedDeviceListenerOne: WifiP2pDevice? = null
        var receivedDescriptionListenerOne: ServiceDescription? = null
        var receivedDeviceListenerTwo: WifiP2pDevice? = null
        var receivedDescriptionListenerTwo: ServiceDescription? = null

        SdpWifiDirectDiscoveryEngine.getInstance().registerDiscoverListener { host, description ->
            receivedDeviceListenerOne = host
            receivedDescriptionListenerOne = description
        }
        val listener =
            WifiServiceDiscoveryListener { host, description ->
                receivedDeviceListenerTwo = host
                receivedDescriptionListenerTwo = description
            }

        SdpWifiDirectDiscoveryEngine.getInstance().registerDiscoverListener(listener)


        SdpWifiDirectDiscoveryEngine.getInstance().startDiscovery()
        SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(testDescriptionFive)
        SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(testDescriptionFour)
        Thread.sleep(1000) // give the discovery thread a moment to init everything

        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFive.serviceRecord, getTestDeviceOne_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFive.serviceName, "_presence._tcp.local." , getTestDeviceOne_Wifi())

        assertEquals(getTestDeviceOne_Wifi(), receivedDeviceListenerOne)
        assertEquals(getTestDeviceOne_Wifi(), receivedDeviceListenerTwo)

        assertEquals(testDescriptionFive, receivedDescriptionListenerOne)
        assertEquals(testDescriptionFive, receivedDescriptionListenerTwo)

        SdpWifiDirectDiscoveryEngine.getInstance().unregisterDiscoveryListener(listener)
        txtListenerCapture.captured.onDnsSdTxtRecordAvailable("", testDescriptionFour.serviceRecord, getTestDeviceTwo_Wifi())
        servListenerCapture.captured.onDnsSdServiceAvailable(testDescriptionFour.serviceName, "_presence._tcp.local." , getTestDeviceTwo_Wifi())

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
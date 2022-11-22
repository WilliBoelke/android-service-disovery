package willi.boelke.services.serviceConnection.wifiDirectServiceConnection

import android.arch.core.executor.testing.CountingTaskExecutorRule
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import io.mockk.*
import junit.framework.TestCase.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import willi.boelke.services.serviceDiscovery.ServiceDescription
import willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery.WifiDirectServiceDiscoveryEngine
import willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery.WifiServiceDiscoveryListener
import willi.boelke.services.testUtils.*
import java.io.IOException


/**
 * These are unit tests for a [WifiDirectConnectionEngine]
 * While other internal classes like [ServiceDescription]s
 * are used, Android classes like Context and WifiP2pManager
 * will be mocked. ALso also the [WifiDirectServiceDiscoveryEngine]
 * will be replaced through a mock object which is only used to capture the
 * listener implementation
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
 * Sine the complete Wifi P2P API is mocked, and the
 * BroadcastReceivers, and especially  [WifiDirectConnectionInfoListener]
 * are not actually functioning, test could be run ignoring the actual flow of events.
 * For example `onUuidsFetched()`, could be called without prior `onDeviceDiscoveryFinished()`,
 * this also would work.
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
class UnitWifiDirectServiceConnectionEngine {

    /**
     * Executing sequentially
     */
    @get:Rule
    val instantTaskExecutorRule = CountingTaskExecutorRule()

    private lateinit var mockedManager: WifiP2pManager
    private lateinit var mockedChannel: WifiP2pManager.Channel
    private lateinit var mockedContext: Context
    private lateinit var mockedWifiManager: WifiManager
    private lateinit var mockedDiscovery: WifiDirectServiceDiscoveryEngine

    private var discoveryListener = CapturingSlot<WifiServiceDiscoveryListener>()

    @Before
    fun setup() {
        //Setup
        mockedManager = mockk<WifiP2pManager>(relaxed = true)
        mockedChannel = mockk<WifiP2pManager.Channel>(relaxed = true)
        mockedWifiManager = mockk<WifiManager>()
        mockedContext = mockk<Context>(relaxed = true)
        mockedDiscovery = mockk<WifiDirectServiceDiscoveryEngine>(relaxed = true)

        justRun {
            mockedDiscovery.registerDiscoverListener(
                capture(discoveryListener)
            )
        }

        justRun { mockedManager.clearLocalServices(any(), any()) }
        justRun { mockedChannel.close() }

        every { mockedContext.applicationContext.getSystemService(Context.WIFI_SERVICE) } returns mockedWifiManager
        every { mockedContext.applicationContext.getSystemService(Context.WIFI_SERVICE) } returns mockedWifiManager
        every { mockedContext.getSystemService(Context.WIFI_P2P_SERVICE) } returns mockedManager
        every { mockedContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) } returns true
        every { mockedManager.initialize(mockedContext, any(), any()) } returns mockedChannel
        every { mockedWifiManager.isP2pSupported } returns true

        //Run
        WifiDirectConnectionEngine.getInstance()
            .start(mockedContext, mockedDiscovery)

        initTestMocks()
    }

    @After
    fun teardown() {
        WifiDirectConnectionEngine.getInstance().callPrivateFunc("teardownEngine")
    }

    //
    //------------  utils ---------------
    //

    /**
     * This implementation shall serve as mock implementation
     * of a bluetooth service client, to verify the engines responses
     */
    private class TestPeer : WifiDirectPeer {
        var found = 0
        var shouldConnect = false
        val foundServices: ArrayList<ServiceDescription> = ArrayList()
        val foundServiceHosts: ArrayList<WifiP2pDevice> = ArrayList()
        val establishedConnections: ArrayList<WifiConnection> = ArrayList()
        var roleAssigned = false
        var groupOwner = false
        var client = false

        constructor() {
            //empty constructor wont - no connection
        }

        constructor(connect: Boolean) {
            shouldConnect = connect
        }

        override fun onBecameGroupOwner() {
            this.roleAssigned = true
            this.groupOwner = true
        }

        override fun onBecameGroupClient() {
            this.roleAssigned = true
            this.client = true
        }

        override fun onConnectionEstablished(connection: WifiConnection) {
            establishedConnections.add(connection)
        }

        override fun shouldConnectTo(
            host: WifiP2pDevice,
            description: ServiceDescription
        ): Boolean {
            return shouldConnect
        }

        override fun onServiceDiscovered(device: WifiP2pDevice, description: ServiceDescription) {
            found++
            foundServiceHosts.add(device)
            foundServices.add(description)
        }

        fun hasFoundServices(i: Int): Boolean {
            return found == i
        }

        fun hasDiscoveredService(description: ServiceDescription): Boolean {
            return foundServices.contains(description)
        }

        fun hasDiscoveredPeer(device: WifiP2pDevice): Boolean {
            return foundServiceHosts.contains(device)
        }
    }

    fun simulateServiceDiscovery(peer: WifiP2pDevice, description: ServiceDescription) {
        discoveryListener.captured.onServiceDiscovered(peer, description)
    }

    @Test
    fun itShouldStart() {
        assertTrue(
            WifiDirectConnectionEngine.getInstance()
                .start(mockedContext, mockedDiscovery)
        )
        verify { mockedDiscovery.registerDiscoverListener(any()) }

    }

    @Test
    fun itShouldRegisterServiceForDiscovery() {
        val peer = TestPeer()
        val description = testDescriptionOne
        val result = WifiDirectConnectionEngine.getInstance().registerService(description, peer)
        verify(exactly = 1) { mockedDiscovery.startService(description) }
        verify(exactly = 1) { mockedDiscovery.startDiscoveryForService(description) }
        assertTrue(result)
    }

    @Test
    fun itShouldNotRegisterASecondService() {
        val peer = TestPeer()
        val description = testDescriptionOne
        WifiDirectConnectionEngine.getInstance().registerService(description, peer)
        val result = WifiDirectConnectionEngine.getInstance().registerService(description, peer)
        verify(exactly = 1) { mockedDiscovery.startService(description) }
        verify(exactly = 1) { mockedDiscovery.startDiscoveryForService(description) }
        assertFalse(result)
    }

    /**
     * When a service was discovered the peer will be notified
     */
    @Test
    fun itShouldNotifyWhenAServiceWasFound() {
        val peer = TestPeer()
        val testDevice = getTestDeviceOne_Wifi()
        val description = testDescriptionOne
        WifiDirectConnectionEngine.getInstance()
            .registerService(description, peer)

        simulateServiceDiscovery(testDevice, description)

        assertTrue(peer.hasFoundServices(1))
        assertTrue(peer.hasDiscoveredService(description))
    }

    /**
     * It will notify the peer when several services are
     * discovered
     */
    @Test
    fun itShouldNotifyWhenSeveralServiceWereFound() {
        val peer = TestPeer()
        val testDeviceOne = getTestDeviceOne_Wifi()
        val testDeviceTwo = getTestDeviceTwo_Wifi()
        val description = testDescriptionOne
        WifiDirectConnectionEngine.getInstance()
            .registerService(description, peer)

        simulateServiceDiscovery(testDeviceOne, description)
        assertTrue(peer.hasFoundServices(1))

        simulateServiceDiscovery(testDeviceTwo, description)

        assertTrue(peer.hasDiscoveredService(description))
        assertTrue(peer.hasDiscoveredPeer(testDeviceOne))
        assertTrue(peer.hasDiscoveredPeer(testDeviceTwo))
    }


    /**
     * I a service / device is  discovered,
     * the service is registered for search
     * and the peer allows to connect.
     * A WifiP2p connection should be started
     */
    @Test
    fun itShouldTryToConnect() {
        val peer = TestPeer(true)
        val description = testDescriptionTwo
        val testDeviceOne = getTestDeviceOne_Wifi()

        WifiDirectConnectionEngine.getInstance()
            .registerService(description, peer)

        simulateServiceDiscovery(testDeviceOne, description)
        val config: CapturingSlot<WifiP2pConfig> = CapturingSlot()
        verify { mockedManager.connect(mockedChannel, capture(config), any()) }

        assertEquals(testDeviceOne.deviceAddress, config.captured.deviceAddress)
    }

    /**
     * I a service / device is  discovered,
     * the service is registered for search
     * but the peer doesn't allow the connection
     * the connection wont be attempted
     */
    @Test
    fun theClientCanDeclineTheConnection() {
        val peer = TestPeer()
        val description = testDescriptionTwo
        val testDeviceOne = getTestDeviceOne_Wifi()

        WifiDirectConnectionEngine.getInstance()
            .registerService(description, peer)

        simulateServiceDiscovery(testDeviceOne, description)
        val config: CapturingSlot<WifiP2pConfig> = CapturingSlot()
        verify(exactly = 0) { mockedManager.connect(mockedChannel, capture(config), any()) }
    }


    /**
     * If a service is discovered and the client callback `shouldConnect`
     * returns true, a connection attempt should be started.
     * This also should notify about more connections
     */
    @Test
    fun itShouldTryToConnectToSeveralServices() {

    }

    /**
     * When connecting the socket an IO Exception can occur
     * this should be handled
     */
    @Test
    fun itShouldHandleIoExceptionsWhenTryingToConnect() {

        val testDeviceOne = getTestDeviceOne()
        val mockedSocket = getSocketToTestDevice(testDeviceOne)

        every { testDeviceOne.createRfcommSocketToServiceRecord(testUUIDTwo) } returns mockedSocket
        every { mockedSocket.connect() } throws IOException()

        val client = TestPeer()
        WifiDirectConnectionEngine.getInstance()
            .registerService(testDescriptionTwo, client)

        //  discoveryListener.captured.onPeerDiscovered(testDeviceOne)
        //  discoveryListener.captured.onServiceDiscovered(testDeviceOne, testDescriptionTwo)

        verify(exactly = 1) { testDeviceOne.createRfcommSocketToServiceRecord(testUUIDTwo) }
        verify(exactly = 1) { mockedSocket.connect() }
        verify(exactly = 2) { mockedSocket.close() }
    }

    /**
     * When a Service was started client connections should be accepted
     * after a connection was accepted the server socket should be reopened
     * and further connections should be accepted.
     */
    @Test
    fun itShouldAcceptConnectionsWhenServiceStarted() {

        val mockedServerSocket = mockk<BluetoothServerSocket>()
        val mockedSocket = getSocketToTestDevice(getTestDeviceOne())
        val answerF = FunctionAnswer { Thread.sleep(2000); mockedSocket }
        every { mockedServerSocket.accept() }.answers(answerF)
        justRun { mockedServerSocket.close() }

        //  every { mockedBtAdapter.listenUsingRfcommWithServiceRecord(any(), any()) } returns mockedServerSocket

        //   WifiDirectConnectionEngine.getInstance().startSDPService(testDescriptionOne) {}

        Thread.sleep(3000)
        // In the given time exactly one connection should be accepted
        // verify(exactly = 1) { mockedBtAdapter.listenUsingRfcommWithServiceRecord(
        //      testDescriptionOne.serviceName, testDescriptionOne.serviceUuid)}
        // Accept will be called twice, once will go trough, then the tread loops and watt for the next connection
        verify(exactly = 2) { mockedServerSocket.accept() }
        Thread.sleep(2000)
        // Still only one Server socket should be opened
        // verify(exactly = 1) { mockedBtAdapter.listenUsingRfcommWithServiceRecord(
        //     testDescriptionOne.serviceName, testDescriptionOne.serviceUuid)}
        // his should accept he second connection and start waiting for the next
        verify(exactly = 3) { mockedServerSocket.accept() }
    }

    /**
     * A service (with a given service description)
     * can only be registered once
     */
    @Test
    fun itShouldNotStartTheSameServiceTwice() {
        val mockedServerSocket = mockk<BluetoothServerSocket>()
        val mockedSocket = getSocketToTestDevice(getTestDeviceOne())
        val answerF = FunctionAnswer { Thread.sleep(2000); mockedSocket }
        every { mockedServerSocket.accept() }.answers(answerF)
        justRun { mockedServerSocket.close() }
        //  every { mockedBtAdapter.listenUsingRfcommWithServiceRecord(any(), any()) } returns mockedServerSocket

        //    val createdFirst =
        //      WifiDirectConnectionEngine.getInstance().startSDPService(testDescriptionOne) {}

        //  val createdSecond =
        //         WifiDirectConnectionEngine.getInstance().startSDPService(testDescriptionOne) {}

        // This should all stay he same, there is no second service created =
        Thread.sleep(3000)
        // In the given time exactly one connection should be accepted
        //   verify(exactly = 1) { mockedBtAdapter.listenUsingRfcommWithServiceRecord(
        //     testDescriptionOne.serviceName, testDescriptionOne.serviceUuid)}
        // Accept will be called twice, once will go trough, then the tread loops and watt for the next connection
        verify(exactly = 2) { mockedServerSocket.accept() }
        Thread.sleep(2000)
        // Still only one Server socket should be opened
        //   verify(exactly = 1) { mockedBtAdapter.listenUsingRfcommWithServiceRecord(
        //       testDescriptionOne.serviceName, testDescriptionOne.serviceUuid)}
        // his should accept he second connection and start waiting for the next
        //   verify(exactly = 3) { mockedServerSocket.accept()}

        // checking return
        //   assertTrue(createdFirst)
        //  TestCase.assertFalse(createdSecond)
    }

    @Test
    fun itShouldNotifyServerOnCreatedConnection() {

    }

    /**
     * Testing if several services and be advertised at the same times, and incoming connections
     * will be accepted by them
     */
    @Test
    fun itShouldBeAbleToRunSeveralServicesAtTheSametime() {

    }

    /**
     * When a service is closed and unregistered
     * the service socket needs to eb closed
     */
    @Test
    fun itShouldCloseTheServerSocketWhenEndingTheService() {

    }

    /**
     * The server socket may produce a null pointer exception which
     * needs to be handled
     */
    @Test
    fun iShouldNotCrashOnAServerSocketNullPointerException() {
    }


}
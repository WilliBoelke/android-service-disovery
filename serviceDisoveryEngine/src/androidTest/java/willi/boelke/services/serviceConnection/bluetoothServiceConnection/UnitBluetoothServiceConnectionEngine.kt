package willi.boelke.services.serviceConnection.bluetoothServiceConnection

import android.arch.core.executor.testing.CountingTaskExecutorRule
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import io.mockk.*
import junit.framework.TestCase.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import willi.boelke.services.serviceDiscovery.ServiceDescription
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryListener
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryVTwo
import willi.boelke.services.testUtils.*
import java.io.IOException

/**
 * These are unit tests for a [BluetoothServiceConnectionEngine]
 * While other internal classes like [ServiceDescription]s
 * are used, Android classes like Context and BluetoothAdapter
 * will be mocked. ALso also the Service discovery will be replaced
 * through a mock object which is only used to capture the
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
class UnitBluetoothServiceConnectionEngine {

    /**
     * Executing sequentially
     */
    @get:Rule
    val instantTaskExecutorRule = CountingTaskExecutorRule()

    private lateinit var mockedBtAdapter: BluetoothAdapter
    private lateinit var mockedContext: Context
    private lateinit var mockedDiscoveryVTwo: BluetoothServiceDiscoveryVTwo

    private var discoveryListener = CapturingSlot<BluetoothServiceDiscoveryListener>()

    @Before
    fun setup() {
        //Setup
        mockedContext = mockk<Context>(relaxed = true)
        mockedBtAdapter = mockk<BluetoothAdapter>()
        mockedDiscoveryVTwo = mockk<BluetoothServiceDiscoveryVTwo>(relaxed = true)


        justRun {
            mockedDiscoveryVTwo.registerDiscoverListener(
                capture(discoveryListener)
            )
        }

        every { mockedBtAdapter.isEnabled } returns true
        every { mockedBtAdapter.isDiscovering } returns false
        every { mockedBtAdapter.startDiscovery() } returns true
        every { mockedBtAdapter.cancelDiscovery() } returns true
        every { mockedBtAdapter.startDiscovery() } returns true
        every { mockedDiscoveryVTwo.isRunning } returns true

        //Run
        BluetoothServiceConnectionEngine.getInstance()
            .start(mockedContext, mockedBtAdapter, mockedDiscoveryVTwo)

        initTestMocks()
    }

    @After
    fun teardown() {
        BluetoothServiceConnectionEngine.getInstance().callPrivateFunc("teardownEngine")
    }

    //
    //------------  utils ---------------
    //

    /**
     * This implementation shall serve as mock implementation
     * of a bluetooth service client, to verify the engines responses
     */
    private class TestClientPeer : BluetoothServiceClient {

        var shouldConnect = false
        val foundDevices: ArrayList<BluetoothDevice> = ArrayList()
        val foundServices: ArrayList<ServiceDescription> = ArrayList()
        val foundServiceHosts: ArrayList<BluetoothDevice> = ArrayList()
        val establishedConnections: ArrayList<BluetoothConnection> = ArrayList()

        constructor() {//empty constructor wont - no connection
        }

        constructor(connect: Boolean) {
            shouldConnect = connect
        }

        override fun onServiceDiscovered(host: BluetoothDevice, description: ServiceDescription) {
            foundServices.add(description)
            foundServiceHosts.add(host)
        }

        override fun onPeerDiscovered(peer: BluetoothDevice) {
            foundDevices.add(peer)
        }

        override fun onConnectedToService(connection: BluetoothConnection) {
            establishedConnections.add(connection)
        }

        override fun shouldConnectTo(
            host: BluetoothDevice,
            description: ServiceDescription
        ): Boolean {
            return shouldConnect
        }
    }

    //
    //------------  tests ---------------
    //

    @Test
    fun itShouldStartDiscoveryEngine() {
        verify { mockedDiscoveryVTwo.start(mockedContext, mockedBtAdapter) }
        verify { mockedDiscoveryVTwo.registerDiscoverListener(any()) }
    }

    @Test
    fun itShouldRegisterServiceForDiscovery() {
        BluetoothServiceConnectionEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionOne, TestClientPeer())
        verify { mockedDiscoveryVTwo.startDiscoveryForService(testDescriptionOne) }

    }

    @Test
    fun itShouldUnregisterServiceFromDiscovery() {
        BluetoothServiceConnectionEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionOne, TestClientPeer())
        BluetoothServiceConnectionEngine.getInstance()
            .stopSDPDiscoveryForService(testDescriptionOne)
        verify { mockedDiscoveryVTwo.stopDiscoveryForService(testDescriptionOne) }
    }

    /**
     * When a peer was discovered clients will be notified
     */
    @Test
    fun itShouldNotifyWhenAPeerWasFound() {
        val client = TestClientPeer()
        val testDevice = getTestDeviceOne()
        BluetoothServiceConnectionEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionOne, client)
        discoveryListener.captured.onPeerDiscovered(testDevice)
        assertEquals(testDevice, client.foundDevices[0])
    }

    /**
     * Several clients can be registered at the connection engine,
     * each of the should be notified about discovered peers
     */
    @Test
    fun itShouldNotifySeveralClientsAboutDiscoveredDevices() {
        val clientOne = TestClientPeer()
        val clientTwo = TestClientPeer()

        BluetoothServiceConnectionEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionOne, clientOne)

        BluetoothServiceConnectionEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionFour, clientTwo)

        val testDeviceOne = getTestDeviceOne()

        discoveryListener.captured.onPeerDiscovered(testDeviceOne)

        assertEquals(testDeviceOne, clientOne.foundDevices[0])
        assertEquals(testDeviceOne, clientTwo.foundDevices[0])
    }

    /**
     * When a service is discovered the listener should be notified
     */
    @Test
    fun itShouldNotifyAboutAServiceDiscovery() {

        val client = TestClientPeer()

        //Start client looking for uuid four, which is part of test array two
        BluetoothServiceConnectionEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionTwo, client)

        // discovered device with
        val testDeviceOne = getTestDeviceOne()
        discoveryListener.captured.onPeerDiscovered(testDeviceOne)
        discoveryListener.captured.onServiceDiscovered(testDeviceOne, testDescriptionTwo)

        assertEquals(testDeviceOne, client.foundServiceHosts[0])
        assertEquals(testDescriptionTwo, client.foundServices[0])
    }

    /**
     * Several client can register to search a service, each should be notified only
     * about the service it looks for
     */
    @Test
    fun itShouldOnlyNotifyTheRightClientAboutItsServices() {
        val clientOne = TestClientPeer()
        val clientTwo = TestClientPeer()

        BluetoothServiceConnectionEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionTwo, clientTwo)

        BluetoothServiceConnectionEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionFour, clientOne)
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()

        discoveryListener.captured.onPeerDiscovered(testDeviceOne)
        discoveryListener.captured.onServiceDiscovered(testDeviceOne, testDescriptionTwo)
        discoveryListener.captured.onPeerDiscovered(testDeviceTwo)
        discoveryListener.captured.onServiceDiscovered(testDeviceTwo, testDescriptionFour)

        assertTrue(clientOne.foundServiceHosts.contains(testDeviceTwo))
        assertFalse(clientOne.foundServiceHosts.contains(testDeviceOne))

        assertTrue(clientTwo.foundServiceHosts.contains(testDeviceOne))
        assertFalse(clientTwo.foundServiceHosts.contains(testDeviceTwo))

        assertTrue(clientOne.foundServices.contains(testDescriptionFour))
        assertFalse(clientOne.foundServices.contains(testDescriptionTwo))

        assertTrue(clientTwo.foundServices.contains(testDescriptionTwo))
        assertFalse(clientTwo.foundServices.contains(testDescriptionFour))
    }

    /**
     * If a service is discovered and the client callback `shouldConnect`
     * returns true, a connection attempt should be started.
     */
    @Test
    fun itShouldTryToConnectToService() {
        val testDeviceOne = getTestDeviceOne()
        val mockedSocket = getSocketToTestDevice(getTestDeviceTwo())
        // some further methods need to be mocked :
        every { testDeviceOne.createRfcommSocketToServiceRecord(testUUIDTwo) } returns mockedSocket
        justRun { mockedSocket.connect() }

        val client = TestClientPeer(true)

        BluetoothServiceConnectionEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionTwo, client)

        discoveryListener.captured.onPeerDiscovered(testDeviceOne)
        discoveryListener.captured.onServiceDiscovered(testDeviceOne, testDescriptionTwo)

        assertEquals(getTestDeviceTwo().name, client.establishedConnections[0].remoteDevice.name)
        assertFalse(client.establishedConnections[0].isServerPeer) // connected as client
    }

    /**
     * If a service is discovered and the client callback `shouldConnect`
     * returns true, a connection attempt should be started.
     * This also should notify about more connections
     */
    @Test
    fun itShouldTryToConnectToSeveralServices() {
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        val mockedSocketOne = getSocketToTestDevice(testDeviceOne)
        val mockedSocketTwo = getSocketToTestDevice(testDeviceTwo)

        // some further methods need to be mocked :
        every { testDeviceOne.createRfcommSocketToServiceRecord(testUUIDTwo) } returns mockedSocketOne
        every { testDeviceTwo.createRfcommSocketToServiceRecord(testUUIDTwo) } returns mockedSocketTwo
        justRun { mockedSocketOne.connect() }
        justRun { mockedSocketTwo.connect() }

        val client = TestClientPeer(true)

        BluetoothServiceConnectionEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionTwo, client)

        discoveryListener.captured.onPeerDiscovered(testDeviceTwo)
        discoveryListener.captured.onServiceDiscovered(testDeviceTwo, testDescriptionTwo)
        discoveryListener.captured.onPeerDiscovered(testDeviceOne)
        discoveryListener.captured.onServiceDiscovered(testDeviceOne, testDescriptionTwo)
        Thread.sleep(1000) // waiting for connect thread
        assertTrue(
            client.establishedConnections[0].remoteDevice.equals(testDeviceOne) ||
                    client.establishedConnections[1].remoteDevice.equals(testDeviceOne)
        )

        assertTrue(
            client.establishedConnections[0].remoteDevice.equals(testDeviceTwo) ||
                    client.establishedConnections[1].remoteDevice.equals(testDeviceTwo)
        )

        assertFalse(client.establishedConnections[0].isServerPeer) // connected as client
        assertFalse(client.establishedConnections[1].isServerPeer) // connected as client
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

        val client = TestClientPeer(true)
        BluetoothServiceConnectionEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionTwo, client)

        discoveryListener.captured.onPeerDiscovered(testDeviceOne)
        discoveryListener.captured.onServiceDiscovered(testDeviceOne, testDescriptionTwo)

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

        every {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                any(),
                any()
            )
        } returns mockedServerSocket

        BluetoothServiceConnectionEngine.getInstance().startSDPService(testDescriptionOne) {}

        Thread.sleep(3000)
        // In the given time exactly one connection should be accepted
        verify(exactly = 1) {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                testDescriptionOne.instanceName, testDescriptionOne.serviceUuid
            )
        }
        // Accept will be called twice, once will go trough, then the tread loops and watt for the next connection
        verify(exactly = 2) { mockedServerSocket.accept() }
        Thread.sleep(2000)
        // Still only one Server socket should be opened
        verify(exactly = 1) {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                testDescriptionOne.instanceName, testDescriptionOne.serviceUuid
            )
        }
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
        every {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                any(),
                any()
            )
        } returns mockedServerSocket

        val createdFirst =
            BluetoothServiceConnectionEngine.getInstance().startSDPService(testDescriptionOne) {}

        val createdSecond =
            BluetoothServiceConnectionEngine.getInstance().startSDPService(testDescriptionOne) {}

        // This should all stay he same, there is no second service created =
        Thread.sleep(3000)
        // In the given time exactly one connection should be accepted
        verify(exactly = 1) {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                testDescriptionOne.instanceName, testDescriptionOne.serviceUuid
            )
        }
        // Accept will be called twice, once will go trough, then the tread loops and watt for the next connection
        verify(exactly = 2) { mockedServerSocket.accept() }
        Thread.sleep(2000)
        // Still only one Server socket should be opened
        verify(exactly = 1) {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                testDescriptionOne.instanceName, testDescriptionOne.serviceUuid
            )
        }
        // his should accept he second connection and start waiting for the next
        verify(exactly = 3) { mockedServerSocket.accept() }

        // checking return
        assertTrue(createdFirst)
        assertFalse(createdSecond)
    }

    @Test
    fun itShouldNotifyServerOnCreatedConnection() {
        val mockedServerSocket = mockk<BluetoothServerSocket>()
        val mockedSocket = getSocketToTestDevice(getTestDeviceOne())
        val answerF = FunctionAnswer { Thread.sleep(1000); mockedSocket }

        every { mockedServerSocket.accept() }.answers(answerF)
        justRun { mockedServerSocket.close() }
        every {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                any(),
                any()
            )
        } returns mockedServerSocket

        var openedConnection: BluetoothConnection? = null

        BluetoothServiceConnectionEngine.getInstance().startSDPService(
            testDescriptionOne
        ) { connection -> openedConnection = connection; }

        Thread.sleep(1500)
        // we used socketToTestDeviceOne:

        assertEquals(true, openedConnection?.isServerPeer)
        assertEquals(testDescriptionOne, openedConnection?.serviceDescription)
        assertEquals(getTestDeviceOne().address, openedConnection?.remoteDevice?.address)
        assertEquals(getTestDeviceOne().name, openedConnection?.remoteDevice?.name)
    }

    /**
     * Testing if several services and be advertised at the same times, and incoming connections
     * will be accepted by them
     */
    @Test
    fun itShouldBeAbleToRunSeveralServicesAtTheSametime() {
        // Setting up two server socket mocks
        val mockedServerSocketOne = mockk<BluetoothServerSocket>()
        val mockedServerSocketTwo = mockk<BluetoothServerSocket>()
        val mockedSocketOne = getSocketToTestDevice(getTestDeviceOne())
        val mockedSocketTwo = getSocketToTestDevice(getTestDeviceTwo())

        // Delayed response to blocking accept call
        val answerOne = FunctionAnswer { Thread.sleep(2000); mockedSocketOne }
        val answerTwo = FunctionAnswer { Thread.sleep(4000); mockedSocketTwo }
        every { mockedServerSocketOne.accept() }.answers(answerOne)
        justRun { mockedServerSocketOne.close() }
        every { mockedServerSocketTwo.accept() }.answers(answerTwo)
        justRun { mockedServerSocketTwo.close() }

        // setting up the adapter to return mocked ServerSockets
        every {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                testDescriptionOne.instanceName,
                any()
            )
        } returns mockedServerSocketOne
        every {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                testDescriptionTwo.instanceName,
                any()
            )
        } returns mockedServerSocketTwo

        var openedConnectionOne: BluetoothConnection? = null
        var openedConnectionTwo: BluetoothConnection? = null

        val createdFirst = BluetoothServiceConnectionEngine.getInstance().startSDPService(
            testDescriptionOne
        ) { connection -> openedConnectionOne = connection; }

        val createdSecond = BluetoothServiceConnectionEngine.getInstance().startSDPService(
            testDescriptionTwo
        ) { connection -> openedConnectionTwo = connection; }

        Thread.sleep(3000)
        // In the given time exactly one connection should be accepted
        verify(exactly = 1) {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                testDescriptionOne.instanceName, testDescriptionOne.serviceUuid
            )
        }
        verify(exactly = 1) {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                testDescriptionTwo.instanceName, testDescriptionTwo.serviceUuid
            )
        }
        verify(exactly = 2) { mockedServerSocketOne.accept() }
        verify(exactly = 1) { mockedServerSocketTwo.accept() }
        Thread.sleep(2000)
        // Still only one Server socket should be opened
        verify(exactly = 1) {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                testDescriptionOne.instanceName, testDescriptionOne.serviceUuid
            )
        }
        verify(exactly = 1) {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                testDescriptionTwo.instanceName, testDescriptionTwo.serviceUuid
            )
        }
        // his should accept he second connection and start waiting for the next
        verify(exactly = 3) { mockedServerSocketOne.accept() }
        verify(exactly = 2) { mockedServerSocketTwo.accept() }

        // checking return
        assertTrue(createdFirst)
        assertTrue(createdSecond)

        // we used socketToTestDeviceOne:
        assertEquals(true, openedConnectionOne?.isServerPeer)
        assertEquals(testDescriptionOne, openedConnectionOne?.serviceDescription)
        assertEquals(getTestDeviceOne().address, openedConnectionOne?.remoteDevice?.address)
        assertEquals(getTestDeviceOne().name, openedConnectionOne?.remoteDevice?.name)

        // we used socketToTestDeviceOne:
        assertEquals(true, openedConnectionTwo?.isServerPeer)
        assertEquals(testDescriptionTwo, openedConnectionTwo?.serviceDescription)
        assertEquals(getTestDeviceTwo().address, openedConnectionTwo?.remoteDevice?.address)
        assertEquals(getTestDeviceTwo().name, openedConnectionTwo?.remoteDevice?.name)
    }

    /**
     * When a service is closed and unregistered
     * the service socket needs to eb closed
     */
    @Test
    fun itShouldCloseTheServerSocketWhenEndingTheService() {
        val mockedServerSocket = mockk<BluetoothServerSocket>()
        val mockedSocket = getSocketToTestDevice(getTestDeviceOne())

        val answerF = FunctionAnswer { Thread.sleep(5000); mockedSocket }
        every { mockedServerSocket.accept() }.answers(answerF)
        justRun { mockedServerSocket.close() }
        every {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                any(),
                any()
            )
        } returns mockedServerSocket
        BluetoothServiceConnectionEngine.getInstance().startSDPService(testDescriptionOne) {
        }

        Thread.sleep(1000)
        BluetoothServiceConnectionEngine.getInstance().stopSDPService(testDescriptionOne)
        Thread.sleep(1000)
        verify(exactly = 1) { mockedServerSocket.close() }
    }

    /**
     * The server socket may produce a null pointer exception which
     * needs to be handled
     */
    @Test
    fun iShouldNotCrashOnAServerSocketNullPointerException() {
        val mockedServerSocket = mockk<BluetoothServerSocket>()
        val mockedSocket = getSocketToTestDevice(getTestDeviceOne())

        val answerF = FunctionAnswer { Thread.sleep(5000); mockedSocket }
        every { mockedServerSocket.accept() }.answers(answerF)

        every { mockedServerSocket.close() } throws NullPointerException()

        every {
            mockedBtAdapter.listenUsingRfcommWithServiceRecord(
                any(),
                any()
            )
        } returns mockedServerSocket

        BluetoothServiceConnectionEngine.getInstance().startSDPService(testDescriptionOne) {
        }
        Thread.sleep(1000)
        BluetoothServiceConnectionEngine.getInstance().stopSDPService(testDescriptionOne)
        Thread.sleep(1000)
        verify(exactly = 1) { mockedServerSocket.close() }
    }
}
package willi.boelke.servicedisoveryengine.bluetooth.sdpBluetoothEngine

import android.arch.core.executor.testing.CountingTaskExecutorRule
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import io.mockk.*
import junit.framework.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import willi.boelke.servicedisoveryengine.bluetooth.testUtils.*
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothDiscovery.SdpBluetoothDiscoveryEngine
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothEngine
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothConnection
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothServiceClient
import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription
import java.io.IOException
import kotlin.collections.ArrayList


/**
 *
 *  These are integration tests for the SDPBluetoothEngine and other subsequently used systems
 *  like the ConnectionManager, ServiceDescription and the SdpBluetoothDiscoveryEngine.
 *
 *  For the tests the android bluetooth api is mocked. This makes it possible for the tests to run
 *  faster and test some more cases then letting them run on the actual api.
 *
 *  For Mocking the android api the mocking framework `Mockk` turned out to be a very good
 *  alternative to mockito, which has problems with mocking final and static classes and methods
 *  which is required to mock the android bt api nad the context.
 *  Mockk is a Kotlin library thus Kotlin will be used for the test.
 *
 *  Also Mockk requires at least android P, so to run the tests an emulator or device
 *  with android p is recommended.
 * ------------------------------------------------------------------
 *  Sine the complete bluetooth api is mocked, and the
 *  BroadcastReceivers are not actually functioning, test could be run
 *  ignoring the actual flow of events.
 *  for example `onUuidsFetched()`, could be called without
 *  prior `onDeviceDiscoveryFinished()`, this also would work.
 *  This would not happen normally, sine the fetching press
 *  will only be started after a device was discovered
 *  For the tests i wont do that, but emulate the Bluetooth API
 *  and behavior as close to the actual thing as possible.
 * -----------------------------------------------------------------
 *  To test the engine we need to monitor its output and emulate its
 *  input:
 *
 *  -Output:
 *  The results of the SdpBluetoothEngines work can be monitored
 *  through implementing the client and server interfaces.
 *  In some cases method calls on mock objets need to be verified to
 *  see results. Especially on the BluetoothAdapter, which can be
 *  mocked and injected, method calls are a essential part of testing
 *  the engines work.
 *
 *  -Input:
 *  The SdpBluetoothEngines input comes from BroadcastReceivers or
 *  from the "user". The user input an be easily emulated using the
 *  public interface.
 *
 *  The BroadcastReceivers are separated form the engine and use
 *  protected methods (`onDeviceDiscovered` , `onUuidsFetched` and `onDeviceDiscoveryFinished`)
 *  to notify the engine and provide necrosis inputs.
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
class SdpBluetoothEngineMockTest {

    /**
     * Executing sequentially
     */
    @get:Rule
    val instantTaskExecutorRule = CountingTaskExecutorRule()

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
        SdpBluetoothEngine.initialize(mockedContext, mockedBtAdapter)
        SdpBluetoothEngine.getInstance().start()
        SdpBluetoothEngine.getInstance().startDeviceDiscovery()

        initTestMocks()
    }

    @After
    fun teardown() {
        SdpBluetoothEngine.getInstance()
            .teardownEngine()
        SdpBluetoothDiscoveryEngine.getInstance()
            .teardownEngine()
    }

    @Test
    fun itShouldStart() {
        //Check
        verify(exactly = 3) {mockedContext.registerReceiver(any(), any())}
    }


    @Test
    fun itShouldNotifyClientAboutDiscoveredDevices() {

        val foundDevices: ArrayList<BluetoothDevice> = ArrayList();

        SdpBluetoothEngine.getInstance()
            .startDeviceDiscovery()
        SdpBluetoothEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionOne, object : SdpBluetoothServiceClient {
                override fun onServiceDiscovered(
                    address: String?,
                    description: ServiceDescription?
                ) {
                    // not tested here
                }

                override fun onPeerDiscovered(peer: BluetoothDevice) {
                    foundDevices.add(peer)
                }

                override fun onConnectedToService(connection: SdpBluetoothConnection?) {
                    // not tested here
                }

                override fun shouldConnectTo(
                    address: String?,
                    description: ServiceDescription?
                ): Boolean {
                    return false
                }
            })
        // discovered device with
        val testDeviceOne = getTestDeviceOne()

        // Discovery happens in the wrapped SdpBluetoothDiscoveryEngine
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)

        assertTrue(foundDevices.size == 1)
        assertEquals(testDeviceOne, foundDevices[0])
    }


    @Test
    fun itShouldNotifySeveralClientsAboutDiscoveredDevices() {

        val foundDevices: ArrayList<BluetoothDevice> = ArrayList()

        SdpBluetoothEngine.getInstance()
            .startDeviceDiscovery()

        SdpBluetoothEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionOne, object : SdpBluetoothServiceClient {
                override fun onServiceDiscovered(
                    address: String?,
                    description: ServiceDescription?
                ) {
                    // not tested here
                }

                override fun onPeerDiscovered(peer: BluetoothDevice) {
                    foundDevices.add(peer)
                }

                override fun onConnectedToService(connection: SdpBluetoothConnection?) {
                    // not tested here
                }

                override fun shouldConnectTo(
                    address: String?,
                    description: ServiceDescription?
                ): Boolean {
                    return false
                }
            })

        SdpBluetoothEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionFour, object : SdpBluetoothServiceClient {
                override fun onServiceDiscovered(
                    address: String?,
                    description: ServiceDescription?
                ) {
                    // not tested here
                }

                override fun onPeerDiscovered(peer: BluetoothDevice) {
                    foundDevices.add(peer)
                }

                override fun onConnectedToService(connection: SdpBluetoothConnection?) {
                    // not tested here
                }

                override fun shouldConnectTo(
                    address: String?,
                    description: ServiceDescription?
                ): Boolean {
                    return false
                }
            })
        // discovered device with
        val testDeviceOne = getTestDeviceOne()

        // Discovery happens in the wrapped SdpBluetoothDiscoveryEngine
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)

        assertTrue(foundDevices.size == 2)
        assertEquals(testDeviceOne, foundDevices[0])
        assertEquals(testDeviceOne, foundDevices[1])
    }


    @Test
    fun itShouldNotifyAboutAServiceDiscovery() {
        val foundDevices: ArrayList<String> = ArrayList()
        val foundServices: ArrayList<ServiceDescription> = ArrayList()

        //Start client looking for uuid four, which is part of test array two
        SdpBluetoothEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionTwo, object :
                SdpBluetoothServiceClient {
                override fun onServiceDiscovered(
                    address: String,
                    description: ServiceDescription
                ) {
                    foundDevices.add(address)
                    foundServices.add(description)
                }

                override fun onPeerDiscovered(peer: BluetoothDevice?) {
                    // not under test
                }

                override fun onConnectedToService(connection: SdpBluetoothConnection?) {
                    // not under test
                }

                override fun shouldConnectTo(
                    address: String?,
                    description: ServiceDescription?
                ): Boolean {
                    return false
                }

            })

        // discovered device with
        val testDeviceOne = getTestDeviceOne()
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")
        verify(exactly = 1) { testDeviceOne.fetchUuidsWithSdp()}
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceOne, getTestUuidArrayOne())

        assertEquals(testDeviceOne.address, foundDevices[0])
        assertEquals(testDescriptionTwo, foundServices[0])
    }



    @Test
    fun itShouldOnlyNotifyTheRightClientAboutItsServices() {

        val foundDevicesOne: ArrayList<String> = ArrayList()
        val foundServicesOne: ArrayList<ServiceDescription> = ArrayList()
        val foundDevicesTwo: ArrayList<String> = ArrayList()
        val foundServicesTwo: ArrayList<ServiceDescription> = ArrayList()

        //Start client looking for uuid four, which is part of test array two
        SdpBluetoothEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionTwo, object :
                SdpBluetoothServiceClient {
                override fun onServiceDiscovered(
                    address: String,
                    description: ServiceDescription
                ) {
                    foundDevicesOne.add(address)
                    foundServicesOne.add(description)
                }

                override fun onPeerDiscovered(peer: BluetoothDevice?) {
                    // not under test
                }

                override fun onConnectedToService(connection: SdpBluetoothConnection?) {
                    // not under test
                }

                override fun shouldConnectTo(
                    address: String?,
                    description: ServiceDescription?
                ): Boolean {
                    return false
                }
            })

        SdpBluetoothEngine.getInstance()
            .startSDPDiscoveryForService(testDescriptionFour, object :
                SdpBluetoothServiceClient {
                override fun onServiceDiscovered(
                    address: String,
                    description: ServiceDescription
                ) {
                    foundDevicesTwo.add(address)
                    foundServicesTwo.add(description)
                }

                override fun onPeerDiscovered(peer: BluetoothDevice?) {
                    // not under test
                }

                override fun onConnectedToService(connection: SdpBluetoothConnection?) {
                    // not under test
                }

                override fun shouldConnectTo(
                    address: String?,
                    description: ServiceDescription?
                ): Boolean {
                    return false
                }

            })

        // discovered device with
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceTwo)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")
        verify(exactly = 1) { testDeviceOne.fetchUuidsWithSdp()}
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceOne, getTestUuidArrayOne())
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceTwo, getTestUuidArrayTwo())

        assertEquals(1, foundDevicesOne.size)
        assertEquals(1, foundDevicesTwo.size)
        assertEquals(testDeviceOne.address, foundDevicesOne[0])
        assertEquals(testDescriptionTwo, foundServicesOne[0])
        assertEquals(testDeviceTwo.address, foundDevicesTwo[0])
        assertEquals(testDescriptionFour, foundServicesTwo[0])
    }

    /**
     * A service can be discovered in a discovery process
     * after that a service discover for even that service can be started.
     *
     * All discovered services should be cashed, so an immediate connection can be attempted
     * without starting another device / service discovery
     */
    @Test
    fun itShouldConnectToServiceIfDiscoveryStartedAfterServiceWasDiscovered() {
        var openedConnection: SdpBluetoothConnection? = null
        SdpBluetoothEngine.getInstance()

            .startSDPDiscoveryForService(testDescriptionTwo, object :
                SdpBluetoothServiceClient {
                override fun onServiceDiscovered(
                    address: String?,
                    description: ServiceDescription?
                ) {
                }

                override fun onPeerDiscovered(peer: BluetoothDevice?) {
                }

                override fun onConnectedToService(connection: SdpBluetoothConnection?) {
                    openedConnection = connection
                }

                override fun shouldConnectTo(
                    address: String?,
                    description: ServiceDescription?
                ): Boolean {
                return true
                }

            })

        // mocked objects
        val testDeviceOne = getTestDeviceOne()
        val mockedSocket = getSocketToTestDevice(getTestDeviceTwo())

        // some further methods need tto be mocked
        every { testDeviceOne.createRfcommSocketToServiceRecord(testUUIDTwo) } returns  mockedSocket
        justRun { mockedSocket.connect() }

        // Discovery and SDP process
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceOne, getTestUuidArrayOne())

        // checking the result and verifying some method calls
        verify (exactly = 1){ testDeviceOne.createRfcommSocketToServiceRecord(testUUIDTwo) }
        verify (exactly = 1){ mockedSocket.connect() }
        assertEquals(getTestDeviceTwo().name, openedConnection?.remoteDevice?.name) // the remote device (testDeviceTwo)
        assert(openedConnection?.isServerPeer == false) // connected as client
    }

    @Test
    fun aServiceCanBeAdvertisedWhileAnotherIsSearched(){
        // TODO
    }

    @Test
    fun itShouldHandleIoExceptionsWhenTryingToConnect(){

        SdpBluetoothEngine.getInstance()

            .startSDPDiscoveryForService(testDescriptionTwo, object :
                SdpBluetoothServiceClient {
                override fun onServiceDiscovered(
                    address: String?,
                    description: ServiceDescription?
                ) {
                }

                override fun onPeerDiscovered(peer: BluetoothDevice?) {
                }

                override fun onConnectedToService(connection: SdpBluetoothConnection?) {
                }

                override fun shouldConnectTo(
                    address: String?,
                    description: ServiceDescription?
                ): Boolean {
                    return true
                }

            })

        // mocked objects
        val testDeviceOne = getTestDeviceOne()
        val mockedSocket = getSocketToTestDevice(getTestDeviceTwo())

        // some further methods need tto be mocked
        every { testDeviceOne.createRfcommSocketToServiceRecord(testUUIDTwo) } returns  mockedSocket
        every { mockedSocket.connect() } throws IOException()

        // Discovery and SDP process
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscovered", testDeviceOne)
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onDeviceDiscoveryFinished")
        SdpBluetoothDiscoveryEngine.getInstance().callPrivateFunc("onUuidsFetched", testDeviceOne, getTestUuidArrayOne())

        // checking the result and verifying some method calls
        verify (exactly = 1){ testDeviceOne.createRfcommSocketToServiceRecord(testUUIDTwo) }
        verify (exactly = 1){ mockedSocket.connect() }
        // When IO Exception is thrown
        verify (exactly = 1){ mockedSocket.close() }
    }


    @Test
    fun itShouldAcceptConnectionsWhenServiceStarted() {
        val mockedServerSocket = mockk<BluetoothServerSocket>()
        val mockedSocket = getSocketToTestDevice(getTestDeviceOne())

        val answerF = FunctionAnswer { Thread.sleep(2000); mockedSocket }
        every { mockedServerSocket.accept() } .answers(answerF)
        justRun { mockedServerSocket.close() }

        every { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(any(), any()) } returns mockedServerSocket

        SdpBluetoothEngine.getInstance().startSDPService(testDescriptionOne) {
        }

        Thread.sleep(3000)
        // In the given time exactly one connection should be accepted
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(
            testDescriptionOne.serviceUuid.toString(), testDescriptionOne.serviceUuid)}
        // Accept will be called twice, once will go trough, then the tread loops and watt for the next connection
        verify(exactly = 2) { mockedServerSocket.accept()}
        Thread.sleep(2000)
        // Still only one Server socket should be opened
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(
            testDescriptionOne.serviceUuid.toString(), testDescriptionOne.serviceUuid)}
        // his should accept he second connection and start waiting for the next
        verify(exactly = 3) { mockedServerSocket.accept()}
    }




    @Test
    fun itShouldNotStartTheSameServiceTwice(){
        val mockedServerSocket = mockk<BluetoothServerSocket>()
        val mockedSocket = getSocketToTestDevice(getTestDeviceOne())

        val answerF = FunctionAnswer { Thread.sleep(2000); mockedSocket }
        every { mockedServerSocket.accept() } .answers(answerF)
        justRun { mockedServerSocket.close() }

        every { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(any(), any()) } returns mockedServerSocket

        val createdFirst = SdpBluetoothEngine.getInstance().startSDPService(testDescriptionOne) {
        }

        val createdSecond = SdpBluetoothEngine.getInstance().startSDPService(testDescriptionOne) {
        }


        // This should all stay he same, there is no second service created =

        Thread.sleep(3000)
        // In the given time exactly one connection should be accepted
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(
            testDescriptionOne.serviceUuid.toString(), testDescriptionOne.serviceUuid)}
        // Accept will be called twice, once will go trough, then the tread loops and watt for the next connection
        verify(exactly = 2) { mockedServerSocket.accept()}
        Thread.sleep(2000)
        // Still only one Server socket should be opened
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(
            testDescriptionOne.serviceUuid.toString(), testDescriptionOne.serviceUuid)}
        // his should accept he second connection and start waiting for the next
        verify(exactly = 3) { mockedServerSocket.accept()}

        // checking return
        assertTrue(createdFirst)
        assertFalse(createdSecond)
    }



    @Test
    fun itShouldNotifyServerOnCreatedConnection()
    {
        val mockedServerSocket = mockk<BluetoothServerSocket>()
        val mockedSocket = getSocketToTestDevice(getTestDeviceOne())
        val answerF = FunctionAnswer { Thread.sleep(1000); mockedSocket }

        every { mockedServerSocket.accept() } .answers(answerF)
        justRun { mockedServerSocket.close() }
        every { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(any(), any()) } returns mockedServerSocket

        var openedConnection: SdpBluetoothConnection? = null

        SdpBluetoothEngine.getInstance().startSDPService(testDescriptionOne
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
        every { mockedServerSocketOne.accept() } .answers(answerOne)
        justRun { mockedServerSocketOne.close() }
        every { mockedServerSocketTwo.accept() } .answers(answerTwo)
        justRun { mockedServerSocketTwo.close() }

        // setting up the adapter to return mocked ServerSockets
        every { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(testDescriptionOne.serviceUuid.toString(), any()) } returns mockedServerSocketOne
        every { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(testDescriptionTwo.serviceUuid.toString(), any()) } returns mockedServerSocketTwo

        var openedConnectionOne: SdpBluetoothConnection? = null
        var openedConnectionTwo: SdpBluetoothConnection? = null

        val createdFirst = SdpBluetoothEngine.getInstance().startSDPService(testDescriptionOne
        ) { connection -> openedConnectionOne = connection; }

        val createdSecond = SdpBluetoothEngine.getInstance().startSDPService( testDescriptionTwo
        ) { connection -> openedConnectionTwo = connection; }


        Thread.sleep(3000)
        // In the given time exactly one connection should be accepted
         verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(
            testDescriptionOne.serviceUuid.toString(), testDescriptionOne.serviceUuid)}
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(
            testDescriptionTwo.serviceUuid.toString(), testDescriptionTwo.serviceUuid)}
        verify(exactly = 2) { mockedServerSocketOne.accept()}
        verify(exactly = 1) { mockedServerSocketTwo.accept()}
        Thread.sleep(2000)
        // Still only one Server socket should be opened
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(
            testDescriptionOne.serviceUuid.toString(), testDescriptionOne.serviceUuid)}
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(
            testDescriptionTwo.serviceUuid.toString(), testDescriptionTwo.serviceUuid)}
        // his should accept he second connection and start waiting for the next
        verify(exactly = 3) { mockedServerSocketOne.accept()}
        verify(exactly = 2) { mockedServerSocketTwo.accept()}

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


    @Test
    fun  itShouldCloseTheServerSocketWhenEndingTheService(){
        val mockedServerSocket = mockk<BluetoothServerSocket>()
        val mockedSocket = getSocketToTestDevice(getTestDeviceOne())

        val answerF = FunctionAnswer { Thread.sleep(5000); mockedSocket }
        every { mockedServerSocket.accept() } .answers(answerF)
        justRun { mockedServerSocket.close() }
        every { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(any(), any()) } returns mockedServerSocket
        SdpBluetoothEngine.getInstance().startSDPService(testDescriptionOne) {
        }

        Thread.sleep(1000)
        SdpBluetoothEngine.getInstance().stopSDPService(testDescriptionOne)
        Thread.sleep(1000)
        verify(exactly = 1) { mockedServerSocket.close()}
    }

    @Test
    fun  iShouldNotCrashOnAServerSocketNullPointerException(){
        val mockedServerSocket = mockk<BluetoothServerSocket>()
        val mockedSocket = getSocketToTestDevice(getTestDeviceOne())

        val answerF = FunctionAnswer { Thread.sleep(5000); mockedSocket }
        every { mockedServerSocket.accept() } .answers(answerF)

        every { mockedServerSocket.close() } throws NullPointerException()

        every { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(any(), any()) } returns mockedServerSocket

        SdpBluetoothEngine.getInstance().startSDPService(testDescriptionOne) {
        }
        Thread.sleep(1000)
        SdpBluetoothEngine.getInstance().stopSDPService(testDescriptionOne)
        Thread.sleep(1000)
        verify(exactly = 1) { mockedServerSocket.close()}
    }



}
package willi.boelke.servicedisoveryengine.bluetooth

import android.arch.core.executor.testing.CountingTaskExecutorRule
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import io.mockk.*
import junit.framework.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.SdpBluetoothEngine
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceClient
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceServer
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread


/**
 * These tests need to run on a connected android device or emulator
 * running android P or higher The tests need to run sequentially
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
class SdpBluetoothEngineTest {

    private var testUUIDOne = UUID.fromString("12345fff-f49a-11ec-b939-0242ac120002")
    private var testUUIDTwo = UUID.fromString("22345fff-f49a-11ec-b939-0242ac120002")
    private var testUUIDThree = UUID.fromString("32345fff-f49a-11ec-b939-0242ac120002")
    private var testUUIDFour = UUID.fromString("42345fff-f49a-11ec-b939-0242ac120002")
    private var testUUIDFive = UUID.fromString("52345fff-f49a-11ec-b939-0242ac120002")

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
        SdpBluetoothEngine.getInstance().startEngine()
        SdpBluetoothEngine.getInstance().startDiscovery()
    }

    @After
    fun teardown() {
        teardownEngineWithReflections()
    }

    @Test
    fun itShouldStart() {
        //Check
        verify(exactly = 2) {mockedContext.registerReceiver(any(), any())}
    }

    /**
     * Testing the timeout system, which prevents UUIDs to  be fetched over and over
     * again and with that start and sopping device discovery and making things slower
     *
     * The timeout system is a integral part of the SdpBluetoothEngine and requires to be
     * tested.
     */
    @Test
    fun itFetchesDeviceUUIDsInOnlyAfterTimeoutExpired() {
        // discovered device with
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()

        // faking first discovery : should fetch
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceOne)
        Thread.sleep(500)
        //faking fetched UUIDs
        SdpBluetoothEngine.getInstance().onUuidsFetched(testDeviceOne, getTestUuidArrayOne())
        // Discovering test deviceTwo, should fetch the UUIDs
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceTwo)
        Thread.sleep(500)
        SdpBluetoothEngine.getInstance().onUuidsFetched(testDeviceTwo, getTestUuidArrayOne())
        // faking second discovery : should not fetch
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceOne)
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceTwo)
        // Expiring UUIDs
        Thread.sleep(SdpBluetoothEngine.DEFAULT_UUID_REFRESH_TIMEOUT)
        // faking third discovery : should fetch
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceOne)
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceTwo)

        verify(exactly = 2) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 2) {testDeviceTwo.fetchUuidsWithSdp()}
    }

    @Test
    fun itShouldNotifyClientAboutDiscoveredDevices() {

        var foundDevices: ArrayList<BluetoothDevice>? = null
        SdpBluetoothEngine.getInstance()
            .startSDPDiscoveryForServiceWithUUID(testUUIDOne, object : SdpBluetoothServiceClient {
                override fun onServiceDiscovered(address: String, serviceUUID: UUID) {}
                override fun onConnectedToService(connection: SdpBluetoothConnection) {}
                override fun shouldConnectTo(address: String, serviceUUID: UUID): Boolean { return false }
                override fun onDevicesInRangeChange(devices: ArrayList<BluetoothDevice>) {
                    foundDevices = devices
                }
            })

        // discovered device with
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()

        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceOne)
        Thread.sleep(500)
        assertTrue(foundDevices?.size == 1)
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceTwo)
        Thread.sleep(500)
        assertTrue(foundDevices?.size == 2)

        assertEquals(testDeviceOne, foundDevices?.get(0))
        assertEquals(testDeviceTwo, foundDevices?.get(1))
    }

    /**
     * A Client should be notified when a service is discovered
     */
    @Test
    fun itShouldNotifyAboutAServiceDiscovery() {
        var foundAddress: String = ""
        var foundUUID: UUID? = null

        //Start client looking for uuid four, which is part of test array two
        SdpBluetoothEngine.getInstance()
            .startSDPDiscoveryForServiceWithUUID(testUUIDFour, object : SdpBluetoothServiceClient {
                override fun onServiceDiscovered(address: String, serviceUUID: UUID) {
                    foundAddress = address
                    foundUUID = serviceUUID
                }
                override fun onConnectedToService(connection: SdpBluetoothConnection) {}
                override fun shouldConnectTo(address: String, serviceUUID: UUID): Boolean { return false }
                override fun onDevicesInRangeChange(devices: ArrayList<BluetoothDevice>) {}
            })

        // discovered device with
        val testDeviceOne = getTestDeviceOne()
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceOne)
        Thread.sleep(500)
        SdpBluetoothEngine.getInstance().onUuidsFetched(testDeviceOne, getTestUuidArrayTwo())
        Thread.sleep(500);

        assertEquals(testDeviceOne.address, foundAddress)
        assertEquals(testUUIDFour, foundUUID)
    }

    /**
     * In some instances he UUID_EXTRA will be `null`
     * the engine needs to handle that without an (unexpected) exception
     */
    @Test
    fun itShouldHandleUuidArraysBeingNull(){
        //Start client looking for uuid four, which is part of test array two
        SdpBluetoothEngine.getInstance()
            .startSDPDiscoveryForServiceWithUUID(testUUIDFour, object : SdpBluetoothServiceClient {
                override fun onServiceDiscovered(address: String, serviceUUID: UUID) {}
                override fun onConnectedToService(connection: SdpBluetoothConnection) {}
                override fun shouldConnectTo(address: String, serviceUUID: UUID): Boolean { return false }
                override fun onDevicesInRangeChange(devices: ArrayList<BluetoothDevice>) {}
            })

        // discovered device with
        val testDeviceOne = getTestDeviceOne()
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceOne)
        Thread.sleep(500)
        SdpBluetoothEngine.getInstance().onUuidsFetched(testDeviceOne, null)
        Thread.sleep(500);
    }


    /**
     * The Engine can search for several services at a time
     */
    @Test
    fun itShouldBeAbleToSearchForSeveralServicesAtATime() {
        var foundAddressOne = ""
        var foundUUIDOne: UUID? = null

        var foundAddressTwo = ""
        var foundUUIDTwo: UUID? = null

        //Start client looking for uuid four, which is part of test array two
        SdpBluetoothEngine.getInstance()
            .startSDPDiscoveryForServiceWithUUID(testUUIDFour, object : SdpBluetoothServiceClient {
                override fun onServiceDiscovered(address: String, serviceUUID: UUID) {
                    foundAddressOne = address
                    foundUUIDOne = serviceUUID
                }
                override fun onConnectedToService(connection: SdpBluetoothConnection) {}
                override fun shouldConnectTo(address: String, serviceUUID: UUID): Boolean { return false }
                override fun onDevicesInRangeChange(devices: ArrayList<BluetoothDevice>) {}
            })

        //Start client looking for uuid four, which is part of test array one
        SdpBluetoothEngine.getInstance()
            .startSDPDiscoveryForServiceWithUUID(testUUIDTwo, object : SdpBluetoothServiceClient {
                override fun onServiceDiscovered(address: String, serviceUUID: UUID) {
                    foundAddressTwo = address
                    foundUUIDTwo = serviceUUID
                }
                override fun onConnectedToService(connection: SdpBluetoothConnection) {}
                override fun shouldConnectTo(address: String, serviceUUID: UUID): Boolean { return false }
                override fun onDevicesInRangeChange(devices: ArrayList<BluetoothDevice>) {}
            })


        // discovered device
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceOne)
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceTwo)
        SdpBluetoothEngine.getInstance().onUuidsFetched(testDeviceOne, getTestUuidArrayTwo())
        SdpBluetoothEngine.getInstance().onUuidsFetched(testDeviceTwo, getTestUuidArrayOne())
        Thread.sleep(500);

        assertEquals(testDeviceOne.address, foundAddressOne)
        assertEquals(testUUIDFour, foundUUIDOne)
        assertEquals(testDeviceTwo.address, foundAddressTwo)
        assertEquals(testUUIDTwo, foundUUIDTwo)
    }



    /**
     * This is a little bit black box breaking,
     * but since these tests don't use an actual bluetooth adapter (Software and Hardware)
     * and thus arent constrained by any hardware limits this behavior needs
     * to be tested to ensure the engine works properly on actual hardware.
     */
    @Test
    fun itShouldPauseTheDiscoveryDuringUuidFetching(){
        // discovered device
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        verify(exactly = 1) {mockedBtAdapter.startDiscovery()} //There was exactly one discovery start up to this point
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceOne)
        // the discovery needs to be stopped to fetch UUIDs
        verify(exactly = 1) {mockedBtAdapter.cancelDiscovery()}
        verify(exactly = 1) {mockedBtAdapter.startDiscovery()} //The discovery should not restart before UUIDs have been fetched
        SdpBluetoothEngine.getInstance().onUuidsFetched(testDeviceOne, getTestUuidArrayTwo())
        // UUIDs fetched, other devices can now be discovered
        verify(exactly = 2) {mockedBtAdapter.startDiscovery()}
        // And another round to verify
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceTwo)
        verify(exactly = 2) {mockedBtAdapter.cancelDiscovery()}
        verify(exactly = 2) {mockedBtAdapter.startDiscovery()}
        SdpBluetoothEngine.getInstance().onUuidsFetched(testDeviceTwo, getTestUuidArrayTwo())
        verify(exactly = 2) {mockedBtAdapter.cancelDiscovery()}
        verify(exactly = 3) {mockedBtAdapter.startDiscovery()}
    }

    /**
     * Same as the last tes, its a little black box breaking,
     * but also this needs to work.
     *
     * During the manual UUID Refresh, discovery should not be resumed when UUIDS
     * are fetched (for the time defined in SdpBluetoothEngine#MANUAL_REFRESH_TIME.
     * (10 seconds).
     */
    @Test
    fun itShouldPauseDiscoveryOnRefreshProcess(){
        // discovered device
        val testDeviceOne = getTestDeviceOne()
        val testDeviceTwo = getTestDeviceTwo()
        // Discovering two devices :
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceOne)
        SdpBluetoothEngine.getInstance().onUuidsFetched(testDeviceOne, getTestUuidArrayTwo())
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceTwo)
        SdpBluetoothEngine.getInstance().onUuidsFetched(testDeviceTwo, getTestUuidArrayTwo())

        verify(exactly = 2) {mockedBtAdapter.cancelDiscovery()}
        verify(exactly = 3) {mockedBtAdapter.startDiscovery()}
        verify(exactly = 1) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 1) {testDeviceTwo.fetchUuidsWithSdp()}

        // Starting the refresh process:
        Thread.sleep(500)
        SdpBluetoothEngine.getInstance().refreshNearbyServices()
        //Should stop discovery
        verify(exactly = 3) {mockedBtAdapter.cancelDiscovery()}
        // should fetch the uuids of all know devices
        verify(exactly = 2) {testDeviceOne.fetchUuidsWithSdp()}
        verify(exactly = 2) {testDeviceTwo.fetchUuidsWithSdp()}

        // it should not restart the discovery till refresh process timed out
        SdpBluetoothEngine.getInstance().onUuidsFetched(testDeviceOne, getTestUuidArrayTwo())
        verify(exactly = 3) {mockedBtAdapter.startDiscovery()}// Should not restart discovery
        SdpBluetoothEngine.getInstance().onUuidsFetched(testDeviceOne, getTestUuidArrayTwo())
        verify(exactly = 3) {mockedBtAdapter.startDiscovery()}// Should not restart discovery
        SdpBluetoothEngine.getInstance().onUuidsFetched(testDeviceTwo, getTestUuidArrayTwo())
        verify(exactly = 3) {mockedBtAdapter.startDiscovery()} // Should not restart discovery

        // Timeout refresh
        Thread.sleep(SdpBluetoothEngine.DEFAULT_UUID_REFRESH_TIMEOUT + 1000)

        // Discovering new UUIDs
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(testDeviceTwo)
        verify(exactly = 3) {testDeviceTwo.fetchUuidsWithSdp()} // should fetch again (time passed)
        verify(exactly = 4) {mockedBtAdapter.cancelDiscovery()} // should pause discovery
        Thread.sleep(500)
        SdpBluetoothEngine.getInstance().onUuidsFetched(testDeviceTwo, getTestUuidArrayTwo())
        verify(exactly = 4) {mockedBtAdapter.startDiscovery()}
    }

    @Test
    fun itShouldAcceptConnectionsWhenServiceStarted() {
        val mockedServerSocket = mockk<BluetoothServerSocket>()
        val mockedSocket = getSocketToTestDevice(getTestDeviceOne())

        val answerF = FunctionAnswer { Thread.sleep(2000); mockedSocket }
        every { mockedServerSocket.accept() } .answers(answerF)
        justRun { mockedServerSocket.close() }

        every { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(any(), any()) } returns mockedServerSocket

        SdpBluetoothEngine.getInstance().startSDPService("TestService", testUUIDOne,
            object : SdpBluetoothServiceServer{
                override fun onClientConnected(connection: SdpBluetoothConnection?) {

                }
            })

        Thread.sleep(3000)
        // In the given time exactly one connection should be accepted
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord("TestService", testUUIDOne)}
        // Accept will be called twice, once will go trough, then the tread loops and watt for the next connection
        verify(exactly = 2) { mockedServerSocket.accept()}
        Thread.sleep(2000)
        // Still only one Server socket should be opened
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord("TestService", testUUIDOne)}
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

        var createdFirst = SdpBluetoothEngine.getInstance().startSDPService("TestService", testUUIDOne,
            object : SdpBluetoothServiceServer{
                override fun onClientConnected(connection: SdpBluetoothConnection?) {

                }
            })

        var createdSecond = SdpBluetoothEngine.getInstance().startSDPService("TestService", testUUIDOne,
            object : SdpBluetoothServiceServer{
                override fun onClientConnected(connection: SdpBluetoothConnection?) {

                }
            })

        // This should all stay he same, there is no second service created =

        Thread.sleep(3000)
        // In the given time exactly one connection should be accepted
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord("TestService", testUUIDOne)}
        // Accept will be called twice, once will go trough, then the tread loops and watt for the next connection
        verify(exactly = 2) { mockedServerSocket.accept()}
        Thread.sleep(2000)
        // Still only one Server socket should be opened
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord("TestService", testUUIDOne)}
        // his should accept he second connection and start waiting for the next
        verify(exactly = 3) { mockedServerSocket.accept()}

        // checking return
        assertTrue(createdFirst)
        assertFalse(createdSecond)
    }

    @Test
    fun itShouldNotifyServerOnCreatedConnection(){
        val mockedServerSocket = mockk<BluetoothServerSocket>()
        val mockedSocket = getSocketToTestDevice(getTestDeviceOne())
        val answerF = FunctionAnswer { Thread.sleep(1000); mockedSocket }
        every { mockedServerSocket.accept() } .answers(answerF)
        justRun { mockedServerSocket.close() }
        every { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(any(), any()) } returns mockedServerSocket

        var openedConnection: SdpBluetoothConnection? = null
        SdpBluetoothEngine.getInstance().startSDPService("TestService", testUUIDOne,
            object : SdpBluetoothServiceServer{
                override fun onClientConnected(connection: SdpBluetoothConnection?) {
                    openedConnection = connection;
                }
            })

        Thread.sleep(1500)
        // we used socketToTestDeviceOne:
        assertEquals(true, openedConnection?.isServerPeer)
        assertEquals(testUUIDOne, openedConnection?.serviceUUID)
        assertEquals(getTestDeviceOne().address, openedConnection?.remoteDevice?.address)
        assertEquals(getTestDeviceOne().name, openedConnection?.remoteDevice?.name)
    }

    @Test
    fun itShouldBeAbleToRunSeveralServicesAtTheSametime() {
        val mockedServerSocketOne = mockk<BluetoothServerSocket>()
        val mockedServerSocketTwo = mockk<BluetoothServerSocket>()
        val mockedSocketOne = getSocketToTestDevice(getTestDeviceOne())
        val mockedSocketTwo = getSocketToTestDevice(getTestDeviceTwo())

        val answerOne = FunctionAnswer { Thread.sleep(2000); mockedSocketOne }
        val answerTwo = FunctionAnswer { Thread.sleep(4000); mockedSocketTwo }
        every { mockedServerSocketOne.accept() } .answers(answerOne)
        justRun { mockedServerSocketOne.close() }
        every { mockedServerSocketTwo.accept() } .answers(answerTwo)
        justRun { mockedServerSocketTwo.close() }

        every { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord("TestService", any()) } returns mockedServerSocketOne
        every { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord("TestServiceTwo", any()) } returns mockedServerSocketTwo

        var openedConnectionOne: SdpBluetoothConnection? = null
        var openedConnectionTwo: SdpBluetoothConnection? = null

        var createdFirst = SdpBluetoothEngine.getInstance().startSDPService("TestService", testUUIDOne,
            object : SdpBluetoothServiceServer{
                override fun onClientConnected(connection: SdpBluetoothConnection?) {
                    openedConnectionOne = connection
                }
            })

        var createdSecond = SdpBluetoothEngine.getInstance().startSDPService("TestServiceTwo", testUUIDTwo,
            object : SdpBluetoothServiceServer{
                override fun onClientConnected(connection: SdpBluetoothConnection?) {
                    openedConnectionTwo = connection
                }
            })

        // This should all stay he same, there is no second service created =

        Thread.sleep(3000)
        // In the given time exactly one connection should be accepted
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord("TestService", testUUIDOne)}
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord("TestServiceTwo", testUUIDTwo)}
        // Accept will be called twice, once will go trough, then the tread loops and watt for the next connection
        verify(exactly = 2) { mockedServerSocketOne.accept()}
        verify(exactly = 1) { mockedServerSocketTwo.accept()}
        Thread.sleep(2000)
        // Still only one Server socket should be opened
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord("TestService", testUUIDOne)}
        verify(exactly = 1) { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord("TestServiceTwo", testUUIDTwo)}
        // his should accept he second connection and start waiting for the next
        verify(exactly = 3) { mockedServerSocketOne.accept()}
        verify(exactly = 2) { mockedServerSocketTwo.accept()}

        // checking return
        assertTrue(createdFirst)
        assertTrue(createdSecond)

        // we used socketToTestDeviceOne:
        assertEquals(true, openedConnectionOne?.isServerPeer)
        assertEquals(testUUIDOne, openedConnectionOne?.serviceUUID)
        assertEquals(getTestDeviceOne().address, openedConnectionOne?.remoteDevice?.address)
        assertEquals(getTestDeviceOne().name, openedConnectionOne?.remoteDevice?.name)

        // we used socketToTestDeviceOne:
        assertEquals(true, openedConnectionTwo?.isServerPeer)
        assertEquals(testUUIDTwo, openedConnectionTwo?.serviceUUID)
        assertEquals(getTestDeviceTwo().address, openedConnectionTwo?.remoteDevice?.address)
        assertEquals(getTestDeviceTwo().name, openedConnectionTwo?.remoteDevice?.name)
    }



    @Test
    fun iShouldNotNotifyWhenAcceptedSockedWasNull(){

    }

    @Test
    fun  itShouldCloseTheServerSocketWhenEndingTheService(){
        val mockedServerSocket = mockk<BluetoothServerSocket>()
        val mockedSocket = getSocketToTestDevice(getTestDeviceOne())

        val answerF = FunctionAnswer { Thread.sleep(5000); mockedSocket }
        every { mockedServerSocket.accept() } .answers(answerF)
        justRun { mockedServerSocket.close() }
        every { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(any(), any()) } returns mockedServerSocket

        SdpBluetoothEngine.getInstance().startSDPService("TestService", testUUIDOne,
            object : SdpBluetoothServiceServer{
                override fun onClientConnected(connection: SdpBluetoothConnection?) {

                }
            })
        Thread.sleep(1000)
        SdpBluetoothEngine.getInstance().stopSDPService(testUUIDOne)
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

        SdpBluetoothEngine.getInstance().startSDPService("TestService", testUUIDOne,
            object : SdpBluetoothServiceServer{
                override fun onClientConnected(connection: SdpBluetoothConnection?) {

                }
            })
        Thread.sleep(1000)
        SdpBluetoothEngine.getInstance().stopSDPService(testUUIDOne)
        Thread.sleep(1000)
        verify(exactly = 1) { mockedServerSocket.close()}
    }

    @Test
    fun  iShouldNotCrashOnAServerSocketIOException(){
        val mockedServerSocket = mockk<BluetoothServerSocket>()
        val mockedSocket = getSocketToTestDevice(getTestDeviceOne())

        val answerF = FunctionAnswer { Thread.sleep(5000); mockedSocket }
        every { mockedServerSocket.accept() } .answers(answerF)

        every { mockedServerSocket.close() } throws IOException()

        every { mockedBtAdapter.listenUsingInsecureRfcommWithServiceRecord(any(), any()) } returns mockedServerSocket

        SdpBluetoothEngine.getInstance().startSDPService("TestService", testUUIDOne,
            object : SdpBluetoothServiceServer{
                override fun onClientConnected(connection: SdpBluetoothConnection?) {

                }
            })
        Thread.sleep(1000)
        SdpBluetoothEngine.getInstance().stopSDPService(testUUIDOne)
        Thread.sleep(1000)
        verify(exactly = 1) { mockedServerSocket.close()}
    }

    @Test
    fun itShouldHandleExceptionWhenClosingSockets(){

    }

    @Test
    fun itShouldHandleIoExceptionWhenAccepting(){

    }


    @Test
    fun itShouldConnectToServiceIfDiscoveryStartedAfterServiceWasDiscovered() {

    }


    //
    //------------ get mocks -------------
    //


    private fun getSocketToTestDevice(device: BluetoothDevice): BluetoothSocket {
        val mockedSocket = mockk<BluetoothSocket>()
        val outputStream = mockk<OutputStream>(relaxed = true)
        val inputStream = mockk<InputStream>(relaxed = true)
        justRun { outputStream.close() }
        justRun { inputStream.close() }
        every {mockedSocket.inputStream} returns  inputStream
        every {mockedSocket.outputStream} returns  outputStream
        every {mockedSocket.remoteDevice} returns  device
        justRun {mockedSocket.close()}
        return mockedSocket
    }


    private fun getTestDeviceOne(): BluetoothDevice {
        val deviceOne = mockk<BluetoothDevice>()
        every { deviceOne.fetchUuidsWithSdp() } returns true
        every { deviceOne.name } returns  "testDeviceOneName"
        every { deviceOne.address } returns  "testDeviceOneAddress"
        return deviceOne
    }

    private fun getTestDeviceTwo(): BluetoothDevice {
        val deviceTwo = mockk<BluetoothDevice>()
        every { deviceTwo.fetchUuidsWithSdp() } returns true
        every { deviceTwo.name } returns  "testDeviceTwoName"
        every { deviceTwo.address } returns  "testDeviceTwoAddress"
        return deviceTwo
    }

    private  fun getTestUuidArrayOne(): Array<ParcelUuid> {
        val parcelUuidTwo = ParcelUuid(testUUIDTwo)
        val parcelUuidThree = ParcelUuid(testUUIDThree)
        return arrayOf<ParcelUuid>(parcelUuidTwo, parcelUuidThree)
    }

    private  fun getTestUuidArrayTwo(): Array<ParcelUuid> {
        val parcelUuidFour = ParcelUuid(testUUIDFour)
        val parcelUuidFive = ParcelUuid(testUUIDFive)
        return arrayOf<ParcelUuid>(parcelUuidFour, parcelUuidFive)
    }

    /**
     * Kotlin reduces the visibility
     * of protected methods to only subclasses.
     * In Java i would have een able to call them, butt in return he Mocking wouldn't
     * work in instrumentation. #
     *
     * Since this is "just" for testing i will
     * use reflections to access them instead of main them
     * generally public.
     * This is for the methods "teardownEngine", "onDeviceDiscovered" and "onUUIDsFetched"
     */
    private fun teardownEngineWithReflections() {
        val method =
            SdpBluetoothEngine.getInstance().javaClass.getDeclaredMethod("teardownEngine")
        method.isAccessible = true
        method.invoke(SdpBluetoothEngine.getInstance())

    }

    private fun callOnDeviceReceivedUsingReflections(device : BluetoothDevice){
        val method = SdpBluetoothEngine.getInstance().javaClass.getDeclaredMethod("onDeviceDiscovered")
        method.isAccessible = true
        method.invoke(SdpBluetoothEngine.getInstance(),  device) // tOdO USE TTHAT
    }

}
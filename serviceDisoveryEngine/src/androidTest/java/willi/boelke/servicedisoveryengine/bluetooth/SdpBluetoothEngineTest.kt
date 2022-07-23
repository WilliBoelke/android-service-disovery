package willi.boelke.servicedisoveryengine.bluetooth

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.ParcelUuid
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.SdpBluetoothEngine
import java.util.*
import kotlin.collections.ArrayList

@RunWith(AndroidJUnit4ClassRunner::class)
class SdpBluetoothEngineTest {

    private var testUUIDOne = UUID.fromString("12345fff-f49a-11ec-b939-0242ac120002")


    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()


    @Before
    fun setup() {
    }

    @After
    fun teardown() {}

    @Test
    fun readFromDirectoryTest() {
        //Setup
        val mockedContext = mockk<Context>(relaxed = true)
        val mockedBtAdapter = mockk<BluetoothAdapter>()
        every { mockedBtAdapter.isEnabled } returns true
        every { mockedBtAdapter.isDiscovering } returns false
        every { mockedBtAdapter.cancelDiscovery() } returns true
        //Run
        SdpBluetoothEngine.initialize(mockedContext, mockedBtAdapter);
        SdpBluetoothEngine.getInstance().startEngine();
        //Check
        verify(exactly = 2) {mockedContext.registerReceiver(any(), any())}
        teardownEngineWithReflections()
    }

    @Test
    fun itGetsTheUUIDsFromTheDevices() {
        //Setup
        val mockedContext = mockk<Context>(relaxed = true)
        val mockedBtAdapter = mockk<BluetoothAdapter>()
        every { mockedBtAdapter.isEnabled } returns true
        every { mockedBtAdapter.isDiscovering } returns false
        every { mockedBtAdapter.startDiscovery() } returns true
        every { mockedBtAdapter.cancelDiscovery() } returns true
        every { mockedBtAdapter.startDiscovery() } returns true
        //Run
        SdpBluetoothEngine.initialize(mockedContext, mockedBtAdapter);
        SdpBluetoothEngine.getInstance().startEngine();
        SdpBluetoothEngine.getInstance().startDiscovery();

        // Start looking for a service

        var savedAddress = "";
        willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.SdpBluetoothEngine.getInstance()
            .startSDPDiscoveryForServiceWithUUID(testUUIDOne, object :
                willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceClient {
                override fun onServiceDiscovered(address: String, serviceUUID: UUID) {
                    savedAddress =  address;
                }

                override fun onConnectedToService(connection: willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection) {}
                override fun shouldConnectTo(address: String, serviceUUID: UUID): Boolean {
                    return false
                }

                override fun onDevicesInRangeChange(devices: ArrayList<BluetoothDevice>) {}
            })

        // discovered device with

        // device one has the correct UUID
        val deviceOne = mockk<BluetoothDevice>()
        val parcelUuidOne = ParcelUuid.fromString("12345fff-f49a-11ec-b939-0242ac120002")
        val parcelUuidTwo = ParcelUuid.fromString("22345fff-f49a-11ec-b939-0242ac120002")
        val uuids = arrayOf<ParcelUuid>(parcelUuidOne, parcelUuidTwo)
        every { deviceOne.uuids } returns  uuids
        every { deviceOne.name } returns  "testDeviceName"
        every { deviceOne.address } returns  "testDeviceAddress"
        // device two has no uuid
        val deviceTwo = mockk<BluetoothDevice>()
        val parcelUuidThree = ParcelUuid.fromString("32345fff-f49a-11ec-b939-0242ac120002")
        val uuidsDeviceTwo = arrayOf<ParcelUuid>(parcelUuidThree)
        every { deviceTwo.uuids } returns  uuidsDeviceTwo
        every { deviceTwo.name } returns  "deviceTwoName"
        every { deviceTwo.address } returns  "deviceTwoDeviceTwoAddress"

        // faking first discovery
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(deviceOne)
        Thread.sleep(2000);
        assert(savedAddress == "testDeviceAddress") // Should have found he UUID and called callback
        savedAddress = ""
        Thread.sleep(2000);
        SdpBluetoothEngine.getInstance().onDeviceDiscovered(deviceTwo)
        assert(savedAddress == "") // Should have found he UUID and called callback


        teardownEngineWithReflections();
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
        val method = SdpBluetoothEngine.getInstance().javaClass.getDeclaredMethod("teardownEngine")
        method.isAccessible = true
        method.invoke(SdpBluetoothEngine.getInstance())
    }

    private fun callOnDeviceReceivedUsingReflections(device : BluetoothDevice){
        val method = SdpBluetoothEngine.getInstance().javaClass.getDeclaredMethod("onDeviceDiscovered")
        method.isAccessible = true
        method.invoke(SdpBluetoothEngine.getInstance(),  device) // tOdO USE TTHAT
    }

}
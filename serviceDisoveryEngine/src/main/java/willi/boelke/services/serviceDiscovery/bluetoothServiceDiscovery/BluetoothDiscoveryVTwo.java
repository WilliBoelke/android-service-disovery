package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;

import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * The SdpBluetoothDiscoveryEngine is the main controller the Android Bluetooth-SDP API.
 * It allows to start a discovery for a (1-n) services simultaneously.
 * <p>
 * Start engine<br>
 * ------------------------------------------------------------<br>
 * The singleton insane can be accessed using {@link #getInstance()}
 * <p>
 * The engine needs to be started by calling {@link #start(Context)} or {@link #start(Context, BluetoothAdapter)} this will also enable bluetooth
 * on the device.
 * Call {@link #stop()} to stop the engine and all running discoveries.
 * <p>
 * Discover Services<br>
 * ------------------------------------------------------------<br>
 * Services can be discovered using {@link #startDiscoveryForService(ServiceDescription)}
 * For a service to be found it is mandatory to run a device discovery using
 * {@link #startDeviceDiscovery()}.
 * <p>
 * The device discovery will run for ~12 seconds, after that all discovered devices
 * will be queried for the Services available on them.
 * <p>
 * The device discovery can be started before or after a service discovery was started.
 * as long as a service discovery runs and was not ended via
 * {@link #stopDiscoveryForService(ServiceDescription)}} services will be discovered
 * on all subsequently and previously discovered devices.
 * <p>
 * Note<br>
 * ------------------------------------------------------------<br>
 * Note: other bluetooth devices and their services will be cashed, it is possible
 * that a service will be found on a bluetooth devices which either moved out of
 * range or stopped accepting clients.
 * <p>
 * To get notified about discovered services and their host devices a
 * {@link BluetoothServiceDiscoveryListener} needs to be registered using
 * {@link #registerDiscoverListener(BluetoothServiceDiscoveryListener)}.
 * A listener can be unregistered using  {@link #unregisterDiscoveryListener(BluetoothServiceDiscoveryListener)}.
 *
 * <p>
 * Sequence Example<br>
 * ------------------------------------------------------------<br>
 * <pre>
 *  ┌────┐                                           ┌───────────────────────────┐
 *  │Peer│                                           │SdpBluetoothDiscoveryEngine│
 *  └─┬──┘                                           └─────────────┬─────────────┘
 *   ┌┴┐                                                           |
 *   │ │                                                           │
 *   │ │                                                           │
 *   │ │              registerDiscoverListener(this)              ┌┴┐
 *   │ │ ────────────────────────────────────────────────────────>│ │
 *   │ │                                                          │ │
 *   │ │                                                          └┬┘
 *   │ │                                                           │
 *   │ │                         start()                          ┌┴┐
 *   │ │ ────────────────────────────────────────────────────────>│ │
 *   │ │                                                          │ │
 *   │ │                                                          └┬┘
 *   │ │                                                           │
 *   │ │          startSDPDiscoveryForService(description)        ┌┴┐
 *   │ │ ────────────────────────────────────────────────────────>│ │
 *   │ │                                                          │ │
 *   │ │                                                          └┬┘
 *   │ │                                                           │
 *   │ │                  startDeviceDiscovery()                  ┌┴┐
 *   │ │ ────────────────────────────────────────────────────────>│ │
 *   │ │                                                          │ │
 *   │ │            onPeerDiscovered(BluetoothDevice)             │ │
 *   │ │ <─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │ │
 *   │ │                                                          │ │
 *   │ │            onPeerDiscovered(BluetoothDevice)             │ │
 *   │ │<─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│ │
 *   │ │                                                          │ │
 *   │ │         onServiceDiscovered(device, description)         │ │
 *   │ │<─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│ │
 *   │ │                                                          └┬┘
 *   │ │                                                           │
 *   │ │                          stop()                          ┌┴┐
 *   │ │ ────────────────────────────────────────────────────────>│ │
 *   │ │                                                          │ │
 *   └┬┘                                                          └┬┘
 *    │                                                            │
 * ------------------------------------------------------------
 * </pre>
 *
 * @author WilliBoelke
 */
public class BluetoothDiscoveryVTwo extends BluetoothDiscoveryEngine
{
    //
    //  ---------- static members ----------
    //
    /**
     * Instance of the class following the singleton pattern
     */
    private static BluetoothDiscoveryVTwo instance;

    //
    //  ----------  instance variables  ----------
    //

    /**
     * Classname for logging only
     */
    private final String TAG = this.getClass().getSimpleName();

    private final ArrayList<BluetoothDevice> fetchedList = new ArrayList<>();
    private boolean refreshing;
    private long refreshingTimeStamp;
    public static final long REFRESH_TIME = 15000;
    //
    //  ----------  initialisation and setup ----------
    //

    /**
     * Returns the singleton instance of the BluetoothDiscoveryEngineSecondVersion.
     *
     * @return The singleton instance of the discovery engine
     */
    public static BluetoothDiscoveryVTwo getInstance()
    {
        if (instance == null)
        {
            instance = new BluetoothDiscoveryVTwo();
        }
        return instance;
    }


    /**
     * Private constructor initializing the singleton {@link #instance}
     */
    private BluetoothDiscoveryVTwo()
    {
        super();
    }

    /**
     * Stops the engine and resets the singleton instance to "null"
     * this is mostly used for testing
     */
    protected void teardownEngine()
    {
        // yes im logging this as error, just to make it visible
        Log.e(TAG, "teardownEngine: ---resetting engine---");
        this.stop();
        instance = null;
    }

    @Override
    protected void onDeviceDiscoveryFinished()
    {
        // wont do anything here
    }

    @Override
    protected void onDeviceDiscovered(BluetoothDevice device)
    {
        // Adding the device to he discovered devices list
        if (!discoveredDevices.contains(device))
        {
            discoveredDevices.add(device);
            notifyOnPeerDiscovered(device);
        }
        // should fetch UUIDs?
        if (shouldFetchUUIDsAgain(device))
        {
            Log.d(TAG, "onDeviceDiscovered: fetching Services from " + device);
            bluetoothAdapter.cancelDiscovery();
            device.fetchUuidsWithSdp();
        }
    }

    @Override
    protected void onUuidsFetched(BluetoothDevice device, Parcelable[] uuidExtra)
    {
        Log.d(TAG, "onUuidsFetched: received UUIDS for " + device);
        if (this.shouldFetchUUIDsAgain(device) && uuidExtra != null)
        {
            Log.d(TAG, "onUuidsFetched: found new uuids");
            notifyListenersIfServiceIsAvailable(device, uuidExtra);
            this.addToFetchedList(device);
        }
        if (!this.isRefreshProcessRunning())
        {
            Log.d(TAG, "onUuidsFetched: not refreshing, restarting discovery");
            internalRestartDiscovery();
        }
    }

    @Override
    protected void onDeviceDiscoveryRestart()
    {
        this.fetchedList.clear();
        refreshingTimeStamp = 0;
    }

    @Override
    protected void onRefreshStarted()
    {
        this.fetchedList.clear();
        this.refreshingTimeStamp = System.currentTimeMillis();
    }

    private boolean shouldFetchUUIDsAgain(BluetoothDevice device)
    {
        return !this.fetchedList.contains(device);
    }

    private void addToFetchedList(BluetoothDevice device)
    {
        fetchedList.add(device);
    }



    public boolean isRefreshProcessRunning()
    {
        if (System.currentTimeMillis() - this.refreshingTimeStamp >= REFRESH_TIME)
        {
            Log.d(TAG, "isRefreshProcessRunning: not refreshing");
            return false;
        }
        Log.d(TAG, "isRefreshProcessRunning: refresh running");
        return  true;
    }
}


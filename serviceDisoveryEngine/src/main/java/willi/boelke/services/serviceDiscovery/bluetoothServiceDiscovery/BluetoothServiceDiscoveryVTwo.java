package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery;

import android.bluetooth.BluetoothDevice;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This implementation of the {@link BluetoothServiceDiscoveryEngine}
 * allows to discover nearby bluetooth peers and services running on them.
 * <p>
 * <h2>V2</h2>
 * The V2 stands for variant two and it is not necessarily "better"
 * then {@link BluetoothServiceDiscoveryVOne} but it works distinctly
 * different.
 * While {@link BluetoothServiceDiscoveryVOne} will perform a bluetooth
 * device discovery first and then runs a service discovery for all
 * discovered devices, this version will alternate between device and
 * service discovery. On each newly discovered device the device
 * discovery will be stopped and a service discovery for this device
 * will be performed.
 * <p>
 * This approach improves the duration between the start of the
 * discovery {@link #startDeviceDiscovery()} and the discovery of
 * services, since a device discovery will ( in its full lengths )
 * take around 12 seconds. Though if there are many discoverable
 * bluetooth devices in range this effect will be negated.
 *
 * <p>
 * <h2>Usage</h2>
 * Please refer to {@link BluetoothServiceDiscovery} and {@link BluetoothServiceDiscoveryEngine}
 *
 * @author WilliBoelke
 */
public class BluetoothServiceDiscoveryVTwo extends BluetoothServiceDiscoveryEngine
{
    //
    //  ---------- static members ----------
    //
    /**
     * Instance of the class following the singleton pattern
     */
    private static BluetoothServiceDiscoveryVTwo instance;

    /**
     * Defines the duration of the stop for the
     * automatic re-enabling of the discovery
     */
    private static final long REFRESH_TIME = 12000;

    //
    //  ----------  instance variables  ----------
    //

    /**
     * Classname for logging only
     */
    private final String TAG = this.getClass().getSimpleName();
    /**
     * Discovered devices will be added to this list in
     * {@link #onDeviceDiscovered(BluetoothDevice)}. When the device discovery
     * stopped {@link #onDeviceDiscoveryFinished()} a service discovery will
     * be performed on the device(es) in this list. Then they will be added to
     * {@link #fetchedList} an removed from this.
     */
    private final List<BluetoothDevice> deviceFetchQueue = new CopyOnWriteArrayList<>(new ArrayList<>());
    /**
     * This list contains all devices where a service discovery was performed on since the start of
     * the discovery process through {@link #startDeviceDiscovery()}.
     * This is to prevent a circling discovery where a device is discovered again and again
     * and the process never stops.
     */
    private final List<BluetoothDevice> fetchedList = new CopyOnWriteArrayList<>(new ArrayList<>());
    /**
     * When a refresh is started through {@link #refreshNearbyServices()}
     * the automatic re-enabling of the device discovery will be stopped
     * for {@link #REFRESH_TIME} seconds. This variable holds
     * the timestamp of the refresh start.
     */
    private long refreshingTimeStamp;

    //
    //  ----------  initialisation and setup ----------
    //

    /**
     * Returns the singleton instance of the {@link BluetoothServiceDiscoveryVTwo}.
     *
     * @return The singleton instance of the discovery engine
     */
    public static BluetoothServiceDiscoveryVTwo getInstance()
    {
        if (instance == null)
        {
            instance = new BluetoothServiceDiscoveryVTwo();
        }
        return instance;
    }

    /**
     * Private constructor initializing the singleton {@link #instance}
     */
    private BluetoothServiceDiscoveryVTwo()
    {
        super();
    }

    /**
     * Stops the engine and resets the singleton instance to "null"
     * this is mostly used for testing
     */
    @Override
    protected void teardownEngine()
    {
        // yes im logging this as error, just to make it visible
        Log.e(TAG, "teardownEngine: ---resetting engine---");
        this.stop();
        instance = null;
    }


    //
    //  ---------- on events ----------
    //

    /**
     * Called when the device discovery finished.
     * Performs a service discovery on all BluetoothDevices
     * in {@link #deviceFetchQueue}
     */
    @Override
    protected void onDeviceDiscoveryFinished()
    {
        ArrayList<BluetoothDevice> fetchedDevices = new ArrayList<>();
        for (BluetoothDevice device : deviceFetchQueue)
        {
            device.fetchUuidsWithSdp();
            fetchedDevices.add(device);
        }
        for (BluetoothDevice fetched : fetchedDevices)
        {
            deviceFetchQueue.remove(fetched);
        }
        deviceFetchQueue.clear();
    }

    /**
     * Called whenever a device was discovered
     * Decides weather to stop the device discovery (new device)
     * or to go on with discovering other devices
     * (already in {@link #discoveredDevices})
     *
     * @param device
     *         The discovered device
     */
    @Override
    protected void onDeviceDiscovered(BluetoothDevice device)
    {
        //--- new device ---//

        if (!discoveredDevices.contains(device))
        {
            Log.d(TAG, "onDeviceDiscovered: discovered a new device " + device);
            discoveredDevices.add(device);
            notifyOnPeerDiscovered(device);
        }

        //--- should fetch UUIDs? ---//

        if (shouldFetchUUIDsAgain(device))
        {
            Log.d(TAG, "onDeviceDiscovered: fetching services from " + device);
            deviceFetchQueue.add(device);
            bluetoothAdapter.cancelDiscovery();
        }
    }

    /**
     * Called when UUIds for a device have been fetched
     * Adds the device to the {@link #fetchedList} if it isn't there already
     * and then starts a check for the discovered services through
     * {@link #notifyListenersIfServiceIsAvailable(BluetoothDevice, Parcelable[])}
     *
     * @param device
     *         The host device
     * @param uuidExtra
     *         A parcelable array, containing the UUIDs of the services
     */
    @Override
    protected void onUuidsFetched(BluetoothDevice device, Parcelable[] uuidExtra)
    {
        Log.d(TAG, "onUuidsFetched: received UUIDS for " + device);
        if (this.shouldFetchUUIDsAgain(device) && uuidExtra != null)
        {
            Log.d(TAG, "onUuidsFetched: found new uuids");
            notifyListenersIfServiceIsAvailable(device, uuidExtra);
        }

        fetchedList.add(device);

        if (!this.isRefreshProcessRunning())
        {
            Log.d(TAG, "onUuidsFetched: not refreshing, restarting discovery");
            internalRestartDiscovery();
        }
    }

    /**
     * Called whenever the device discovery is started manually
     * through {@link #startDeviceDiscovery()}
     * Clears the cache of discovered devices
     * and stops the refresh (by setting the timestamp to 0)
     */
    @Override
    protected void onDeviceDiscoveryRestart()
    {
        this.fetchedList.clear();
        refreshingTimeStamp = 0;
    }

    /**
     * This is called whenever a refresh is started through
     * {@link #refreshNearbyServices()} the fetchedList will be cleared to allow new
     * service UUIDs to come in and it sets the {@link #refreshingTimeStamp}
     * to the current time.
     */
    @Override
    protected void onRefreshStarted()
    {
        this.fetchedList.clear();
        this.refreshingTimeStamp = System.currentTimeMillis();
    }

    /**
     * Devices whether or not a service discovery should be performed on a device
     *
     * @param device
     *         the device in question
     *
     * @return true if a se
     */
    private boolean shouldFetchUUIDsAgain(BluetoothDevice device)
    {
        Log.d(TAG, "shouldFetchUUIDsAgain: Already on list " + fetchedList.contains(device));
        return !this.fetchedList.contains(device);
    }

    /**
     * Determines whether the refresh period is still running
     *
     * @return true if still refreshing, else false
     */
    public boolean isRefreshProcessRunning()
    {
        if (System.currentTimeMillis() - this.refreshingTimeStamp >= REFRESH_TIME)
        {
            Log.d(TAG, "isRefreshProcessRunning: not refreshing");
            return false;
        }
        Log.d(TAG, "isRefreshProcessRunning: refresh running");
        return true;
    }
}


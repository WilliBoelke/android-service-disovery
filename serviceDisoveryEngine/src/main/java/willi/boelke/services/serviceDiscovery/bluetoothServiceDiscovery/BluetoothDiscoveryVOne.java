package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Parcelable;
import android.util.Log;

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
 *   └┬┘                                                          └┬┘
 *    │                                                            │
 * ------------------------------------------------------------
 * </pre>
 *
 * @author WilliBoelke
 */
public class BluetoothDiscoveryVOne extends BluetoothDiscoveryEngine
{
    //
    //  ---------- static members ----------
    //

    /**
     * Instance of the class following the singleton pattern
     */
    private static BluetoothDiscoveryVOne instance;

    //
    //  ----------  instance variables  ----------
    //

    /**
     * Classname for logging only
     */
    private final String TAG = this.getClass().getSimpleName();

    //
    //  ----------  initialisation and setup ----------
    //

    /**
     * Returns the singleton instance of the BluetoothDiscoveryEngine.
     *
     * @return The singleton instance of the discovery engine
     */
    public static BluetoothDiscoveryVOne getInstance()
    {
        if (instance == null)
        {
            instance = new BluetoothDiscoveryVOne();
        }
        return instance;
    }


    /**
     * Private constructor initializing the singleton {@link #instance}
     */
    private BluetoothDiscoveryVOne()
    {
        super();
    }


    @Override
    protected void onDeviceDiscoveryFinished()
    {
        //Starting SDP
        requestServiceFromDiscoveredDevices();
    }

    @Override
    protected void onUuidsFetched(BluetoothDevice device, Parcelable[] uuidExtra)
    {
        Log.d(TAG, "onUuidsFetched: received UUIDS fot " + device);

        if (uuidExtra != null)
        {
            if (this.notifyAboutAllServices)
            {
                notifyListenersAboutServices(device, uuidExtra);
            }
            else
            {
                notifyListenersIfServiceIsAvailable(device, uuidExtra);
            }
        }
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
    protected void onDeviceDiscovered(BluetoothDevice device)

    {
        // Adding the device to he discovered devices list
        if (!discoveredDevices.contains(device))
        {
            discoveredDevices.add(device);
            // Notifying client about newly found devices
            this.notifyOnPeerDiscovered(device);
        }
    }

    @Override
    protected void onDeviceDiscoveryRestart()
    {
        // do nothing
    }

    @Override
    protected void onRefreshStarted()
    {

    }
}


package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery;

import android.bluetooth.BluetoothDevice;
import android.os.Parcelable;
import android.util.Log;

/**
 * This implementation of the {@link BluetoothServiceDiscoveryEngine}
 * allows to discover nearby bluetooth peers and services running on them.
 * <p>
 * <h2>V1</h2>
 * V1 stands for Variant one and does not mean that this is a older version then
 * {@link BluetoothServiceDiscoveryVTwo} for example.
 * The difference to the version two implementation is the sequence of events during the
 * service discovery. While version two alternates between a device and service discovery
 * this version performs a single, full (12 sec) device discovery and after that
 * a service discovery request will be send to al discovered devices.
 * <p>
 * This makes is comparable slower then version 2, especially when fewer devices are available.
 * On the other hand it offers more stability and should offer a better performance
 * when many peers are available
 * <p>
 * <h2>Usage</h2>
 * Please refer to {@link BluetoothServiceDiscovery} and {@link BluetoothServiceDiscoveryEngine}
 *
 * @author WilliBoelke
 */
public class BluetoothServiceDiscoveryVOne extends BluetoothServiceDiscoveryEngine
{
    //
    //  ---------- static members ----------
    //

    /**
     * Instance of the class following the singleton pattern
     */
    private static BluetoothServiceDiscoveryVOne instance;

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
    public static BluetoothServiceDiscoveryVOne getInstance()
    {
        if (instance == null)
        {
            instance = new BluetoothServiceDiscoveryVOne();
        }
        return instance;
    }


    /**
     * Private constructor initializing the singleton {@link #instance}
     */
    private BluetoothServiceDiscoveryVOne()
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


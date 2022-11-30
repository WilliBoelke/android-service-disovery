package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * This BroadcastReceiver listens on Broadcasts regarding the state of the
 * bluetooth api.
 * <p>
 * The only broadcasts used here is the {@link BluetoothAdapter#ACTION_DISCOVERY_FINISHED}
 * which will cause {@link BluetoothServiceDiscoveryEngine#onDeviceDiscoveryFinished()}
 * to be called, this will start the UUID fetching process.
 * <p>
 * -------
 * Not all broadcasts which are listened on here are in the
 * intent filter, since most of them are for logging and debugging.
 * -------
 *
 * @author WilliBoeke
 */
class DeviceDiscoveryStateReceiver extends BroadcastReceiver
{

    //
    //  ---------- instance variables ----------
    //

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    /*
     * Reference to the engine
     */
    private final BluetoothServiceDiscoveryEngine discoveryEngine;


    //
    //  ----------  constructor and init ----------
    //

    /**
     * Public constructor
     *
     * @param engine
     *         SdpBtDiscoveryEngine, to be notified
     *         when certain intents are received.
     */
    public DeviceDiscoveryStateReceiver(BluetoothServiceDiscoveryEngine engine)
    {
        this.discoveryEngine = engine;
    }


    //
    //  ----------  broadcast receiver ----------
    //

    @Override
    public void onReceive(Context context, Intent intent)
    {
        String action = intent.getAction();
        if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action))
        {
            Log.e(TAG, "ACTION_DISCOVERY_STARTED: Start Discovery");
        }
        else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
        {
            Log.e(TAG, "ACTION_DISCOVERY_FINISHED: Finished Discovery");
            discoveryEngine.onDeviceDiscoveryFinished();
        }
    }
}
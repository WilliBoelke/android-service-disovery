package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import javax.security.auth.SubjectDomainCombiner;

import willi.boelke.servicedisoveryengine.serviceDiscovery.Utils;

/**
 * This BroadcastReceiver is used in the SDPBluetooth engine.
 * Not all broadcasts which are listened on here are in the
 * intent filter, since most of them are for logging and debugging.
 *
 * The only broadcasts used here is the {@link BluetoothAdapter#ACTION_DISCOVERY_FINISHED}
 * which will cause {@link SdpBluetoothEngine#onDeviceDiscoveryFinished()}
 * to be called.
 *
 * @see SdpBluetoothEngine#registerReceivers()}
 * @see SdpBluetoothEngine#unregisterReceivers()
 *
 * @author WilliBoeke
 */
class BluetoothBroadcastReceiver extends BroadcastReceiver
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
    * TODO  better define an interface
    */
    private SdpBluetoothEngine engine;


    //
    //  ----------  constructor and init ----------
    //

    /**
     * Public constructor
     * @param engine
     * SdpBluetoothEngine, to call methods
     * when certain intents are received.
     */
    public BluetoothBroadcastReceiver(SdpBluetoothEngine engine){
            this.engine = engine;
    }
    

    //
    //  ----------  broadcast receiver ----------
    //

    @Override
    public void onReceive(Context context, Intent intent)
    {
        String action = intent.getAction();

        if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED))
        {
            int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);
            switch (mode)
            {
                case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                    Log.d(TAG, "EXTRA_SCAN_MODE: Discoverability Enabled.");
                    break;
                case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                    Log.d(TAG, "EXTRA_SCAN_MODE: Discoverability Disabled. Able to receive connections.");
                    break;
                case BluetoothAdapter.SCAN_MODE_NONE:
                    Log.d(TAG, "EXTRA_SCAN_MODE: Discoverability Disabled. Not able to receive connections.");
                    break;
                case BluetoothAdapter.STATE_CONNECTING:
                    Log.d(TAG, "EXTRA_SCAN_MODE: Connecting");
                    break;
                case BluetoothAdapter.STATE_CONNECTED:
                    Log.d(TAG, "EXTRA_SCAN_MODE: Connected");
                    break;
                default:
                    Log.d(TAG, "onReceive: unknown scan mode change");
            }
        }
        else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
        {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Log.e(TAG, "ACTION_ACL_CONNECTED: Low level connection established with "+ Utils.getRemoteDeviceString(device));
        }
        else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action))
        {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Log.e(TAG, "ACTION_ACL_DISCONNECTED: Low level connection ended with " + Utils.getRemoteDeviceString(device));
        }
        else if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device.getBondState() == BluetoothDevice.BOND_BONDED)
            {
                Log.d(TAG, "ACTION_BOND_STATE_CHANGED: BOND_BONDED with " + Utils.getRemoteDeviceString(device));
            }
            if (device.getBondState() == BluetoothDevice.BOND_BONDING)
            {
                Log.d(TAG, "ACTION_BOND_STATE_CHANGED: BOND_BONDING with" + Utils.getRemoteDeviceString(device));
            }
            if (device.getBondState() == BluetoothDevice.BOND_NONE)
            {
                Log.d(TAG, "ACTION_BOND_STATE_CHANGED: BOND_NONE with" + Utils.getRemoteDeviceString(device));
            }
        }
        else if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action))
        {
            Log.e(TAG, "ACTION_DISCOVERY_STARTED: Start Discovery");

        }
        if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
        {
            engine.onDeviceDiscoveryFinished();
            Log.e(TAG, "ACTION_DISCOVERY_STARTED: Finished Discovery");
        }
    }
}
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
 * This Broadcast Receiver can be used in the SdpBluetoothEngine
 * it needs o be registered and also unregistered in
 * {@link SdpBluetoothEngine#registerReceivers()} / {@link SdpBluetoothEngine#unregisterReceivers()}
 *
 * This Broadcast receiver wont add any functionality its only purpose is
 * to log bluetooth events.
 *
 * @author WilliBoeke
 */
class DebuggingBroadcastReceiver extends BroadcastReceiver
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();
    private SdpBluetoothEngine engine;

    public DebuggingBroadcastReceiver(SdpBluetoothEngine engine){
            this.engine = engine;
    }


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
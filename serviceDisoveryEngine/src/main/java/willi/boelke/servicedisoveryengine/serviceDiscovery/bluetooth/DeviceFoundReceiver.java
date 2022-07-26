package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * While a bluetooth device discovery is running, this
 * broadcast receiver will notify the SdpBluetooth engine 
 * about discovered devices using {@link SdpBluetoothEngine#onDeviceDiscovered(BluetoothDevice)}
 */
class DeviceFoundReceiver extends BroadcastReceiver
{

    //
    //  ----------  instance variables ----------
    //

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();
    /**
     * SdpBluetooth engine to notify
     */
    private final SdpBluetoothEngine engine;


    //
    //  ----------  constructor and initialisation ----------
    //


    public DeviceFoundReceiver(SdpBluetoothEngine engine){
        Log.d(TAG, "DeviceFoundReceiver: initialised receiver");
        this.engine = engine;
    }

    //
    //  ----------  broadcast receiver methods  ----------
    //

    @Override
    public void onReceive(Context context, Intent intent)
    {
        final String action = intent.getAction();
        if (action.equals(BluetoothDevice.ACTION_FOUND))
        {
            //Getting new BTDevice from intent
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Log.d(TAG, "onReceive: discovered new device " + Utils.getBluetoothDeviceString(device));
            this.engine.onDeviceDiscovered(device);
        }
    }
}

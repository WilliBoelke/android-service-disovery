package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.util.Log;

class UUIDFetchedReceiver extends BroadcastReceiver
{

    //
    //  ----------  instance variables ----------
    //

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private SdpBluetoothEngine engine;


    //
    //  ----------  constructor and initialisation ----------
    //

    public UUIDFetchedReceiver(SdpBluetoothEngine engine) {
        this.engine = engine;
    }


    //
    //  ----------  broadcast receiver methods ----------
    //

    @Override
    public void onReceive(Context context, Intent intent)
    {
        final String action = intent.getAction();
        if (action.equals(BluetoothDevice.ACTION_UUID))
        {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Parcelable[] uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
            Log.d(TAG, "onReceive: received UUIDs for " + Utils.getBluetoothDeviceString(device));
            this.engine.onUuidsFetched(device, uuidExtra);
        }
    }
}

package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;

public class UUIDFetchedReceiver extends BroadcastReceiver
{
    private final String TAG = this.getClass().getSimpleName();
    private SdpBluetoothEngine engine;

    public UUIDFetchedReceiver(SdpBluetoothEngine engine) {
        this.engine = engine;
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        final String action = intent.getAction();
        if (action.equals(BluetoothDevice.ACTION_UUID))
        {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Parcelable[] uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
            this.engine.onUuidsFetched(device, uuidExtra);
        }
    }
}

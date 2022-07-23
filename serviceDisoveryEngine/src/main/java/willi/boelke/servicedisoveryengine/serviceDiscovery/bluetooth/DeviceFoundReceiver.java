package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class DeviceFoundReceiver extends BroadcastReceiver
{
    private final String TAG = this.getClass().getSimpleName();

    private final SdpBluetoothEngine engine;

    public DeviceFoundReceiver(SdpBluetoothEngine engine){
        Log.d(TAG, "DeviceFoundReceiver: initialised receiver");
        this.engine = engine;
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        final String action = intent.getAction();
        if (action.equals(BluetoothDevice.ACTION_FOUND))
        {
            //Getting  new BTDevice from intent
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Log.d(TAG, "foundDeviceReceiver: onReceive: discovered new device " + engine.getDeviceString(device));
            this.engine.onDeviceDiscovered(device);
        }
    }
}

package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

/**
 * This BroadcastReceiver listens on changes in the Wifi Direct State
 */
public class WifiDirectStateChangeReceiver extends BroadcastReceiver
{
    private final String TAG = this.getClass().getSimpleName();
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final WifiP2pManager.ConnectionInfoListener connectionInfoListener;

    public WifiDirectStateChangeReceiver(WifiP2pManager manager
            , WifiP2pManager.Channel channel, WifiDirectConnectionInfoListener connectionInfoListener)
    {
        super();
        this.manager = manager;
        this.channel = channel;
        this.connectionInfoListener = connectionInfoListener;
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        // Stolen from https://developer.android.com/training/connect-devices-wirelessly/wifi-direct
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action))
        {
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
            {
                Log.d(TAG, "Wifi direct was enabled");
            }
            else
            {
                Log.d(TAG, "Wifi direct was disabled");
            }
        }
        else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action))
        {
            //----------------------------------
            // NOTE : NetworkInfo is apparently deprecated, official android documentation
            // though only gives this way and does not provide any replacement
            // EXTRA fields in this intent. TODO check that again
            //----------------------------------
            NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            if (networkInfo.isConnected())
            {
                Log.e(TAG, "onReceive: connection changed, connected to peer ");
                manager.requestConnectionInfo(channel, connectionInfoListener);
                manager.requestGroupInfo(channel, (WifiP2pManager.GroupInfoListener) connectionInfoListener);
            }

        }
        else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action))
        {
            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
        }
    }
}

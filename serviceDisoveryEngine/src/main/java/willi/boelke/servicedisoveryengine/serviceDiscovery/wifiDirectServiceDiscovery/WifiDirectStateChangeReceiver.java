package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirectServiceDiscovery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

/**
 * This BroadcastReceiver listens on changes in the Wifi Direct State
 */
public class WifiDirectStateChangeReceiver extends BroadcastReceiver
{
    private final String TAG = this.getClass().getSimpleName();
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;

    public WifiDirectStateChangeReceiver(WifiP2pManager manager
    , WifiP2pManager.Channel channel)
    {
        super();
        this.manager = manager;
        this.channel = channel;
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        // Stolen from https://developer.android.com/training/connect-devices-wirelessly/wifi-direct

        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action))
        {
            // Determine if Wifi P2P mode is enabled or not, alert
            // the Activity.
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
            {
                Log.d(TAG, "Wifi direct was enabled");
                //activity.setIsWifiP2pEnabled(true);
            }
            else
            {
                Log.d(TAG, "Wifi direct was disabled");
                //activity.setIsWifiP2pEnabled(false);
            }
        }
        else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action))
        {
            Log.d(TAG, "The peer list changed");
            if (manager != null) {
                //   manager.requestPeers(channel, listener);
            }
        }
        else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action))
        {
            NetworkInfo networkInfo = (NetworkInfo) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            if (networkInfo.isConnected())
            {
                //manager.requestConnectionInfo(channel, connectionListener);
            }

        }
        else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action))
        {
            //intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
        }
    }
}

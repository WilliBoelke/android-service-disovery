package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect;

import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import willi.boelke.servicedisoveryengine.serviceDiscovery.tcp.TCPChannelMaker;

/**
 *
 */
public class WifiDirectConnectionInfoListener implements WifiP2pManager.ConnectionInfoListener
{


    //
    //  ----------  instance variables ----------
    //
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private final SdpWifiEngine sdpWifiEngine;

    private TCPChannelMaker serverChannelCreator = null;


    //
    //  ----------  constructor and initialisation ----------
    //
    public WifiDirectConnectionInfoListener(SdpWifiEngine sdpWifiEngine) {
        this.sdpWifiEngine = sdpWifiEngine;
    }


    //
    //  ----------  connection info listener ----------
    //


    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info)
    {
        //----------------------------------
        // NOTE : this code is partially taken from the ASAPAndroid WifiConnectionPeerInfoListener
        //----------------------------------
        Log.d(TAG, "onConnectionInfoAvailable: received connection info");

        TCPChannelMaker.max_connection_loops = 10;
        TCPChannelMaker channelCreator = null;
        if(info.isGroupOwner)
        {
            Log.d(TAG, "onConnectionInfoAvailable: local peer became group owner");

            if(this.serverChannelCreator == null)
            {
                Log.d(TAG, "start server channel maker");
                Log.d(TAG, "onConnectionInfoAvailable: start server channel");
                this.serverChannelCreator = TCPChannelMaker.getTCPServerCreator(sdpWifiEngine.getPortNumber(), true);
            }
            else
            {
                Log.d(TAG, "onConnectionInfoAvailable: Server channel already exists");
            }

            channelCreator = this.serverChannelCreator;

        }
        else
        {
            String hostAddress = info.groupOwnerAddress.getHostAddress();
            Log.d(TAG, "onConnectionInfoAvailable: local peer client, group owner = " + hostAddress);

            channelCreator = TCPChannelMaker.getTCPClientCreator(hostAddress, sdpWifiEngine.getPortNumber());
        }
        this.sdpWifiEngine.onSocketConnectionStarted(channelCreator);
    }

}

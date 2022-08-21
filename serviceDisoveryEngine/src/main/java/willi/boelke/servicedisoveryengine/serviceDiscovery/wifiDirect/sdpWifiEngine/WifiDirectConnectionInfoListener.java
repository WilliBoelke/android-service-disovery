package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.sdpWifiEngine;

import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import willi.boelke.servicedisoveryengine.serviceDiscovery.tcp.TCPChannelMaker;
import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.sdpWifiEngine.SdpWifiEngine;

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
        // NOTE : apparently this method will also be called
        // when a client leaves a group, with this containing.
        // the GO will try to establish a connection to the given device,
        // and this will end in a infinite loop
        // in the TCPServer...
        //----------------------------------

        Log.d(TAG, "onConnectionInfoAvailable: received connection info");
        Log.d(TAG, "onConnectionInfoAvailable: " + info);
        TCPChannelMaker.max_connection_loops = 10;
        TCPChannelMaker channelCreator = null;
        if(info.isGroupOwner)
        {
            //--- creating server channel - just once as group owner ---//

            if(this.serverChannelCreator == null)
            {
                sdpWifiEngine.onBecameGroupOwner();
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
            sdpWifiEngine.onBecameClient();
            String hostAddress = info.groupOwnerAddress.getHostAddress();
            Log.d(TAG, "onConnectionInfoAvailable: local peer client, group owner = " + hostAddress);

            channelCreator = TCPChannelMaker.getTCPClientCreator(hostAddress, sdpWifiEngine.getPortNumber());
        }
        Log.e(TAG, "onConnectionInfoAvailable: channel creator is null = " + (channelCreator == null) );
        this.sdpWifiEngine.onSocketConnectionStarted(channelCreator);
    }
}

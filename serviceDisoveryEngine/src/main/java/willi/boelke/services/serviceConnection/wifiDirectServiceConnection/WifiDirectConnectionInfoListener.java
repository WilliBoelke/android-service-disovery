package willi.boelke.services.serviceConnection.wifiDirectServiceConnection;

import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import willi.boelke.services.serviceConnection.wifiDirectServiceConnection.tcp.TCPChannelMaker;

/**
 *
 */
class WifiDirectConnectionInfoListener implements WifiP2pManager.ConnectionInfoListener
{
    //
    //  ----------  instance variables ----------
    //
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private final WifiDirectConnectionEngine wifiDirectConnectionEngine;

    private TCPChannelMaker serverChannelCreator = null;

    private boolean establishConnection;

    //
    //  ----------  constructor and initialisation ----------
    //

    public WifiDirectConnectionInfoListener(WifiDirectConnectionEngine wifiDirectConnectionEngine)
    {
        this.wifiDirectConnectionEngine = wifiDirectConnectionEngine;
    }

    //
    //  ----------  connection info listener ----------
    //

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info)
    {
        //----------------------------------
        // NOTE : apparently this method will also be called
        // for the GO, when a client leaves the group and other
        // clients are connected. That's because in the  WifiDirectStateChangeReceiver,
        // the condition `isConnected()` is still true (second device is still connected).
        //
        // The GO will try to establish a connection to the given device,
        // and this ended in a infinite loop in the TCPServer.
        // To prevent this there is now a maximum of retires in the TCP
        // server.
        //
        // Though there should be a way to distinguish the two cases prior..
        // i could not ind anything so far.
        //----------------------------------

        Log.d(TAG, "onConnectionInfoAvailable: received connection info");
        Log.d(TAG, "onConnectionInfoAvailable: " + info);

        if (!establishConnection)
        {
            Log.e(TAG, "onConnectionInfoAvailable: should not establish connections");
            return;
        }
        TCPChannelMaker.max_connection_loops = 10;
        TCPChannelMaker channelCreator = null;
        if (info.isGroupOwner)
        {
            //--- creating server channel - just once as group owner ---//

            if (this.serverChannelCreator == null)
            {
                Log.d(TAG, "onConnectionInfoAvailable: start server channel");
                this.serverChannelCreator = TCPChannelMaker.getTCPServerCreator(wifiDirectConnectionEngine.getPort(), true);
            }
            else
            {
                Log.d(TAG, "onConnectionInfoAvailable: Server channel already exists");
            }
            channelCreator = this.serverChannelCreator;
            wifiDirectConnectionEngine.onBecameGroupOwner();
        }
        else
        {

            String hostAddress = info.groupOwnerAddress.getHostAddress();
            Log.d(TAG, "onConnectionInfoAvailable: local peer client, group owner = " + hostAddress);
            channelCreator = TCPChannelMaker.getTCPClientCreator(hostAddress, wifiDirectConnectionEngine.getPort());
            wifiDirectConnectionEngine.onBecameClient();
        }
        Log.e(TAG, "onConnectionInfoAvailable: channel creator is null = " + (channelCreator == null));
        this.wifiDirectConnectionEngine.onSocketConnectionStarted(channelCreator);
    }

    /**
     * This can be used to prevent the listener from establish connections as soon as
     * the are received.
     * <p>
     * In many cases this behavior isn't (for example when connections are already established)
     *
     * @param shouldEstablish
     * boolean determining whether a connection should be attempted
     */
    protected void establishConnections(boolean shouldEstablish)
    {
        Log.e(TAG, "establishConnections: SHOULD CONNECT = " + shouldEstablish);
        this.establishConnection = shouldEstablish;
    }
}

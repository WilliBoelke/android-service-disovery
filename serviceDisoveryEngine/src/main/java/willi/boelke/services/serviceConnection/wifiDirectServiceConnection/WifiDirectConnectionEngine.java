package willi.boelke.services.serviceConnection.wifiDirectServiceConnection;

import static android.net.wifi.p2p.WifiP2pManager.BUSY;
import static android.net.wifi.p2p.WifiP2pManager.ERROR;
import static android.net.wifi.p2p.WifiP2pManager.NO_SERVICE_REQUESTS;
import static android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import willi.boelke.services.serviceConnection.wifiDirectServiceConnection.tcp.TCPChannelMaker;
import willi.boelke.services.serviceDiscovery.ServiceDescription;
import willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery.WifiDirectDiscoveryEngine;
import willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery.WifiServiceDiscoveryListener;

/**
 * Starts service discovery and service advertisement,
 * manages connection establishment between services and clients.
 * <p>
 * Sets up a group between peers, the Group owner will be
 * chosen by the underlying Android implementation of wifi direct.
 * Establishes TCP connections between group owner and peers.
 * <p>
 * Initialisation<br>
 * ---------------------------------------------<br>
 * To obtain the singleton instance call {@link #getInstance()}.
 * <p>
 * After initialization the engine needs to be started before using
 * it {@link #start(Context)}. A running engine can be stopped by calling
 * {@link #stop()} this will cancel the discovery, unregister the
 * service if it was registered and remove the local device from
 * the group and close all establish connections.
 * <p>
 * Service advertisement and discovery<br>
 * ------------------------------------------------------------<br>
 * After the engine was started it can advertise and search
 * exactly one service at a time. To register a service for
 * advertisement and search call
 * {@link #registerService(ServiceDescription, SdpWifiPeer)}.
 * <p>
 * To start the general discovery (which is needed to find the service)
 * call {@link #startDiscovery()}. The discovery process will run around 20
 * seconds. It can be restarted and stopped as long as the engine runs
 * by calling {@link #startDiscovery()} or {@link #stopDiscovery()}
 * <p>
 * Listener<br>
 * ------------------------------------------------------------<br>
 * To use the engine and get notified about established connections, group
 * info (becoming GO or client) it is needed to implement the {@link SdpWifiPeer}
 * interface and pass it when calling the aforementioned methods.
 * <p>
 * Limitations<br>
 * ------------------------------------------------------------<br>
 * Please note that the SdpWifiEngine only support the advertisement and
 * search for one service at a given time.
 * A service / service discovery will only be started when the
 * already running discovery / service has been stopped by calling the methods
 * <p>
 * Groups<br>
 * ------------------------------------------------------------<br>
 * As specified in by the wifi direct protocol connections between peers
 * will always happen in the boundaries of a wifi direct group made of one group owner
 * and n clients.
 * * A client cannot join several groups at the same time
 * * A client cannot be a group owner at the same time as being a groups client
 * * A group owner cannot connect to other group owners.
 * ------------------------------------------------------------
 * <p>
 * Permissions:
 * Android requires a number of permissions to allow the usage of wifi direct,
 */
@SuppressLint("MissingPermission")
public class WifiDirectConnectionEngine
{

    //
    //  ----------  static members  ----------
    //

    /**
     * The singleton instance
     */
    private static WifiDirectConnectionEngine instance;

    //
    //  ----------  instance members ----------
    //

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    /**
     * The App Context
     */
    private Context context;

    /**
     * Wifi direct channel
     */
    private WifiP2pManager.Channel channel;

    /**
     * The wifi direct manager
     */
    private WifiP2pManager manager;

    /**
     * Implemented SdpWifiPeer interface
     * to callback on events.
     */
    private SdpWifiPeer peer;

    /**
     * BroadcastReceiver to listen to Android System Broadcasts specified in the intentFilter
     */
    private WifiDirectStateChangeReceiver mWifiReceiver;

    /**
     * The connection info listener
     */
    private WifiDirectConnectionInfoListener connectionListener;

    /**
     * The discovery engine
     */
    private WifiDirectDiscoveryEngine discoveryEngine;

    /**
     * The service the engine tries to find
     * at a given times.
     * @see #registerService(ServiceDescription, SdpWifiPeer)
     * @see #unregisterService()
     */
    private ServiceDescription currentServiceDescription;

    /**
     * Determines if the engine was or not
     *
     * @see #start(Context) ()
     * @see #stop()
     */
    private boolean engineRunning = false;

    //
    //  ----------  constructor and initialization ----------
    //


    /**
     * Returns the singleton instance
     *
     * @return The instance
     */
    public static WifiDirectConnectionEngine getInstance()
    {
        if (instance == null)
        {
            instance = new WifiDirectConnectionEngine();
        }
        return instance;
    }

    /**
     * Private constructor following the singleton pattern
     */
    private WifiDirectConnectionEngine()
    {
        // private singleton constructor
    }

    public void start(Context context)
    {
        if (isRunning())
        {
            Log.e(TAG, "start: engine already running");
            return;
        }
        Log.d(TAG, "setup: setup wifi engine");
        // Initialize manager and channel
        this.context = context;
        this.manager = (WifiP2pManager) this.context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.channel = manager.initialize(this.context, this.context.getMainLooper(), null);
        this.registerReceiver();

        //--- setting up discovery engine ---//

        this.discoveryEngine = WifiDirectDiscoveryEngine.getInstance();
        this.discoveryEngine.start(manager, channel);
        this.discoveryEngine.registerDiscoverListener(serviceDiscoveryListener);

        this.engineRunning = true;
    }


    /**
     * This stops the engine and disconnects from the group
     * the singleton instance will be reset to null.
     * This is mainly used for testing.
     */
    protected void teardownEngine()
    {
        this.stop();
        instance = null;
    }

    public void stop()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "engine not started - wont stop");
            return;
        }
        this.unregisterReceiver();
        this.unregisterService();
        this.discoveryEngine.stop();
        this.disconnectFromGroup();
        this.discoveryEngine.unregisterDiscoveryListener(serviceDiscoveryListener);
        try
        {
            this.manager.cancelConnect(this.channel, null);
        }
        catch (RuntimeException e)
        {
            // nothing to do here
        }
        this.engineRunning = false;
    }

    private void registerReceiver()
    {
        IntentFilter intentFilter = new IntentFilter();
        // Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        this.connectionListener = new WifiDirectConnectionInfoListener(this);
        this.mWifiReceiver = new WifiDirectStateChangeReceiver(manager, channel, this.connectionListener);
        this.context.registerReceiver(mWifiReceiver, intentFilter);
    }

    private void unregisterReceiver()
    {
        try
        {
            this.context.unregisterReceiver(mWifiReceiver);
        }
        catch (IllegalArgumentException e)
        {
            Log.d(TAG, "unregisterReceiver: receiver was not registered");
        }
    }

    //
    //  ---------- default wifip2p functions ----------
    //

    /**
     * This registers the service for discovery, other devices can find this service then.
     * Also the service with the given UUID (serviceUUID) will be looked for.
     * The service discovery however needs to be started and stopped separately, for that please
     * refer to {@link #startDiscovery()} and {@link #stopDiscovery()} respectively.
     *
     * @param description
     *         Service description
     * @param serviceClient
     *         Implementation of the SdpWifiPeer interface
     *
     * @return true if a service discovery could be started, false if another service discovery
     *         is running already
     */
    public boolean registerService(ServiceDescription description, SdpWifiPeer serviceClient)
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "registerService: engine not started - wont register servie");
            return false;
        }
        if (this.currentServiceDescription != null)
        {
            Log.e(TAG, "registerService: a service is already registered");
            return false; // already running
        }

        this.peer = serviceClient;
        this.currentServiceDescription = description;
        // Enable connection establishment (again)
        this.connectionListener.establishConnections(true);
        this.discoveryEngine.startService(description);
        this.discoveryEngine.startDiscoveryForService(description);
        return true;
    }

    /**
     * This stops the engine that means other devices cant find the advertised service anymore,
     * the discovery will stop and the discovered service will be unset,
     * to start the engine again call {@link #registerService(ServiceDescription, SdpWifiPeer)}.
     * <p>
     * This however wont cancel existing connections,
     * to leave the current group call {@link #disconnectFromGroup()}.
     */
    public void unregisterService()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "unregisterService: engine not started - wont unregister");
            return;
        }
        if(currentServiceDescription == null) {
            return; // service description was already null - means no service was registered
        }
        this.discoveryEngine.stopDiscoveryForService(currentServiceDescription);
        this.discoveryEngine.stopService(currentServiceDescription);
        this.currentServiceDescription = null;
        this.connectionListener.establishConnections(false);
        this.peer = null;
    }

    /**
     * Removes the local peer from the current Wi-Fi direct
     * group, this will also close all current connections
     * And if the local peer is the group owner, completely
     * end the group and disconnect all clients.
     */
    public void disconnectFromGroup()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "unregisterService: engine not started - wont disconnect");
            return;
        }
        // taken from, replacing my own method
        // https://stackoverflow.com/questions/18679481/wifi-direct-end-connection-to-peer-on-android

        if (this.manager != null && this.channel != null)
        {
            this.manager.requestGroupInfo(this.channel, group ->
            {
                if (group != null && manager != null && channel != null)
                {
                    manager.removeGroup(channel, new WifiP2pManager.ActionListener()
                    {

                        @Override
                        public void onSuccess()
                        {
                            Log.d(TAG, "disconnectFromGroup: disconnected succesfully");
                        }

                        @Override
                        public void onFailure(int reason)
                        {
                            WifiDirectConnectionEngine.logReason(TAG, "disconnectFromGroup: failed to disconnect ", reason);
                        }
                    });
                }
            });
        }

        //--- accepting connections again ---//
        this.connectionListener.establishConnections(true);
    }


    //
    //  ----------  service discovery start/stop----------
    //

    /**
     * This starts the discovery process, which will discover all nearby services
     * a connection will only be established if a service was specified with in
     * {@link #registerService(ServiceDescription, SdpWifiPeer)}
     */
    public void startDiscovery()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "startDiscovery: engine not started - wont start");
            return;
        }
        this.discoveryEngine.startDiscovery();
    }

    /**
     * Stops the discovery process
     */
    public void stopDiscovery()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "stopDiscovery: engine not started - wont stop");
            return;
        }
        this.discoveryEngine.stopDiscovery();
    }


    //
    //  ----------  SdpWifiServiceDiscoverListener interface ----------
    //


    //
    //  ----------  connecting ----------
    //

    private final WifiServiceDiscoveryListener serviceDiscoveryListener = this::tryToConnect;

    /**
     * Checks if a connection should be established to the remote device
     * if that's the case it sends a connection request.
     *
     * @param device
     *         the remote device
     * @param description
     *         the description of the devices service
     */
    private void tryToConnect(WifiP2pDevice device, ServiceDescription description)
    {
        Log.d(TAG, "tryToConnect: received a service - trying to connect");
        Log.e(TAG, "tryToConnect: " + peer);
        if (peer == null)
        {
            Log.e(TAG, "tryToConnect: peer was null, stop");
            return;
        }


        if (peer.shouldConnectTo(device.deviceAddress, description))
        {
            Log.d(TAG, "tryToConnect: trying to connect to  " + device);
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = device.deviceAddress;

            this.manager.connect(this.channel, config, new WifiP2pManager.ActionListener()
            {
                @Override
                public void onSuccess()
                {
                    Log.d(TAG, "Successfully send connection request to " + device);
                }

                @Override
                public void onFailure(int reason)
                {
                    logReason(TAG, "Failed sending connection request to " + device, reason);
                }
            });
        }
        else
        {
            Log.d(TAG, "tryToConnect: peer decided not to connect");
        }
    }

    //
    //  ----------  on connection events ----------
    //

    /**
     * This will be called by the {@link WifiDirectConnectionInfoListener}
     * when a connection request was answered and the local peer became the GO
     * in a group
     */
    protected void onBecameGroupOwner()
    {
        Log.d(TAG, "onBecameGroupOwner: became group owner, doing group owner stuff");
        if (peer != null)
        {
            this.peer.onBecameGroupOwner();
        }
        // As a group owner we cant connect to other devices
        // so we also can stop discovery :
        this.stopDiscovery();
    }


    /**
     * This will be called by the {@link WifiDirectConnectionInfoListener}
     * when a connection request was answered and the local peer became a client in a
     * group
     */
    protected void onBecameClient()
    {
        if (peer != null)
        {
            this.peer.onBecameGroupClient();
        }
        Log.d(TAG, "onBecameClient: became client to a GO, doing client stuff");
        this.discoveryEngine.stopService(currentServiceDescription);
        this.connectionListener.establishConnections(false);
        //----------------------------------
        // NOTE : as a client, the local device does
        // not need to discover further devices, since
        // has found a group owner.
        // Also the service doesn't need to be advertised
        // anymore.
        // this especially brought some troubles. since
        // if another device found the service
        // another connection was established - including
        // another GO election. This would break
        // the existing group.
        //----------------------------------
        this.stopDiscovery();
    }

    protected void onSocketConnected(SdpWifiConnection connection)
    {
        Log.d(TAG, "onSocketConnected: Connection established " + connection);
        if (peer != null)
        {
            peer.onConnectionEstablished(connection);
        }
        else
        {
            connection.close();
        }
    }

    /**
     * Starts a thread to wait for socket connection being established
     * and then calls {@link #onSocketConnected(SdpWifiConnection)}
     *
     * @param channelCreator
     */
    protected void onSocketConnectionStarted(TCPChannelMaker channelCreator)
    {
        AsyncSdpWifiConnectionCreator awaitThread = new AsyncSdpWifiConnectionCreator(channelCreator, this, this.currentServiceDescription);
        awaitThread.start();
    }

    //
    //  ----------  misc ----------
    //

    protected int getPortNumber()
    {
        // todo make it better ... there shoudl be a whole port option in here maybe ?
        // and giving the port via the txt records
        return 7777;
    }

    public boolean isRunning()
    {
        return this.engineRunning;
    }

    private boolean engineIsNotRunning()
    {
        return !this.engineRunning;
    }

    protected static void logReason(String tag, String msg, int arg0)
    {
        String reason;
        switch (arg0)
        {
            case ERROR:
                reason = "error";
                break;
            case NO_SERVICE_REQUESTS:
                reason = "no service requests";
                break;
            case BUSY:
                reason = "busy";
                break;
            case P2P_UNSUPPORTED:
                reason = "unsupported";
                break;
            default:
                reason = "unexpected error";
        }

        Log.e(tag, msg + " reason : " + reason);
    }
}

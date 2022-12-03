package willi.boelke.services.serviceConnection.wifiDirectServiceConnection;

import static android.net.wifi.p2p.WifiP2pManager.BUSY;
import static android.net.wifi.p2p.WifiP2pManager.ERROR;
import static android.net.wifi.p2p.WifiP2pManager.NO_SERVICE_REQUESTS;
import static android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED;

import android.Manifest;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import willi.boelke.services.serviceConnection.wifiDirectServiceConnection.tcp.TCPChannelMaker;
import willi.boelke.services.serviceDiscovery.ServiceDescription;
import willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery.WifiDirectServiceDiscovery;
import willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery.WifiServiceDiscoveryListener;

/**
 * This is a prototypic implementation to establish
 * wifi direct connections between android devices based on the
 * {@link WifiDirectServiceDiscovery}.
 * The connections and groups will be formed automatically.
 * <p>
 * <h2>General</h2>
 * Starts service discovery and service advertisement,
 * manages connection establishment between services and clients.
 * <p>
 * Sets up a group between peers, the Group owner will be
 * chosen by the underlying Android implementation of wifi direct.
 * Establishes TCP connections between group owner and peers.
 * <p>
 * <h2>Usage</h2>
 * <h3>Initialisation</h3>
 * To obtain the singleton instance call {@link #getInstance()}.
 * <p>
 * After initialization the engine needs to be started before using
 * it {@link #start(Context, WifiDirectServiceDiscovery)}. A running engine can be stopped by calling
 * {@link #stop()} this will cancel the discovery, unregister the
 * service if it was registered and remove the local device from
 * the group and close all establish connections.
 * <p>
 * <h3>Service advertisement </h3>
 * After the engine was started it can advertise and search
 * exactly one service at a time. To register a service for
 * advertisement and search call
 * {@link #registerService(ServiceDescription, WifiDirectPeer)}.
 * <p>
 * <h3>Service discovery</h3>
 * To start the general discovery (which is needed to find the service)
 * call {@link #startDiscovery()}. The discovery process will run around 20
 * seconds. It can be restarted and stopped as long as the engine runs
 * by calling {@link #startDiscovery()} or {@link #stopDiscovery()}
 * <p>
 * <h2>Listener</h2>
 * To use the engine and get notified about established connections, group
 * info (becoming GO or client) it is needed to implement the {@link WifiDirectPeer}
 * interface and pass it when calling the aforementioned methods.
 * <p>
 * <h2>Ports</h2>
 * Connections between devices will be established through TCP Sockets.
 * For the purpose of this prototype a fixed port {@link #DEFAULT_PORT} is specified.
 * This will be known to client and server.
 * It would be possible to transfer the port number though the txt record
 * to potential clients, which then can connect without prior
 * knowledge of the port, this should be implemented.
 * For now though it is also possible to set a custom port {@link #setPort(int)}
 * <p>
 * <h2>Limitations</h2>
 * Please note that the {@link WifiDirectConnectionEngine}
 * only support the advertisement and search for one service at a given time.
 * A service / service discovery will only be started when the
 * already running discovery / service has been stopped {@link #stopDiscovery()}
 * <p>
 * <h2>Groups</h2>
 * As specified in by the wifi direct protocol connections between peers
 * will always happen in the boundaries of a wifi direct group made of one group owner
 * and `n` clients.
 * <ul>
 * <li>A client cannot join several groups at the same time</li>
 * <li>A client cannot be a group owner at the same time as being a groups client</li>
 * <li>A group owner cannot connect to other group owners.</li>
 * </ul>
 * <p>
 * In its current state the engine will form a group randomly.
 * It will try to establish a connection with the first matching service it finds
 * and form a group. The group owner election is managed by Android.
 * <p>
 * When a GO was elected and a group formed the client will stop advertising its service
 * as well as stop sending connection requests. Other clients can still discover
 * and connect to the group owner.
 * That though also means that if several devices are in range several groups
 * can be formed independently.
 *
 * <h2>Permissions</h2>
 * For the usage of the Wi-Fi APIs several android permissions are needed.
 * Here though especially {@link Manifest.permission#ACCESS_FINE_LOCATION}
 * is required. A permission check should be performed.
 * Methods which require the permission are marked as such.
 */
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

    private final int DEFAULT_PORT = 4242;

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
    private WifiDirectPeer peer;

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
    private WifiDirectServiceDiscovery discoveryEngine;

    /**
     * The service the engine tries to find
     * at a given times.
     *
     * @see #registerService(ServiceDescription, WifiDirectPeer)
     * @see #unregisterService()
     */
    private ServiceDescription currentServiceDescription;

    /**
     * Determines if the engine was or not
     *
     * @see #start(Context, WifiDirectServiceDiscovery)
     * @see #stop()
     */
    private boolean engineRunning = false;
    private boolean isConnected = false;
    private int usedPort = DEFAULT_PORT;

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

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public boolean start(Context context, WifiDirectServiceDiscovery serviceDiscovery)
    {
        if (isRunning())
        {
            Log.e(TAG, "start: engine already running");
            return true;
        }

        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null)
        {
            Log.e(TAG, "start: Wifi Service not available");
            return false;
        }
        if (!wifiManager.isP2pSupported())
        {
            Log.e(TAG, "start: Wifi turned off or not available");
            return false;
        }
        this.manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        if (manager == null)
        {
            Log.e(TAG, "start:Wifi Service not available");
            return false;
        }
        this.channel = manager.initialize(context, context.getMainLooper(), null);
        if (channel == null)
        {
            Log.e(TAG, "start: cant init WiFi direct");
            return false;
        }

        Log.d(TAG, "start: setup wifi engine");

        // Initialize manager and channel
        this.context = context.getApplicationContext();


        this.registerReceiver();

        //--- setting up discovery engine ---//

        this.discoveryEngine = serviceDiscovery;
        this.discoveryEngine.start(context, manager, channel);
        this.discoveryEngine.registerDiscoverListener(serviceDiscoveryListener);

        this.engineRunning = true;
        return true;
    }


    /**
     * This stops the engine and disconnects from the group
     * the singleton instance will be reset to null.
     * This is mainly used for testing.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    protected void teardownEngine()
    {
        this.stop();
        instance = null;
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
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
     * Also the service with the given ServiceDescriptioe will be looked for.
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
    public boolean registerService(ServiceDescription description, WifiDirectPeer serviceClient)
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
     * to start the engine again call {@link #registerService(ServiceDescription, WifiDirectPeer)}.
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
        if (currentServiceDescription == null)
        {
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
     * group, this will also close all current connections.
     * If the local peer is the group owner, the group will
     * be terminated and all peers will disconnect.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
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
                            Log.d(TAG, "disconnectFromGroup: disconnected successfully");
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
        this.isConnected = false;
    }


    //
    //  ----------  service discovery start/stop----------
    //

    /**
     * This starts the discovery process, which will discover all nearby services
     * a connection will only be established if a service was specified with in
     * {@link #registerService(ServiceDescription, WifiDirectPeer)}
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
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private void tryToConnect(WifiP2pDevice device, ServiceDescription description)
    {

        Log.d(TAG, "tryToConnect: received a service - trying to connect");
        if (peer == null)
        {
            Log.e(TAG, "tryToConnect: peer was null, wont send connection request to " + device);
            return;
        }
        peer.onServiceDiscovered(device, description);
        if (isConnected)
        {
            Log.d(TAG, "tryToConnect: already connected, wont send connection request to " + device);
            return;
        }
        if (!peer.shouldConnectTo(device, description))
        {
            Log.d(TAG, "tryToConnect: peer decided not to connect to " + device);
            return;
        }

        Log.d(TAG, "tryToConnect: trying to connect to  " + device);
        WifiP2pConfig config = new WifiP2pConfig();
        config.wps.setup = WpsInfo.PBC;
        config.deviceAddress = device.deviceAddress;
        manager.connect(channel, config, new WifiP2pManager.ActionListener()
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
        this.isConnected = true;
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

        // this.discoveryEngine.stopService(currentServiceDescription);
        // this.stopDiscovery();
        this.connectionListener.establishConnections(false);
    }

    protected void onSocketConnected(WifiConnection connection)
    {
        Log.d(TAG, "onSocketConnected: Connection established " + connection);
        if (peer != null)
        {
            Log.d(TAG, "onSocketConnected: connection was successfully established, notify peer");
            peer.onConnectionEstablished(connection);
        }
        else
        {
            Log.e(TAG, "onSocketConnected: no peer registered, closing connection");
            connection.close();
        }
    }

    /**
     * Starts a thread to wait for socket connection being established
     * and then calls {@link #onSocketConnected(WifiConnection)}
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

    protected int getPort()
    {
        return this.usedPort;
    }

    public void setPort(int port)
    {
        this.usedPort = port;
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

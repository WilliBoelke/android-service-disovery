package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.Utils;
import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;
import willi.boelke.servicedisoveryengine.serviceDiscovery.tcp.TCPChannelMaker;
import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.sdpClientServerInterfaces.SdpWifiPeer;

/**
 * Starts service discovery and service advertisement, manages connection
 * establishment between services and clients.
 *
 * Sets up a group between peers, the Group owner will be
 * defined by the underlying Android implementation of wifi direct.
 * ------------------------------------------------------------
 *
 * To start an service or a discovery (for a specific service) please refer to
 * {@link #start(ServiceDescription, SdpWifiPeer)}, to start the general service discovery (which is needed to find a service)
 * call {@link #startDiscovery()}
 *
 * To use the engine and get notified about established connections, group
 * info (becoming GO or client) it is needed to implement the {@link SdpWifiPeer}
 * interface and pass it when calling the aforementioned methods.
 * ------------------------------------------------------------
 *
 * Please note that the SdpWifiEngine only support the advertisement and
 * search for one service at a given time.
 * A service / service discovery will only be started when the
 * already running discovery / service has been stopped by calling the methods
 * {@link #stopSDPService()} {@link #stopSDPDiscovery()}
 * ------------------------------------------------------------
 *
 * As specified in by the wifi direct protocol connections between peers
 * will always happen in the boundaries of a wifi direct group made of one group owner
 * and n clients.
 * * A client cannot join several groups at the same time
 * * A client cannot be a group owner at the same time as being a groups client
 * * A group owner cannot connect to other group owners.
 * ------------------------------------------------------------
 *
 * Permissions:
 * Android requires a number of permissions to allow the usage of wifi direct,
 *
 */
@SuppressLint("MissingPermission")
public class SdpWifiEngine implements SdpWifiDiscoveryThread.WifiSdpServiceDiscoveryListener
{

    //
    //  ----------  static members  ----------
    //

    /**
     * The singleton instance
     */
    private static SdpWifiEngine instance;

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

    private SdpWifiDiscoveryThread serviceDiscovery;

    private HashMap<UUID, ArrayList<WifiP2pDevice>> discoveredServices = new HashMap<>();

    /**
     * Service registered in {@link #startSDPService(ServiceDescription)}
     * and unregistered in {@link #stopSDPDiscovery()}
     */
    private WifiP2pServiceInfo runningService;

    /**
     * Reverence to the currently running discovery thread
     */
    private SdpWifiDiscoveryThread discoveryThread;

    /**
     * The UUID of the service to discover
     * this will be set in {@link #startSDPDiscoveryForService(ServiceDescription)}
     */
    private ServiceDescription serviceToLookFor;

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


    //
    //  ----------  constructor and initialization ----------
    //

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public static SdpWifiEngine initialize(Context context)
    {
        if (instance == null)
        {
            instance = new SdpWifiEngine(context);
        }
        return instance;
    }

    public static SdpWifiEngine getInstance()
    {
        if (instance != null)
        {
            return instance;
        }
        else
        {
            return null;
        }
    }

    private SdpWifiEngine(Context context)
    {
        this.context = context;
        this.setup();
    }

    private void setup()
    {
        Log.d(TAG, "setup: setup wifi engine");
        // Initialize manager and channel
        this.manager = (WifiP2pManager) this.context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.channel = manager.initialize(this.context, this.context.getMainLooper(), null);
        // disconnecting from any group the device may currently be connected to
        this.disconnectFromGroup();
        this.connectionListener = new WifiDirectConnectionInfoListener(this);
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
        mWifiReceiver = new WifiDirectStateChangeReceiver(manager, channel, this.connectionListener);
        this.context.registerReceiver(mWifiReceiver, intentFilter);
    }

    private void unregisterReceiver()
    {
        try
        {
            this.context.unregisterReceiver(mWifiReceiver);
        }
        catch(IllegalArgumentException e){
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
     * refer to {@link #startDiscovery()} and {@link #stopDiscovery()} respectivly.
     *
     * @param description
     *         Service description
     * @param serviceClient
     *         Implementation of the SdpWifiPeer interface
     *
     * @return true if a service discovery could be started, false if another service discovery
     *         is running already
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public boolean start(ServiceDescription description, SdpWifiPeer serviceClient)
    {
        //--- check if a service discovery is already running ---//

        if (this.serviceToLookFor != null)
        {
            Log.d(TAG, "startSDPDiscoveryForServiceWithUUID: Service discovery already running, stop discovery first");
            return false;
        }

        //--- starting discovery ---//

        this.peer = serviceClient;
        this.registerReceiver();
        this.startSDPService(description);
        this.startSDPDiscoveryForService(description);

        return true;
    }

    /**
     * This stops the engine that means other devices cant find the advertised service anymore,
     * the discovery will stop and the discovered service will be unset,
     * to start the engine again call {@link #start(ServiceDescription, SdpWifiPeer)}.
     *
     * This however wont cancel existing connections,
     * to leave the current group call {@link #disconnectFromGroup()}.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void stop()
    {
        this.unregisterReceiver();
        this.stopDiscovery();
        this.stopSDPService();
        this.stopSDPDiscovery();
        this.manager.clearLocalServices(channel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {

            }

            @Override
            public void onFailure(int reason)
            {

            }
        });
    }

    /**
     * removes the local peer from the current Wi-Fi direct
     * group, this will also close all current connections
     * And if the local peer is the group owner, completely
     * end the group and disconnect all clients.
     */
    public void disconnectFromGroup(){
        manager.removeGroup(channel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(TAG, "onSuccess: remove group");
            }

            @Override
            public void onFailure(int reason)
            {
                Log.e(TAG, "onFailure: could not removed group");
            }
        });
    }

    /**
     * This stops the engine and disconnects from the group
     * the singleton instance will be reset to null.
     * This is mainly used for testing.
     */
    protected void teardownEngine()
    {
        this.stop();
        this.disconnectFromGroup();
        instance = null;
    }


    ////
    ////------------  service discovery specific methods ---------------
    ////


    //
    //  ----------  service discovery start/stop----------
    //

    /**
     * This starts the discovery process, which will discover all nearby services
     * a connection will only be established if a service was specified with in
     * {@link #start(ServiceDescription, SdpWifiPeer)}
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void startDiscovery()
    {
        this.stopDiscovery();
        this.discoveryThread = new SdpWifiDiscoveryThread(manager, channel, this);
        discoveryThread.start();
    }

    /**
     * Stops the discovery process
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void stopDiscovery()
    {
        //--- if the discovery thread is running -> cancel it ---//
        if(discoveryThread != null && discoveryThread.isDiscovering()){
            discoveryThread.cancel();
        }

        this.discoveryThread = null;
    }


    //
    //  ----------  "client" side ----------
    //


    /**
     * Starts looking for the service specified with the `serviceUUID` parameter.
     *
     * @param description
     *         The Service description
     */
    private void startSDPDiscoveryForService(ServiceDescription description)
    {
        Log.d(TAG, "Starting service discovery");
        // Are we already looking for he service?

        //----------------------------------
        // NOTE : here the sequence actually matters, the service client needs to be se before
        // trying to find already discovered services. Else this wil lead to a null pointer
        // when trying to connect to a service.
        //----------------------------------

        // Trying to find service on devices hat where discovered earlier in the engine run
        this.tryToConnectToServiceAlreadyInRange(description);
        // Adding the service to  be found in the future
        this.serviceToLookFor = description;
    }

    /**
     * This stops the discovery for the service given previously
     * trough calling {@link #startSDPDiscoveryForService(ServiceDescription)} (UUID, SdpWifiPeer)}.
     *
     * This however does not end any existing connections and does not cancel the overall service discovery
     * refer to {@link #stopDiscovery()}
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private void stopSDPDiscovery()
    {
        Log.d(TAG, "End service discovery for service with " + serviceToLookFor);

        this.serviceToLookFor = null;
        this.peer = null;
        this.stopDiscovery();
    }

    private void tryToConnectToServiceAlreadyInRange(ServiceDescription description)
    {
        // TODO
    }

    //
    //  ----------  "server" side ----------
    //

    /**
     *
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private void startSDPService(ServiceDescription description)
    {
        Log.d(TAG, "startSDPService: starting service : " + description);
        WifiP2pServiceInfo serviceRecords = WifiP2pDnsSdServiceInfo.newInstance(description.getServiceName(), "_presence._tcp", description.getServiceRecord());
        manager.addLocalService(channel, serviceRecords, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(TAG, "startSDPService: service successfully added : " + description);
                runningService = serviceRecords;
            }

            @Override
            public void onFailure(int arg0)
            {
                Utils.logReason("startSDPService: service could not be added : " + description , arg0);
            }
        });

    }

    /**
     * This stops the advertisement of the service,
     * other peers who are running a service discovery wont
     *
     *
     */
    private void stopSDPService()
    {
        if (this.runningService == null)
        {
            return;
        }

        manager.removeLocalService(channel, this.runningService, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                runningService = null;
                Log.d(TAG, "stopSDPService: service removed successfully ");
            }

            @Override
            public void onFailure(int reason)
            {
                Utils.logReason("stopSDPService: could not remove service ", reason);
            }
        });
    }


    //
    //  ----------  SdpWifiServiceDiscoverListener interface ----------
    //


    /**
     * Called by the {@link SdpWifiDiscoveryThread} as described
     * in the interface {@link SdpWifiDiscoveryThread.WifiSdpServiceDiscoveryListener}
     *
     * @param device
     *         The device which hosts the service
     * @param serviceRecord
     *         The services TXT Records
     * @param fullDomain
     *         The services domain
     */
    @Override
    public void onServiceDiscovered(WifiP2pDevice device, Map<String, String> serviceRecord, String fullDomain)
    {
        Log.d(TAG, "onServiceDiscovered: discovered a new Service on " + Utils.getRemoteDeviceString(device));

        ServiceDescription description = new ServiceDescription(serviceRecord);
        Log.d(TAG, "onServiceDiscovered: received " + description);

        if (this.peer != null)
        {
            peer.onServiceDiscovered(device.deviceAddress, description);
        }

        //--- adding to list of remembered services ---//

        ArrayList<WifiP2pDevice> serviceDevices = this.discoveredServices.get(description.getServiceUuid());
        if (serviceDevices == null)
        {
            serviceDevices = new ArrayList<>();
        }

        serviceDevices.add(device);
        this.discoveredServices.put(description.getServiceUuid(), serviceDevices);

        //--- trying to connect ---//

        this.tryToConnect(device, description);
    }

    @Override
    public void onDiscoveryFinished()
    {
        Log.d(TAG, "onDiscoveryFinished: the discovery process finished");
    }

    //
    //  ----------  connecting ----------
    //


    /**
     * Checks if a connection should be established to the remote device
     * if that's the case it sends a connection request.
     * @param device
     * the remote device
     * @param description
     * the description of the devices service
     */
    private void tryToConnect(WifiP2pDevice device, ServiceDescription description)
    {

        if (serviceToLookFor == null)
        {
            // no discovery started, UUID is null
            // no need to try to connect
            Log.d(TAG, "connectIfServiceAvailableAndNoConnectedAlready: not looking for a service, wont connect");
            return;
        }

        if (serviceToLookFor.equals(description) && peer.shouldConnectTo(device.deviceAddress, description))
        {

            Log.d(TAG, "tryToConnect: trying to connect to  " + Utils.getRemoteDeviceString(device));
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = device.deviceAddress;

            if (ContextCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                Log.e(TAG, "tryToConnect: no permission, wont connect");
            }
            else
            {
                this.manager.connect(
                        this.channel,
                        config,
                        new WifiP2pManager.ActionListener()
                        {
                            @Override
                            public void onSuccess()
                            {
                                Log.d(TAG, "Successfully send connection request to " + Utils.getRemoteDeviceString(device));
                    }

                    @Override
                    public void onFailure(int reason)
                    {
                        Utils.logReason("Failed sending connection request to " + Utils.getRemoteDeviceString(device), reason);
                    }
                }
        );}
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
    protected void onBecameGroupOwner(){
        Log.d(TAG, "onBecameGroupOwner: became group owner, doing group owner stuff");
        this.peer.onBecameGroupOwner();

        // As a group owner we cant connect to other devices
        // so we also can stop discovery :
        this.stopDiscovery();
    }


    /**
     * This will be called by the {@link WifiDirectConnectionInfoListener}
     * when a connection request was answered and the local peer became a client in a
     * group
     */
    protected void onBecameClient(){
        this.peer.onBecameGroupClient();
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
        this.stopDiscovery();
        this.stopSDPService();
    }

    protected void onSocketConnected(SdpWifiConnection connection)
    {
        Log.d(TAG, "onSocketConnected: Connection established");
        if(peer != null){
            peer.onConnectionEstablished(connection);
        }
        else{
            connection.close();
        }
    }

    protected void onSocketConnectionStarted(TCPChannelMaker channelCreator)
    {
        AsyncSdpWifiConnectionCreator awaitThread = new AsyncSdpWifiConnectionCreator(channelCreator, this, this.serviceToLookFor);
        awaitThread.start();
    }


    //
    //  ----------  misc ----------
    //

    protected int getPortNumber()
    {
        // todo make it better
        return 7777;
    }
}

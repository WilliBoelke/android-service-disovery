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
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.Utils;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceClient;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceServer;
import willi.boelke.servicedisoveryengine.serviceDiscovery.tcp.TCPChannelMaker;

/**
 * Manages the Wifi Direct android API
 */
public class SdpWifiEngine
{

    final static String SERVICE_DESCRIPTION = "service-description";
    final static String SERVICE_NAME = "service-name";
    final static String SERVICE_UUID = "service-uuid";

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

    private DiscoveryThread serviceDiscovery;

    private static SdpWifiEngine instance;

    private HashMap<UUID, ArrayList<WifiP2pDevice>> discoveredServices = new HashMap<>();

    private WifiP2pServiceInfo runningService;

    private DiscoveryThread discoveryThread;

    private UUID serviceToLookFor;

    private SdpBluetoothServiceClient serviceClient;

    /**
     *
     */
    /**
     * BroadcastReceiver to listen to Android System Broadcasts specified in the intentFilter
     */
    private WifiDirectStateChangeReceiver mWifiReceiver;

    private WifiDirectConnectionInfoListener connectionListener;
    private WifiP2pDnsSdServiceRequest serviceRequest;


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
        Log.d(TAG, "Setup Wifip2p Engine ");
        // Initialize manager and channel
        this.manager = (WifiP2pManager) this.context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.channel = manager.initialize(this.context, this.context.getMainLooper(), null);
        this.connectionListener = new WifiDirectConnectionInfoListener(this);
    }

    private void registerReceiver()
    {
        IntentFilter intentFilter = new IntentFilter();
        // Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        // Indicates a change in the list of available peers.
        // intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        mWifiReceiver = new WifiDirectStateChangeReceiver(manager, channel, this.connectionListener);
        this.context.registerReceiver(mWifiReceiver, intentFilter);
    }

    private void unregisterReceiver()
    {
        this.context.unregisterReceiver(mWifiReceiver);
    }

    //
    //  ---------- default wifip2p functions ----------
    //

    public void start()
    {
        this.registerReceiver();
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void stop()
    {
        this.unregisterReceiver();
        this.stopDiscovery();
        this.stopSDPService();
        manager.clearLocalServices(channel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
            }

            @Override
            public void onFailure(int reason)
            {
                // cant do much here
                Utils.logReason("stop: could not remove services", reason);
            }
        });
    }

    protected void teardownEngine()
    {
        this.stop();
        instance = null;
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void startDiscovery()
    {
        //----------------------------------
        // NOTE : Right now i don't see any
        // use in the information give here,
        // though i will let it here -
        // for logging and for easy later use.
        //
        // Okay from testing this several times i figured out a few things:
        // 1. Services only will be discovered when they are already running
        // at the moment `discoverServices` is called.
        // 2. Sometimes services wont bew discovered even when they are
        // already running.
        // 3. Services if they are discovered will be discovered several times
        // and not only once.
        //----------------------------------

        stopDiscovery();
        WifiP2pManager.DnsSdTxtRecordListener txtListener = (fullDomain, record, device) ->
        {
            Log.d(TAG, "startDiscovery: found service record: on  " + Utils.getRemoteDeviceString(device) + " record: " + record);
            this.onServiceDiscovered(device, record, fullDomain);
        };

        // service listener
        WifiP2pManager.DnsSdServiceResponseListener servListener = (instanceName, registrationType, resourceType) ->
        {

            Log.d(TAG, "startDiscovery: bonjour service available :\n" +
                    "name =" + instanceName +"\n"+
                    "registration type = " + registrationType +"\n" +
                    "resource type = " + resourceType);
        };

        manager.setDnsSdResponseListeners(channel, servListener, txtListener);

        this.serviceRequest = WifiP2pDnsSdServiceRequest.newInstance(); // looking for DnsSdServices
        manager.addServiceRequest(channel, serviceRequest,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "startDiscovery: added service requests");
                    }

                    @Override
                    public void onFailure(int arg0) {
                        Utils.logReason("startDiscovery: failed to add servcie request", arg0);
                    }
                });
        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "startDiscovery: initiated service discovery");
            }

            @Override
            public void onFailure(int arg0) {
                Utils.logReason("startDiscovery: failed to initiate service discoevry", arg0);
            }
        });

    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void stopDiscovery()
    {
        if(serviceRequest == null)
        {
            // discovery was not yet initiated, no need to stop
            return;
        }
        manager.removeServiceRequest(channel, serviceRequest, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(TAG, "onSuccess: successfully removed service request");
            }

            @Override
            public void onFailure(int reason)
            {
                Utils.logReason("could not remove service request", reason);
            }
        });

        //----------------------------------
        // NOTE : Apparently there is no need to stop the service discovery (?)
        // there is no real documentation available on how long the service discovery will run
        // or how to stop it...
        //----------------------------------

    }


    ////
    ////------------  serve discovery specific methods ---------------
    ////

    //
    //  ----------  "client" side ----------
    //


    public void startSDPDiscoveryForServiceWithUUID(UUID serviceUUID, SdpBluetoothServiceClient serviceClient)
    {
        Log.d(TAG, "Starting service discovery");
        // Are we already looking for he service?
        if (this.serviceToLookFor != null)
        {
            Log.d(TAG, "startSDPDiscoveryForServiceWithUUID: Service discovery already running, stop discovery first");
            return;
        }

        //----------------------------------
        // NOTE : here the sequence actually matters, the service client needs to be se before
        // trying to find already discovered services. Else this wil lead to a null pointer
        // when trying to connect to a service.
        //----------------------------------

        // Adding the service client ot the list
        this.serviceClient = serviceClient;
        // Trying to find service on devices hat where discovered earlier in the engine run
        this.tryToConnectToServiceAlreadyInRange(serviceUUID);
        // Adding the service to  be found in the future
        this.serviceToLookFor = serviceUUID;
    }

    private void tryToConnectToServiceAlreadyInRange(UUID serviceUUID)
    {

    }

    /**
     * This stops the discovery for the service given previously
     * trough calling {@link #startSDPDiscoveryForServiceWithUUID(UUID, SdpBluetoothServiceClient)}.
     *
     * This however does not end any existing connections and does not cancel the overall service discovery
     * refer to {@link #stopDiscovery()}
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void stopSDPDiscovery()
    {
        Log.d(TAG, "End service discovery for service with UUID " + serviceToLookFor);
        // removing from list of services
        this.serviceToLookFor = null;
        // removing the client from the list
        serviceClient = null;
        this.stopDiscovery();
    }

    //
    //  ----------  "server" side ----------
    //

    /**
     *
     * @param serviceName
     * @param serviceUUID
     * @param server
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void startSDPService(String serviceName, UUID serviceUUID, SdpBluetoothServiceServer server)
    {
        Log.d(TAG, "startSDPService: starting service : " + serviceName + " | " + serviceUUID);
        WifiP2pServiceInfo serviceRecords = generateServiceRecords(serviceName, serviceUUID);
        manager.addLocalService(channel, serviceRecords, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(TAG, "startSDPService: service successfully added : " + serviceName + " | " + serviceUUID);
                runningService = serviceRecords;
            }

            @Override
            public void onFailure(int arg0)
            {
                Utils.logReason("startSDPService: service could not be added : " + serviceName + " | " + serviceUUID, arg0);
            }
        });

    }

    public void stopSDPService()
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

    private WifiP2pDnsSdServiceInfo generateServiceRecords(String serviceName, UUID serviceUUID)
    {
        Log.d(TAG, "generateServiceRecords: generating service records");
        Map<String, String> serviceRecords = new HashMap<>();
        serviceRecords.put(SERVICE_UUID, serviceUUID.toString());
        serviceRecords.put(SERVICE_NAME, serviceName);
        Log.d(TAG, "generateServiceRecords: generated records : " + serviceRecords);

        return WifiP2pDnsSdServiceInfo.newInstance(serviceName, "_presence._tcp", serviceRecords);
    }

    protected void onServiceDiscovered(WifiP2pDevice device, Map<String, String> record, String fullDomain)
    {
        Log.d(TAG, "onServiceDiscovered: discovered a new Service on " + Utils.getRemoteDeviceString(device));
        String uuidAsString = record.get(SERVICE_UUID);
        if(uuidAsString == null)
        {
            Log.d(TAG, "onServiceDiscovered: no uuid in service records");
            // no uuid in record
            return;
        }
        Log.d(TAG, "onServiceDiscovered: received UUID " + uuidAsString);
        UUID serviceUUID;
        try
        {
           serviceUUID = UUID.fromString(uuidAsString); // TODO what happens if not a uuid??

        }
        catch (IllegalArgumentException e){
            Log.d(TAG, "onServiceDiscovered: UUID was not formatted correctly");
            return;
        }
        ArrayList<WifiP2pDevice> serviceDevices = this.discoveredServices.get(serviceUUID);

        // Adding service to list
        if (serviceDevices == null)
        {
            serviceDevices = new ArrayList<>();
        }
        if (!serviceDevices.contains(device))
        {
            Log.d(TAG, "onServiceDiscovered: new service discovered, adding to list");
            serviceDevices.add(device);
            this.discoveredServices.put(serviceUUID, serviceDevices);
            this.connectIfServiceAvailableAndNoConnectedAlready(device, serviceUUID);
        }
        else
        {
            Log.d(TAG, "onServiceDiscovered: service already discovered, wont add again");
        }
    }




    private void connectIfServiceAvailableAndNoConnectedAlready(WifiP2pDevice device, UUID serviceUuid)
    {
        if(serviceToLookFor == null){
            // no discovery started, UUID is null
            // no need to try to connect
            return;
        }

        if (serviceToLookFor.equals(serviceUuid))
        {
            serviceClient.onServiceDiscovered(device.deviceAddress, serviceUuid);
            if (serviceClient.shouldConnectTo(device.deviceAddress, serviceUuid))
            {
                this.tryToConnect(device);
            }
        }
    }

    private void tryToConnect(WifiP2pDevice device)
    {
        Log.d(TAG, "tryToConnect: trying to connect to  " + Utils.getRemoteDeviceString(device));
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        if (ContextCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Log.e(TAG, "tryToConnect: no permission, wont connect");
        }
        else{
        this.manager.connect(
                this.channel,
                config,
                new WifiP2pManager.ActionListener()
                {
                    @Override
                    public void onSuccess()
                    {
                        Log.d(TAG, "Successfully send connection request to "+ Utils.getRemoteDeviceString(device));
                    }

                    @Override
                    public void onFailure(int reason)
                    {
                        Utils.logReason("Failed sending connection request to " + Utils.getRemoteDeviceString(device), reason);
                    }
                }
        );}
    }

    public int getPortNumber()
    {
        return 7777;
    }
    
    public void onSocketConnected(InputStream is, OutputStream os)
    {
        Log.d(TAG, "onSocketConnected: Connection established");
    }

    public void onSocketConnectionStarted(TCPChannelMaker channelCreator)
    {
        AwaitSocketConenctionThread awaitSocketConenctionThread = new AwaitSocketConenctionThread(channelCreator, this);
        awaitSocketConenctionThread.start();
    }

}

package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect;

import static android.net.wifi.p2p.WifiP2pManager.BUSY;
import static android.net.wifi.p2p.WifiP2pManager.ERROR;
import static android.net.wifi.p2p.WifiP2pManager.NO_SERVICE_REQUESTS;
import static android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.Utils;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.SdpBluetoothEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceClient;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceServer;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpConnectorThreads.BluetoothClientConnector;

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
    private WifiP2pManager.Channel mChannel;
    /**
     * The wifi direct manager
     */
    private WifiP2pManager mManager;

    private DiscoveryThread serviceDiscovery;

    private static SdpWifiEngine instance;

    private HashMap<UUID, ArrayList<WifiP2pDevice>> discoveredServices = new HashMap<>();

    private final HashMap<UUID, WifiP2pServiceInfo> runningServices = new HashMap<>();

    private DiscoveryThread discoveryThread;

    /**
     *
     */
    /**
     * BroadcastReceiver to listen to Android System Broadcasts specified in the intentFilter
     */
    private WifiDirectStateChangeReceiver mWifiReceiver;

    private final ArrayList<UUID> servicesToLookFor = new ArrayList<>();
    private final HashMap<UUID, SdpBluetoothServiceClient> serviceClients = new HashMap<>();


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
        this.mManager = (WifiP2pManager) this.context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.mChannel = mManager.initialize(this.context, this.context.getMainLooper(), null);
        this.discoveryThread = new DiscoveryThread(mManager, mChannel, this);
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
        mWifiReceiver = new WifiDirectStateChangeReceiver(mManager, mChannel);
        this.context.registerReceiver(mWifiReceiver, intentFilter);
    }

    private void unregisterReceiver()
    {

    }

    //
    //  ---------- default wifip2p functions ----------
    //

    public void start()
    {
        this.registerReceiver();
    }

    public void stop()
    {
        this.unregisterReceiver();
        mManager.clearLocalServices(mChannel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                //
            }

            @Override
            public void onFailure(int reason)
            {
                    // cant do much here
                Utils.logReason("stop: could not remove services",  reason);
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
        if(this.discoveryThread.isDiscovering()){
            this.stopDiscovery();
        }
        this.discoveryThread.start();
    }

    public void stopDiscovery()
    {
       discoveryThread.cancel();
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
        if (this.isServiceAlreadyInDiscoveryList(serviceUUID))
        {
            Log.d(TAG, "startSDPDiscoveryForServiceWithUUID: Service discovery already running ");
            return;
        }

        //----------------------------------
        // NOTE : here the sequence actually matters, the service client needs to be se before
        // trying to find already discovered services. Else this wil lead to a null pointer
        // when trying to connect to a service.
        //----------------------------------

        // Adding the service client ot the list
        this.serviceClients.put(serviceUUID, serviceClient);
        // Trying to find service on devices hat where discovered earlier in the engine run
        this.tryToConnectToServiceAlreadyInRange(serviceUUID);
        // Adding the service to  be found in the future
        this.servicesToLookFor.add(serviceUUID);
    }

    private void tryToConnectToServiceAlreadyInRange(UUID serviceUUID)
    {
    }


    /**
     * Checks if the UUID is already in {@link #servicesToLookFor} list
     *
     * @param serviceUUID
     *         The UUID o look for
     *
     * @return false if the service is not in the list, else returns true
     */
    private boolean isServiceAlreadyInDiscoveryList(UUID serviceUUID)
    {
        for (UUID uuid : this.servicesToLookFor)
        {
            if (uuid.equals(serviceUUID))
            {
                return true;
            }
        }
        return false;
    }


    /**
     * This removes the service with he given UUID.
     * This means there wont be any Connections made to his service anymore
     * from this point on.
     *
     * Already established connections will stay and won be closed.
     *
     * Given that his removes the only / last service  which is looked for,
     * this will end the Bluetooth discovery process completely.
     * (Foremost o save battery).
     *
     * If all connections to a service should be closed please refer to
     * {@link SdpBluetoothEngine#disconnectFromServicesWithUUID(UUID)}
     *
     * @param serviceUUID
     *  The UUID of the service to sop connection to
     */
    public void stopSDPDiscoveryForServiceWithUUID(UUID serviceUUID)
    {
        Log.d(TAG, "End service discovery for service with UUID " + serviceUUID.toString());
        // removing from list of services
        this.servicesToLookFor.remove(serviceUUID);
        // removing the client from the list
        serviceClients.remove(serviceUUID);
    }

    public void disconnectFromServicesWithUUID(UUID serviceUUID)
    {
    }


    //
    //  ----------  "server" side ----------
    //
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public boolean startSDPService(String serviceName, UUID serviceUUID, SdpBluetoothServiceServer server)
    {
        Log.d(TAG, "startSDPService: starting service : " + serviceName + " | " + serviceUUID);
        WifiP2pServiceInfo serviceRecords = generateServiceRecords(serviceName, serviceUUID);
        mManager.addLocalService(mChannel, serviceRecords, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(TAG, "startSDPService: service successfully added : " + serviceName + " | " + serviceUUID);
                runningServices.put(serviceUUID,serviceRecords);
            }

            @Override
            public void onFailure(int arg0)
            {
               Utils.logReason("startSDPService: service could not be added : " + serviceName + " | " + serviceUUID , arg0);
            }
        });

        return true;
    }

    public void stopSDPService(UUID serviceUUID)
    {
        WifiP2pServiceInfo records;
        try{
             records  = runningServices.get(serviceUUID);
        }
        catch (NullPointerException e){
            Log.e(TAG, "stopSDPService: service with UUID " +  serviceUUID + " not available ");
            return;
        }
        if(records == null){
            return;
        }


        mManager.removeLocalService(mChannel, records , new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                runningServices.remove(serviceUUID);
                Log.d(TAG, "stopSDPService: service removed successfully ");

            }

            @Override
            public void onFailure(int reason)
            {
                Utils.logReason("stopSDPService: could not remove service ", reason);
            }
        });
    }

    private boolean isConnectionAlreadyEstablished(String deviceAddress, UUID serviceUUID)
    {
        Log.d(TAG, "isConnectionAlreadyEstablished: checking if there is a connection established");
        return false;
    }

    /**
     * Closes all connections / sockets to a service (specified by its UUID) running on this device.
     * Note that his just closes all current connections from clients to this device.
     * It does not to prevent the Service from accepting new connections from his point on.
     * <p>
     * To stop new connexions from being made use {@link SdpBluetoothEngine#stopSDPService(UUID)}
     *
     * @param serviceUUID
     *         The UUID of the service
     */
    public void disconnectFromClientsWithUUID(UUID serviceUUID)
    {
        Log.d(TAG, "disconnectFromClientsWithUUID: closing client connections to service " + serviceUUID);
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

    protected void onServiceDiscovered(WifiP2pDevice device, Map<String, String> record, String fullDomain){
        Log.d(TAG, "onServiceDiscovered: discovered a new Service on " + Utils.getRemoteDeviceString(device));
        UUID serviceUUID = UUID.fromString(record.get(SERVICE_UUID)); // TODO what happens if not a uuid??
        ArrayList<WifiP2pDevice> serviceDevices = this.discoveredServices.get(serviceUUID);

        // Adding service to list
        if(serviceDevices == null){
            serviceDevices = new ArrayList<>();
        }
        if(!serviceDevices.contains(device)){
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

    private void connectIfServiceAvailableAndNoConnectedAlready(WifiP2pDevice device, UUID serviceUuid){
        for (UUID uuidToLookFor : this.servicesToLookFor){
            if (uuidToLookFor.equals(serviceUuid)){
                serviceClients.get(serviceUuid).onServiceDiscovered(device.deviceAddress, serviceUuid);
            }
        }

    }
}

package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.sdpWifiDirectDiscovery;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import willi.boelke.servicedisoveryengine.serviceDiscovery.Utils;
import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;

/**
 * Discover nearby Wifi Direct / Bonjour Services.
 *
 * -----------------------------------------------
 * Call {@link #initialize(Context)} to initialize the engine.
 * after that it is ready to be used.
 * -----------------------------------------------
 * To Search for a specific Service a {@link ServiceDescription}
 * can be registered through
 * {@link #startSdpDiscoveryForService(ServiceDescription)}.
 * Several serves can be searched simultaneously by calling there methods with different
 * ServiceDescription`s. {@link #startSdpDiscoveryForService(ServiceDescription)}
 * wont return any services immediately.
 * To stop the search for a service call {@link #stopSDPDiscovery(ServiceDescription)}
 * -----------------------------------------------
 * After the services where registered the actual discovery process can be started by
 * calling {@link #startDiscovery()}, only after this point services will be discovered.
 * The discovery can be stopped {@link #stopDiscovery()} or started again as needed.
 * A Service discovery will run for 2.5 Minutes (that's no official number - i found that through 
 * a number of test and it may be different on other devices). 
 * -----------------------------------------------
 * To get notified about discoveries a listener needs to be registered. 
 * {@link #registerDiscoverListener(WifiServiceDiscoveryListener)} thi allows 
 * to asynchronous notify about discovered Services. 
 * Several listeners can  be registered at the same time.
 * Every listener wil get notified about every discovered serves.
 * Listeners may unregister by calling {@link #unregisterDiscoveryListener(WifiServiceDiscoveryListener)}
 * -----------------------------------------------
 * if the engine should notify about every service discovered 
 * {@link #notifyAboutEveryService(boolean)} can be called with `true`
 * from that moment on until it was called with `false` the engine will notify about
 * every discovered service even if it was not registered through {@link #startSDPService(ServiceDescription)}
 *
 * ------------------------------------------------
 * To stop the engine call {@link #stop()}
 *
 */
@SuppressLint("MissingPermission")
public class SdpWifiDirectDiscoveryEngine
{

    //
    //  ----------  static members  ----------
    //

    /**
     * The singleton instance
     */
    private static SdpWifiDirectDiscoveryEngine instance;

    //
    //  ----------  instance members ----------
    //

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    /**
     * Wifi direct channel
     */
    private final WifiP2pManager.Channel channel;
    /**
     * The wifi direct manager
     */
    private final WifiP2pManager manager;

    /**
     * This stores all devices where a service was discovered on
     * by the service description.
     * It is used to not notify listeners about the same service twice
     * Also it is intended to serve as a cache, to be able to connect to services
     * without having to run the service discovery again.
     * for that see {@link #checkIfServiceAlreadyHasBeenDiscovered(ServiceDescription)}
     *
     * It will be reset whenever the discovery is started again, to not keep
     * devices that are not in range anymore
     */
    private HashMap<ServiceDescription, ArrayList<WifiP2pDevice>> discoveredServices = new HashMap<>();

    /**
     * Reverence to the currently running discovery thread
     */
    private SdpWifiDiscoveryThread discoveryThread;

    /**
     * The UUID of the service to discover
     * this will be set in {@link #startSdpDiscoveryForService(ServiceDescription)}
     */
    private final ArrayList<ServiceDescription> servicesToLookFor = new ArrayList<>();

    private final HashMap<ServiceDescription, WifiP2pServiceInfo> runningServices = new HashMap<>();

    /**
     * List of all listeners who registered
     * using {@link #registerDiscoverListener(WifiServiceDiscoveryListener)}
     *
     * @see #unregisterDiscoveryListener(WifiServiceDiscoveryListener)
     */
    private final ArrayList<WifiServiceDiscoveryListener> discoveryListeners = new ArrayList<>();
    private boolean shouldNotifyAboutAll;


    //
    //  ----------  constructor and initialization ----------
    //

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public static SdpWifiDirectDiscoveryEngine initialize(Context context)
    {
        if (instance == null)
        {
            WifiP2pManager manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
            WifiP2pManager.Channel channel = manager.initialize(context, context.getMainLooper(), null);
            instance = new SdpWifiDirectDiscoveryEngine( manager, channel);
        }
        return instance;
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public static SdpWifiDirectDiscoveryEngine initialize( WifiP2pManager manager, WifiP2pManager.Channel channel)
    {
        if (instance == null)
        {
            instance = new SdpWifiDirectDiscoveryEngine(manager, channel);
        }
        return instance;
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public static SdpWifiDirectDiscoveryEngine getInstance()
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

    /**
     * Private singleton constructor, called by {@link #initialize(Context)}
     * or {@link #initialize( WifiP2pManager, WifiP2pManager.Channel)}
     * @param manager
     * @param channel
     */
    private SdpWifiDirectDiscoveryEngine(WifiP2pManager manager, WifiP2pManager.Channel channel)
    {
        this.manager = manager;
        this.channel = channel;
    }


    //
    //  ---------- default wifip2p functions ----------
    //

    /**
     * This stops the engine that means other devices cant find the advertised service anymore,
     * the discovery will stop and the services to search will be unregistered
     *
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void stop()
    {
        this.stopDiscovery();
        this.stopAllServices();
        channel.close();
    }

    /**
     * Clears all locally registered services
     *
     * @see #runningServices
     */
    private void stopAllServices()
    {
        // both don't seem to work reliable
        for(ServiceDescription description : runningServices.keySet()){
            stopSDPService(description);
        }

        this.manager.clearLocalServices(channel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                // Nothing to do here
            }

            @Override
            public void onFailure(int reason)
            {
                // Nothing to do here

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
        instance = null;
    }


    //
    //  ----------  service discovery start/stop----------
    //

    /**
     * This starts the discovery process, which will discover all nearby services
     * this is just the discovery process, to get notified about a service
     * it needs to be specified in {@link #startSdpDiscoveryForService(ServiceDescription)}
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void startDiscovery()
    {
        Log.d(TAG, "startDiscovery: staring discovery");
        this.discoveredServices = new HashMap<>();
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
        Log.d(TAG, "stopDiscovery: stopping discovery");
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
    public void startSdpDiscoveryForService(ServiceDescription description)
    {
        Log.d(TAG, "startSDPDiscoveryForService Starting service discovery for " + description);
        // Are we already looking for he service?
        this.servicesToLookFor.add(description);
    }

    /**
     * This stops the discovery for the service given previously
     * trough calling {@link #startSdpDiscoveryForService(ServiceDescription)} (UUID, SdpWifiPeer)}.
     *
     * This however does not end any existing connections and does not cancel the overall service discovery
     * refer to {@link #stopDiscovery()}
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void stopSDPDiscovery(ServiceDescription description)
    {
        Log.d(TAG, "stopSDPDiscovery End service discovery for service with " + description);
        this.servicesToLookFor.remove(description);
        this.stopDiscovery();
    }

    /**
     * Checks the Map of discovered services if it already contains the given service
     * if that's the case notifies all listeners about the discovered service
     * -----
     * This does not seem to work, since connection attempts to cached devices always
     * fail with an `error` code in the onFailure of WifiP2pManager#connect(),
     * Maybe there is a solution -but i couldn't find one (Wifi Direct on Android
     * sometimes is not documented very wel - especially in regards tro connecting several devices to one GO
     * and closing connections again. Soo it may be the case that there is a way  to make that work )
     * I would like to figure this one out, when there is some time at hand
     * This may be helpful:
     *  https://stackoverflow.com/questions/23713176/what-can-fail-wifip2pmanager-connect
     * @see #discoveredServices
     *
     * @param description
     * The description of the given service
     *
     * @deprecated
     * because it does not work just yet 
     */
    @Deprecated
    private void checkIfServiceAlreadyHasBeenDiscovered(ServiceDescription description)
    {
        Log.d(TAG, "checkIfServiceAlreadyHasBeenDiscovered: looking if service has been discovere and cached");
        for (ServiceDescription discoveredService : discoveredServices.keySet())
        {
            Log.d(TAG, "checkIfServiceAlreadyHasBeenDiscovered: comparing \n"
            + discoveredService + "\n"
            + description);
            if(discoveredService.equals(description)){
                if(discoveredServices.get(description) == null)
                {
                    Log.e(TAG, "checkIfServiceAlreadyHasBeenDiscovered: no devices for the given description");
                    break; // this should never be the case i think, but better be carefull
                }
                for ( WifiP2pDevice device : discoveredServices.get(description))
                {
                    Log.d(TAG, "checkIfServiceAlreadyHasBeenDiscovered: found service host, notify listener");
                    notifyOnServiceDiscovered(device, description);
                }
            }
        }
    }

    //
    //  ----------  "server" side ----------
    //

    /**
     *  This registers a new service, making it visible to other devices running a service discovery
     * // TODO maybe it would be useful to add make the service type changable ?
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void startSDPService(ServiceDescription description)
    {
        Log.d(TAG, "startSDPService: starting service : " + description);
        WifiP2pServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(description.getServiceUuid().toString(), "_presence._tcp", description.getServiceRecord());
        manager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(TAG, "startSDPService: service successfully added : " + description);
                runningServices.put(description, serviceInfo);
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
    public void stopSDPService(ServiceDescription description)
    {
        try
        {
            manager.removeLocalService(channel, this.runningServices.get(description), new WifiP2pManager.ActionListener()
            {
                @Override
                public void onSuccess()
                {
                    runningServices.remove(description);
                    Log.d(TAG, "stopSDPService: service removed successfully ");
                }

                @Override
                public void onFailure(int reason)
                {
                    Utils.logReason("stopSDPService: could not remove service ", reason);
                }
            });
        }catch (IllegalArgumentException e)
        {
            Log.e(TAG, "stopSDPService: tried to stop service which is not regsitered");
        }
    }


    //
    //  ----------  listeners ----------
    // TODO define a abstract class for this and the bluetooth discovery engine, this belongs into there for example

    /**
     * Registers a {@link WifiServiceDiscoveryListener} to be notified about
     * discovered devices and services
     *
     * @see #unregisterDiscoveryListener(WifiServiceDiscoveryListener) ()
     *
     * @param listener
     *  implementation of then listener interface
     */
    public void registerDiscoverListener(WifiServiceDiscoveryListener listener){

        if(discoveryListeners.contains(listener)){
            return;
        }
        this.discoveryListeners.add(listener);
    }

    public void unregisterDiscoveryListener(WifiServiceDiscoveryListener listener){
        discoveryListeners.remove(listener);
    }

    /**
     * Calls {@link WifiServiceDiscoveryListener#onServiceDiscovered(WifiP2pDevice, ServiceDescription)}
     * on all listeners in {@link #discoveryListeners}
     *
     * @param device
     *  the discovered device
     * @param description
     *  the description of the discovered service
     */
    private void notifyOnServiceDiscovered(WifiP2pDevice device, ServiceDescription description){
        for (WifiServiceDiscoveryListener listener : this.discoveryListeners){
            //Notify client about discovery
            listener.onServiceDiscovered(device, description);
        }
    }


    //
    //  ----------  SdpWifiServiceDiscoverListener interface ----------
    //

    /**
     * Called by the {@link SdpWifiDiscoveryThread}
     *
     * @param device
     *         The device which hosts the service
     * @param serviceRecord
     *         The services TXT Records
     * @param fullDomain
     *         The services domain
     */
    protected void onServiceDiscovered(WifiP2pDevice device, Map<String, String> serviceRecord, String fullDomain)
    {
        Log.d(TAG, "onServiceDiscovered: discovered a new Service on " + Utils.getRemoteDeviceString(device));
        ServiceDescription description = new ServiceDescription(serviceRecord);
        Log.d(TAG, "onServiceDiscovered: received " + description);

        //--- updating discovered services list ---//

        boolean newService = false;

        if(this.discoveredServices.containsKey(description)
        && this.discoveredServices.get(description).contains(device))
        {
            //--- service already cached ---//
            Log.d(TAG, "onServiceDiscovered: already knew the service");
        }
        else if(! this.discoveredServices.containsKey(description))
        {
            //--- service and device new ---//
            Log.d(TAG, "onServiceDiscovered: discovered new service");
            ArrayList<WifiP2pDevice> serviceDevices = new ArrayList<>();
            serviceDevices.add(device);
            this.discoveredServices.put(description, serviceDevices);
            newService = true;
        }
        else
        {
            //--- device new ---//
            Log.e(TAG, "onServiceDiscovered: knew the service, but this is a new host");
            discoveredServices.get(description).add(device);
            newService = true;
        }

        if(newService || shouldNotifyAboutAll)
        {
            onNewServiceDiscovered(device, description);
        }
    }

    /**
     * Called by {@link #onServiceDiscovered(WifiP2pDevice, Map, String)}
     * When the service was not already in {@link #discoveredServices}
     * @param device
     * the remote device hosting the service
     * @param description
     * the description of the discovered service
     */
    private void onNewServiceDiscovered(WifiP2pDevice device, ServiceDescription description)
    {
        Log.d(TAG, "onNewServiceDiscovered: got service, checking if looked for");
        if(servicesToLookFor.contains(description)){
            Log.d(TAG, "onNewServiceDiscovered: service is registered for search notify listeners");
            notifyOnServiceDiscovered(device, description);
        }
    }

    protected void onDiscoveryFinished()
    {
        Log.d(TAG, "onDiscoveryFinished: the discovery process finished");
    }

    /**
     * The group owner intent specifies the degree in which the local
     * peer should / wants to become group owner.
     * The intent is a number between 0 and 15, with 15 meaning
     * these device should become the GO and 0 tha this
     * should be the client. If this is not set, android will determine the
     * GO.
     *
     * @param intent
     */
    public void setGroupOwnerIntent(int intent){
        if(intent > 15 || intent < 0){
            return;
        }

    }
    

    /**
     * Setting this to true will notify ALL registered listener
     * about every discovered service.
     * Even though the service was not registered through {@link #startSdpDiscoveryForService}.
     * This can be deactivate again at any time by calling this method again with false
     */
    public void notifyAboutEveryService(boolean shouldNotifyAboutAll)
    {
        this.shouldNotifyAboutAll = shouldNotifyAboutAll;
    }
}
package willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery;

import static android.net.wifi.p2p.WifiP2pManager.BUSY;
import static android.net.wifi.p2p.WifiP2pManager.ERROR;
import static android.net.wifi.p2p.WifiP2pManager.NO_SERVICE_REQUESTS;
import static android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.util.Log;
import willi.boelke.services.serviceDiscovery.ServiceDescription;
import androidx.annotation.RequiresPermission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import willi.boelke.services.serviceDiscovery.ServiceDiscoveryEngine;


/**
 * Discover nearby Wifi Direct / Bonjour Services.
 * <p>
 * Searching for services<br>
 * ------------------------------------------------------------<br>
 * Services are advertised as "_presence._tcp", so it is expected to use TCP as transport.
 * The Service discovery currently doesn't support other protocols
 * to connect to other kinds of services like network printers.
 * This may be added in a future release.
 * <p>
 * Searching for services<br>
 * ------------------------------------------------------------<br>
 * To Search for a specific Service a {@link ServiceDescription}
 * can be registered through
 * {@link #startDiscoveryForService(ServiceDescription)}.
 * Several serves can be searched simultaneously by calling there methods with different
 * ServiceDescription`s. {@link #startDiscoveryForService(ServiceDescription)}
 * wont return any services immediately.
 * To stop the search for a service call {@link #stopDiscoveryForService(ServiceDescription)}
 * <p>
 * Service advertisement<br>
 * ------------------------------------------------------------<br>
 * Services can be advertised using {@link #startService(ServiceDescription)}
 * the service will stay advertised until {@link #stopService(ServiceDescription)}
 * or {@link #stop()} is called.
 * For a service to be Discoverable the device also needs to run the discovery.
 * (TODO this is weird behavior look into that more...could not find much in documenatation)
 * <p>
 * Discovery<br>
 * ------------------------------------------------------------<br>
 * After the services where registered the actual discovery process can be started by
 * calling {@link #startDiscovery()}, only after this point services will be discovered.
 * The discovery can be stopped {@link #stopDiscovery()} or started again as needed.
 * A Service discovery will run for 2.5 Minutes (that's no official number - i found that through
 * a number of test and it may be different on other devices).
 * <p>
 * Listener / Observer<br>
 * ------------------------------------------------------------<br>
 * To get notified about discoveries a listener needs to be registered.
 * {@link #registerDiscoverListener(WifiServiceDiscoveryListener)} thi allows
 * to asynchronous notify about discovered Services.
 * Several listeners can  be registered at the same time.
 * Every listener wil get notified about every discovered serves.
 * Listeners may unregister by calling {@link #unregisterDiscoveryListener(WifiServiceDiscoveryListener)}
 * if the engine should notify about every service discovered
 * {@link #notifyAboutAllServices(boolean)} can be called with `true`
 * from that moment on until it was called with `false` the engine will notify about
 * every discovered service even if it was not registered through {@link #startService(ServiceDescription)}
 * <p>
 * Stop the engine<br>
 * ------------------------------------------------------------<br>
 * To stop the engine call {@link #stop()}
 */
@SuppressLint("MissingPermission")
public class WifiDirectServiceDiscoveryEngine extends ServiceDiscoveryEngine
{

    //
    //  ----------  static members  ----------
    //

    /**
     * The singleton instance
     */
    private static WifiDirectServiceDiscoveryEngine instance;

    //
    //  ----------  instance members ----------
    //

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private final String SERVICE_TYPE = "_presence._tcp";

    /**
     * Wifi direct channel
     */
    private WifiP2pManager.Channel channel;
    /**
     * The wifi direct manager
     */
    private WifiP2pManager manager;

    /**
     * This stores all devices where a service was discovered on
     * by the service description.
     * It is used to not notify listeners about the same service twice
     * Also it is intended to serve as a cache, to be able to connect to services
     * without having to run the service discovery again.
     * for that see {@link #checkIfServiceAlreadyHasBeenDiscovered(ServiceDescription)}
     * <p>
     * It will be reset whenever the discovery is started again, to not keep
     * devices that are not in range anymore
     */
    private HashMap<ServiceDescription, ArrayList<WifiP2pDevice>> discoveredServices = new HashMap<>();

    /**
     * Reverence to the currently running discovery thread
     */
    private WifiDiscoveryThread discoveryThread;

    /**
     * Keeps all started services (WifiP2pServiceInfo)
     * when they where started.
     * This is to remove them again at a later point.
     *
     * @see #startService(ServiceDescription)
     * @see #stopService(ServiceDescription)
     */
    private final HashMap<ServiceDescription, WifiP2pServiceInfo> runningServices = new HashMap<>();

    /**
     * List of all listeners who registered
     * using {@link #registerDiscoverListener(WifiServiceDiscoveryListener)}
     *
     * @see #unregisterDiscoveryListener(WifiServiceDiscoveryListener)
     */
    private final ArrayList<WifiServiceDiscoveryListener> discoveryListeners = new ArrayList<>();


    //  ----------  constructor and initialization ----------
    //

    /**
     * Returns the singleton instance of the engine
     *
     * @return instance
     */
    public static WifiDirectServiceDiscoveryEngine getInstance()
    {
        if (instance == null)
        {
            instance = new WifiDirectServiceDiscoveryEngine();
        }
        return instance;
    }

    /**
     * Private singleton constructor
     */
    private WifiDirectServiceDiscoveryEngine()
    {
        // empty private constructor
    }

    /**
     * Starts the engine
     * needs to eb called before doing anything else
     * before starting the engine all calls will be returned immediately.
     *
     * @param context
     *         the application context
     *
     * @see #stop()
     */
    @Override
    public void start(Context context)
    {
        WifiP2pManager tempManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        WifiP2pManager.Channel tempChannel = tempManager.initialize(context, context.getMainLooper(), null);
        start(tempManager, tempChannel);
    }

    /**
     * Starts the engine
     * needs to eb called before doing anything else
     * before starting the engine all calls will be returned immediately.
     * <p>
     * Alternatively use {@link #start(Context)}
     *
     * @param manager
     *         the wifi manager service
     * @param channel
     *         a channel
     *
     * @see #stop()
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void start(WifiP2pManager manager, WifiP2pManager.Channel channel)
    {
        if (isRunning())
        {
            Log.e(TAG, "start: engine already started");
        }
        this.manager = manager;
        this.channel = channel;
        engineRunning = true;
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


    /**
     * This stops the engine, the discovery will be stopped
     * and all registered services will be unregistered
     */
    @Override
    public void stop()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "engine not started - wont stop");
            return;
        }
        this.stopDiscovery();
        this.stopAllServices();
        channel.close();
    }


    //
    //  ----------  service discovery start/stop----------
    //

    /**
     * This starts the discovery process, which will discover all nearby services
     * this is just the discovery process, to get notified about a service
     * it needs to be specified in {@link #startDiscoveryForService(ServiceDescription)}
     */
    public void startDiscovery()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "startDiscovery: engine not running - wont discover");
            return;
        }
        this.discoveredServices.clear();
        this.stopDiscovery();
        Log.d(TAG, "startDiscovery: staring discovery");
        this.discoveryThread = new WifiDiscoveryThread(manager, channel, this);
        this.discoveryThread.start();
    }

    /**
     * Stops the discovery process
     */
    public void stopDiscovery()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "stopDiscovery: engine not running - wont stop");
            return;
        }
        //--- if the discovery thread is running -> cancel it ---//
        Log.d(TAG, "stopDiscovery: stopping discovery");
        if (discoveryThread != null && discoveryThread.isDiscovering())
        {
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
    @Override
    public void startDiscoveryForService(ServiceDescription description)
    {
        super.startDiscoveryForService(description);
    }

    @Override
    protected void onNewServiceToDiscover(ServiceDescription description)
    {
        // checkIfServiceAlreadyHasBeenDiscovered(description);
    }

    /**
     * This stops the discovery for the service given previously
     * trough calling {@link #startDiscoveryForService(ServiceDescription)} (UUID, SdpWifiPeer)}.
     * <p>
     * This however does not end any existing connections and does not cancel the overall service discovery
     * refer to {@link #stopDiscovery()}
     */
    @Override
    public void stopDiscoveryForService(ServiceDescription description)
    {
        if(!engineIsNotRunning())
            super.stopDiscoveryForService(description);
    }

    @Override
    protected void onServiceRemoveFromDiscovery(ServiceDescription description)
    {
        // nothing to do here s
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
     * https://stackoverflow.com/questions/23713176/what-can-fail-wifip2pmanager-connect
     *
     * @param description
     *         The description of the given service
     *
     * @see #discoveredServices
     * @deprecated because it does not work just yet
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
            if (discoveredService.equals(description))
            {
                if (discoveredServices.get(description) == null)
                {
                    Log.e(TAG, "checkIfServiceAlreadyHasBeenDiscovered: no devices for the given description");
                    break; // this should never be the case i think, but better be carefull
                }
                for (WifiP2pDevice device : discoveredServices.get(description))
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
     * This registers a new service, making it visible to other devices running a service discovery
     * // TODO maybe it would be useful to add make the service type changeable ?
     */
    public void startService(ServiceDescription description)
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "startSdpService: engine not running - wont start service");
            return;
        }
        Log.d(TAG, "startSdpService: starting service : " + description);
        WifiP2pServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(description.getServiceName(), SERVICE_TYPE, description.getServiceRecord());
        manager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(TAG, "startSdpService: service successfully added : " + description);
                runningServices.put(description, serviceInfo);
            }

            @Override
            public void onFailure(int arg0)
            {
                logReason(TAG, "startSdpService: service could not be added : " + description, arg0);
            }
        });
    }

    /**
     * This stops the advertisement of the service,
     * other peers who are running a service discovery wont
     */
    public void stopService(ServiceDescription description)
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "startSDPService: engine not running - wont stop service");
            return;
        }
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
                    logReason(TAG, "stopSDPService: could not remove service ", reason);
                }
            });
        }
        catch (IllegalArgumentException e)
        {
            Log.e(TAG, "stopSDPService: tried to stop service which is not regsitered");
        }
    }

    /**
     * Clears all locally registered services
     *
     * @see #runningServices
     */
    private void stopAllServices()
    {
        // both don't seem to work reliable
        for (ServiceDescription description : runningServices.keySet())
        {
            stopService(description);
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

    //
    //  ----------  listeners ----------
    //

    /**
     * Registers a {@link WifiServiceDiscoveryListener} to be notified about
     * discovered devices and services
     *
     * @param listener
     *         implementation of then listener interface
     *
     * @see #unregisterDiscoveryListener(WifiServiceDiscoveryListener)
     */
    public void registerDiscoverListener(WifiServiceDiscoveryListener listener)
    {

        if (discoveryListeners.contains(listener))
        {
            return;
        }
        this.discoveryListeners.add(listener);
    }

    /**
     * Removes a discovery listener from the list
     * the removed listener wont be notified anymore
     *
     * @param listener
     *         the listener to be removed
     */
    public void unregisterDiscoveryListener(WifiServiceDiscoveryListener listener)
    {
        discoveryListeners.remove(listener);
    }

    /**
     * Calls {@link WifiServiceDiscoveryListener#onServiceDiscovered(WifiP2pDevice, ServiceDescription)}
     * on all listeners in {@link #discoveryListeners}
     *
     * @param device
     *         the discovered device
     * @param description
     *         the description of the discovered service
     */
    private void notifyOnServiceDiscovered(WifiP2pDevice device, ServiceDescription description)
    {
        Log.d(TAG, "notifyOnServiceDiscovered: notifying " + this.discoveryListeners.size() + " listeners");
        ArrayList<WifiServiceDiscoveryListener> expiredListeners = new ArrayList<>();
        for (WifiServiceDiscoveryListener listener : this.discoveryListeners)
        {
            //Notify client about discovery
            try
            {
                listener.onServiceDiscovered(device, description);
            }
            catch (NullPointerException e)
            {
                Log.e(TAG, "notifyOnServiceDiscovered: listener was null -remove");
                expiredListeners.add(listener);
            }
        }
        for (WifiServiceDiscoveryListener listener : expiredListeners)
        {
            unregisterDiscoveryListener(listener);
        }
    }

    //
    //  ----------  SdpWifiServiceDiscoverListener interface ----------
    //

    /**
     * Called by the {@link WifiDiscoveryThread}
     * Since the {@link WifiDiscoveryThread} may discover services 2 or more times
     * this method checks the incoming services through a cache kept in {@link #discoveredServices}
     * which will be kept until a new discovery is started.
     * If the Pair {service, device} is not yet cached
     * {@link #onNewServiceDiscovered(WifiP2pDevice, ServiceDescription)} be called.
     *
     * @param device
     *         The device which hosts the service
     * @param serviceRecord
     *         The services TXT Records
     * @param registrationType
     *         The service type
     */
    protected void onServiceDiscovered(WifiP2pDevice device, Map<String, String> serviceRecord, String registrationType, String instanceName)
    {
        Log.d(TAG, "onServiceDiscovered: ----discovered a new Service on " + device + "----");
        if (!registrationType.equals(SERVICE_TYPE + ".local."))
        {
            Log.e(TAG, "onServiceDiscovered: not a " + SERVICE_TYPE + " service - stop");
            return;
        }
        ServiceDescription description = new ServiceDescription(instanceName, serviceRecord);
        //--- updating discovered services list ---//

        boolean newService = false;

        if (this.discoveredServices.containsKey(description)
                && this.discoveredServices.get(description).contains(device))
        {
            //--- service already cached ---//
            Log.d(TAG, "onServiceDiscovered: already knew the service");
        }
        else if (!this.discoveredServices.containsKey(description))
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
            Log.d(TAG, "onServiceDiscovered: knew the service, but this is a new host");
            Objects.requireNonNull(discoveredServices.get(description)).add(device);
            newService = true;
        }
        if (newService)
        {
            onNewServiceDiscovered(device, description);
        }
    }

    /**
     * Called by {@link #onServiceDiscovered(WifiP2pDevice, Map, String, String)}
     * When the service was not already in {@link #discoveredServices}
     *
     * @param device
     *         the remote device hosting the service
     * @param description
     *         the description of the discovered service
     */
    private void onNewServiceDiscovered(WifiP2pDevice device, ServiceDescription description)
    {
        Log.d(TAG, "onNewServiceDiscovered: got service, checking if looked for");
        if (servicesToLookFor.contains(description) || notifyAboutAllServices)
        {
            Log.d(TAG, "onNewServiceDiscovered: service is registered for search notify listeners");
            notifyOnServiceDiscovered(device, description);
        }
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


    private void discoverService() {

        /*
         * Register listeners for DNS-SD services. These are callbacks invoked
         * by the system when a service is actually discovered.
         */

        manager.setDnsSdResponseListeners(channel,

                (instanceName, registrationType, srcDevice) ->
                {

                },
                (fullDomainName, record, device) ->
                {

                });

        // After attaching listeners, create a service request and initiate
        // discovery.
        WifiP2pDnsSdServiceRequest serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        manager.addServiceRequest(channel, serviceRequest,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "onSuccess: successfully set service requests ");
                    }

                    @Override
                    public void onFailure(int arg0) {
                        Log.e(TAG, "error settings service requests " + arg0) ;
                    }
                });

        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "onSuccess: successfully set service requests ");
            }

            @Override
            public void onFailure(int arg0) {
                Log.e(TAG, "error settings service requests " + arg0) ;
            }
        });
    }
}
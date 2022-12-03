package willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery;

import static android.net.wifi.p2p.WifiP2pManager.BUSY;
import static android.net.wifi.p2p.WifiP2pManager.ERROR;
import static android.net.wifi.p2p.WifiP2pManager.NO_SERVICE_REQUESTS;
import static android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import willi.boelke.services.serviceDiscovery.ServiceDescription;
import willi.boelke.services.serviceDiscovery.ServiceDiscoveryEngine;


/**
 * Discover nearby Bonjour / mDNS services for wifi direct.
 * <p>
 * <h2>Searching for services</h2>
 * This engine will search for Bonjour / mDNS service on the local link.
 * Service can be specified through {@link ServiceDescription}s.
 * The engine will filter services by the service type as specified in the
 * service description.
 * While the types have to match the service TXT record
 * (given through the ServiceDescriptions) service record wont
 * be used to filter services.
 * <p>
 * <h3>Service Descriptions</h3>
 * To specify the searched service a {@link ServiceDescription}
 * needs to be registered through {@link #startDiscoveryForService(ServiceDescription)}.
 * Several services can be searched simultaneously with different
 * ServiceDescription`s. {@link #startDiscoveryForService(ServiceDescription)}
 * wont return any services immediately. To stop the search for a
 * service call {@link #stopDiscoveryForService(ServiceDescription)}.
 * <p>
 * <h2>Service advertisement</h2>
 * Services can be advertised using {@link #startService(ServiceDescription)}
 * the service will stay advertised until {@link #stopService(ServiceDescription)}
 * or {@link #stop()} is called.
 * For a service to be Discoverable the device also needs to run the discovery.
 * A service will be advertised using the {@link ServiceDescription}s
 * service name as instance name and the type as service type,
 * The Service Record will be published as TXT record in the sense of (m)DNS
 * and available for potential clients.
 * <p>
 * <h2>Discovery</h2>
 * After the services where registered the actual discovery process can be started by
 * calling {@link #startDiscovery()}, only after this point services will be discovered.
 * The discovery can be stopped {@link #stopDiscovery()} or started again as needed.
 * A Service discovery will run for 2.5 Minutes (that's no official number - i found that through
 * a number of test and it may be different on other devices).
 * <p>
 * <h2>Listener</h2>
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
 * <h2>Starting and stopping the engine</h2>
 * To use the engine {@link #start(Context)} or {@link #start(Context, WifiP2pManager, WifiP2pManager.Channel)}
 * are needed to be called. The engine will then start and check if necessary hard- and software
 * are available. To verify that the engine started {@link #isRunning()} can be called and should return true.
 * To stop the engine call {@link #stop()}
 * The engine wont react to any other calls as long as it hasn't been successfully started.
 */
@SuppressLint("MissingPermission")
public class WifiDirectServiceDiscoveryEngine extends ServiceDiscoveryEngine implements WifiDirectServiceDiscovery
{

    //
    //  ----------  static members  ----------
    //

    /**
     * The singleton instance
     */
    private static WifiDirectServiceDiscoveryEngine instance;

    /**
     * That the "TLD" established by Bonjour/mDNS for link local
     * mDNS domain names.
     */
    private static final String LOCAL_TLD = ".local.";

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
    private WifiP2pManager.Channel channel;
    /**
     * The wifi direct manager
     */
    private WifiP2pManager manager;

    /**
     * This stores all devices where a service was discovered on
     * by the service description.
     * It is used to not notify listeners about the same service twice
     * <p>
     * It will be reset whenever the discovery is started again, to not keep
     * devices that are not in range anymore
     */
    private final HashMap<ServiceDescription, ArrayList<WifiP2pDevice>> discoveredServices = new HashMap<>();


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
     * Returns the singleton instance of the WifiDirectServiceDiscoveryEngine.
     *
     * @return The singleton instance of the discovery engine
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
     * @return true if the engine was successfully started, else returns false
     *
     * @see #stop()
     */
    @Override
    public boolean start(Context context)
    {
        WifiP2pManager tempManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        WifiP2pManager.Channel tempChannel = tempManager.initialize(context, context.getMainLooper(), null);
        return start(context, tempManager, tempChannel);
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
    @Override
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public boolean start(Context context, WifiP2pManager manager, WifiP2pManager.Channel channel)
    {
        if (isRunning())
        {
            Log.e(TAG, "start: engine already started");
            return true;
        }
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT))
        {
            Log.e(TAG, "start: Wifi Direct not supported");
            return false;
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
        if (manager == null)
        {
            Log.e(TAG, "start:Wifi Service not available");
            return false;
        }
        if (channel == null)
        {
            Log.e(TAG, "start: cant init WiFi direct");
            return false;
        }
        this.manager = manager;
        this.channel = channel;
        engineRunning = true;
        return true;
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
    @Override
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
        discoverService();
    }

    /**
     * Stops the discovery process
     */
    @Override
    public void stopDiscovery()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "stopDiscovery: engine not running - wont stop");
            return;
        }
        //--- if the discovery thread is running -> cancel it ---//
        Log.d(TAG, "stopDiscovery: stopping discovery");
        cancelServiceDiscovery();
    }

    //
    //  ----------  "client" side ----------
    //

    /**
     * Starts looking for the service specified with
     * the same ServiceType
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
        // nothing to do here
    }

    @Override
    protected void onServiceRemoveFromDiscovery(ServiceDescription description)
    {
        // nothing to do here
    }

    //
    //  ----------  "server" side ----------
    //

    /**
     * This registers a new service, making it visible to other devices running a service discovery
     */
    @Override
    public void startService(ServiceDescription description)
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "startSdpService: engine not running - wont start service");
            return;
        }
        Log.d(TAG, "startSdpService: starting service : " + description);
        WifiP2pServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
                description.getInstanceName(),
                description.getServiceType(),
                description.getTxtRecord());
        manager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(TAG, "startSdpService: service successfully added : " + description);
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
    @Override
    public void stopService(ServiceDescription description)
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "startSDPService: engine not running - wont stop service");
            return;
        }
        try
        {
            WifiP2pServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
                    description.getInstanceName(),
                    description.getServiceType(),
                    description.getTxtRecord());

            manager.removeLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener()
            {
                @Override
                public void onSuccess()
                {
                    Log.d(TAG, "stopService: service removed successfully ");
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
            Log.e(TAG, "stopSDPService: tried to stop service which is not registered");
        }
    }

    /**
     * Clears all locally registered services
     */
    private void stopAllServices()
    {

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
     * discovered devices and services.
     *
     * @param listener
     *         implementation of then listener interface
     *
     * @see #unregisterDiscoveryListener(WifiServiceDiscoveryListener)
     */
    @Override
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
     *
     * @see #registerDiscoverListener(WifiServiceDiscoveryListener)
     */
    @Override
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


    /**
     * Checks the incoming services through a cache kept in {@link #discoveredServices}
     * which will be kept until a new discovery is started.
     * If the Pair {service, device} is not yet cached
     * the listeners will be notified
     *
     * @param device
     *         The device which hosts the service
     * @param serviceRecord
     *         The services TXT Records
     * @param registrationType
     *         The service type
     */
    protected synchronized void onServiceDiscovered(WifiP2pDevice device, Map<String, String> serviceRecord, String registrationType, String instanceName)
    {
        Log.d(TAG, "onServiceDiscovered: ----discovered a new Service on " + device + "----");

        //--- updating discovered services list ---//

        registrationType = registrationType.replace(LOCAL_TLD, "");
        ServiceDescription description = new ServiceDescription(instanceName, serviceRecord, registrationType);

        boolean newService = false;

        //--- service already cached ---//

        if (this.discoveredServices.containsKey(description) &&
                this.discoveredServices.get(description).contains(device))
        {
            Log.d(TAG, "onServiceDiscovered: already knew the service");
        }

        //--- service and device new ---//

        else if (!this.discoveredServices.containsKey(description))
        {
            Log.d(TAG, "onServiceDiscovered: discovered new service");
            ArrayList<WifiP2pDevice> serviceDevices = new ArrayList<>();
            serviceDevices.add(device);
            this.discoveredServices.put(description, serviceDevices);
            newService = true;
        }

        //--- device new ---//

        else
        {
            Log.d(TAG, "onServiceDiscovered: knew the service, but this is a new host");
            Objects.requireNonNull(discoveredServices.get(description)).add(device);
            newService = true;
        }

        //--- notify listeners ? ---//

        if (newService && isServiceBeingLockedFor(description))
        {
            notifyOnServiceDiscovered(device, description);
        }
    }

    /**
     * Deceives whether or now a service is "being looked for"
     * meaning either on {@link #servicesToLookFor} or
     * {@link #notifyAboutAllServices} == true
     *
     * @param description#The description of the service to check
     * @return
     * true if the service is being searched for, else return false
     */
    private boolean isServiceBeingLockedFor(ServiceDescription description)
    {
        if(notifyAboutAllServices)
        {
            return true;
        }
        for (ServiceDescription serviceDescription : servicesToLookFor)
        {
            String searchedServiceType = serviceDescription.getServiceType();
            Log.e(TAG, "isServiceBeingLockedFor: " + searchedServiceType + " / " + description.getServiceType());
            if (serviceDescription.equals(description))
            {
                return true;
            }
        }
        return false;
    }


    /**
     * Just here to quickly log if an error occurred in the wifi api
     *
     * @param tag
     *         The classes logging tag (since this method can be used in other classes of this package)
     * @param msg
     *         The log message
     * @param arg0
     *         The error code as provided by the onFailure callback
     */
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


    //
    //  ---------- no threaded discovery (test) ----------
    //

    /**
     * One service discovery will
     * cues two independent callbacks to be called
     * this HashMap caches the values from the first one
     * to retrieve them when teh second arrives
     */
    private final HashMap<String, Map<String, String>> tmpRecordCache = new HashMap<>();

    private void discoverService()
    {
        setupDiscoveryCallbacks();
        runServiceDiscovery();
    }

    private void runServiceDiscovery()
    {

        Log.d(TAG, "startDiscovery: started discovery");

        //--- adding service requests (again) ---//

        //----------------------------------
        // NOTE : Bonjour services are used,
        // so WifiP2pDnsSdServiceRequests are
        // used here.
        //----------------------------------
        manager.clearServiceRequests(this.channel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                manager.addServiceRequest(channel, WifiP2pDnsSdServiceRequest.newInstance(), new WifiP2pManager.ActionListener()
                {
                    @Override
                    public void onSuccess()
                    {
                        //--- starting the service discovery (again) ---//
                        manager.discoverServices(channel, new WifiP2pManager.ActionListener()
                        {
                            @Override
                            public void onSuccess()
                            {
                                Log.d(TAG, "Started service discovery");
                            }

                            @Override
                            public void onFailure(int code)
                            {
                                Log.d(TAG, "failed to start service discovery");
                            }
                        });
                    }

                    @Override
                    public void onFailure(int code)
                    {
                        Log.d(TAG, "failed to add service discovery request");
                    }
                });
            }

            @Override
            public void onFailure(int reason)
            {
                Log.d(TAG, "failed to add service discovery request");
            }
        });
    }

    /**
     * This interrupts the service thread
     * and unregisters all service requests.
     * the service discovery will stop.
     */
    protected void cancelServiceDiscovery()
    {
        Log.d(TAG, "cancel: canceling service discovery");
        manager.clearServiceRequests(channel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                // nothing to do here
            }

            @Override
            public void onFailure(int reason)
            {
                WifiDirectServiceDiscoveryEngine.logReason(TAG, "DiscoveryThread: cancel: could not clear service requests ", reason);
            }
        });
        Log.d(TAG, "cancel: canceled service discovery");
    }

    /**
     * Setting up the callbacks which wil be called when
     * a service was discovered, proving the TXT records and other
     * service information.
     * <p>
     * This only needs to be set up once, at the Thread start.
     * It shouldn't called while lopping (re-starting service discovery).
     */
    private void setupDiscoveryCallbacks()
    {
        tmpRecordCache.clear();
        Log.d(TAG, "setupDiscoveryCallbacks: setting up callbacks");

        //--- TXT Record listener ---//

        WifiP2pManager.DnsSdTxtRecordListener txtListener = (fullDomain, txtRecord, device) ->
                tmpRecordCache.put(device.deviceAddress, txtRecord);

        //--- Service response listener - gives additional service info ---//

        WifiP2pManager.DnsSdServiceResponseListener servListener = (instanceName, registrationType, device) ->
        {
            Map<String, String> receivedTxtRecord = tmpRecordCache.get(device.deviceAddress);
            if (receivedTxtRecord == null)
            {
                receivedTxtRecord = new HashMap<>();
            }
            onServiceDiscovered(device, receivedTxtRecord, registrationType, instanceName);
        };

        //--- setting the listeners ---//

        manager.setDnsSdResponseListeners(channel, servListener, txtListener);
    }

    /**
     * Set the engine to notify about every service, even though it
     * doesn't match the descriptions set through {@link #startDiscoveryForService(ServiceDescription)}.
     * This can be disabled and enabled at will and will apply to all future discoveries.
     *
     * @param all
     *         boolean - true to notify about all services, false to just notify about the ones
     */
    @Override
    public void notifyAboutAllServices(boolean all)
    {
        super.notifyAboutAllServices(all);
    }

}
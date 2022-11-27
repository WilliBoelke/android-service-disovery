package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import willi.boelke.services.serviceDiscovery.ServiceDescription;
import willi.boelke.services.serviceDiscovery.ServiceDiscoveryEngine;

/**
 * <h1>BluetoothServiceDiscoveryEngine</h1>
 * The BluetoothDiscoveryEngine is the parent class for a bluetooth service
 * discovery. This implements several methods to started and execute a
 * discovery and enables listeners to subscribe to get notified about
 * important events (device discovery and service discovery).
 * <p>
 * It provides public methods to start and stop a device discovery.
 * A service discovery will be conducted automatically. Depending
 * <p>
 * The premise here is :
 * -The device discovery will be started.<br>
 * -A service discovery will be conducted on all discovered devices
 * depending on the implementation in the subclasses {@link BluetoothServiceDiscoveryVTwo}
 * and/or {@link BluetoothServiceDiscoveryVOne}.<br>
 * -Listeners will be notified about discovered services and devices/peers.<br>
 * -Methods are provided to register a number (0-n) specific services to get notified about.<br>
 * -Methods are provided to get notified about each and every service which is discovered.<br>
 * -The above methods can be combined.<br>
 *
 * <p>
 * <h2>Starting the engine</h2>
 * The engine needs to be started by calling {@link #start(Context)}
 * or {@link #start(Context, BluetoothAdapter)} this will also enable bluetooth
 * on the device.
 * Call {@link #stop()} to stop the engine and all running discoveries.
 *
 * <p>
 * <h2>Discover Services</h2>
 * Services can be discovered using
 * {@link #startDiscoveryForService(ServiceDescription)}. For a service
 * to be found it is mandatory to run a device discovery using
 * {@link #startDeviceDiscovery()}.
 * <p>
 * The time before services are discovered is dependant on the later
 * implementation in the subclasses
 * <p>
 * The device discovery can be started before or after a service
 * discovery was started. as long as a service discovery runs and
 * was not ended via {@link #stopDiscoveryForService(ServiceDescription)}}
 * services will be discovered on all subsequently and previously d
 * is covered devices.
 * <p>
 * If a general service discovery is required and no specific
 * UUIDs / ServiceDescriptions are known
 * {@link #notifyAboutAllServices(boolean)} can be used.
 *
 * <p>
 * <h2>UUIDs and Service descriptions</h2>
 * For registering specific services {@link ServiceDescription}s are used.
 * Service descriptions will generate a UUID. For services which are
 * registered and a description is known the fitting description will be
 * returned. If the option is used to get notified about all services it
 * is likely that no description for the given UUID is known to the engine.
 * The engine then will return a description only containing the UUID
 * without any further information.
 *
 * <p>
 * <h2>Caching of devices and Services</h2>
 * Note: other bluetooth devices and their services will be cashed, it is possible
 * that a service will be found on a bluetooth devices which either moved out of
 * range or stopped accepting clients. This can happen when a Service is added after
 * a service discovery was conducted. {@link #refreshNearbyServices()} will refresh
 * the cached services. {@link #startDeviceDiscovery()} will refresh
 * both cached devices and services.
 *
 * <p>
 * <h2>Listener</h2>
 * To get notified about discovered services and their host devices a
 * {@link BluetoothServiceDiscoveryListener} needs to be registered using
 * {@link #registerDiscoverListener(BluetoothServiceDiscoveryListener)}.
 * A listener can be unregistered using  {@link #unregisterDiscoveryListener(BluetoothServiceDiscoveryListener)}.
 * <p>
 * Several listeners can be registered simultaneously alas all listeners will be notified about
 * the same services. If {@link #notifyAboutAllServices(boolean)} all sisters will be equally
 * notified about all services.
 *
 * <p>
 * <h2>Sequence Example</h2>
 * <pre>
 *  ┌───────────┐                           ┌───────────────────────────┐
 *  │Application│                           │SdpBluetoothDiscoveryEngine│
 *  └─┬─────────┘                           └──────────────────┬────────┘
 *   ┌┴┐                                                       |
 *   │ │                                                       │
 *   │ │                                                       │
 *   │ │             registerDiscoverListener(this)           ┌┴┐
 *   │ │ ────────────────────────────────────────────────────>│ │
 *   │ │                                                      │ │
 *   │ │                                                      └┬┘
 *   │ │                                                       │
 *   │ │                       start()                        ┌┴┐
 *   │ │ ────────────────────────────────────────────────────>│ │
 *   │ │                                                      │ │
 *   │ │                                                      └┬┘
 *   │ │                                                       │
 *   │ │         startSDPDiscoveryForService(description)     ┌┴┐
 *   │ │ ────────────────────────────────────────────────────>│ │
 *   │ │                                                      │ │
 *   │ │                                                      └┬┘
 *   │ │                                                       │
 *   │ │                  startDeviceDiscovery()              ┌┴┐
 *   │ │ ────────────────────────────────────────────────────>│ │
 *   │ │                                                      │ │
 *   │ │            onPeerDiscovered(BluetoothDevice)         │ │
 *   │ │ <─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │ │
 *   │ │                                                      │ │
 *   │ │           onPeerDiscovered(BluetoothDevice)          │ │
 *   │ │<─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│ │
 *   │ │                                                      │ │
 *   │ │         onServiceDiscovered(device, description)     │ │
 *   │ │<─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│ │
 *   │ │                                                      └┬┘
 *   │ │                                                       │
 *   │ │                          stop()                      ┌┴┐
 *   │ │ ────────────────────────────────────────────────────>│ │
 *   └┬┘                                                      └┬┘
 *    │                                                        │
 * ------------------------------------------------------------
 * </pre>
 *
 * @author WilliBoelke
 */
public abstract class BluetoothServiceDiscoveryEngine extends ServiceDiscoveryEngine implements BluetoothServiceDiscovery
{
    //
    //  ----------  instance variables  ----------
    //

    /**
     * Classname for logging only
     */
    private final String TAG = this.getClass().getSimpleName();

    /**
     * Application context
     */
    private Context context;

    /**
     * The bluetooth adapter, will be set to
     * the default adapter at initialisation
     */
    protected BluetoothAdapter bluetoothAdapter;

    /**
     * This keeps track of all bluetooth devices which are discovered.
     * A bluetoothDevice will be added on ACTION_FOUND (if no yet on this list)
     * and removed on ACTION_ACL_DISCONNECTED.
     */
    protected final ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();

    /**
     * BroadcastReceiver listening at discovered devices intent
     * {@link DeviceFoundReceiver}
     *
     * @see #registerReceivers()
     * @see #unregisterReceivers()
     */
    private final BroadcastReceiver foundDeviceReceiver;

    /**
     * BroadcastReceiver listens on  {@link BluetoothDevice#ACTION_UUID} intent
     * {@link UUIDFetchedReceiver}
     *
     * @see #registerReceivers()
     * @see #unregisterReceivers()
     */
    private final BroadcastReceiver fetchedUuidReceiver;

    /**
     * BroadcastReceiver to log bluetooth api events
     * {@link BluetoothBroadcastReceiver}
     *
     * @see #registerReceivers()
     * @see #unregisterReceivers()
     */
    private final BroadcastReceiver bluetoothReceiver;

    /**
     * Determines whether discovered service UUIDs
     * should only be evaluated the way the where received
     * or also in a bytewise revered format
     * This is to workaround a issue which causes UUIDs
     * to be received in a little endian format.
     * <p>
     * It is true by default, to ensure everything working correctly.
     * But can be disabled be the user.
     * <p>
     *
     * @see BluetoothServiceDiscoveryEngine#shouldCheckLittleEndianUuids(boolean)
     * @see ServiceDescription#getBytewiseReverseUuid()
     * @see BluetoothServiceDiscoveryEngine#notifyListenersIfServiceIsAvailable(BluetoothDevice, Parcelable[])
     * @see BluetoothServiceDiscoveryEngine#notifyListenersAboutServices(BluetoothDevice, Parcelable[])
     */
    private boolean checkLittleEndianUuids = true;

    /**
     * List of all listeners who registered
     * using {@link #registerDiscoverListener(BluetoothServiceDiscoveryListener)}
     *
     * @see #unregisterReceivers()
     */
    private final ArrayList<BluetoothServiceDiscoveryListener> bluetoothDiscoveryListeners = new ArrayList<>();

    //
    //  ----------  initialisation and setup ----------
    //


    /**
     * Private constructor initializing the singleton
     */
    protected BluetoothServiceDiscoveryEngine()
    {
        this.foundDeviceReceiver = new DeviceFoundReceiver(this);
        this.fetchedUuidReceiver = new UUIDFetchedReceiver(this);
        this.bluetoothReceiver = new BluetoothBroadcastReceiver(this);
    }

    /**
     * Registers broadcast receiver
     *
     * @see UUIDFetchedReceiver
     * @see DeviceFoundReceiver
     * @see BluetoothBroadcastReceiver
     */
    private void registerReceivers()
    {
        IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter actionUUID = new IntentFilter(BluetoothDevice.ACTION_UUID);
        IntentFilter debuggingFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        context.registerReceiver(bluetoothReceiver, debuggingFilter);
        context.registerReceiver(fetchedUuidReceiver, actionUUID);
        context.registerReceiver(foundDeviceReceiver, discoverDevicesIntent);
    }

    /**
     * Stops the engine and resets the singleton instance to "null"
     * this is mostly used for testing
     */
    protected abstract void teardownEngine();


    /**
     * Starts the discovery engine
     * a context needs to be supplied
     * Optionally a bluetooth adapter can also be
     * specified by calling {@link #start(Context, BluetoothAdapter)}
     *
     * @param context
     *         the application context
     *
     * @return
     */
    @Override
    public boolean start(Context context)
    {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null)
        {
            Log.e(TAG, "Bluetooth adapter was null, the device probably does not support bluetooth - engine wont start");
            return false;
        }
        return start(context, adapter);
    }

    /**
     * Starts the engine
     *
     * @return
     */
    @Override
    public boolean start(Context context, BluetoothAdapter adapter)
    {
        if (adapter == null)
        {
            Log.e(TAG, "start: Bluetooth adapter was null, the device probably does not support bluetooth - engine wont start");
            return false;
        }
        if (!adapter.isEnabled())
        {
            Log.e(TAG, "start: Bluetooth not enabled");
            return false;
        }
        Log.d(TAG, "start: starting engine");
        this.bluetoothAdapter = adapter;
        this.context = context;
        this.enableBluetooth();
        this.registerReceivers();
        this.engineRunning = true;
        return true;
    }

    //
    //  ----------  shutdown and teardown ----------
    //

    /**
     * Stops the service discovery
     * Registered services will be cleared
     * and listeners will be removed
     */
    @Override
    public void stop()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "stop: engine is not running - wont stop");
            return;
        }
        unregisterReceivers();
        stopDeviceDiscovery();
        this.servicesToLookFor = new ArrayList<>();
        this.engineRunning = false;
    }

    private void unregisterReceivers()
    {
        try
        {
            this.context.unregisterReceiver(fetchedUuidReceiver);
        }
        catch (IllegalArgumentException e)
        {
            Log.e(TAG, "unregisterReceivers: fetchedUuidReceiver was not registered ");
        }
        try
        {
            this.context.unregisterReceiver(foundDeviceReceiver);
        }
        catch (IllegalArgumentException e)
        {
            Log.e(TAG, "unregisterReceivers: foundDeviceReceiver was not registered ");
        }
        try
        {
            this.context.unregisterReceiver(bluetoothReceiver);
        }
        catch (IllegalArgumentException e)
        {
            Log.e(TAG, "unregisterReceivers: foundDeviceReceiver was not registered ");
        }
    }

    //
    //  ----------  standard (no sdp specific) bluetooth----------
    //

    /**
     * Enables Bluetooth on the device, if bluetooth is available and not enabled
     * yet.
     */
    private void enableBluetooth()
    {
        Log.d(TAG, "enableBluetooth: enabling Bluetooth");
        if (!bluetoothAdapter.isEnabled())
        {
            // Enable Bluetooth
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            context.sendBroadcast(enableBluetoothIntent);
        }
    }

    /**
     * Starts discovering other devices
     * NOTE : devices not services, use
     * {@link #startDiscoveryForService(ServiceDescription)}
     * to start a service discovery.
     * <p>
     * A device discovery has to run before services will be discovered.
     * <p>
     * This also will cause the cached devices to be reset, meaning
     * a listener may will be notified about a peer / client already
     * known to him again.
     */
    @Override
    public boolean startDeviceDiscovery()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "startDeviceDiscovery: engine is not running - wont start");
            return false;
        }
        // resetting discovered devices
        this.discoveredDevices.clear();
        this.onDeviceDiscoveryRestart();
        return internalRestartDiscovery();
    }

    /**
     * This just (re) starts the device discovery
     * without clearing the device list
     * publicly its used through {@link #startDeviceDiscovery()}
     * though in some Internal cases the discovery may need to be restarted without clearing the list
     *
     * @return true if the discovery was started, else returns false
     */
    protected boolean internalRestartDiscovery()
    {

        Log.d(TAG, "startDeviceDiscovery: start looking for other devices");
        if (bluetoothAdapter.isDiscovering())
        {
            Log.d(TAG, "startDeviceDiscovery: already scanning, restarting ... ");
            this.bluetoothAdapter.cancelDiscovery();
        }

        Log.d(TAG, "startDeviceDiscovery: enabled ? = " + bluetoothAdapter.isEnabled());
        if (this.bluetoothAdapter.startDiscovery())
        {
            Log.d(TAG, "startDeviceDiscovery: started device discovery");
            return true;
        }
        else
        {
            Log.e(TAG, "startDeviceDiscovery: could not start Discovery");
            return false;
        }
    }

    /*
     * Ends the bluetooth device
     * discovery
     */
    @Override
    public void stopDeviceDiscovery()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "stopDeviceDiscovery: engine is not running - wont start");
            return;
        }
        bluetoothAdapter.cancelDiscovery();
    }

    //
    //  ----------  sdp specific methods ----------
    //

    /**
     * Stars looking for the specified service.
     * <p>
     * This will make the engine connect to devices running this service
     * which where already discovered (and are still in range) and to
     * Devices tha will be discovered from now on (given that the bluetooth discovery is enabled)
     * <p>
     * The service discovery will run till
     * {@link BluetoothServiceDiscoveryEngine#stopDiscoveryForService(ServiceDescription)
     * with the same UUID is called,  no matter hwo many devies will be disovered ill then.
     * (Or to make i short, this wont stop afer the first connecion was made)
     *
     * @param serviceUUID
     *         The UUID of the service to connect o
     */
    @Override
    public void startDiscoveryForService(ServiceDescription description)
    {
        super.startDiscoveryForService(description);
    }

    /**
     * called when a new service was added to through
     * {@link #startDiscoveryForService(ServiceDescription)}
     *
     * @param description
     *         the description of the new service
     */
    @Override
    protected void onNewServiceToDiscover(ServiceDescription description)
    {
        this.tryToFindAlreadyDiscoveredServices(description);
    }

    /**
     * This removes the service with he given UUID.
     * This means there wont be any Connections made to his service anymore
     * from this point on.
     * <p>
     * Already established connections will stay and won be closed.
     * <p>
     * Given that his removes the only / last service  which is looked for,
     * this will end the Bluetooth discovery process completely.
     * (Foremost o save battery).
     *
     * @param description
     *         The service description
     */
    @Override
    public void stopDiscoveryForService(ServiceDescription description)
    {
        super.stopDiscoveryForService(description);
    }

    @Override
    protected void onServiceRemoveFromDiscovery(ServiceDescription description)
    {
        this.cancelDiscoveryIfNothingToLookFor();
    }

    /**
     * Checks if {@link #servicesToLookFor} is empty
     * and cancels the discovery in that case.
     * <p>
     * This should be called always when removing something from the list,
     * to minimize battery drain and improve performance.
     */
    private void cancelDiscoveryIfNothingToLookFor()
    {
        if (engineIsNotRunning())
        {
            Log.d(TAG, "cancelDiscoveryIfNothingToLookFor: engine not running -- wont cancel discovery");
            return;
        }
        if (this.servicesToLookFor.isEmpty())
        {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    /**
     * This will start a refreshing process
     * of all nearby services.
     * This also will cause the device discovery to stop.
     * <p>
     * Calling {@link #startDeviceDiscovery()} while this is running is not recommended.
     */
    @Override
    public void refreshNearbyServices()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "refreshNearbyServices: engine is not running - wont start");
            return;
        }
        Log.d(TAG, "refreshNearbyServices: start refreshing");
        this.bluetoothAdapter.cancelDiscovery();
        this.onRefreshStarted();
        requestServiceFromDiscoveredDevices();
    }

    /**
     * Iterates over {@link #discoveredDevices} and
     * fetches the UUIDs of all devices.
     */
    protected void requestServiceFromDiscoveredDevices()
    {
        for (BluetoothDevice deviceInRange : this.discoveredDevices)
        {
            Log.d(TAG, "requestServiceFromDiscoveredDevices: for " + deviceInRange);
            deviceInRange.fetchUuidsWithSdp();
        }
    }


    //
    //  ----------  listeners ----------
    //

    /**
     * Registers a {@link BluetoothServiceDiscoveryListener} to be notified about
     * discovered devices and services
     *
     * @param listener
     *         implementation of then listener interface
     *
     * @see #unregisterDiscoveryListener(BluetoothServiceDiscoveryListener)
     */
    @Override
    public void registerDiscoverListener(BluetoothServiceDiscoveryListener listener)
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "registerDiscoverListener: engine is not running - wont start");
            return;
        }
        if (bluetoothDiscoveryListeners.contains(listener))
        {
            Log.d(TAG, "registerDiscoverListener: listener already registered");
            return;
        }
        Log.d(TAG, "registerDiscoverListener: new listener registered");
        this.bluetoothDiscoveryListeners.add(listener);
    }

    @Override
    public void unregisterDiscoveryListener(BluetoothServiceDiscoveryListener listener)
    {
        bluetoothDiscoveryListeners.remove(listener);
    }

    /**
     * Calls {@link BluetoothServiceDiscoveryListener#onServiceDiscovered(BluetoothDevice, ServiceDescription)}
     * on all listeners in {@link #bluetoothDiscoveryListeners}
     *
     * @param device
     *         the discovered device
     * @param description
     *         the description of the discovered service
     */
    protected void notifyOnServiceDiscovered(BluetoothDevice device, ServiceDescription description)
    {
        for (BluetoothServiceDiscoveryListener lister : this.bluetoothDiscoveryListeners)
        {
            //Notify client about discovery
            lister.onServiceDiscovered(device, description);
        }
    }

    /**
     * Calls {@link BluetoothServiceDiscoveryListener#onPeerDiscovered(BluetoothDevice)}
     * on all listeners in {@link #bluetoothDiscoveryListeners}
     *
     * @param device
     *         the discovered device
     */
    protected void notifyOnPeerDiscovered(BluetoothDevice device)
    {
        for (BluetoothServiceDiscoveryListener lister : this.bluetoothDiscoveryListeners)
        {
            //Notify client about discovery
            lister.onPeerDiscovered(device);
        }
    }

    //
    //  ---------- abstract methods to be overwritten in subclasses ----------
    //

    /**
     * This should be called when a new device was discovered
     * <p>
     * The device will be added to {@link #discoveredDevices} if it was not yet.
     * and notify the {@link #bluetoothDiscoveryListeners} about a new device.
     *
     * @param device
     *         The discovered device
     *
     * @see DeviceFoundReceiver
     */
    protected abstract void onDeviceDiscovered(BluetoothDevice device);

    /**
     * This will be called when the device discovery stops,
     * either because of a timeout (12 sec after started)
     * or due to a manual stop.
     */
    protected abstract void onDeviceDiscoveryFinished();

    /**
     * This will be called when UUIDs of a device were fetched.
     *
     * @param device
     *         The host device
     * @param uuidExtra
     *         The service UUIDs
     */
    protected abstract void onUuidsFetched(BluetoothDevice device, Parcelable[] uuidExtra);

    /**
     * This will be calle whenever the device discovery was started
     * through {@link #startDeviceDiscovery()}
     */
    protected abstract void onDeviceDiscoveryRestart();

    /**
     * Called whenever the manual refresh is started through {@link #refreshNearbyServices()}
     */
    protected abstract void onRefreshStarted();


    //
    //  ---------- notify listeners ----------
    //


    /**
     * Notifies listeners about every discovered service
     *
     * @param device
     *         the device
     * @param uuidExtra
     *         the discovered service UUIDs
     */
    protected void notifyListenersAboutServices(BluetoothDevice device, Parcelable[] uuidExtra)
    {
        for (Parcelable pUuid : uuidExtra)
        {
            UUID uuid = ((ParcelUuid) pUuid).getUuid();
            Log.d(TAG, "notifyListenersAboutServices: checking uuid " + uuid);
            ServiceDescription description = new ServiceDescription("", new HashMap<>(), ""); // empty description
            description.overrideUuidForBluetooth(uuid);

            // overriding with registered service description
            if (this.servicesToLookFor.contains(description))
            {
                Log.d(TAG, "notifyListenersAboutServices: found uuid " + uuid);
                description = servicesToLookFor.get(servicesToLookFor.indexOf(description));
            }
            else if (checkLittleEndianUuids)
            {
                description.overrideUuidForBluetooth(description.getBytewiseReverseUuid());
                if (this.servicesToLookFor.contains(description))
                {
                    Log.d(TAG, "notifyListenersAboutServices: found reversed uuid " + uuid);
                    description = servicesToLookFor.get(servicesToLookFor.indexOf(description));
                }
            }
            description.overrideUuidForBluetooth(description.getBytewiseReverseUuid());
            this.notifyOnServiceDiscovered(device, description);
        }
    }

    /**
     * Notifies all listeners only if a service is available that is searched for
     *
     * @param device
     *         the host device
     * @param uuidExtra
     *         the service uuids on the given device
     */
    protected void notifyListenersIfServiceIsAvailable(BluetoothDevice device, Parcelable[] uuidExtra)
    {
        if (this.notifyAboutAllServices)
        {
            Log.d(TAG, "notifyListenersIfServiceIsAvailable: notifying out all services:\n ---- Service found on " + device + "----");
            this.notifyListenersAboutServices(device, uuidExtra);
            return;
        }

        for (Parcelable pUuid : uuidExtra)
        {
            UUID uuid = ((ParcelUuid) pUuid).getUuid();
            Log.d(TAG, "notifyListenersIfServiceIsAvailable: checking uuid " + uuid);
            for (ServiceDescription serviceToLookFor : this.servicesToLookFor)
            {
                if ((uuid.equals(serviceToLookFor.getServiceUuid())) ||
                        (this.checkLittleEndianUuids && uuid.equals(serviceToLookFor.getBytewiseReverseUuid())))
                {
                    Log.d(TAG, "notifyListenersIfServiceIsAvailable: \n ---- Service found on " + device + "----");
                    this.notifyOnServiceDiscovered(device, serviceToLookFor);
                }
            }
        }
    }

    /**
     * This checks if the service was discovered previously and if
     * it is still in range.
     *
     * @param description
     *         Description of the service
     */
    private void tryToFindAlreadyDiscoveredServices(ServiceDescription description)
    {
        Log.d(TAG, "tryToFindAlreadyDiscoveredServices: checking if " + description + " was discovered before ");

        // iterating through devices already discovered
        for (BluetoothDevice device : this.discoveredDevices)
        {
            // The devices geUuids() may return null
            try
            {
                // Looking for each UUID on the device and if it matches open a connection
                for (ParcelUuid pUuid : device.getUuids())
                {
                    UUID uuid = pUuid.getUuid();
                    if (uuid.equals(description.getServiceUuid()) || (this.checkLittleEndianUuids && uuid.equals(description.getBytewiseReverseUuid())))
                    {
                        notifyOnServiceDiscovered(device, description);
                    }
                }
            }
            catch (NullPointerException e)
            {
                Log.e(TAG, "tryToConnectToServiceAlreadyInRange: we have no uuids of his device " + device);
            }
        }
    }


    //
    //  ---------- config ----------
    //

    /**
     * On some devices service uuids will be
     * received in a little endian format.
     * The engine will by default reverse UUIDs and chek them as well
     * <p>
     * Set this to `false` to disable this behaviour.
     *
     * @param checkLittleEndianUuids
     *         determines whether little endian UUIDs should be checked or not
     */
    @Override
    public void shouldCheckLittleEndianUuids(boolean checkLittleEndianUuids)
    {
        this.checkLittleEndianUuids = checkLittleEndianUuids;
    }
}

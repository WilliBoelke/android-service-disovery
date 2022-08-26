package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothDiscovery;

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
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.Utils;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.SdpException;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;

/**
 * The SdpBluetoothDiscoveryEngine is the main controller the Android Bluetooth-SDP API.
 * It allows to start a discovery for a (1-n) services simultaneously.
 * <p>
 * This is a singleton class, before accessing {@link #getInstance()} this needs to be initialized
 * by calling {@link #initialize(Context, BluetoothAdapter)} or {@link #initialize(Context)}
 * <p>
 * The engine needs to be started by calling {@link #start()} this will also enable bluetooth
 * on the device.
 * Call {@link #stop()} to stop the engine and all running discoveries.
 * <p>
 * Services can be discovered using {@link #startSDPDiscoveryForService(ServiceDescription)}
 * For a service to be found it is mandatory to run a device discovery using 
 * {@link #startDeviceDiscovery()}.
 * <p>
 * The device discovery will run for ~12 seconds, after that all discovered devices 
 * will be queried for the Services available on them.
 * <p>
 * The device discovery can be started before or after a service discovery was started.
 * as long as a service discovery runs and was not ended via
 * {@link #stopSDPDiscoveryForService(ServiceDescription)}} services will be discovered 
 * on all subsequently and previously discovered devices.
 * <p>
 * Note: other bluetooth devices and their services will be cashed, it is possible 
 * that a service will be found on a bluetooth devices which either moved out of 
 * range or stopped accepting clients.
 * <p>
 * To get notified about discovered services and their host devices a 
 * {@link BluetoothServiceDiscoveryListener} needs to be registered using
 * {@link #registerDiscoverListener(BluetoothServiceDiscoveryListener)}.
 * A listener can be unregistered using  {@link #unregisterDiscoveryListener(BluetoothServiceDiscoveryListener)}.
 *
 * <pre>
 * --------------------------------------------------------------------------------
 *  ┌────┐                                           ┌───────────────────────────┐
 *  │Peer│                                           │SdpBluetoothDiscoveryEngine│
 *  └─┬──┘                                           └─────────────┬─────────────┘
 *   ┌┴┐                   initialize(Context)                    ┌┴┐
 *   │ │ ────────────────────────────────────────────────────────>│ │
 *   │ │                                                          └┬┘
 *   │ │                                                           │
 *   │ │                                                           │
 *   │ │              registerDiscoverListener(this)              ┌┴┐
 *   │ │ ────────────────────────────────────────────────────────>│ │
 *   │ │                                                          │ │
 *   │ │                                                          └┬┘
 *   │ │                                                           │
 *   │ │                                                           │
 *   │ │                         start()                          ┌┴┐
 *   │ │ ────────────────────────────────────────────────────────>│ │
 *   │ │                                                          │ │
 *   │ │                                                          └┬┘
 *   │ │                                                           │
 *   │ │                                                           │
 *   │ │     startSDPDiscoveryForService(ServiceDescription)      ┌┴┐
 *   │ │ ────────────────────────────────────────────────────────>│ │
 *   │ │                                                          │ │
 *   │ │                                                          └┬┘
 *   │ │                                                           │
 *   │ │                                                           │
 *   │ │                  startDeviceDiscovery()                  ┌┴┐
 *   │ │ ────────────────────────────────────────────────────────>│ │
 *   │ │                                                          │ │
 *   │ │                                                          │ │
 *   │ │            onPeerDiscovered(BluetoothDevice)             │ │
 *   │ │ <─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │ │
 *   │ │                                                          │ │
 *   │ │            onPeerDiscovered(BluetoothDevice)             │ │
 *   │ │ <─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │ │
 *   │ │                                                          │ │
 *   │ │                                                          │ │
 *   │ │                                                          │ │
 *   │ │onServiceDiscovered(BluetoothDevice, ServiceDescription)  │ │
 *   │ │<─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│ │
 *   │ │                                                          └┬┘
 *   │ │                                                           │
 *   │ │                                                           │
 *   │ │                          stop()                          ┌┴┐
 *   │ │ ────────────────────────────────────────────────────────>│ │
 *   │ │                                                          │ │
 *   │ │                                                          │ │
 *   └┬┘                                                          └┬┘
 *    │                                                            │
 *    │                                                            │
 * --------------------------------------------------------------------------------
 * </pre>
 *
 * @author WilliBoelke
 */
public class SdpBluetoothDiscoveryEngine
{
    //
    //  ---------- static members ----------
    //
    /**
     * Instance of the class following the singleton pattern
     */
    private static SdpBluetoothDiscoveryEngine instance;

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
    private final Context context;

    /**
     * The bluetooth adapter, will be set to
     * the default adapter at initialisation
     */
    private final BluetoothAdapter bluetoothAdapter;

    /**
     * This keeps track of all bluetooth devices which are discovered.
     * A bluetoothDevice will be added on ACTION_FOUND (if no yet on this list)
     * and removed on ACTION_ACL_DISCONNECTED.
     */
    private final ArrayList<BluetoothDevice> discoveredDevices =  new ArrayList<>();

    /**
     * Stores UUIDs of services which should be discovered
     * (and connected to)
     */
    private final ArrayList<ServiceDescription> servicesToLookFor =  new ArrayList<>();

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
     *
     * It is true by default, to ensure everything working correctly.
     * But can be disabled be the user.
     *
     * // Todo find a better solution
     *
     * @see SdpBluetoothDiscoveryEngine#shouldCheckLittleEndianUuids(boolean)
     * @see ServiceDescription#getBytewiseReverseUuid()
     */
    private boolean checkLittleEndianUuids = true;

    /**
     * List of all listeners who registered 
     * using {@link #registerDiscoverListener(BluetoothServiceDiscoveryListener)}
     *
     * @see #unregisterReceivers()
     */
    private final ArrayList<BluetoothServiceDiscoveryListener> discoveryListeners = new ArrayList<>();

    //
    //  ----------  initialisation and setup ----------
    //

    /**
     * Initializes the SdpBluetoothDiscoveryEngine with the given context and
     * the default bluetooth adapter.
     * After initialisation the instance can be obtained by calling {@link SdpBluetoothDiscoveryEngine#getInstance()}.
     *
     * A not default bluetooth adapter can be used by calling {@link SdpBluetoothDiscoveryEngine#initialize(Context, BluetoothAdapter)}.
     *
     * @param context
     *  the app context
     * @return
     *  the created (or already existing) instance of the BluetoothDiscoveryEngine
     */
    public static SdpBluetoothDiscoveryEngine initialize(Context context)
    {
        if (instance == null)
        {
            instance = new SdpBluetoothDiscoveryEngine(context, BluetoothAdapter.getDefaultAdapter());
        }
        return instance;
    }

    /**
     * Initializes the singleton instance of the SdpBluetoothDiscoveryEngine with app context and a
     * BluetoothAdapter.
     *
     * @param context
     * The App context to register receivers
     * @param adapter
     *  A Bluetooth Adapter, if the default adapter ({@link BluetoothAdapter#getDefaultAdapter()})
     *  is set to be used this can be skipped and just {@link SdpBluetoothDiscoveryEngine#initialize(Context)}
     *  needs to be called.
     * @return
     *  the created (or already existing) instance of the BluetoothDiscoveryEngine
     */
    public static SdpBluetoothDiscoveryEngine initialize(Context context, BluetoothAdapter adapter)
    {
        if (instance == null)
        {
            instance = new SdpBluetoothDiscoveryEngine(context, adapter);
        }
        return instance;
    }

    /**
     * Returns the singleton instance of the BluetoothDiscoveryEngine.
     * Requires {@link #initialize(Context)} or {@link #initialize(Context, BluetoothAdapter)}
     * to have been called prior.
     *
     * This method will not create a new instance if the engine was not yet initialized.
     * It will return `null` instead.
     *
     * @return
     * The instance of the BluetoothDiscoveryEngine created by calling {@link #initialize(Context)}
     * else returns `null`
     */
    public static SdpBluetoothDiscoveryEngine getInstance()
    {
        if (instance == null)
        {
            Log.e("SdpBluetoothDiscoveryEngine", "getInstance: the engine was not initialized");
        }
        return instance;
    }


    /**
     * Private constructor initializing the singleton {@link #instance}
     * @param context
     * app context
     * @param adapter
     * a bluetooth adapter
     */
    private SdpBluetoothDiscoveryEngine(Context context, BluetoothAdapter adapter)
    {
        this.context = context;
        this.bluetoothAdapter = adapter;
        this.foundDeviceReceiver = new DeviceFoundReceiver(this);
        this.fetchedUuidReceiver = new UUIDFetchedReceiver(this);
        this.bluetoothReceiver = new BluetoothBroadcastReceiver(this);
    }

    /**
     * Registers broadcast receiver
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
     * Starts the engine
     */
    public void start()
    {
        if (this.bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter was null, the device probably does not support bluetooth - engine wont start");
            return;
        }
        this.enableBluetooth();
        this.registerReceivers();
    }

    //
    //  ----------  shutdown and teardown ----------
    //


    /**
     * Stops the service discovery
     */
    public void stop()
    {
        unregisterReceivers();
        stopDeviceDiscovery();
    }


    /**
     * Stops the engine and resets the singleton instance to "null"
     * this is mostly used for testing
     */
    public void teardownEngine()
    {
        // yes im logging this as error, just to make it visible
        Log.e(TAG, "teardownEngine: ---resetting engine---");
        this.stop();
        instance = null;
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
     * {@link #startSDPDiscoveryForService(ServiceDescription)}
     * to start a service discovery.
     *
     * A device discovery has to run before services will be discovered.
     */
    public boolean startDeviceDiscovery()
    {
        Log.d(TAG, "startDiscovery: start looking for other devices");
        if (bluetoothAdapter.isDiscovering())
        {
            Log.d(TAG, "startDiscovery: already scanning, restarting ... ");
            this.bluetoothAdapter.cancelDiscovery();
        }

        if(this.bluetoothAdapter.startDiscovery()) {
            Log.d(TAG, "startDiscovery: started device discovery");
            return true;
        } else {
            Log.e(TAG, "startDiscovery: could not start Discovery");
            return false;
        }
    }

    /*
     * Ends the bluetooth device
     * discovery
     */
    public void stopDeviceDiscovery()
    {
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
     * {@link SdpBluetoothDiscoveryEngine#stopSDPDiscoveryForService(ServiceDescription)
     * with the same UUID is called,  no matter hwo many devies will be disovered ill then.
     * (Or to make i short, this wont stop afer the first connecion was made)
     *
     * @param serviceUUID
     *         The UUID of the service to connect o
     */
    public void startSDPDiscoveryForService(ServiceDescription description)
    {
        Log.d(TAG, "Starting service discovery");
        // Are we already looking for he service?
        if (this.isServiceAlreadyInDiscovery(description))
        {
            Log.d(TAG, "startSDPDiscoveryForServiceWithUUID: Service discovery already running ");
            return;
        }

        // Trying to find service on devices hat where discovered earlier in the engine run
        this.tryToFindAlreadyDiscoveredServices(description);
        // Adding the service to  be found in the future
        this.servicesToLookFor.add(description);
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
     * @param description
     *  The service description
     */
    public void stopSDPDiscoveryForService(ServiceDescription description)
    {
        Log.d(TAG, "End service discovery for service with UUID " + description.toString());
        // removing from list of services
        this.servicesToLookFor.remove(description);

        // removing the client from the list
        this.cancelDiscoveryIfNothingToLookFor();
    }

    /**
     * Checks if the service description is already in {@link #servicesToLookFor} list
     *
     * @param description
     *         Description of the service to look for
     *
     * @return false if the service is not in the list, else returns true
     */
    private boolean isServiceAlreadyInDiscovery(ServiceDescription description)
    {
        return servicesToLookFor.contains(description);
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
        if (this.servicesToLookFor.isEmpty())
        {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    /**
     * This will start a refreshing process
     * of all nearby services.
     * This also will cause the device discovery to stop.
     *
     * Calling {@link #startDeviceDiscovery()} while this is running is not recommended.
     */
    public void refreshNearbyServices(){
        Log.d(TAG, "refreshNearbyServices: start refreshing");
        this.bluetoothAdapter.cancelDiscovery();
        requestServiceFromDiscoveredDevices();

    }

    private void requestServiceFromDiscoveredDevices(){
        for (BluetoothDevice deviceInRange: this.discoveredDevices)
        {
            Log.d(TAG, "refreshNearbyServices: for " + Utils.getRemoteDeviceString(deviceInRange));
            deviceInRange.fetchUuidsWithSdp();
        }
    }


    //
    //  ----------  listeners ----------
    // TODO define a abstract class for this and the wifi discovery engine, this belongs into there for example

    /**
     * Registers a {@link BluetoothServiceDiscoveryListener} to be notified about
     * discovered devices and services
     *
     * @see #unregisterReceivers()
     *
     * @param listener
     *  implementation of then listener interface
     */
    public void registerDiscoverListener(BluetoothServiceDiscoveryListener listener){

        if(discoveryListeners.contains(listener)){
            return;
        }
        this.discoveryListeners.add(listener);
    }


    public void unregisterDiscoveryListener(BluetoothServiceDiscoveryListener listener){
        discoveryListeners.remove(listener);
    }

    /**
     * Calls {@link BluetoothServiceDiscoveryListener#onServiceDiscovered(BluetoothDevice, ServiceDescription)}
     * on all listeners in {@link #discoveryListeners}
     *
     * @param device
     *  the discovered device
     * @param description
     *  the description of the discovered service
     */
    private void notifyOnServiceDiscovered(BluetoothDevice device, ServiceDescription description){
        for (BluetoothServiceDiscoveryListener lister : this.discoveryListeners){
            //Notify client about discovery
            lister.onServiceDiscovered(device, description);
        }
    }

    /**
     * Calls {@link BluetoothServiceDiscoveryListener#onPeerDiscovered(BluetoothDevice)}
     * on all listeners in {@link #discoveryListeners}
     *
     * @param device
     *  the discovered device
     */
    private void notifyOnPeerDiscovered(BluetoothDevice device){
        for (BluetoothServiceDiscoveryListener lister : this.discoveryListeners){
            //Notify client about discovery
            lister.onPeerDiscovered(device);
        }
    }

    //
    //  ----------  on bluetooth events ----------
    //

    /**
     * This should be called when a new device was discovered
     *
     * The device will be added to {@link #discoveredDevices} if it was not yet.
     * and notify the {@link #discoveryListeners} about a new device.
     *
     * @see DeviceFoundReceiver
     *
     * @param device
     *     The discovered device
     */
    protected void onDeviceDiscovered(BluetoothDevice device)
    {
        // Adding the device to he discovered devices list
        if (!discoveredDevices.contains(device))
        {
            discoveredDevices.add(device);
            // Notifying client about newly found devices
            this.notifyOnPeerDiscovered(device);
        }
    }

    protected void onDeviceDiscoveryFinished(){
        //Starting SDP
        requestServiceFromDiscoveredDevices();
    }

    protected void onUuidsFetched(BluetoothDevice device, Parcelable[] uuidExtra){
        Log.d(TAG, "fetchedUuidReceiver: received UUIDS fot " + Utils.getRemoteDeviceString(device));

        if(uuidExtra != null){
            notifyListenersIfServiceIsAvailable(device, uuidExtra);
        }
    }

    private void notifyListenersIfServiceIsAvailable(BluetoothDevice device, Parcelable[] uuidExtra)
    {
        for (Parcelable pUuid : uuidExtra)
        {
            UUID uuid = ((ParcelUuid) pUuid).getUuid();
            Log.d(TAG, "connectIfServiceAvailableAndNoConnectedAlready: Service found on " + device.getName() + "  | uuid:  " + pUuid);
            for (ServiceDescription serviceToLookFor : this.servicesToLookFor)
            {
                if (uuid.equals(serviceToLookFor.getServiceUuid()) || (this.checkLittleEndianUuids && uuid.equals(serviceToLookFor.getBytewiseReverseUuid())))
                {
                    Log.d(TAG, "connectIfServiceAvailableAndNoConnectedAlready: Service found on " + Utils.getRemoteDeviceString(device));
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
     *      Description of the service
     */
    private void tryToFindAlreadyDiscoveredServices(ServiceDescription description)
    {
        Log.d(TAG, "tryToConnectToServiceAlreadyInRange: checking if " + description + " was discovered before ");

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
                Log.e(TAG, "tryToConnectToServiceAlreadyInRange: we have no uuids of his device " + Utils.getRemoteDeviceString(device));
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
     *
     * Set this to `false` to disable this behaviour.
     *
     * @param checkLittleEndianUuids
     * determines whether little endian UUIDs should be checked or not
     */
    public void shouldCheckLittleEndianUuids (boolean checkLittleEndianUuids){
        this.checkLittleEndianUuids = checkLittleEndianUuids;
    }

}


package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.Utils;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceClient;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceServer;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpConnectorThreads.BluetoothClientConnector;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpConnectorThreads.BluetoothServiceConnector;

/**
 * The BluetoothSDP engine is the main controller the android Bluetooth-SDP API
 * It is a Singleton so it can be accessed application-wide.
 *
 * I can be used to start a service, a client or both.#
 *
 */
public class SdpBluetoothEngine
{
    //
    //  ---------- static members ----------
    //

    public static final int DEFAULT_DISCOVERABLE_TIME = 120;
    public static final int SHORT_DISCOVERABLE_TIME = 60;
    public static final int MIN_DISCOVERABLE_TIME = 10;
    public static final int LONG_DISCOVERABLE_TIME = 180;
    public static final int MAX_DISCOVERABLE_TIME = 300;


    public static final long MANUAL_REFRESH_TIME = 10000;

    /**
     * Instance of the class following the singleton pattern
     */
    private static SdpBluetoothEngine instance;

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
     * this can be set by the user (application using
     * this component)
     * If this is true the {@link SdpBluetoothServiceClient#shouldConnectTo(String, UUID)}
     * callback methods will be ignored and a connection will be established with every
     * discovered service.
     */
    private final boolean automaticallyConnectWhenServiceFound = false;

    private int discoverableTimeInSeconds;

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
    private final ArrayList<UUID> servicesToLookFor =  new ArrayList<>();

    private final HashMap<UUID, SdpBluetoothServiceClient> serviceClients = new HashMap<>();
    /**
     *
     */
    private final List<BluetoothServiceConnector> runningServiceConnectors = new ArrayList<>();

    private final List<BluetoothClientConnector> runningClientConnectors  = new ArrayList<>();

    private final SdpBluetoothConnectionManager connectionManager;

    private final BroadcastReceiver foundDeviceReceiver;

    private final BroadcastReceiver fetchedUuidReceiver;

    private boolean refreshing;

    private long refreshingTimeStamp  = 0;

    /**
     * Determines whether discovered service UUIDs
     * should only be evaluated the way the where received
     * or also in a bytewise revered format
     * This is to workaround a issue which causes UUIDs
     * to be received in a little endian format.
     *
     * It is true by default, to ensure everything working correctly.
     * But can be disabled be the user.
     * @see SdpBluetoothEngine#shouldCheckLittleEndianUuids(boolean)
     */
    private boolean checkLittleEndianUuids = true;

    //
    //  ----------  initialisation and setup ----------
    //

    /**
     * Initializes the SdpBluetoothEngine with the given context and 
     * the default bluetooth adapter. 
     * After initialisation the instance can be obtained by calling {@link SdpBluetoothEngine#getInstance()}.
     *
     * A not default bluetooth adapter can be used by calling {@link SdpBluetoothEngine#initialize(Context, BluetoothAdapter)}.
     *
     * @param context
     *  the app context
     * @return
     *  the created (or already existing) instance of the SdpBluetoothEngine
     */
    public static SdpBluetoothEngine initialize(Context context)
    {
        if (instance == null)
        {
            instance = new SdpBluetoothEngine(context, BluetoothAdapter.getDefaultAdapter());
        }
        return instance;
    }

    /**
     * Initializes the singleton instance of the SDpBluetooth engine with app context and a
     * BluetoothAdapter.
     *
     * @param context
     * @param adapter
     *  A Bluetooth Adapter, if the default adapter ({@link BluetoothAdapter#getDefaultAdapter()})
     *  is set to be used this can be skipped and just {@link SdpBluetoothEngine#initialize(Context)}
     *  needs to be called.
     * @return
     * the created (or already existing) instance of the SdpBluetoothEngine
     */
    public static SdpBluetoothEngine initialize(Context context, BluetoothAdapter adapter)
    {
        if (instance == null)
        {
            instance = new SdpBluetoothEngine(context, adapter);
        }
        return instance;
    }

    /**
     * Returns the singleton instance of the SdpBluetoothEngine.
     * Requires {@link #initialize(Context)} or {@link #initialize(Context, BluetoothAdapter)}
     * to have been called prior.
     *
     * This method will not create a new instance if the engine was not yet initialized.
     * It will return `null` instead.
     *
     * @return
     * The instance of the SDPBluetoothEngine created by calling {@link #initialize(Context)}
     * else returns `null`
     */
    public static SdpBluetoothEngine getInstance()
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

    private SdpBluetoothEngine(Context context, BluetoothAdapter adapter)
    {
        this.context = context;
        this.bluetoothAdapter = adapter;
        this.connectionManager = new SdpBluetoothConnectionManager();
        this.foundDeviceReceiver = new DeviceFoundReceiver(this);
        this.fetchedUuidReceiver = new UUIDFetchedReceiver(this);
        this.refreshing = false;
        this.setDefaultDiscoverableTimeInSeconds(DEFAULT_DISCOVERABLE_TIME);
    }

    private void registerReceivers()
    {
        IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter actionUUID = new IntentFilter(BluetoothDevice.ACTION_UUID);
        IntentFilter debuggingFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        BluetoothBroadcastReceiver receiver = new BluetoothBroadcastReceiver(this);
        context.registerReceiver(receiver, debuggingFilter);

        context.registerReceiver(fetchedUuidReceiver, actionUUID);
        context.registerReceiver(foundDeviceReceiver, discoverDevicesIntent);
    }

    public void start()
    {
        if (this.bluetoothAdapter == null) {
            Log.e(TAG, "No BluetoothAdapter given, the probably does not support Bluetooth");
            return;
        }
        this.enableBluetooth();
        this.registerReceivers();
    }

    //
    //  ----------  shutdown and teardown ----------
    //

    /**
     * This needs o be called o sop heSDP engine and unsubscribe all eh receivers
     */
    public void stop()
    {
        unregisterReceivers();
        stopDiscovery();
        stopAllServiceConnector();
        stopAllClientConnectors();
        this.connectionManager.closeAllConnections();
    }

    /**
     * Stops the engine and resets the singleton instance to "null"
     * this is mostly used for testing
     */
    protected void teardownEngine()
    {
        // yes im logging this as error, just to make it visible
        Log.e(TAG, "teardownEngine: ---resetting engine---");
        this.stop();
        instance = null;
    }

    private void stopAllClientConnectors()
    {
        for (BluetoothClientConnector connector : this.runningClientConnectors)
        {
            connector.cancel();
        }
    }

    private void stopAllServiceConnector()
    {
        for (BluetoothServiceConnector connector : this.runningServiceConnectors)
        {
            connector.cancel();
        }
    }

    private void unregisterReceivers()
    {
        try
        {
            this.context.unregisterReceiver(fetchedUuidReceiver);
        }
        catch (IllegalArgumentException e)
        {
            Log.e(TAG, "onDestroy: fetchedUuidReceiver was not registered ");
        }
        try
        {
            this.context.unregisterReceiver(foundDeviceReceiver);
        }
        catch (IllegalArgumentException e)
        {
            Log.e(TAG, "onDestroy: foundDeviceReceiver was not registered ");
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
     * Makes the device discoverable for other bluetooth devices
     */
    public void startDiscoverable()
    {
        Log.d(TAG, "makeDiscoverable: making device discoverable for " + discoverableTimeInSeconds + " ms");
        //Discoverable Intent
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DEFAULT_DISCOVERABLE_TIME);
        context.startActivity(discoverableIntent);
    }

    /**
     * Starts discovering other devices
     */
    public boolean startDiscovery()
    {
        // in case a manual UUID refresh process is running it will end here
        stopRefreshingNearbyDevices();

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
     * Ends the BluetoothDiscovery
     */
    public void stopDiscovery()
    {
        bluetoothAdapter.cancelDiscovery();
    }

    ////
    ////------------  SDP SPECIFIC METHODS  ---------------
    ////

    //
    //  ----------  "client" side----------
    //

    /**
     * Stars looking for the specified service.
     * <p>
     * This will make the engine connect to devices running this service
     * which where already discovered (and are still in range) and to
     * Devices tha will be discovered from now on (given that the bluetooth discovery is enabled)
     * <p>
     * The service discovery will run till
     * {@link SdpBluetoothEngine#stopSDPDiscoveryForServiceWithUUID(UUID serviceUUID)
     * with the same UUID is called,  no matter hwo many devies will be disovered ill then.
     * (Or to make i short, this wont stop afer the first connecion was made)
     *
     * @param serviceUUID
     *         The UUID of the service to connect o
     */
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


        // stopping all client connectors which may sill run and try to connect to this service
        ArrayList<BluetoothClientConnector> connectorsToClose = new ArrayList<>();
        for (BluetoothClientConnector clientConnector : this.runningClientConnectors)
        {
            if (clientConnector.getServiceUUID().equals(serviceUUID))
            {
                connectorsToClose.add(clientConnector);
            }
        }
        for (BluetoothClientConnector connectorToClose : connectorsToClose)
        {
            this.runningClientConnectors.remove(connectorToClose);
            connectorToClose.cancel();
        }

        // removing the client from the list
        serviceClients.remove(serviceUUID);
        this.cancelDiscoveryIfNothingToLookFor();
    }

    /**
     * This will close all client connections to the service specified he UUID.
     * This alone does not end the discovery of the given service, it just
     * closes already established connections.
     *
     * @see #stopSDPDiscoveryForServiceWithUUID(UUID) to sop the discovery of a service
     *
     * @param serviceUUID
     *         The UUID of the service
     */
    public void disconnectFromServicesWithUUID(UUID serviceUUID)
    {
        this.connectionManager.closeAllClientConnectionsToServiceWihUUID(serviceUUID);
    }

    /**
     * This checks if the service was discovered previously and if
     * it is still in range.
     * <p>
     * If that is he case it will ry to open a Connection to this service.
     *
     * @param serviceUUID
     *         the UUID of he service to connect
     */
    private void tryToConnectToServiceAlreadyInRange(UUID serviceUUID)
    {
        Log.d(TAG, "tryToConnectToServiceAlreadyInRange: checking if service " + serviceUUID + " was discovered before ");

        // iterating through devices already discovered
        for (BluetoothDevice device : this.discoveredDevices)
        {
            // The devices geUuids() may return null
            try
            {
                // Looking for each UUID on the device and if it matches open a connection
                for (ParcelUuid uuid : device.getUuids())
                {
                    UUID tUUID = uuid.getUuid();
                    if (tUUID.equals(serviceUUID) || (this.checkLittleEndianUuids && Utils.bytewiseReverseUuid(tUUID).equals(serviceUUID)))
                    {
                        if (!this.isConnectionAlreadyEstablished(device.getAddress(), serviceUUID))
                        {
                            Log.d(TAG, "tryToConnectToServiceAlreadyInRange:  service " + serviceUUID + " has already been discovered on " + Utils.getRemoteDeviceString(device));
                            // Okay the service was discovered
                            // and here was is no connection open to it, lets change that and connect
                            tryToConnectToService(device, serviceUUID);
                        }
                    }
                }
            }
            catch (NullPointerException e)
            {
                e.printStackTrace();
                Log.e(TAG, "tryToConnectToServiceAlreadyInRange: we have no uuids of his device " + Utils.getRemoteDeviceString(device));
            }
        }
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
        if(servicesToLookFor.contains(serviceUUID))
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
     * Will be called when a device was found which hosts a service with the specified UUID
     *  * If {@link #automaticallyConnectWhenServiceFound}  is set to rue,
     *    the connection will be established automatically.
     *  * Else {@link SdpBluetoothServiceClient#shouldConnectTo(String, UUID)}
     *    Of the client with the matching UUID will be called to decide
     *    whether a connection should be established or not.
     *
     * @see #connectIfServiceAvailableAndNoConnectedAlready(BluetoothDevice, Parcelable[]):
     *  Will call this method if a Service server was found
     * @see #startClientThread(BluetoothDevice, UUID)
     *  Will be called when a connection should be established
     *
     * @param device
     *         device to connect to
     * @param serviceUUID
     *         service oi connect to
     */
    private void tryToConnectToService(BluetoothDevice device, UUID serviceUUID)
    {
        Log.d(TAG, "tryToConnectToService:  SDP service found, trying to connect");
        if (this.automaticallyConnectWhenServiceFound)
        {
            Log.d(TAG, "tryToConnectToService:  Connecting automatically");
            startClientThread(device, serviceUUID);
        }
        else // No auto connect
        {
            Log.d(TAG, "tryToConnectToService: Connecting manually");
            if (this.serviceClients.get(serviceUUID).shouldConnectTo(device.getAddress(), serviceUUID))
            {
                Log.d(TAG, "tryToConnectToService: staring client thread to " + Utils.getRemoteDeviceString(device));
                startClientThread(device, serviceUUID);
            }
            else
            {
                Log.d(TAG, "tryToConnectToService: should not connect to " + Utils.getRemoteDeviceString(device));
            }
        }
    }

    /**
     * This method devices if a connection to a discovered device
     * should be established.
     * The decision making here depends on two factors:
     * 1. Does the device run any of the services we are looking for ?
     * 2. Is already a connection established between the two devices (and services on them) ?
     *
     * @param device
     *      The device to check
     * @param uuidExtra
     *      he UUIDs of services on the device
     * @return
     * False if no connection was attempted
     */
    private boolean connectIfServiceAvailableAndNoConnectedAlready(BluetoothDevice device, Parcelable[] uuidExtra)
    {
        if( uuidExtra == null){
            return false;
        }
        boolean found = false;
        try
        {
            for (Parcelable uuid : uuidExtra)
            {
                UUID tUUID = ((ParcelUuid) uuid).getUuid();
                Log.d(TAG, "connectIfServiceAvailableAndNoConnectedAlready: Service found on " + device.getName() + "  | uuid:  " + uuid);

                for (UUID uuidToLookFor : this.servicesToLookFor)
                {
                    if (tUUID.equals(uuidToLookFor) || (this.checkLittleEndianUuids && Utils.bytewiseReverseUuid(tUUID).equals(uuidToLookFor)))
                    {
                        Log.d(TAG, "connectIfServiceAvailableAndNoConnectedAlready: Service found on " + Utils.getRemoteDeviceString(device));
                        if (!this.isConnectionAlreadyEstablished(device.getAddress(), uuidToLookFor))
                        {
                            //Notify client about discovery
                            this.serviceClients.get(uuidToLookFor).onServiceDiscovered(device.getAddress(), uuidToLookFor);
                            tryToConnectToService(device, uuidToLookFor);
                        }

                        found = true;
                    }
                }
            }
        }
        catch (NullPointerException e)
        {
           e.printStackTrace();
        }
        return found;
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
     * This will run a maximum of 10 seconds.
     * It an be stopped by calling {@link #stopRefreshingNearbyDevices()}
     *
     * This also will cause the device discovery to stop.
     * calling {@link #startDiscovery()} will resume the discovery of new devices
     * but stop the refreshing process.
     *
     * Calling {@link #startDiscovery()} while this is running is not recommended.
     * You can check if the refresh is still running using {@link #isRefreshProcessRunning()}
     */
    public void refreshNearbyServices(){
        Log.d(TAG, "refreshNearbyServices: start refreshing");
        this.bluetoothAdapter.cancelDiscovery();
        this.refreshing = true;
        this.refreshingTimeStamp = System.currentTimeMillis();
        Log.d(TAG, "refreshNearbyServices: refreshing set up, fetching UUIDs");
        for (BluetoothDevice deviceInRange: this.discoveredDevices)
        {
            Log.d(TAG, "refreshNearbyServices: for " + Utils.getRemoteDeviceString(deviceInRange));
            deviceInRange.fetchUuidsWithSdp();
        }
    }

    private void stopRefreshingNearbyDevices(){
        Log.d(TAG, "stopRefreshingNearbyDevices: stop refresh process");
        refreshing = false;
        refreshingTimeStamp = 0;
    }

    public boolean isRefreshProcessRunning(){
        Log.d(TAG, "isRefreshProcessRunning: refreshing : " + refreshing + " | timeout : " + (System.currentTimeMillis() - this.refreshingTimeStamp >= MANUAL_REFRESH_TIME));
        if(!this.refreshing){
            return false;
        }
        else if (System.currentTimeMillis() - this.refreshingTimeStamp >= MANUAL_REFRESH_TIME)
        {
            stopRefreshingNearbyDevices();
            return false;
        }
        return true;
    }

    //
    //  ----------  "server" side ----------
    //

    /**
     * Starts a new Bluetooth SDP Service, by opening
     * a RFOMm Socket to a service record
     * with the given service name und UUID and starting a Thread to
     * accept connections.
     *
     * I checks if a service wih the given UUID is already running (Thread running and Socket open).
     * The same Service cant be registered twice.
     *
     * @see #stopSDPService(UUID) method to stop the service
     * @param serviceName
     *         The name of the service
     * @param serviceUUID
     *         The services UUID
     * @return boolean
     *         false if a service with he given UUID is currently running else returns true
     *
     */
    public boolean startSDPService(String serviceName, UUID serviceUUID, SdpBluetoothServiceServer server)
    {
        Log.d(TAG, "Staring new Service Service");
        if (this.serviceAlreadyRunning(serviceUUID))
        {
            Log.e(TAG, "A service with the same UUID is already running, return ");
            return false;
        }
        this.startServiceThread(serviceName, serviceUUID, server);
        return true;
    }

    /**
     * Returns true if there is a {@link BluetoothServiceConnector} with the same UUID
     * running and present in {@see #runningServiceConnectors}
     * @param serviceUUID
     *      The UUID of the service to check
     * @return
     *      true if service already runs
     */
    private boolean serviceAlreadyRunning(UUID serviceUUID)
    {
        for (BluetoothServiceConnector connector : this.runningServiceConnectors)
        {
            if (connector.getServiceUUID().equals(serviceUUID))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * This sops the service specified by ots UUID from accepting new
     * connections, hoverer connections already established up to
     * this point will remain connected and working.
     * @param serviceUUID
     */
    public void stopSDPService(UUID serviceUUID)
    {
        ArrayList<BluetoothServiceConnector> connectorsToEnd = new ArrayList<>();
        for (BluetoothServiceConnector serviceConnector : this.runningServiceConnectors)
        {
            if (serviceConnector.getServiceUUID().equals(serviceUUID))
            {
                connectorsToEnd.add(serviceConnector);
            }
        }
        for (BluetoothServiceConnector connectorToEnd : connectorsToEnd)
        {
            connectorToEnd.cancel();
            this.runningServiceConnectors.remove(connectorToEnd);
        }
    }

    /**
     * Checks if a Connection {Device, Service} already exists.
     *
     * Just wraps the {@link SdpBluetoothConnectionManager#isAlreadyConnected(String, UUID)}
     * method for easier access inside the SdpEngine.
     *
     * @param deviceAddress
     *      The Mac address of he device
     * @param serviceUUID
     *      The UUID of the service
     * @return
     *      true if the connection exists, else false
     */
    private boolean isConnectionAlreadyEstablished(String deviceAddress, UUID serviceUUID)
    {
        Log.d(TAG, "isConnectionAlreadyEstablished: checking if there is a connection established");
        return this.connectionManager.isAlreadyConnected(deviceAddress, serviceUUID);
    }

    /**
     * Closes all connections / sockets to a service (specified by its UUID) running on this device.
     * Note that his just closes all current connections from clients to this device.
     * It does not to prevent the Service from accepting new connections from his point on.
     *
     * To stop new connexions from being made use {@link SdpBluetoothEngine#stopSDPService(UUID)}
     *
     * @param serviceUUID
     *      The UUID of the service
     */
    public void disconnectFromClientsWithUUID(UUID serviceUUID)
    {
        Log.d(TAG, "disconnectFromClientsWithUUID: closing client connections to service " + serviceUUID);
        this.connectionManager.closeServiceConnectionsToServiceWithUUID(serviceUUID);
    }

    //
    //  ----------  starting the connection threads ----------
    //


    private void startClientThread(BluetoothDevice device, UUID serviceUUID)
    {
        Log.d(TAG, "Staring Client");
        BluetoothClientConnector bluetoothClientConnector = new BluetoothClientConnector(bluetoothAdapter, serviceUUID, device, new BluetoothClientConnector.ConnectionEventListener()
        {
            @Override
            public void onConnectionFailed(UUID uuid, BluetoothClientConnector failedConnector)
            {
                runningClientConnectors.remove(failedConnector);
            }

            @Override
            public void inConnectionSuccess(SdpBluetoothConnection connection)
            {
                connectionManager.addConnection(connection);
                serviceClients.get(serviceUUID).onConnectedToService(connection);
            }
        });
        this.runningClientConnectors.add(bluetoothClientConnector);
        bluetoothClientConnector.start();
    }

    private void startServiceThread(String serviceName, UUID serviceUUID, SdpBluetoothServiceServer serviceServer)
    {
        BluetoothServiceConnector bluetoothServiceConnector = new BluetoothServiceConnector(bluetoothAdapter, serviceName, serviceUUID, new BluetoothServiceConnector.ConnectionEventListener()
        {
            @Override
            public void onConnectionFailed(UUID uuid, BluetoothClientConnector failedClientConnector)
            {
                // Noting for now jus go on
            }

            @Override
            public void inConnectionSuccess(SdpBluetoothConnection connection)
            {
                connectionManager.addConnection(connection);
                serviceServer.onClientConnected(connection);
            }
        });
        try
        {
            bluetoothServiceConnector.startService();
            this.runningServiceConnectors.add(bluetoothServiceConnector);
        }
        catch (IOException e)
        {
            Log.e(TAG, "startServiceThread:  could not start accept thread");
        }
    }


    //
    //  ----------  uuid fetched timestamps ----------
    //


    //
    //  ----------  on bluetooth events ----------
    //

    /**
     * This should be called when a new device was discovered
     *
     *  The device will be added to the discovered devices list, if it was not yet.
     *  and notify the SdpClients about a new device // todo , this actually is just for my debugging and could be removed in final
     *
     *  It will also determine if he UUIDs of this device should be fetched (first discovery or refresh).
     *  In this ase it will trigger the UUIDs fetching process and pause the discovery
     * @param device
     */
    public void onDeviceDiscovered(BluetoothDevice device)
    {
        // Adding the device to he discovered devices list
        if (!discoveredDevices.contains(device))
        {
            discoveredDevices.add(device);

            // Notifying client about newly found devices
            for (HashMap.Entry<UUID, SdpBluetoothServiceClient> set : serviceClients.entrySet())
            {
                set.getValue().onDevicesInRangeChange(discoveredDevices);
            }
        }

    }

    public void onUuidsFetched(BluetoothDevice device, Parcelable[] uuidExtra){
        Log.e(TAG, "onUuidsFetched: is discovering " + bluetoothAdapter.isDiscovering() );
        Log.d(TAG, "fetchedUuidReceiver: received UUIDS fot " + Utils.getRemoteDeviceString(device));

        if(uuidExtra != null){
            connectIfServiceAvailableAndNoConnectedAlready(device, uuidExtra);
        }
    }

    public void onDeviceDiscoveryFinished(){
        this.refreshNearbyServices();
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

    /**
     * Set the time the device should be made discoverable
     * The max value here is 300 (seconds) and the
     * min value is 10 (seconds).
     * The value will be capped to fit this interval.
     *
     * If this method is not used, the discoverable time will
     * be set to the default of 120 seconds.
     *
     * There are also predefined values which an be used here :
     * @see SdpBluetoothEngine#DEFAULT_DISCOVERABLE_TIME
     * @see SdpBluetoothEngine#MAX_DISCOVERABLE_TIME
     * @see SdpBluetoothEngine#MIN_DISCOVERABLE_TIME
     * @see SdpBluetoothEngine#LONG_DISCOVERABLE_TIME
     * @see SdpBluetoothEngine#SHORT_DISCOVERABLE_TIME
     *
     * @param seconds
     */
    public void setDefaultDiscoverableTimeInSeconds(int seconds){
        if(seconds > MAX_DISCOVERABLE_TIME){
            seconds = MAX_DISCOVERABLE_TIME;
        }
        if(seconds < MIN_DISCOVERABLE_TIME)
        {
            seconds = MIN_DISCOVERABLE_TIME;
        }
        this.discoverableTimeInSeconds = seconds;
    }
}


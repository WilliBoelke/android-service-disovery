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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnectionManager;
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

    public static final int DEFAULT_DISCOVERY_TIME_IN_SECONDS = 120;
    public static final long UUID_REFRESH_TIME_IN_MILLIS_SHORT = 30000;
    public static final long UUID_REFRESH_TIME_IN_MILLIS_MEDIUM = 60000;
    public static final long UUID_REFRESH_TIME_IN_MILLIS_LONG = 120000;

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
    private final boolean automaticallyConnectWhenServiceFound;

    private final int discoverabilityTimeInSeconds;

    /**
     * This is used to sore a timestamp (Long)
     * for every discovered devices MAc Address,
     * indicating when the UUIDs for this device where
     * fetched the last time.
     */
    private final HashMap<String, Long> deviceUUIDsFetchedTimeStamps;

    /**
     * This keeps track of all bluetooth devices which are discovered.
     * A bluetoothDevice will be added on ACTION_FOUND (if no yet on this list)
     * and removed on ACTION_ACL_DISCONNECTED.
     */
    private final ArrayList<BluetoothDevice> discoveredDevices;

    /**
     * Stores UUIDs of services which should be discovered
     * (and connected to)
     */
    private final ArrayList<UUID> servicesToLookFor;

    private final HashMap<UUID, SdpBluetoothServiceClient> serviceClients;
    /**
     *
     */
    private List<BluetoothServiceConnector> runningServiceConnectors;

    private List<BluetoothClientConnector> runningClientConnectors;

    private SdpBluetoothConnectionManager connectionManager;

    private BroadcastReceiver foundDeviceReceiver;

    private BroadcastReceiver fetchedUuidReceiver;

    /**
     * This prevents the engine from starting the discovery again
     * (for example after fetching UUIDs) even though it was disabled
     * though {@link #stopDiscovery()}
     */
    private boolean shouldDiscover;
    private boolean checkLittleEndianUuids;

    //
    //  ----------  initialisation and setup ----------
    //

    public static SdpBluetoothEngine initialize(Context context)
    {
        if (instance == null)
        {
            instance = new SdpBluetoothEngine(context, BluetoothAdapter.getDefaultAdapter());
        }
        return instance;
    }

    public static SdpBluetoothEngine initialize(Context context, BluetoothAdapter adapter)
    {
        if (instance == null)
        {
            instance = new SdpBluetoothEngine(context, adapter);
        }
        return instance;
    }

    public static SdpBluetoothEngine getInstance()
    {
        if (instance != null)
        {
            return instance;
        }
        else
        {
            return null; // TODO exception
        }
    }

    private SdpBluetoothEngine(Context context, BluetoothAdapter adapter)
    {
        this.context = context;
        this.bluetoothAdapter = adapter;
        this.discoverabilityTimeInSeconds = DEFAULT_DISCOVERY_TIME_IN_SECONDS;
        this.automaticallyConnectWhenServiceFound = false;
        this.runningServiceConnectors = Collections.synchronizedList(new ArrayList<BluetoothServiceConnector>());
        this.runningClientConnectors = Collections.synchronizedList(new ArrayList<BluetoothClientConnector>());
        this.discoveredDevices = new ArrayList<>();
        this.servicesToLookFor = new ArrayList<>();
        this.connectionManager = new SdpBluetoothConnectionManager();
        this.deviceUUIDsFetchedTimeStamps = new HashMap<>();
        this.serviceClients = new HashMap<>();
        this.shouldDiscover = false;
        this.foundDeviceReceiver = new DeviceFoundReceiver(this);
        this.fetchedUuidReceiver = new UUIDFetchedReceiver(this);
        this.checkLittleEndianUuids = true;
    }

    private void registerReceivers()
    {
        IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter actionUUID = new IntentFilter(BluetoothDevice.ACTION_UUID);
        context.registerReceiver(fetchedUuidReceiver, actionUUID);
        context.registerReceiver(foundDeviceReceiver, discoverDevicesIntent);
    }

    public void startEngine()
    {
        this.enableBluetooth();
        this.registerReceivers();
    }

    //
    //  ----------  shutdown and teardown ----------
    //

    /**
     * This needs o be called o sop heSDP engine and unsubscribe all eh receivers
     */
    public void stopEngine()
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
        this.stopEngine();
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

        if (bluetoothAdapter == null)
        {
            Log.e(TAG, "enableBluetooth: This Device does not support Bluetooth");
        }
        else if (!bluetoothAdapter.isEnabled())
        {
            // Enable Bluetooth
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            context.startActivity(enableBluetoothIntent);
        }
    }

    /**
     * Makes the device discoverable for other bluetooth devices
     */
    public void askToMakeDeviceDiscoverable()
    {
        Log.d(TAG, "makeDiscoverable: making device discoverable for " + discoverabilityTimeInSeconds + " ms");
        //Discoverable Intent
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DEFAULT_DISCOVERY_TIME_IN_SECONDS);
        context.startActivity(discoverableIntent);
    }

    /**
     * Starts discovering other devices
     */
    public void startDiscovery()
    {
        Log.d(TAG, "startDiscovery: start looking for other devices");
        if (bluetoothAdapter.isDiscovering())
        {
            Log.d(TAG, "makeDiscoverable: already scanning, restarting ... ");
            this.bluetoothAdapter.cancelDiscovery();
        }
        this.shouldDiscover = true;
        bluetoothAdapter.startDiscovery();
        Log.d(TAG, "startDiscovery: started device discovery");
    }

    /*
     * Ends the BluetoothDiscovery
     */
    public void stopDiscovery()
    {
        this.shouldDiscover = false;
        bluetoothAdapter.cancelDiscovery();
    }

    private void startDiscoveryIfAllowed()
    {
        Log.d(TAG, "startDiscoveryIfAllowed: starting discovery again");
        if (this.shouldDiscover)
        {
            this.startDiscovery();
            return;
        }
        Log.d(TAG, "startDiscoveryIfAllowed: discovery restart is not allowed");
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
        for (BluetoothClientConnector connetorClose : connectorsToClose)
        {
            this.runningClientConnectors.remove(connetorClose);
            connetorClose.cancel();
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
                            Log.d(TAG, "tryToConnectToServiceAlreadyInRange:  service " + serviceUUID + " has already been discovered on " + Utils.getBluetoothDeviceString(device));
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
                Log.e(TAG, "tryToConnectToServiceAlreadyInRange: we have no uuids of his device " + Utils.getBluetoothDeviceString(device));
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
                Log.d(TAG, "tryToConnectToService: staring client thread to " + Utils.getBluetoothDeviceString(device));
                startClientThread(device, serviceUUID);
            }
            else
            {
                Log.d(TAG, "tryToConnectToService: should not connect to " + Utils.getBluetoothDeviceString(device));
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
        boolean found = false;
        try
        {
            for (Parcelable uuid : uuidExtra)
            {
                UUID tUUID = ((ParcelUuid) uuid).getUuid();

                Log.d(TAG, "connectIfServiceAvailableAndNoConnectedAlready: Service found on " + device.getName() + "  | uuid:  " + uuid);
                // Log.d(TAG, "connectIfServiceAvailableAndNoConnectedAlready: Services to look for = " + this.servicesToLookFor);
                for (UUID uuidToLookFor : this.servicesToLookFor)
                {
                    // Log.d(TAG, "connectIfServiceAvailableAndNoConnectedAlready: comparing: " + uuidToLookFor + " => " + Utils.bytewiseReverseUuid(tUUID));
                    if (tUUID.equals(uuidToLookFor) || (this.checkLittleEndianUuids && Utils.bytewiseReverseUuid(tUUID).equals(uuidToLookFor)))
                    {
                        Log.d(TAG, "connectIfServiceAvailableAndNoConnectedAlready: Service found on " + Utils.getBluetoothDeviceString(device));
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
            Log.e(TAG, "connectIfServiceAvailableAndNoConnectedAlready: No UUIDs available on device  " + Utils.getBluetoothDeviceString(device));
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
        if (this.servicesToLookFor.size() == 0)
        {
            bluetoothAdapter.cancelDiscovery();
        }
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

    /**
     * This method determines weather uuids for a devices
     * should be fetched again (causing a discovery interruption and restart)
     * his will be decided based on when the UUID for he given devices where
     * fetched the last time.
     * This is realised by saving a time swamp and the devices MAc address
     * into the {@link #deviceUUIDsFetchedTimeStamps} HashMap.
     *
     * UUIDs will be fetched again after the specified time {@link #UUID_REFRESH_TIME_IN_MILLIS_SHORT}
     * passed since the time stamp.
     *
     * @see #addFetchedDeviceTimestamp(String)
     * Adds a timestamp after UUIDs where fetched
     * @param address
     * The MAC address of the device
     * @return
     * true, when the UUIDS can be fetched again, else false
     */
    private boolean shouldFetchUUIDsAgain(String address)
    {
        long timestamp;
        try
        {
            timestamp = this.deviceUUIDsFetchedTimeStamps.get(address);
        }
        catch (NullPointerException e)
        {
            return true;
        }

        long elapsedTime = System.currentTimeMillis() - timestamp;
        Log.d(TAG, "shouldFetchUUIDsAgain: device " + address + " was last refreshed " + elapsedTime + " millis ago");
        return UUID_REFRESH_TIME_IN_MILLIS_SHORT <= elapsedTime;
    }

    private void addFetchedDeviceTimestamp(String address)
    {
        this.deviceUUIDsFetchedTimeStamps.put(address, System.currentTimeMillis());
    }


    //
    //  ----------  broadcast receiver ----------
    //


    public void onDeviceDiscovered(BluetoothDevice device)
    {
        // Adding the device to he discovered devices list
        if (!discoveredDevices.contains(device))
        {
            discoveredDevices.add(device);
            // Notifying client about newly found devices
            // serviceClients.onDevicesInRangeChange(devicesInRange);
            for (HashMap.Entry<UUID, SdpBluetoothServiceClient> set : serviceClients.entrySet())
            {
                set.getValue().onDevicesInRangeChange(discoveredDevices);
            }
        }

        /*
        //----------------------------------
        // NOTE : Here we get the UUIDs, There are two options a device
        // already, gave us the UUIDs without a request being issued.
        // That's good, because then we don't have to send a SDPRequest
        // and interrupt discovery. This though isn't always  the case,
        // then the getUuids method will return null.
        // Then we need to explicitly ask for UUIDs.
        // This seems to be rare on the android devices side as all
        // my test devices (5 divergent android phones and tables)
        // are giving the UUIDs implicitly and without a request.
        // This means the discovery will mostly be interrupted by
        // devices that wont have the service running a all.
        // Bu there is no 100% certainty.
        // UPDATE ON THIS :
        // Sadly android will keep UUIDS till the BT Adapter restarts
        // (Bluetooth was turned on and off once). This means the UUIDS
        // from .getUuids will be outdated mos of the time.
        // even if the devices just connected getUuids() will return the
        // uuids from the first time the devices connected.
        //----------------------------------

        if (device.getUuids() != null)
        {
            Log.d(TAG, "onDeviceDiscovered: the device " + Utils.getBluetoothDeviceString(device) + " already gave us UUIDs, no need to fetch");
            this.rememberedUUIDs.put(device.getAddress(), device.getUuids());
            addFetchedDeviceTimestamp(device.getAddress()); // also we an add a timestamp here since we go the UUIDs
            connectIfServiceAvailableAndNoConnectedAlready(device, device.getUuids());
        }
        else {
        }
         */

        Log.d(TAG, "onDeviceDiscovered: the device " + Utils.getBluetoothDeviceString(device) + " did not transferred any UUIDs");
        if (shouldFetchUUIDsAgain(device.getAddress()))
        {
            Log.d(TAG, "onDeviceDiscovered: the UUIDs on " + Utils.getBluetoothDeviceString(device) + " weren't refreshed recently, fetching them now");

            bluetoothAdapter.cancelDiscovery(); //  We need to cancel he discovery here, so we an receive the fetched UUIDs quickly
            device.fetchUuidsWithSdp();
        }
        else
        {
            Log.d(TAG, "onDeviceDiscovered: UUIDs where refreshed recently, no need to fetch");
        }

    }

    protected void onUuidsFetched(BluetoothDevice device, Parcelable[] uuidExtra){
        Log.d(TAG, "fetchedUuidReceiver: received UUIDS fot " + Utils.getBluetoothDeviceString(device));
        if(!shouldFetchUUIDsAgain(device.getAddress()))
        {
            Log.d(TAG, "fetchedUuidReceiver: UUIDs where refreshed only recently. blocked");
            return;
        }

        addFetchedDeviceTimestamp(device.getAddress());
        try
        {
            // Getting the Service UUIDs
            connectIfServiceAvailableAndNoConnectedAlready(device, uuidExtra); // Does that work??
        }
        catch (NullPointerException e)
        {
            Log.e(TAG, "fetchedUuidReceiver: Nu UUIDs received ");
        }
        startDiscoveryIfAllowed();
    }

    //
    //  ----------  helper ----------
    //


    //
    //  ----------  unused / debugging code ----------
    //

    /*

    //----------------------------------
    // NOTE : The following code was and can be used for debugging
    // I is not actually needed for anything to work.
    // If u want to use those BroadcastReceivers don forget to register and unregister them.
    // Just uncommenting wont make them work
    //----------------------------------


    private final BroadcastReceiver aclConnectionStateChanged = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
            {
                // Get the BluetoothDevice object from the Intent
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action))
            {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.e(TAG, "Device Disconnected " + Utils.getBluetoothDeviceString(device));
                devicesInRange.remove(device);
                for (HashMap.Entry<UUID, BluetoothServiceClient> set : serviceClients.entrySet())
                {
                    set.getValue().onDevicesInRangeChange(devicesInRange);
                }

            }
        }
    };

    private final BroadcastReceiver bondStateChangedReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            {
                BluetoothDevice mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (mDevice.getBondState() == BluetoothDevice.BOND_BONDED)
                {
                    Log.d(TAG, "mBroadcastReceiver4: BOND_BONDED.");
                }
                if (mDevice.getBondState() == BluetoothDevice.BOND_BONDING)
                {
                    Log.d(TAG, "mBroadcastReceiver4: BOND_BONDING.");
                }
                if (mDevice.getBondState() == BluetoothDevice.BOND_NONE)
                {
                    Log.d(TAG, "mBroadcastReceiver4: BOND_NONE.");
                }
            }
        }
    };

    private final BroadcastReceiver actionStateChangedReceiver = new BroadcastReceiver()
    {
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            // When discovery finds a device
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED))
            {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch (state)
                {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "actionStateChangedReceiver: STATE OFF");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "actionStateChangedReceiver: STATE TURNING OFF");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "actionStateChangedReceiver: STATE ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "actionStateChangedReceiver: STATE TURNING ON");
                        break;
                }
            }
        }
    };

    /**
     * BroadcastReceiver for Bluetooth ACTION_SCAN_MODE_CHANGED
     * <p>
     * (mainly for logging at this moment, can be disabled in production)

    private final BroadcastReceiver actionScanModeChangedReceiver = new BroadcastReceiver()
    {
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED))
            {
                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);
                switch (mode)
                {
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Log.d(TAG, "actionScanModeChangedReceiver: Discoverability Enabled.");
                        break;
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        Log.d(TAG, "actionScanModeChangedReceiver: Discoverability Disabled. Able to receive connections.");
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Log.d(TAG, "actionScanModeChangedReceiver: Discoverability Disabled. Not able to receive connections.");
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        Log.d(TAG, "actionScanModeChangedReceiver: Connecting....");
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Log.d(TAG, "actionScanModeChangedReceiver: Connected.");
                        break;
                }
            }
        }
    };


     */
}


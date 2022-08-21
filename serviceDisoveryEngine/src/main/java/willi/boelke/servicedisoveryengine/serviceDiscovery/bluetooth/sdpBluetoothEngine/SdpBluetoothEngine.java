package willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import willi.boelke.servicedisoveryengine.serviceDiscovery.Utils;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothDiscovery.SdpBluetoothDiscoveryEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothDiscovery.ServiceDiscoveryListener;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpConnectorThreads.BluetoothClientConnector;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpConnectorThreads.BluetoothConnectorThread;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpConnectorThreads.BluetoothServiceConnector;
import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;


/**
 * Establishes connections between bluetooth devices.
 * Utilizes {@link SdpBluetoothDiscoveryEngine} to discover
 * it grants access to all methods on the {@link SdpBluetoothDiscoveryEngine}
 * to simplify usage.
 *
 * At every given time there is oly one connection between two devices possible.
 *
 *
 * -- server side --
 *
 * For using this to advertise a service {@link SdpBluetoothServiceServer} should be
 * implemented to get notified about new client connections.
 * To start the advertisement of a service refer to {@link #startSDPService(ServiceDescription, SdpBluetoothServiceServer)}
 * which will also take the {@link SdpBluetoothServiceServer} as an listener.
 *
 *
 * -- client side side --
 *
 * To look discover services the interface {@link SdpBluetoothServiceClient} needs to  be
 * implemented, it serves as listener and provides callback functions the discovery
 * process. The discovery can be started using {@link #startSDPDiscoveryForService(ServiceDescription, SdpBluetoothServiceClient)}
 *
 * -- Service Descriptions --
 *
 * To identify services and provide additional information a
 *
 *
 * @see ServiceDescription
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

    private int discoverableTimeInSeconds;

    private final HashMap<ServiceDescription, SdpBluetoothServiceClient> serviceClients = new HashMap<>();
    /**
     *
     */
    private final List<BluetoothServiceConnector> runningServiceConnectors = new ArrayList<>();

    private final List<BluetoothClientConnector> runningClientConnectors  = new ArrayList<>();

    private final SdpBluetoothConnectionManager connectionManager;


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
        this.setDefaultDiscoverableTimeInSeconds(DEFAULT_DISCOVERABLE_TIME);
        SdpBluetoothDiscoveryEngine.initialize(context, adapter);
    }



    public void start()
    {
        if (this.bluetoothAdapter == null)
        {
            Log.e(TAG, "No BluetoothAdapter given, the probably does not support Bluetooth");
            return;
        }
        setupDiscoveryEngine();
    }

    /**
     * Starts the Discovery Engine and registers as listener
     * Sets the listener callbacks
     */
    private void setupDiscoveryEngine(){
        SdpBluetoothDiscoveryEngine.getInstance().start();
        SdpBluetoothDiscoveryEngine.getInstance().registerDiscoverListener(new ServiceDiscoveryListener()
        {
            @Override
            public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
            {
                serviceClients.get(description).onServiceDiscovered(host.getAddress(), description);
                launchConnectionAttempt(host, description);
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice device)
            {
                for (SdpBluetoothServiceClient client : serviceClients.values())
                {
                    client.onPeerDiscovered(device);
                }
            }
        });

    }

    //
    //  ----------  shutdown and teardown ----------
    //

    /**
     * This needs o be called o sop heSDP engine and unsubscribe all eh receivers
     */
    public void stop()
    {
        stopDeviceDiscovery();
        stopAllServiceConnector();
        stopAllClientConnectors();
        this.connectionManager.closeAllConnections();
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


    //
    //  ----------  standard (no sdp specific) bluetooth----------
    //

    /**
     * Makes the device discoverable for other bluetooth devices
     */
    public void startDiscoverable()
    {
        Log.d(TAG, "makeDiscoverable: making device discoverable for " + discoverableTimeInSeconds + " ms");
        //Discoverable Intent
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DEFAULT_DISCOVERABLE_TIME);
        discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // tests  wand we do add this
        context.startActivity(discoverableIntent);
    }

    /**
     * Starts discovering other devices
     */
    public boolean startDeviceDiscovery()
    {
        //----------------------------------
        // NOTE : todo: actually it would probably appropriate to make a common abstract superclass here
        //----------------------------------
       return  SdpBluetoothDiscoveryEngine.getInstance().startDeviceDiscovery();
    }

    /*
     * Ends the BluetoothDiscovery
     */
    public void stopDeviceDiscovery()
    {
       SdpBluetoothDiscoveryEngine.getInstance().stopDeviceDiscovery();
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
     * {@link SdpBluetoothEngine#stopSDPDiscoveryForService(ServiceDescription)
     * with the same UUID is called,  no matter hwo many devies will be disovered ill then.
     * (Or to make i short, this wont stop afer the first connecion was made)
     *
     * @param serviceUUID
     *         The UUID of the service to connect o
     */
    public void startSDPDiscoveryForService(ServiceDescription serviceDescription, SdpBluetoothServiceClient serviceClient)
    {
        // Adding the service client ot the list
        this.serviceClients.put(serviceDescription, serviceClient);
        SdpBluetoothDiscoveryEngine.getInstance().startSDPDiscoveryForService(serviceDescription);
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
     * {@link SdpBluetoothEngine#disconnectFromServicesWith(ServiceDescription)}
     *
     * @param description
     *  The service description
     */
    public void stopSDPDiscoveryForService(ServiceDescription description)
    {
        Log.d(TAG, "End service discovery for " + description);
        SdpBluetoothDiscoveryEngine.getInstance().stopSDPDiscoveryForService(description);

        // stopping all client connectors which may sill run and try to connect to this service
        ArrayList<BluetoothClientConnector> connectorsToClose = new ArrayList<>();
        for (BluetoothClientConnector clientConnector : this.runningClientConnectors)
        {
            if (clientConnector.getServiceDescription().equals(description))
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
        serviceClients.remove(description);
    }

    /**
     * This will close all client connections to the service specified he UUID.
     * This alone does not end the discovery of the given service, it just
     * closes already established connections.
     *
     * @see #stopSDPDiscoveryForService(ServiceDescription) to sop the discovery of a service
     *
     * @param description
     *         Description of teh service
     */
    public void disconnectFromServicesWith(ServiceDescription description)
    {
        this.connectionManager.closeAllClientConnectionsToService(description);
    }




    /**
     * Will be called when a device was found which hosts a service with the specified UUID
     *  * Else {@link SdpBluetoothServiceClient#shouldConnectTo(String, ServiceDescription)}
     *    Of the client with the matching UUID will be called to decide
     *    whether a connection should be established or not.
     *
     * @see #startClientThread(BluetoothDevice, ServiceDescription)
     *  Will be called when a connection should be established
     *
     * @param device
     *         device to connect to
     * @param description
     *         service oi connect to
     */
    private void launchConnectionAttempt(BluetoothDevice device, ServiceDescription description)
    {
        Log.d(TAG, "tryToConnectToService:  SDP service found, trying to connect");

        if (this.serviceClients.get(description).shouldConnectTo(device.getAddress(), description) && !isConnectionAlreadyEstablished(device.getAddress(), description))
        {
            Log.d(TAG, "tryToConnectToService: staring client thread to " + Utils.getRemoteDeviceString(device));
            startClientThread(device, description);
        }
        else
        {
            Log.d(TAG, "tryToConnectToService: should not connect to " + Utils.getRemoteDeviceString(device));
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
        SdpBluetoothDiscoveryEngine.getInstance().refreshNearbyServices();
    }


    //
    //  ----------  "server" side ----------
    //

    /**
     * Starts a new Bluetooth SDP Service, by opening
     * a RFCOMM Socket to a service record
     * with the given service name und UUID and starting a Thread to
     * accept connections.
     *
     * It checks if a service wih the given UUID is already running (Thread running and Socket open).
     * The same Service cant be registered twice.
     *
     * @see #stopSDPService(ServiceDescription) method to stop the service
     * @param description
     *         The service to be advertised
     * @return boolean
     *         false if a service with he given UUID is currently running else returns true
     *
     */
    public boolean startSDPService(ServiceDescription description, SdpBluetoothServiceServer server)
    {
        Log.d(TAG, "Staring new Service Service");
        if (this.serviceAlreadyRunning(description))
        {
            Log.e(TAG, "A service with the same UUID is already running, return ");
            return false;
        }
        this.startServiceThread(description, server);
        return true;
    }

    /**
     * Returns true if there is a {@link BluetoothServiceConnector} with the same UUID
     * running and present in {@see #runningServiceConnectors}
     * @param description
     *      The service to check
     */
    private boolean serviceAlreadyRunning(ServiceDescription description)
    {
        for (BluetoothServiceConnector connector : this.runningServiceConnectors)
        {
            if (connector.getService().equals(description))
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
     * @param description
     */
    public void stopSDPService(ServiceDescription description)
    {
        ArrayList<BluetoothServiceConnector> connectorsToEnd = new ArrayList<>();
        for (BluetoothServiceConnector serviceConnector : this.runningServiceConnectors)
        {
            if (serviceConnector.getService().equals(description))
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
     * Just wraps the {@link SdpBluetoothConnectionManager#isAlreadyConnected(String, ServiceDescription)}
     * method for easier access inside the SdpEngine.
     *
     * @param deviceAddress
     *      The Mac address of he device
     * @param description
     *      The UUID of the service
     * @return
     *      true if the connection exists, else false
     */
    private boolean isConnectionAlreadyEstablished(String deviceAddress, ServiceDescription description)
    {
        Log.d(TAG, "isConnectionAlreadyEstablished: checking if there is a connection established");
        return this.connectionManager.isAlreadyConnected(deviceAddress, description);
    }

    /**
     * Closes all connections / sockets to a service (specified by its UUID) running on this device.
     * Note that his just closes all current connections from clients to this device.
     * It does not to prevent the Service from accepting new connections from his point on.
     *
     * To stop new connexions from being made use {@link SdpBluetoothEngine#stopSDPService(ServiceDescription)}
     *
     * @param description
     *      The UUID of the service
     */
    public void disconnectFromClientsWithUUID(ServiceDescription description)
    {
        Log.d(TAG, "disconnectFromClientsWithUUID: closing client connections to service " + description);
        this.connectionManager.closeServiceConnectionsToServiceWithUUID(description);
    }

    //
    //  ----------  starting the connection threads ----------
    //


    private void startClientThread(BluetoothDevice device, ServiceDescription description)
    {
        Log.d(TAG, "Staring Client");
        BluetoothClientConnector bluetoothClientConnector = new BluetoothClientConnector(bluetoothAdapter, description, device, new BluetoothClientConnector.ConnectionEventListener()
        {
            @Override
            public void onConnectionFailed(UUID uuid, BluetoothConnectorThread failedConnector)
            {
                runningClientConnectors.remove(failedConnector);
            }

            @Override
            public void inConnectionSuccess(BluetoothConnectorThread bluetoothClientConnector, SdpBluetoothConnection connection)
            {
                connectionManager.addConnection(connection);
                serviceClients.get(description).onConnectedToService(connection);
                runningClientConnectors.remove(bluetoothClientConnector);
            }
        });
        this.runningClientConnectors.add(bluetoothClientConnector);
        bluetoothClientConnector.start();
    }

    /**
     * Starts a {@link BluetoothServiceConnector} for the given {@link ServiceDescription}
     * or more clearly for the UUID given by the service description.
     *
     * @param description
     *  The service Description
     * @param serviceServer
     *  Implementation of the server interface to handle established connections
     */
    private void startServiceThread(ServiceDescription description, SdpBluetoothServiceServer serviceServer)
    {
        BluetoothServiceConnector bluetoothServiceConnector = new BluetoothServiceConnector(bluetoothAdapter, description, new BluetoothServiceConnector.ConnectionEventListener()
        {
            @Override
            public void onConnectionFailed(UUID uuid, BluetoothConnectorThread failedClientConnector)
            {
                // Noting for now just go on
            }

            @Override
            public void inConnectionSuccess(BluetoothConnectorThread bluetoothClientConnector, SdpBluetoothConnection connection)
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
        SdpBluetoothDiscoveryEngine.getInstance().shouldCheckLittleEndianUuids(checkLittleEndianUuids);
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
     *  the time the device should be discoverable in seconds
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


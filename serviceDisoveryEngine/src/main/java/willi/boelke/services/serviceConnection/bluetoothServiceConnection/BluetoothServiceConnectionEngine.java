package willi.boelke.services.serviceConnection.bluetoothServiceConnection;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import willi.boelke.services.serviceConnection.bluetoothServiceConnection.connectorThreads.BluetoothClientConnector;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.connectorThreads.BluetoothConnectorThread;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.connectorThreads.BluetoothServiceConnector;
import willi.boelke.services.serviceDiscovery.ServiceDescription;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscovery;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryListener;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryVOne;

/**
 * Establishes connections between bluetooth devices.
 * It serves as a wrapper around the {@link BluetoothServiceDiscoveryVOne}
 * and grants access to its interface. Though enhances it by
 * being able to establish and manage connections {@link BluetoothConnection}
 * to discovered services.
 * <p>
 * Start the engine<br>
 * ------------------------------------------------------------<br>
 * The engine needs to be started to work, if the engine wont
 * react to any calls.
 * <p>
 * To start the engine call {@link #start(Context, BluetoothServiceDiscovery)} with the application context
 * or if adapter different from the default one should be provided
 * {@link #start(Context, BluetoothAdapter, BluetoothServiceDiscovery)}
 * <p>
 * The engine can be stopped using {@link #stop()}, this will
 * require a new engine start to use it again.
 * To get the current engine state call {@link #isRunning()}
 * <p>
 * Advertise Service<br>
 * ------------------------------------------------------------<br>
 * For using this to advertise a service {@link BluetoothServiceServer} needs to be
 * implemented to get notified about new client connections.
 * To start the advertisement of a service refer to
 * {@link #startSDPService(ServiceDescription, BluetoothServiceServer)}
 * which will also take the {@link BluetoothServiceServer} as an listener.
 * <p>
 * Discover Services<br>
 * ------------------------------------------------------------<br>
 * To look discover services the interface {@link BluetoothServiceClient} needs to be
 * implemented, it serves as listener and provides callback functions the discovery
 * process. The discovery can be started using
 * {@link #startSDPDiscoveryForService(ServiceDescription, BluetoothServiceClient)}
 * <p>
 * Service Descriptions<br>
 * ------------------------------------------------------------<br>
 * To identify services and provide additional information as and instance of
 * {@link ServiceDescription}in the case of Bluetooth a UUID will be generated
 * from Hashes of the Service attributes and exchanged using SDP.
 * <p>
 * By comparing it to the local Service descriptions after receiving such a UUID,
 * the service description can be resolved reliably. But since it is based around
 * Hashes a 100% accuracy can not be guaranteed.
 *
 * @author WilliBoelke
 * @see ServiceDescription
 */
public class BluetoothServiceConnectionEngine
{
    //
    //  ---------- static members ----------
    //

    public static final int DEFAULT_DISCOVERABLE_TIME = 120;
    public static final int MIN_DISCOVERABLE_TIME = 10;
    public static final int MAX_DISCOVERABLE_TIME = 300;

    /**
     * Instance of the class following the singleton pattern
     */
    private static BluetoothServiceConnectionEngine instance;

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
    private BluetoothAdapter bluetoothAdapter;

    private int discoverableTime;

    /**
     * Stores key value pairs of all service clients
     * and the services they look fo  r
     */
    private HashMap<ServiceDescription, BluetoothServiceClient> serviceClients = new HashMap<>();
    /**
     *
     */
    private final List<BluetoothServiceConnector> runningServiceConnectors = new CopyOnWriteArrayList<>(new ArrayList<>());

    private final List<BluetoothClientConnector> runningClientConnectors = new CopyOnWriteArrayList<>(new ArrayList<>());

    /**
     * The connection manager to store and ..well manage.. all opened connections
     */
    private final BluetoothConnectionManager connectionManager;

    /**
     * determines whether the engine is running or not
     *
     * @see #start(Context, BluetoothServiceDiscovery)
     * @see #start(Context, BluetoothAdapter, BluetoothServiceDiscovery)
     * @see #stop()
     * @see #isRunning()
     * @see #engineIsNotRunning()
     */
    private boolean engineRunning = false;

    /**
     * The discovery engine which is used
     * for service discovery
     */
    private BluetoothServiceDiscovery discoveryEngine;

    //
    //  ----------  initialisation and setup ----------
    //

    /**
     * Can be used to obtain the singleton instance.
     *
     * @return Returns the singleton instance of this class
     */
    public static BluetoothServiceConnectionEngine getInstance()
    {
        if (instance == null)
        {
            instance = new BluetoothServiceConnectionEngine();
        }
        return instance;
    }

    /**
     * Private constructor following the singleton pattern
     */
    private BluetoothServiceConnectionEngine()
    {
        this.connectionManager = new BluetoothConnectionManager();
        this.setDefaultDiscoverableTimeInSeconds(DEFAULT_DISCOVERABLE_TIME);
    }

    /**
     * Starts the engine and the given implementation
     * of {@link BluetoothServiceDiscovery}.
     * Using this will default to the default bluetooth adapter.
     * <b>Note: please provide application context, to prevent memory leaks</b>
     *
     * @param context
     *         the application context
     * @param discoveryEngine
     *         The {@link BluetoothServiceDiscovery} to be used for service discovery
     */
    public void start(Context context, BluetoothServiceDiscovery discoveryEngine)
    {
        //--- if no adapter we can stop right here ---//
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null)
        {
            Log.e(TAG, "Bluetooth adapter was null, the device probably does not support bluetooth - engine wont start");
            return;
        }

        start(context, adapter, discoveryEngine);
    }

    /**
     * Starts the engine and the given implementation
     * of {@link BluetoothServiceDiscovery}.
     * <b>Note: please provide application context, to prevent memory leaks</b>
     *
     * @param context
     *         the application context
     * @param discoveryEngine
     *         The {@link BluetoothServiceDiscovery} to be used for service discovery.
     * @param adapter
     *         The bluetooth adapter to be used
     */
    public void start(Context context, BluetoothAdapter adapter, BluetoothServiceDiscovery discoveryEngine)
    {
        //--- if no adapter we can stop right here ---//

        if (adapter == null)
        {
            Log.e(TAG, "start: Bluetooth adapter was null, the device probably does not support bluetooth - engine wont start");
            return;
        }
        if (!adapter.isEnabled())
        {
            Log.e(TAG, "start: Bluetooth not enabled");
            return;
        }

        this.bluetoothAdapter = adapter;
        this.context = context.getApplicationContext();

        //--- starting the discovery engine ---//
        Log.e(TAG, "start: " + discoveryEngine);
        this.discoveryEngine = discoveryEngine;
        this.discoveryEngine.start(context, adapter);
        this.discoveryEngine.registerDiscoverListener(new BluetoothServiceDiscoveryListener()
        {
            @Override
            public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
            {
                try
                {
                    Objects.requireNonNull(serviceClients.get(description)).onServiceDiscovered(host, description);
                    launchConnectionAttempt(host, description);
                }
                catch (NullPointerException e)
                {
                    //service client may be null - stop
                    Log.e(TAG, "onServiceDiscovered: service client was null - cant notify");
                    serviceClients.remove(description);
                }
            }

            @Override
            public void onPeerDiscovered(BluetoothDevice device)
            {
                for (BluetoothServiceClient client : serviceClients.values())
                {
                    client.onPeerDiscovered(device);
                }
            }
        });

        this.engineRunning = true;
    }


    //
    //  ----------  shutdown and teardown ----------
    //

    /**
     * This stops the SDP engine completely
     * All registered services will be cleared
     */
    public void stop()
    {
        stopDeviceDiscovery();
        stopAllServiceConnector();
        stopAllClientConnectors();
        this.connectionManager.closeAllConnections();
        this.serviceClients = new HashMap<>();
        Log.e(TAG, "stop: " + this.discoveryEngine);
        this.discoveryEngine.stop();
        this.engineRunning = false;
    }

    /**
     * Stops the engine and resets the singleton instance to "null"
     * this is mostly used for testing
     * does the same to the SdpBluetoothDiscoveryEngine it uses
     */
    protected void teardownEngine()
    {
        Log.e(TAG, "teardownEngine: ---resetting engine---");
        this.stop();
        instance = null;
    }

    /**
     * Stops all running {@link BluetoothClientConnector}s
     * which are currently trying to connect to a remote device
     *
     * @see #runningClientConnectors
     */
    private void stopAllClientConnectors()
    {
        for (BluetoothClientConnector connector : this.runningClientConnectors)
        {
            connector.cancel();
        }
    }

    /**
     * Stop all {@link BluetoothServiceConnector}s
     * which are currently running and accepting connections.
     *
     * @see #runningServiceConnectors
     */
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
     * The time the device will be visible can be specified by {@link #setDefaultDiscoverableTimeInSeconds(int)}
     */
    public void startDiscoverable()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "startDiscoverable: the engine was not initialized or bluetooth is not available");
            return;
        }

        Log.d(TAG, "makeDiscoverable: making device discoverable for " + discoverableTime + " ms");
        //Discoverable Intent
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoverableTime);
        discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // tests  wand we do add this
        context.startActivity(discoverableIntent);
    }

    /**
     * Starts a bluetooth device discovery
     * this si required to find services,
     * after a device discovery finished the discovered
     * devices will be queried for services running on them.
     * <p>
     * The device discovery will run for around 12 seconds
     * it can be stopped at any time using {@link #stopDeviceDiscovery()}
     */
    public boolean startDeviceDiscovery()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "startDiscoverable: the engine was not initialized or bluetooth is not available");
            return false;
        }
        return this.discoveryEngine.startDeviceDiscovery();
    }

    /**
     * This stops the Bluetooth device discovery,
     * no further devices will be discovered.
     * The discovery can be restarted at any time using
     * {@link #startDeviceDiscovery()}
     */
    public void stopDeviceDiscovery()
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "startDiscoverable: the engine was not initialized or bluetooth is not available");
            return;
        }
        this.discoveryEngine.stopDeviceDiscovery();
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
     * {@link BluetoothServiceConnectionEngine#stopSDPDiscoveryForService(ServiceDescription)
     * with the same UUID is called,  no matter hwo many devies will be disovered ill then.
     * (Or to make i short, this wont stop afer the first connecion was made)
     *
     * @param serviceUUID
     *         The UUID of the service to connect o
     */
    public void startSDPDiscoveryForService(ServiceDescription serviceDescription, BluetoothServiceClient serviceClient)
    {
        // Adding the service client ot the list
        this.serviceClients.put(serviceDescription, serviceClient);
        this.discoveryEngine.startDiscoveryForService(serviceDescription);
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
     * <p>
     * If all connections to a service should be closed please refer to
     * {@link BluetoothServiceConnectionEngine#disconnectFromServicesWith(ServiceDescription)}
     *
     * @param description
     *         The service description
     */
    public void stopSDPDiscoveryForService(ServiceDescription description)
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "stopSDPDiscoveryForService: the engine is not running, wont stop");
        }
        Log.d(TAG, "End service discovery for " + description);
        this.discoveryEngine.stopDiscoveryForService(description);

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
     * @param description
     *         Description of teh service
     *
     * @see #stopSDPDiscoveryForService(ServiceDescription) to sop the discovery of a service
     */
    public void disconnectFromServicesWith(ServiceDescription description)
    {
        Log.d(TAG, "disconnectFromServicesWith: disconnecting from servers with " + description);
        this.connectionManager.closeAllClientConnectionsToService(description);
    }


    /**
     * Will be called when a device was found which hosts a service with the specified UUID
     * * Else {@link BluetoothServiceClient#shouldConnectTo(BluetoothDevice, ServiceDescription)}
     * Of the client with the matching UUID will be called to decide
     * whether a connection should be established or not.
     *
     * @param device
     *         device to connect to
     * @param description
     *         service oi connect to
     *
     * @see #startClientThread(BluetoothDevice, ServiceDescription)
     *         Will be called when a connection should be established
     */
    private void launchConnectionAttempt(BluetoothDevice device, ServiceDescription description)
    {
        Log.d(TAG, "tryToConnectToService:  SDP service found, trying to connect");

        if (Objects.requireNonNull(this.serviceClients.get(description))
                .shouldConnectTo(device, description)
                && !isConnectionAlreadyEstablished(device.getAddress(), description))
        {
            Log.d(TAG, "tryToConnectToService: staring client thread to " + device);
            startClientThread(device, description);
        }
        else
        {
            Log.d(TAG, "tryToConnectToService: should not connect to " + device);
        }
    }

    /**
     * This will start a refreshing process
     * of all nearby services.
     * This also will cause the device discovery to stop.
     * <p>
     * Calling {@link #startDeviceDiscovery()} while this is running is not recommended.
     */
    public void refreshNearbyServices()
    {
        if (this.engineIsNotRunning())
        {
            Log.e(TAG, "refreshNearbyServices: the engine is not running - wont refresh");
            return;
        }
        this.discoveryEngine.refreshNearbyServices();
    }


    //
    //  ----------  "server" side ----------
    //

    /**
     * Starts a new Bluetooth SDP Service, by opening
     * a RFCOMM Socket to a service record
     * with the given service name und UUID and starting a Thread to
     * accept connections.
     * <p>
     * It checks if a service wih the given UUID is already running (Thread running and Socket open).
     * The same Service cant be registered twice.
     *
     * @param description
     *         The service to be advertised
     *
     * @return boolean
     *         false if a service with he given UUID is currently running else returns true
     *
     * @see #stopSDPService(ServiceDescription) method to stop the service
     */
    public boolean startSDPService(ServiceDescription description, BluetoothServiceServer server)
    {
        Log.d(TAG, "Staring new Service Service");
        if (engineIsNotRunning())
        {
            Log.e(TAG, "startSDPService: engine is not running - wont start");
            return false;
        }
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
     *
     * @param description
     *         The service to check
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
     *
     * @param description
     *         The description of the service
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
     * <p>
     * Just wraps the {@link BluetoothConnectionManager#isAlreadyConnected(String, ServiceDescription)}
     * method for easier access inside the SdpEngine.
     *
     * @param deviceAddress
     *         The Mac address of he device
     * @param description
     *         The UUID of the service
     *
     * @return true if the connection exists, else false
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
     * <p>
     * To stop new connexions from being made use {@link BluetoothServiceConnectionEngine#stopSDPService(ServiceDescription)}
     *
     * @param description
     *         The UUID of the service
     */
    public void disconnectFromClientsWithUUID(ServiceDescription description)
    {
        Log.d(TAG, "disconnectFromClientsWithUUID: closing client connections to service " + description);
        this.connectionManager.closeServerConnectionsToService(description);
    }

    //
    //  ----------  starting the connection threads ----------
    //

    /**
     * Starts a {@link BluetoothClientConnector} to attempt
     * to open a socket connection to the remote service/device.
     * Also implements the necessary callback methods
     * to react to a successful connection or a failure.
     *
     * @param device
     *         The remote device / host
     * @param description
     *         The description of the serve to connect to (containing the service UUID)
     */
    private void startClientThread(BluetoothDevice device, ServiceDescription description)
    {
        Log.d(TAG, "Staring Client");
        BluetoothClientConnector bluetoothClientConnector = new BluetoothClientConnector(description, device, new BluetoothClientConnector.ConnectionEventListener()
        {
            @Override
            public void onConnectionFailed(UUID uuid, BluetoothConnectorThread failedConnector)
            {
                BluetoothClientConnector failedClient = (BluetoothClientConnector) failedConnector;
                failedClient.cancel();
                runningClientConnectors.remove(failedClient);
            }

            @Override
            public void onConnectionSuccess(BluetoothConnectorThread bluetoothClientConnector, BluetoothConnection connection)
            {
                BluetoothClientConnector client = (BluetoothClientConnector) bluetoothClientConnector;
                connectionManager.addConnection(connection);
                Objects.requireNonNull(serviceClients.get(description)).onConnectedToService(connection);
                runningClientConnectors.remove(client);
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
     *         The service Description
     * @param serviceServer
     *         Implementation of the server interface to handle established connections
     */
    private void startServiceThread(ServiceDescription description, BluetoothServiceServer serviceServer)
    {
        BluetoothServiceConnector bluetoothServiceConnector = new BluetoothServiceConnector(bluetoothAdapter, description, new BluetoothServiceConnector.ConnectionEventListener()
        {
            @Override
            public void onConnectionFailed(UUID uuid, BluetoothConnectorThread failedConnector)
            {
                BluetoothServiceConnector failedServer = (BluetoothServiceConnector) failedConnector;
                failedServer.cancel();
                runningServiceConnectors.remove(failedServer);
                startServiceThread(description, serviceServer);
                Log.e(TAG, "onConnectionFailed: server socket died , trying to restart");
                // this probably should not run indefinitely - keep an eye on the behavior
            }

            @Override
            public void onConnectionSuccess(BluetoothConnectorThread bluetoothClientConnector, BluetoothConnection connection)
            {
                connectionManager.addConnection(connection);
                serviceServer.onClientConnected(connection);
            }
        });

        bluetoothServiceConnector.start();
        this.runningServiceConnectors.add(bluetoothServiceConnector);
    }

    //
    //  ---------- config ----------
    //

    /**
     * On some devices service UUIDs will be
     * received in a little endian format.
     * The engine will by default reverse UUIDs
     * and check them as well, preventing a services
     * not being found.
     * <p>
     * Set this to `false` to disable this behaviour.
     *
     * @param checkLittleEndianUuids
     *         determines whether little endian UUIDs should be checked or not
     */
    public void shouldCheckLittleEndianUuids(boolean checkLittleEndianUuids)
    {
        this.discoveryEngine.shouldCheckLittleEndianUuids(checkLittleEndianUuids);
    }

    /**
     * Set the time the device should be made discoverable
     * The max value here is 300 (seconds) and the
     * min value is 10 (seconds).
     * The value will be capped to fit this interval.
     * <p>
     * If this method is not used, the discoverable time will
     * be set to the default of 120 seconds.
     * <p>
     * There are also predefined values which an be used here :
     *
     * @param seconds
     *         the time the device should be discoverable in seconds
     *
     * @see BluetoothServiceConnectionEngine#DEFAULT_DISCOVERABLE_TIME
     * @see BluetoothServiceConnectionEngine#MAX_DISCOVERABLE_TIME
     * @see BluetoothServiceConnectionEngine#MIN_DISCOVERABLE_TIME
     */
    public void setDefaultDiscoverableTimeInSeconds(int seconds)
    {
        if (seconds > MAX_DISCOVERABLE_TIME)
        {
            seconds = MAX_DISCOVERABLE_TIME;
        }
        if (seconds < MIN_DISCOVERABLE_TIME)
        {
            seconds = MIN_DISCOVERABLE_TIME;
        }
        this.discoverableTime = seconds;
    }

    /**
     * Returns true if the engine is not running
     *
     * @return A boolean determining id the engine isn't running
     *
     * @see #start(Context, BluetoothServiceDiscovery)
     * @see #stop()
     * @see #engineRunning
     */
    private boolean engineIsNotRunning()
    {
        return !this.engineRunning;
    }

    /**
     * Returns true if the engine was started successfully
     * using {@link #start(Context, BluetoothServiceDiscovery)},
     * This needs a working BluetoothAdapter to be available on the device
     *
     * @return running state of the engine
     */
    public boolean isRunning()
    {
        return this.engineRunning;
    }
}


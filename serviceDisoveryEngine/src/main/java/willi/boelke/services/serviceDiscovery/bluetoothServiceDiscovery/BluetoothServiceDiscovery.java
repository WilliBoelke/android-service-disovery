package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import willi.boelke.services.serviceDiscovery.IServiceDiscoveryEngine;
import willi.boelke.services.serviceDiscovery.ServiceDescription;

public interface BluetoothServiceDiscovery extends IServiceDiscoveryEngine
{

    /**
     * Starts the discovery engine
     * a context needs to be supplied
     * Optionally a bluetooth adapter can also be
     * specified by calling {@link #start(Context, BluetoothAdapter)}
     *
     * @param context
     *         the application context
     */
    @Override
    void start(Context context);

    /**
     * Starts the engine
     */
    void start(Context context, BluetoothAdapter adapter);

    //
    //  ----------  shutdown and teardown ----------
    //

    /**
     * Stops the service discovery
     * Registered services will be cleared
     * and listeners will be removed
     */
    @Override
    void stop();

    //
    //  ----------  standard (no sdp specific) bluetooth----------
    //

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
    boolean startDeviceDiscovery();

    /*
     * Ends the bluetooth device
     * discovery
     */
    void stopDeviceDiscovery();


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
    void startDiscoveryForService(ServiceDescription description);

    /**
     * This will start a refreshing process
     * of all nearby services.
     * This also will cause the device discovery to stop.
     * <p>
     * Calling {@link #startDeviceDiscovery()} while this is running is not recommended.
     */
    void refreshNearbyServices();

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
    void registerDiscoverListener(BluetoothServiceDiscoveryListener listener);

    void unregisterDiscoveryListener(BluetoothServiceDiscoveryListener listener);

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
    void shouldCheckLittleEndianUuids(boolean checkLittleEndianUuids);
}

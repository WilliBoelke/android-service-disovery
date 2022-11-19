package willi.boelke.services.serviceDiscovery;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;

import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryVOne;
import willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery.WifiDirectServiceDiscoveryEngine;


/**
 * Abstract class for {@link WifiDirectServiceDiscoveryEngine}
 * and  {@link BluetoothServiceDiscoveryVOne}
 * <p>
 * Allows the
 */
public abstract class ServiceDiscoveryEngine implements IServiceDiscoveryEngine
{


    //
    //  ----------  instance variables ----------
    //

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    /**
     * List containing the services (ServiceDescriptions)
     * of the services registered through {@link #startDiscoveryForService(ServiceDescription)}
     */
    protected ArrayList<ServiceDescription> servicesToLookFor = new ArrayList<>();

    /**
     * Gives information about the state of the engine, will
     * set to true if the engine was started {@link  #start(Context)}
     * and false when its stopped {@link #stop()}
     *
     * can be accessed through {@link #isRunning()}
     */
    protected boolean engineRunning = false;

    /**
     * If this is true all listeners
     */
    protected boolean notifyAboutAllServices = false;

    protected boolean engineIsNotRunning()
    {
        return !engineRunning;
    }

    /**
     * Returns true if the engine was started successfully
     * This needs a working BluetoothAdapter to be available on the device
     *
     * @return running state of the engine
     */
    @Override
    public boolean isRunning()
    {
        return engineRunning;
    }

    @Override
    public abstract void start(Context context);

    @Override
    public abstract void stop();

    //
    //  ----------  add service for discovery ----------
    //

    @Override
    public void startDiscoveryForService(ServiceDescription description)
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "startDiscoveryForService: engine is not running - wont start");
        }
        Log.d(TAG, "Starting service discovery");
        // Are we already looking for he service?
        if (this.isServiceAlreadyInDiscovery(description))
        {
            Log.d(TAG, "startDiscoveryForService: Service discovery already running ");
            return;
        }
        // Adding the service to  be found in the future
        this.servicesToLookFor.add(description);

        // subclasses call
        onNewServiceToDiscover(description);
    }

    /**
     * Called whenever a new service was added to be discovery
     * though {@link #startDiscoveryForService(ServiceDescription)}
     *
     * @param description
     *         the description of the service
     */
    protected abstract void onNewServiceToDiscover(ServiceDescription description);

    //
    //  ----------  remove service from discovery ----------
    //

    @Override
    public void stopDiscoveryForService(ServiceDescription description)
    {
        if (engineIsNotRunning())
        {
            Log.e(TAG, "startDiscoveryForService: engine is not running - wont start");
        }
        Log.d(TAG, "startDiscoveryForService: End service discovery for service with UUID " + description.toString());
        // removing from list of services
        this.servicesToLookFor.remove(description);

        // subclasses call
        onServiceRemoveFromDiscovery(description);
    }


    /**
     * called whenever a  service was removed to the discovery
     * can be implemented in subclasses
     *
     * @param description
     *         the description of the service
     */
    protected abstract void onServiceRemoveFromDiscovery(ServiceDescription description);

    /**
     * Checks if the service description is already in {@link #servicesToLookFor} list
     *
     * @param description
     *         Description of the service to look for
     *
     * @return false if the service is not in the list, else returns true
     */
    protected boolean isServiceAlreadyInDiscovery(ServiceDescription description)
    {
        return servicesToLookFor.contains(description);
    }

    /**
     * Setting this to true will notify all receivers about all
     * discovered services and not just the ones which where
     * looked for.
     * Services for which no service description is present
     * will just contain the UUID and empty service attributes
     * <p>
     * for the services which are registered though
     * service attributes can be resolved and will be available
     *
     * @param all
     *         boolean - true to notify about all services, false to just notify about the ones
     *         given through {@link #startDiscoveryForService(ServiceDescription)}
     */
    @Override
    public void notifyAboutAllServices(boolean all)
    {
        Log.d(TAG, "notifyAboutAllServices: notifying about all service = " + all);
        this.notifyAboutAllServices = all;
    }
}

package willi.boelke.services.serviceDiscovery;

import android.content.Context;

/**
 * Interface for a service discovery engine.
 * gives methods for starting and stopping the engine as well as
 * for starting and stopping the discovery of a service.
 */
public interface IServiceDiscoveryEngine
{

    /**
     * Starts the engine
     *
     * @param context
     *         The application context
     */
    boolean start(Context context);

    /**
     * Stops the engine,
     * to use the engine again it needs to be started again
     */
    void stop();

    /**
     * This registers a {@link ServiceDescription} for the discovery
     *
     * @param description
     *         The description of the service to be discovered
     */
    void startDiscoveryForService(ServiceDescription description);

    /**
     * Unregisters a service from the discovery which was previously
     * registered through
     * {@link #startDiscoveryForService(ServiceDescription)}
     *
     * @param description
     *         The description which with
     *         the service ahs been registered.
     */
    void stopDiscoveryForService(ServiceDescription description);

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
    void notifyAboutAllServices(boolean all);

    /**
     * To determine whether the engine was started or not
     *
     * @return true if the engine was started
     *         and is running, else returns false
     */
    boolean isRunning();
}

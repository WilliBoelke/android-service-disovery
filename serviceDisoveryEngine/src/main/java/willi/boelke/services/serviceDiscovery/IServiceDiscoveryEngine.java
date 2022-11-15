package willi.boelke.services.serviceDiscovery;

import android.content.Context;


public interface IServiceDiscoveryEngine
{
    boolean isRunning();

    void start(Context context);

    void stop();

    //
    //  ----------  add service for discovery ----------
    //

    void startDiscoveryForService(ServiceDescription description);

    //
    //  ----------  remove service from discovery ----------
    //

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
}

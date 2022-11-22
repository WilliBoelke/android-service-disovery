package willi.boelke.service_discovery_demo.controller;


import willi.boelke.services.serviceConnection.ServiceConnection;

public interface ControllerListener<T extends ServiceConnection, D>
{
    void onMessageChange(String message);

    void onNewNotification(String notification);

    void onConnectionLost(T connection);

    void onNewConnection(T connection);

    void onNewDiscovery(D device);
}

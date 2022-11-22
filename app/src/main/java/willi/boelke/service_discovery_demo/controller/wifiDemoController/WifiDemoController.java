package willi.boelke.service_discovery_demo.controller.wifiDemoController;

import android.Manifest;
import android.net.wifi.p2p.WifiP2pDevice;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.util.concurrent.CopyOnWriteArrayList;

import willi.boelke.service_discovery_demo.controller.ControllerListener;
import willi.boelke.service_discovery_demo.controller.ReadThread;
import willi.boelke.service_discovery_demo.controller.WriteThread;
import willi.boelke.services.serviceConnection.wifiDirectServiceConnection.WifiConnection;
import willi.boelke.services.serviceConnection.wifiDirectServiceConnection.WifiDirectConnectionEngine;
import willi.boelke.services.serviceConnection.wifiDirectServiceConnection.WifiDirectPeer;
import willi.boelke.services.serviceDiscovery.ServiceDescription;


/**
 * Demo implementation of a SdpWifiPeer.
 * <p>
 * A peer can either be Group Owner or Client in a Wifi Direct Group.
 * In this demo implementation the GO will periodically write messages
 * tho the clients in the group.
 * <p>
 * other implementations may do it the other wa around or implement a
 * different protocol for communications.
 * Or even the GO routing messages between the clients.
 *
 * @author Willi Boelke
 */
public class WifiDemoController implements WifiDirectPeer
{
    private static final String GROUP_OWNER_DEFAULT_MESSAGE = "writing to clients...";

    /**
     * UUID of the service being advertised / looked for using this controller
     */
    private final ServiceDescription description;
    private final CopyOnWriteArrayList<WifiConnection> connections = new CopyOnWriteArrayList<>();
    private ControllerListener<WifiConnection, WifiP2pDevice> listener;
    private ReadThread<WifiConnection, WifiP2pDevice> readThread;
    private WriteThread<WifiConnection, WifiP2pDevice> writeThread;

    /**
     * Indicating whether a role (Go or client) was assigned before or not.
     * <p>
     * This will be flipped as soon as a role
     * was assigned through the callbacks in the WifiConnectionEngine.
     */
    private boolean gotRoleAssigned = false;

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();


    //
    //  ----------  constructor and initialisation ----------
    //

    /**
     * Public constructor
     *
     * @param description
     *         A service description to identify the service and prove additional information about it.
     */
    public WifiDemoController(ServiceDescription description)
    {
        this.description = description;
    }

    public void startService()
    {
        this.writeThread = new WriteThread<>(this.connections, this.description, this.listener);
        this.readThread = new ReadThread<>(this.connections, this.listener);
        WifiDirectConnectionEngine.getInstance().registerService(this.description, this);
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void stop()
    {
        WifiDirectConnectionEngine.getInstance().unregisterService();
        WifiDirectConnectionEngine.getInstance().stopDiscovery();
        for (WifiConnection connection : connections)
        {
            connection.close();
        }
        WifiDirectConnectionEngine.getInstance().disconnectFromGroup();
        try
        {
            this.writeThread.cancel();
        }
        catch (NullPointerException e)
        {
            Log.d(TAG, "stopService: -thread was not initialized");
        }
        try
        {
            this.readThread.cancel();
        }
        catch (NullPointerException e)
        {
            Log.d(TAG, "stopService: thread was not initialized");
        }
        this.readThread = null;
        this.writeThread = null;
        for (WifiConnection connection : this.connections)
        {
            this.listener.onConnectionLost(connection);
        }
        this.connections.clear();
        this.gotRoleAssigned = false;
        this.listener.onMessageChange("");
        this.listener.onNewNotification("Stopped client - disconnected");
    }


    @Override
    public void onServiceDiscovered(WifiP2pDevice device, ServiceDescription description)
    {
        listener.onNewNotification("Found service " + description.getServiceUuid());
        listener.onNewDiscovery(device);
    }


    @Override
    public void onBecameGroupOwner()
    {
        Log.d(TAG, "onBecameGroupOwner: became group owner");
        if (!this.gotRoleAssigned) // just the first time
        {
            Log.d(TAG, "onBecameGroupOwner: first time - starting write thread");
            // lets notify subscribers
            listener.onMessageChange(GROUP_OWNER_DEFAULT_MESSAGE);
            listener.onNewNotification(GROUP_OWNER_DEFAULT_MESSAGE);
            writeThread.start();

            this.gotRoleAssigned = true;
        }
    }

    @Override
    public void onBecameGroupClient()
    {
        Log.d(TAG, "onBecameGroupClient: became group owner");
        if (!this.gotRoleAssigned) // just the first time
        {
            Log.d(TAG, "onBecameGroupClient: first time - starting read thread");
            // lets notify subscribers
            listener.onNewNotification("Became GroupClient");
            readThread.start();

            this.gotRoleAssigned = true;
        }
    }

    @Override
    public void onConnectionEstablished(WifiConnection connection)
    {
        Log.d(TAG, "onConnectionEstablished: got new connection : " + connection);
        this.connections.add(connection);
        this.listener.onNewConnection(connection);
        Log.e(TAG, "onConnectionEstablished: " + connections);
    }

    @Override
    public boolean shouldConnectTo(WifiP2pDevice device, ServiceDescription description)
    {
        return true;
    }

    public void setListener(ControllerListener<WifiConnection, WifiP2pDevice> listener)
    {
        Log.e(TAG, "setListener: listener set ");
        this.listener = listener;
    }
}

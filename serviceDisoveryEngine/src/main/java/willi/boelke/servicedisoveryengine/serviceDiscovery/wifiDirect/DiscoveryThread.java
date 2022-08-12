package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect;

import android.annotation.SuppressLint;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.util.Log;

import java.util.Map;

import willi.boelke.servicedisoveryengine.serviceDiscovery.Utils;

/**
 * Discovers nearby services periodically as long as {@link #isDiscovering}
 * is `true`.
 * The discovery will be restarted every 5 seconds, to ensure the discovery
 * of nearby services.
 *
 * There will be no filter applied to discovered services,  this thread does not care
 * what he discovers.
 * Also a connection wont be established from here.
 *
 * Every advertised and discovered service will be passed to {@link SdpWifiEngine#onServiceDiscovered(WifiP2pDevice, Map, String)}
 * only there the service records will be evaluated and a connection may be establish.
 *
 * @author {willi boelke}
 */
public class DiscoveryThread extends Thread
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private final int WAIT_BEFORE_RETRY = 10000;

    private final SdpWifiEngine engine;

    private boolean isDiscovering;

    private WifiP2pManager manager;

    private WifiP2pManager.Channel channel;

    private Thread thread;

    //
    //  ---------- constructor and initialisation ----------
    //

    public DiscoveryThread(WifiP2pManager manager, WifiP2pManager.Channel channel, SdpWifiEngine engine){
            this.manager = manager;
            this.channel = channel;
            this.engine = engine;
    }
    

    @Override
    public void run()
    {
        isDiscovering = true;
        this.thread = currentThread();

        //--- setting up callbacks ---//
        setupDiscoveryCallbacks();

        while (isDiscovering){
            try
            {
                startDiscovery();
                pause(15000);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
        Log.d(TAG, "run: discovery thread ended final");
    }


    //
    //  ---------- discovery ----------
    //

    private void setupDiscoveryCallbacks(){

        //--- TXT Record listener ---//

        WifiP2pManager.DnsSdTxtRecordListener txtListener = (fullDomain, record, device) ->
        {
            Log.d(TAG, "run: found service record: on  " + Utils.getRemoteDeviceString(device) + " record: " + record);
            // don't make a connection here engine.onServiceDiscovered(device, record, fullDomain);
        };

        //--- Service response listener - gives additional service info ---//

        WifiP2pManager.DnsSdServiceResponseListener servListener = (instanceName, registrationType, resourceType) ->
        {
            //----------------------------------
            // NOTE : Right now i don't see any
            // use in the information give here,
            // though i will let it here -
            // for logging and for easy later use
            //----------------------------------
            Log.d(TAG, "run: bonjour service available :\n" +
                    "name =" + instanceName +"\n"+
                    "registration type = " + registrationType +"\n" +
                    "resource type = " + resourceType);
        };

        //--- setting the listeners ---//
        
        manager.setDnsSdResponseListeners(channel, servListener, txtListener);
    }


    @SuppressLint("MissingPermission")
    private void startDiscovery(){

        //--- clearing already running service requests ---//

        manager.clearServiceRequests(channel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(TAG, "Cleared local service requests");

                //--- adding service requests (again) ---//
                
                //----------------------------------
                // NOTE : Bonjour services are used,
                // so WifiP2pDnsSdServiceRequests are
                // used here.
                //----------------------------------
                manager.addServiceRequest(channel, WifiP2pDnsSdServiceRequest.newInstance(), new WifiP2pManager.ActionListener()
                {
                    @Override
                    public void onSuccess()
                    {
                        //--- starting the service discovery (again) ---//

                        manager.discoverServices(channel, new WifiP2pManager.ActionListener()
                        {
                            @Override
                            public void onSuccess()
                            {
                                Log.d(TAG, "Started service discovery");
                            }

                            @Override
                            public void onFailure(int code)
                            {
                                Log.d(TAG, "failed to start service discovery");
                                onServiceDiscoveryFailure();
                            }
                        });
                    }

                    @Override
                    public void onFailure(int code)
                    {
                        Log.d(TAG, "failed to add service discovery request");
                        onServiceDiscoveryFailure();
                    }
                });
            }
            @Override
            public void onFailure(int code)
            {
                Log.d(TAG, "Failed to clear local service requests");
                onServiceDiscoveryFailure();
            }
        });
    }

    private void pause(int pause) throws InterruptedException
    {
        synchronized(this)
        {
            try
            {
                this.wait(pause);
            }
            catch (InterruptedException e)
            {
                Log.d(TAG, "pause: interrupted wait");
                throw e; // throw again, this will be caught in `run`
            }
        }
    }

    private void onServiceDiscoveryFailure(){
        //----------------------------------
        // NOTE : There doesn't seem to be
        // much i can do here, wifi could be restarted
        // (of / on) but that's all
        //----------------------------------
    }


    //
    //  ---------- others ----------
    //

    public void cancel(){
        Log.d(TAG, "cancel: canceling service discovery");
        this.thread.interrupt();
        this.isDiscovering = false;
        manager.clearServiceRequests(channel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(TAG, "cancel: cleared service requests");
            }

            @Override
            public void onFailure(int reason)
            {
                Utils.logReason("cancel: could not clear service request ", reason);
            }
        });
        Log.d(TAG, "cancel: canceled service discovery");
    }

    public boolean isDiscovering(){
        return this.isDiscovering;
    }
}

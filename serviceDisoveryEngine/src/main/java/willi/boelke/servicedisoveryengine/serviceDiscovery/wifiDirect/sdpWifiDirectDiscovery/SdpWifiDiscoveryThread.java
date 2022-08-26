package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.sdpWifiDirectDiscovery;

import android.annotation.SuppressLint;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import willi.boelke.servicedisoveryengine.serviceDiscovery.Utils;
import willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription.ServiceDescription;
import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.sdpWifiEngine.SdpWifiEngine;

/**
 * Discovers nearby services periodically as long as {@link #isDiscovering}
 * is `true`.
 * The discovery will be restarted 3 times with 7 second gaps in between, to ensure the discovery
 * of nearby services.
 *
 * There will be no filter applied to discovered services, this thread does not care
 * what it discovers.
 * Also a connection wont be established from here.
 *
 * Every discovered service will be passed to {@link SdpWifiDirectDiscoveryEngine#onServiceDiscovered(WifiP2pDevice, Map, String)}
 * only there the service records will be evaluated.
 *
 * @author {willi boelke}
 */
class SdpWifiDiscoveryThread extends Thread
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private final int WAIT_BEFORE_RETRY = 10000;

    private final int TRIES = 1;

    private int retries;

    private int runningTries = 0;

    private final SdpWifiDirectDiscoveryEngine engine;

    private boolean isDiscovering;

    private final WifiP2pManager manager;

    private final WifiP2pManager.Channel channel;

    private Thread thread;

    //
    //  ---------- constructor and initialisation ----------
    //

    /**
     * Constructor
     *
     * @param manager
     * The WifiP2P manager
     * @param channel
     *  The Channel
     * @param engine
     * The WifiDirectDiscoveryEngine to callback
     */
    public SdpWifiDiscoveryThread(WifiP2pManager manager, WifiP2pManager.Channel channel, SdpWifiDirectDiscoveryEngine engine){
            this.manager = manager;
            this.channel = channel;
            this.engine = engine;
            this.retries = TRIES;
    }

    public SdpWifiDiscoveryThread(WifiP2pManager manager, WifiP2pManager.Channel channel, SdpWifiDirectDiscoveryEngine engine, int retries){
        this.manager = manager;
        this.channel = channel;
        this.engine = engine;
        this.retries = retries;
    }



    //
    //  ----------  discovery ----------
    //


    @Override
    public void run()
    {
        Log.d(TAG, "run: starting discovery thread");
        //--- setting to running ---//

        isDiscovering = true;
        this.thread = currentThread();

        //--- setting up callbacks ---//

        setupDiscoveryCallbacks();

        //--- discovery loop ---//

        while (isDiscovering && runningTries < retries){
            try
            {
                runningTries++;
                startDiscovery();
                // give it some time
                synchronized (this){
                    this.wait(WAIT_BEFORE_RETRY);
                }
            }
            catch (InterruptedException e){
                Log.e(TAG, "run:discovery thread was interrupted (maybe cancelled)");
            }
        }

        //--- end ---//

        engine.onDiscoveryFinished();
        isDiscovering = false;
        Log.d(TAG, "run: discovery thread ended final");
    }

    //
    //  ---------- discovery ----------
    //

    /**
     * Setting up the callbacks which wil be called when
     * a service was discovered, proving the TXT records and other
     * service information.
     *
     * This only needs to be set up once, at the Thread start.
     * It shouldn't called while lopping (re-starting service discovery).
     *
     */
    private void setupDiscoveryCallbacks(){

        Log.d(TAG, "setupDiscoveryCallbacks: setting up callbacks");

        //--- TXT Record listener ---//

        WifiP2pManager.DnsSdTxtRecordListener txtListener = (fullDomain, txtRecord, device) ->
        {
            Log.d(TAG, "run: found service record: on  " + Utils.getRemoteDeviceString(device) + " record: " + txtRecord);

            engine.onServiceDiscovered(device, txtRecord, fullDomain);
        };

        //--- Service response listener - gives additional service info ---//

        WifiP2pManager.DnsSdServiceResponseListener servListener = (instanceName, registrationType, resourceType) ->
        {
            //----------------------------------
            // NOTE : Right now i don't see any
            // use in the information give here,
            // but i am sure it can be useful at some point.
            // (That's why its there)
            //----------------------------------
        };

        //--- setting the listeners ---//
        
        manager.setDnsSdResponseListeners(channel, servListener, txtListener);
    }

    @SuppressLint("MissingPermission")
    private void startDiscovery(){

        Log.d(TAG, "startDiscovery: started discovery");

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

    //
    //  ---------- others ----------
    //

    protected void cancel(){
        Log.d(TAG, "cancel: canceling service discovery");
        this.thread.interrupt();
        this.isDiscovering = false;
        manager.clearServiceRequests(channel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                // nothing todo here
            }

            @Override
            public void onFailure(int reason)
            {
                Utils.logReason("DiscoveryThread: cancel: could not clear service requests ", reason);
            }
        });
        Log.d(TAG, "cancel: canceled service discovery");
    }

    protected boolean isDiscovering(){
        return this.isDiscovering;
    }

    private void onServiceDiscoveryFailure(){
        //----------------------------------
        // NOTE : There doesn't seem to be
        // much i can do here, wifi could be restarted
        // (of / on) but that's all
        //----------------------------------
    }

    /**
     * Sets the amount retries for the service discovery
     * the default is 1 try.
     * A higher value will restart the discovery every 10 seconds
     * as many times as specified trough the tries
     *
     * @param tries
     * the number of tries
     */
    protected void setTries(int tries){
        this.retries = tries;
    }
}

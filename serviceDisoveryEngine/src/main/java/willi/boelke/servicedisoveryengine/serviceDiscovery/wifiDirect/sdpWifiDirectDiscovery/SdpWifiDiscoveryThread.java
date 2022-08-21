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
public class SdpWifiDiscoveryThread extends Thread
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private final int WAIT_BEFORE_RETRY = 7000;

    private final int TRIES = 3;

    private int runningTries = 0;

    private WifiSdpServiceDiscoveryListener listener;

    private boolean isDiscovering;

    private WifiP2pManager manager;

    private WifiP2pManager.Channel channel;

    private Thread thread;

    /**
     * Stores all discovered services, to compare newly discovered
     * services with and do nothing id already discovered
     * Todo do we need this ? its already done in the SdpEngine
     */
    private ArrayList<Map<String, String>> discoveredServices = new ArrayList();

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
     * @param listener
     * an implementation of the listener interface,
     * to get updated on new discoveries.
     */
    public SdpWifiDiscoveryThread(WifiP2pManager manager, WifiP2pManager.Channel channel, WifiSdpServiceDiscoveryListener listener){
            this.manager = manager;
            this.channel = channel;
            this.listener = listener;
    }
    

    @Override
    public void run()
    {
        //--- setting to running ---//

        isDiscovering = true;
        this.thread = currentThread();

        //--- setting up callbacks ---//

        setupDiscoveryCallbacks();

        //--- discovery loop ---//

        while (isDiscovering && runningTries < TRIES){
            runningTries++;
            try
            {
                startDiscovery();
                pause(WAIT_BEFORE_RETRY);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        //--- end ---//

        listener.onDiscoveryFinished();
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

        //--- TXT Record listener ---//

        WifiP2pManager.DnsSdTxtRecordListener txtListener = (fullDomain, txtRecord, device) ->
        {
            Log.d(TAG, "run: found service record: on  " + Utils.getRemoteDeviceString(device) + " record: " + txtRecord);
            if(this.checkIfNewService(device, txtRecord))
            {
                listener.onServiceDiscovered(device, txtRecord, fullDomain);
            }
            else {
                Log.d(TAG, "setupDiscoveryCallbacks: service was discovered before");
            }
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

    private boolean checkIfNewService(WifiP2pDevice device, Map<String,String> serviceRecord)
    {
        Map<String,String> serviceToRemember = new HashMap<>();
        //ToDo define some constants for them
        serviceToRemember.put("addr", device.deviceAddress);
        serviceToRemember.put("uuid", ServiceDescription.getUuidForServiceRecord(serviceRecord).toString());
        if(this.discoveredServices.contains(serviceToRemember))
        {
            return true;
        }
        discoveredServices.add(serviceToRemember);
        return false;
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

    /**
     * Pauses after the discovery started to wait if any
     * services can be found.
     *
     * @param pause
     * time in millis
     *
     * @throws InterruptedException
     * when the thread was interrupted
     */
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


    /**
     * Interface to be implemented by classes using the {@link SdpWifiDiscoveryThread}
     */
    public interface WifiSdpServiceDiscoveryListener
    {
        /**
         * Called when a service has been discovered, providing
         * the listener with all means to connect to a service / remote device
         *
         * @param device
         * The device which hosts the service
         * @param txtRecord
         * the services TXT records
         * @param fullDomain
         * the services domain
         */
         void onServiceDiscovered(WifiP2pDevice device, Map<String, String> txtRecord, String fullDomain);

        /**
         * called when the discovery process finished
         */
        void onDiscoveryFinished();
    }

    private void onServiceDiscoveryFailure(){
        //----------------------------------
        // NOTE : There doesn't seem to be
        // much i can do here, wifi could be restarted
        // (of / on) but that's all
        //----------------------------------
    }
}

package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect;

import android.annotation.SuppressLint;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.util.Log;

import java.util.Map;

import willi.boelke.servicedisoveryengine.serviceDiscovery.Utils;

/**
 * This implementation follows the example google gave,
 * its for measuring performance differences in a number of
 * experiments.
 *
 * (In this case the while Thread actually is not needed since there wont me any looping and
 * re-starting the discovery)
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
        setupDiscoveryCallbacks();
        startDiscovery();
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
            // just testing dont want connections here engine.onServiceDiscovered(device, record, fullDomain);
        };

        //--- Service response listener - gives additional service info ---//

        WifiP2pManager.DnsSdServiceResponseListener servListener = (instanceName, registrationType, resourceType) ->
        {
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

        manager.addServiceRequest(channel,  WifiP2pDnsSdServiceRequest.newInstance(),
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "startDiscovery: added service requests");
                    }

                    @Override
                    public void onFailure(int arg0) {
                        Utils.logReason("startDiscovery: failed to add servcie request", arg0);
                    }
        });

        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "startDiscovery: initiated service discovery");
            }

            @Override
            public void onFailure(int arg0) {
                Utils.logReason("startDiscovery: failed to initiate service discoevry", arg0);
            }
        });

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

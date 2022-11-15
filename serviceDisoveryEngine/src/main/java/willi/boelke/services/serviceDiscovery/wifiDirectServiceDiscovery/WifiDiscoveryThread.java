package willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery;

import android.annotation.SuppressLint;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Starts the discovery and sets up callback to notify
 * the engine when a service is discovered.
 *
 * <p>
 * Retries<br>
 * ------------------------------------------------------------<br>
 * Retries are per default set to two, which increases the chance
 * of discovering a service (as my finding are). This seems to
 * prevent some cases in which the discovery does not
 * discovery any services - even though no error was
 * given.
 * If the discovery fails due to "busy", "error" etc.
 * the service discovery will be restarted up to 3 times.
 * Giving it a maximum of 5 restarts (if needed).
 *
 * <p>
 * Retries<br>
 * ------------------------------------------------------------<br>
 * The thread is just for setting up the discovery.
 * The discovery itself - once it started will notify about
 * services through the callbacks set top the WifiP2pManager.
 * There was no exact number to be found on how long this works.
 * It does not seem to work indefinitely.
 * However through a series of tests it seemed that after 2.5 minutes
 * a discovery was not performed anymore.
 *
 * <p>
 * Whats returned?<br>
 * ------------------------------------------------------------<br>
 * There will be no filter applied to discovered services, this
 * thread does not care what it discovers or how often, depending
 * on the amount of retries set -s service can be discovered several times.
 *
 * <p>
 * SdpWifiDiscoveryEngine<br>
 * ------------------------------------------------------------<br>
 * Every discovered service will be passed to
 * {@link WifiDirectServiceDiscoveryEngine#onServiceDiscovered(WifiP2pDevice, Map, String, String)}
 * only there the service records will be evaluated.
 *
 * @author WilliBoelke
 */
@SuppressLint("MissingPermission")
class WifiDiscoveryThread extends Thread
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private int retries = 2;

    private int runningTries = 0;

    private final WifiDirectServiceDiscoveryEngine engine;

    private boolean isDiscovering;

    private final WifiP2pManager manager;

    private final WifiP2pManager.Channel channel;

    private Thread thread;

    private final HashMap<String, Map<String, String>> tmpRecordCache = new HashMap<>();

    //
    //  ---------- constructor and initialisation ----------
    //

    /**
     * Constructor
     *
     * @param manager
     *         The WifiP2P manager
     * @param channel
     *         The Channel
     * @param engine
     *         The WifiDirectDiscoveryEngine to callback
     */
    public WifiDiscoveryThread(WifiP2pManager manager, WifiP2pManager.Channel channel, WifiDirectServiceDiscoveryEngine engine)
    {
        this.manager = manager;
        this.channel = channel;
        this.engine = engine;
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

        while (isDiscovering && runningTries < retries)
        {
            runningTries++;
            startDiscovery();
            synchronized (this)
            {
                try
                {
                    this.wait(6000);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }

        //--- end ---//

        Log.d(TAG, "run: discovery thread finished");
    }

    //
    //  ---------- discovery ----------
    //

    /**
     * Setting up the callbacks which wil be called when
     * a service was discovered, proving the TXT records and other
     * service information.
     * <p>
     * This only needs to be set up once, at the Thread start.
     * It shouldn't called while lopping (re-starting service discovery).
     */
    private void setupDiscoveryCallbacks()
    {
        tmpRecordCache.clear();
        Log.d(TAG, "setupDiscoveryCallbacks: setting up callbacks");

        //----------------------------------
        // NOTE : Well its a little bit weird i think
        // that the TXT record and the DnsService response
        // come in separate callbacks. It seems so that
        // the always come in the same order so i can match
        // both using the device address.
        // https://developer.android.com/training/connect-devices-wirelessly/nsd-wifi-direct
        // does it similar, though in their example
        // there just is one single service. I am sure there
        // is some reason for it.. which i cant quite
        // understand. I think both should come in the same callback
        // because i am sure google could make a more reliable
        // matching between the two then i can do here.
        //----------------------------------

        //--- TXT Record listener ---//

        WifiP2pManager.DnsSdTxtRecordListener txtListener = (fullDomain, txtRecord, device) ->
        {
            Log.d(TAG, "run: found service record: on  " + device + " record: " + txtRecord);

            tmpRecordCache.put(device.deviceAddress, txtRecord);
        };

        //--- Service response listener - gives additional service info ---//

        WifiP2pManager.DnsSdServiceResponseListener servListener = (instanceName, registrationType, device) ->
        {
            Map<String, String> record = tmpRecordCache.get(device.deviceAddress);
            engine.onServiceDiscovered(device, record, registrationType, instanceName);
            this.interrupt();
        };

        //--- setting the listeners ---//

        manager.setDnsSdResponseListeners(channel, servListener, txtListener);
    }

    private void startDiscovery()
    {

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

    /**
     * This interrupts the service thread
     * and unregisters all service requests.
     * the service discovery will stop.
     */
    protected void cancel()
    {
        Log.d(TAG, "cancel: canceling service discovery");
        this.thread.interrupt();
        this.isDiscovering = false;
        manager.clearServiceRequests(channel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                // nothing to do here
            }

            @Override
            public void onFailure(int reason)
            {
                WifiDirectServiceDiscoveryEngine.logReason(TAG, "DiscoveryThread: cancel: could not clear service requests ", reason);
            }
        });
        Log.d(TAG, "cancel: canceled service discovery");
    }

    protected boolean isDiscovering()
    {
        return this.isDiscovering;
    }

    /**
     * If the Service discovery fails it mostly
     * happens through a "busy" error, Which means
     * that Wifi P2P is Currently doing some other things.
     * Maybe because a service was registered or
     * something else happened.
     */
    private void onServiceDiscoveryFailure()
    {
        if(retries <= 5)
        {
            retries++;
        }
        try
        {
            synchronized (this)
            {
                // wait an additional 2 seconds so other processes can (hopefully) finish
                this.wait(2000);
            }
        }
        catch (InterruptedException e){
            Log.e(TAG, "onServiceDiscoveryFailure: wait interrupted");
        }
    }
}

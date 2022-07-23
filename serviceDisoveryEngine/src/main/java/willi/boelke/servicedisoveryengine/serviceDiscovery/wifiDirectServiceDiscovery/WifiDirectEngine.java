package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirectServiceDiscovery;

import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Manages the Wifi Direct android API
 */
public class WifiDirectEngine
{
    /**
     * The App Context
     */
    private Context context;

    private final String TAG = this.getClass().getSimpleName();

    /**
     * Intent filter for the Broadcast Receiver
     */
    private final IntentFilter intentFilter = new IntentFilter();

    /**
     * Wifi direct channel
     */
    private WifiP2pManager.Channel mChannel;

    /**
     * The wifi direct manager
     */
    private WifiP2pManager mManager;

    ServiceDiscovery serviceDiscovery;

    /**
     *
     */
    private final List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();

    /**
     * BroadcastReceiver to listen to Android System Broadcasts specified in the intentFilter
     */
    private WifiDirectStateChangeReceiver mWifiReceiver;

    public WifiDirectEngine(Context context)
    {
        this.context = context;
        this.serviceDiscovery = new ServiceDiscovery();
        this.setup();
    }

    private void setup()
    {
        Log.d(TAG, "Setup Wifip2p Engine ");
        // Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        // Indicates a change in the list of available peers.
      //  intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        // Initialize manager and channel
        this.mManager = (WifiP2pManager) this.getContext().getSystemService(Context.WIFI_P2P_SERVICE);
        this.mChannel = mManager.initialize(this.getContext(), this.getContext().getMainLooper(), null);

        mWifiReceiver = new WifiDirectStateChangeReceiver(mManager, mChannel);
        this.getContext().registerReceiver(mWifiReceiver, intentFilter);

    }

    public void startEngine()
    {
        setup();
    }

    /**
     * Starts a new Service
     *
     * @param service
     */
    public void startService(WifiDirectService service)
    {
        mManager.clearLocalServices(mChannel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                mManager.addLocalService(mChannel, service.getServiceInfo(), new WifiP2pManager.ActionListener()
                {
                    @Override
                    public void onSuccess()
                    {
                        service.onSuccess();
                    }

                    @Override
                    public void onFailure(int arg0)
                    {
                        service.onFailure(arg0);
                        // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                    }
                });
            }
            @Override
            public void onFailure(int i)
            {

            }
        });

    }


    public void stopService()
    {
        mManager.clearLocalServices(mChannel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {

            }

            @Override
            public void onFailure(int i)
            {

            }
        });
    }

    public void stopServiceDiscovery()
    {
        mManager.clearServiceRequests(mChannel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                serviceDiscovery.stopDiscovery();
                serviceDiscovery.interrupt();
            }

            @Override
            public void onFailure(int i)
            {

            }
        });

    }

    final HashMap<String, String> services = new HashMap();

    public void discoverServices()
    {
        this.stopServiceDiscovery();
        Log.d(TAG, "Starting service discovery");
        // txt listener
        /* Callback includes:
         * fullDomain: full domain name: e.g "printer._ipp._tcp.local."
         * record: TXT record dta as a map of key/value pairs.
         * device: The device running the advertised service.
         */
        WifiP2pManager.DnsSdTxtRecordListener txtListener = (fullDomain, record, device) ->
        {
            Log.d(TAG, "DnsSdTxtRecord available -" + record.toString());
            services.put(device.deviceAddress, record.get(WifiDirectService.SERVICE_NAME));
        };

        // service listener
        WifiP2pManager.DnsSdServiceResponseListener servListener = (instanceName, registrationType, resourceType) ->
        {

            // Update the device name with the human-friendly version from
            // the DnsTxtRecord, assuming one arrived.
            resourceType.deviceName = services
                    .containsKey(resourceType.deviceAddress) ? services
                    .get(resourceType.deviceAddress) : resourceType.deviceName;
            Log.d(TAG, "onBonjourServiceAvailable " + instanceName);
        };

        // adding to the manager
        mManager.setDnsSdResponseListeners(mChannel, servListener, txtListener);

        WifiP2pDnsSdServiceRequest serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        mManager.addServiceRequest(mChannel,
                serviceRequest,
                new WifiP2pManager.ActionListener()
                {
                    @Override
                    public void onSuccess()
                    {
                        Log.d(TAG, "Service request send");
                    }

                    @Override
                    public void onFailure(int code)
                    {
                        Log.d(TAG, "Service request failed due to " + code);
                    }
                });


        //  Starting the discovery
        mManager.discoverServices(mChannel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(TAG, "Started Service Discovery");
            }

            @Override
            public void onFailure(int code)
            {
                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY

                Log.d(TAG, "Service discovery failed due to " + code);
            }
        });
    }


    public void testDiscoverServices()
    {
        serviceDiscovery.stopDiscovery();
        serviceDiscovery  = new ServiceDiscovery();
        serviceDiscovery.start();
    }


    private class ServiceDiscovery extends Thread{

        private boolean isDiscovering;

        public ServiceDiscovery(){
            isDiscovering = true;
        }

        public void stopDiscovery(){
            this.isDiscovering = false;
        }

        @Override
        public void run()
        {
            while(isDiscovering)
            {
                Log.d(TAG, "Starting Service Discovery");
                // Clear locally running services
                mManager.clearLocalServices(mChannel, new WifiP2pManager.ActionListener()
                {
                    @Override
                    public void onSuccess()
                    {
                        Log.d(TAG, "Cleared local services");
                        {
                            // ToDO should go into new private method
                            // txt listener
                            /* Callback includes:
                             * fullDomain: full domain name: e.g "printer._ipp._tcp.local."
                             * record: TXT record dta as a map of key/value pairs.
                             * device: The device running the advertised service.
                             */
                            WifiP2pManager.DnsSdTxtRecordListener txtListener = (fullDomain, record, device) ->
                            {
                                Log.d(TAG, "DnsSdTxtRecord available -" + record.toString());
                                services.put(device.deviceAddress, record.get(WifiDirectService.SERVICE_NAME));
                            };

                            // service listener
                            WifiP2pManager.DnsSdServiceResponseListener servListener = (instanceName, registrationType, resourceType) ->
                            {

                                // Update the device name with the human-friendly version from
                                // the DnsTxtRecord, assuming one arrived.
                                resourceType.deviceName = services
                                        .containsKey(resourceType.deviceAddress) ? services
                                        .get(resourceType.deviceAddress) : resourceType.deviceName;
                                Log.d(TAG, "onBonjourServiceAvailable " + instanceName);
                            };

                            mManager.setDnsSdResponseListeners(mChannel, servListener, txtListener);
                            // Stop running service request
                            mManager.clearServiceRequests(mChannel, new WifiP2pManager.ActionListener()
                            {
                                @Override
                                public void onSuccess()
                                {
                                    Log.d(TAG, "Cleared local service requests");

                                    mManager.addServiceRequest(mChannel, WifiP2pDnsSdServiceRequest.newInstance(), new WifiP2pManager.ActionListener()
                                    {
                                        @Override
                                        public void onSuccess()
                                        {
                                            Log.d(TAG, "Added service requests");
                                            mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener()
                                            {
                                                @Override
                                                public void onSuccess()
                                                {
                                                    Log.d(TAG, "Started peer discovery");
                                                    mManager.discoverServices(mChannel, new WifiP2pManager.ActionListener()
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
                                                        }
                                                    });
                                                }

                                                @Override
                                                public void onFailure(int code)
                                                {
                                                    Log.d(TAG, "failed to start peer discovery");
                                                }
                                            });
                                        }

                                        @Override
                                        public void onFailure(int code)
                                        {
                                            Log.d(TAG, "failed to add service discovery request");
                                        }
                                    });
                                }

                                @Override
                                public void onFailure(int code)
                                {
                                    Log.d(TAG, "Failed to clear local service requests");
                                }
                            });
                        }
                    }

                    @Override
                    public void onFailure(int code)
                    {
                        Log.d(TAG, "Failed to clear local service");
                    }
                });

                synchronized(this)
                {
                    try
                    {
                        this.wait(5000);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    //-----Getter and Setter-----


    public Context getContext()
    {
        return context;
    }

    public void setContext(Context context)
    {
        this.context = context;
    }

    public IntentFilter getIntentFilter()
    {
        return intentFilter;
    }

    public WifiP2pManager.Channel getmChannel()
    {
        return mChannel;
    }

    public void setChannel(WifiP2pManager.Channel mChannel)
    {
        this.mChannel = mChannel;
    }

    public WifiP2pManager getManager()
    {
        return mManager;
    }

    public void setManager(WifiP2pManager mManager)
    {
        this.mManager = mManager;
    }

    public WifiDirectStateChangeReceiver getWifiReceiver()
    {
        return mWifiReceiver;
    }

    public void setWifiReceiver(WifiDirectStateChangeReceiver mWifiReceiver)
    {
        this.mWifiReceiver = mWifiReceiver;
    }
}

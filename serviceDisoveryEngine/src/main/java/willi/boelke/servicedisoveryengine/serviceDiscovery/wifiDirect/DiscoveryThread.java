package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect;

import android.annotation.SuppressLint;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.util.Log;

import willi.boelke.servicedisoveryengine.serviceDiscovery.Utils;

@SuppressLint("MissingPermission")
public class DiscoveryThread extends Thread
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private final SdpWifiEngine engine;

    private boolean isDiscovering;

    private WifiP2pManager mManager;

    private WifiP2pManager.Channel mChannel;

    private Thread thread;

    //
    //  ----------  constructor and initialisation ----------
    //

    public DiscoveryThread(WifiP2pManager manager, WifiP2pManager.Channel channel, SdpWifiEngine engine){
            mManager = manager;
            mChannel = channel;
            this.engine = engine;
    }

    public void cancel(){
        this.thread.interrupt();
        this.isDiscovering = false;
    }

    @Override
    public void run()
    {
        isDiscovering = true;
        this.thread = currentThread();
        while(isDiscovering)
        {
            WifiP2pManager.DnsSdTxtRecordListener txtListener = (fullDomain, record, device) ->
            {
                Log.d(TAG, "run: found service record: on  " + Utils.getRemoteDeviceString(device) + " record: " + record);
                engine.onServiceDiscovered(device, record, fullDomain);
            };

            // service listener
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




            synchronized(this)
            {
                try
                {
                    this.wait(10000);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean isDiscovering(){
        return this.isDiscovering;
    }
}

package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import willi.boelke.servicedisoveryengine.serviceDiscovery.tcp.TCPChannelMaker;

/**
 * Also this code is partially taken for asapAndroid, for the purpose
 * of establishing connections and staying close to the ASAP Implementation
 */
public class AwaitSocketConenctionThread extends Thread
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private final TCPChannelMaker channelMaker;
    private final SdpWifiEngine engine;
    private InputStream is;
    private OutputStream os;

    public AwaitSocketConenctionThread(TCPChannelMaker channelMaker, SdpWifiEngine engine)
    {
        this.channelMaker = channelMaker;
        this.is = null;
        this.os = null;
        this.engine = engine;
    }


    public void run()
    {
        Log.d(TAG, "run: waiting for channel maker to establish connection");
        try
        {
            if (this.is == null)
            {
                if (!this.channelMaker.running())
                {
                    Log.d(TAG, "run: starting channel maker");
                    this.channelMaker.start();
                }
                else
                {
                    this.channelMaker.nextConnection();
                }

                Log.d(TAG, "channel maker started, wait for connection");
                this.channelMaker.waitUntilConnectionEstablished();
                Log.d(TAG, "connected - start handle connection");
                this.is = this.channelMaker.getInputStream();
                this.os = this.channelMaker.getOutputStream();
            }

            Log.d(TAG, "call asapMultiEngine to handle connection");
            this.engine.onSocketConnected(this.is, this.os);
        }
        catch (IOException e)
        {
            Log.d(TAG, "while launching asap connection: " + e.getMessage());
            try
            {
                this.os.close();
            }
            catch (IOException ex)
            {
                // won't probably work due to failure before - ignore any further exception
            }
        }
    }
}

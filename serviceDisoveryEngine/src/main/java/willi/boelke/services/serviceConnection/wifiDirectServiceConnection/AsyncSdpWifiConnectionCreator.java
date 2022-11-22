package willi.boelke.services.serviceConnection.wifiDirectServiceConnection;

import android.util.Log;

import java.io.IOException;
import java.net.Socket;

import willi.boelke.services.serviceConnection.wifiDirectServiceConnection.tcp.TCPChannelMaker;
import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * This Thread awaits a {@link TCPChannelMaker} to establish a socket connection
 * (or to fail doing that...). If a connection was established a {@link WifiConnection}
 * will be passed to {@link WifiDirectConnectionEngine#onSocketConnected(WifiConnection)}
 *
 * <p>
 * Note<br>
 * ------------------------------------------------------------<br>
 * This code is partially taken from AsapAndroid
 */
class AsyncSdpWifiConnectionCreator extends Thread
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private final TCPChannelMaker channelMaker;
    private final WifiDirectConnectionEngine engine;
    private final ServiceDescription serviceDescription;
    private Socket socket;

    public AsyncSdpWifiConnectionCreator(TCPChannelMaker channelMaker, WifiDirectConnectionEngine engine, ServiceDescription description)
    {
        this.serviceDescription = description;
        this.channelMaker = channelMaker;
        this.socket = null;
        this.engine = engine;
    }


    @Override
    public void run()
    {
        Log.d(TAG, "run: waiting for channel maker to establish connection");
        try
        {
            if (this.socket == null)
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

                awaitConnection();
                this.socket = channelMaker.getSocket();
                Log.d(TAG, "run: socket " + socket);
            }

            // Creating the connection
            WifiConnection connection = new WifiConnection(this.socket, this.serviceDescription);
            Log.d(TAG, "run: socked to " + socket.getInetAddress() + " with " + serviceDescription + " has been connected");
            this.engine.onSocketConnected(connection);
        }
        catch (IOException e)
        {
            Log.e(TAG, "run: IOException while trying to create connection", e);
            try
            {
                this.socket.close();
            }
            catch (IOException | NullPointerException ex)
            {
                Log.e(TAG, "run: cannot close the socket...", ex);
            }
        }
    }

    private void awaitConnection() throws IOException
    {
        //----------------------------------
        // NOTE : There is a race condition between
        // this and the Channel maker. The channel
        // maker may not have created the channel
        // when calling this, leading to a
        // NullPointerException when calling
        // `channel.isConnected()` We will give it
        // a try (since it works in about 80% of the
        // cases) and then wait before trying again.
        // This fix actually works very well now
        // after testing it a while, though there may
        // be room for improvements
        //----------------------------------
        try
        {
            this.channelMaker.waitUntilConnectionEstablished();
        }
        catch (NullPointerException e)
        {
            synchronized (this)
            {
                Log.e(TAG, "run: race condition happened");
                try
                {
                    this.wait(2000);
                }
                catch (InterruptedException ex)
                {
                    ex.printStackTrace();
                }
            }
            this.channelMaker.waitUntilConnectionEstablished();
        }
    }
}

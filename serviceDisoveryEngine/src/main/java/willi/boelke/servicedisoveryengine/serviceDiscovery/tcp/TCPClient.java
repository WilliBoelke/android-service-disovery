package willi.boelke.servicedisoveryengine.serviceDiscovery.tcp;

import android.util.Log;

import java.io.IOException;
import java.net.Socket;

class TCPClient extends TCPChannel
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();
    private final String hostname;
    private final int port;

    TCPClient(String hostname, int port)
    {
        this.hostname = hostname;
        this.port = port;
    }

    void createSocket()
    {
        int i = TCPChannelMaker.max_connection_loops;

        for(;i > 0; i--)
        {
            try
            {
                StringBuilder b = new StringBuilder();
                b.append("TCPClient:");
                b.append("try to connect to ");
                b.append(this.hostname);
                b.append(" port: ");
                b.append(port);
                b.append(" remaining tries: ");
                b.append(i);
                Log.d(TAG, "createSocket: " +b);
                this.setSocket(new Socket(this.hostname, this.port));

                // break loop if no exception thrown
                return;
            }
            catch(IOException ioe) {
                StringBuilder b = new StringBuilder();
                b.append("TCPClient:");
                b.append("failed / wait and re-try");
                b.append(port);
                Log.e(TAG, "createSocket: " +b);

                try
                {
                    Thread.sleep(TCPChannel.WAIT_LOOP_IN_MILLIS);
                }
                catch (InterruptedException ex) {
                    // ignore
                }
            }
        }
        Log.d(TAG, "createSocket: could not establish connection, run out of tries");
    }
}
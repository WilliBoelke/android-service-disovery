package willi.boelke.serviceDiscovery.tcp;

import android.util.Log;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

class TCPServer extends TCPChannel
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private final int WAITING_TRIES = 10;

    /**
     * The server socket to accept tcp connections
     *
     */
    private ServerSocket srvSocket;

    private final int port;

    private final boolean multiple;

    private List<Socket> socketList;

    private Thread acceptThread = null;

    TCPServer(int port, boolean multiple) throws IOException
    {
        this.port = port;
        this.multiple = multiple;

        // create server socket
        this.srvSocket = new ServerSocket(port);

        // create list oif socket - used if multiple flag set
        socketList = new ArrayList<>();
    }

    /**
     * This method is called within a thread.
     * If multiple is set true
     * each client connection is accept and kept in {@link #socketList}.
     *
     */
    void createSocket() throws IOException
    {
        Log.d(TAG, "createSocket called");

        // called first time
        if (this.acceptThread == null)
        {
            Log.d(TAG, "accept thread null - going to accept");
            // wait for connection attempt
            Socket newSocket = srvSocket.accept();

            // got a socket
            if (multiple)
            {
               this.startAcceptThread();
            }

            // set first found socket on top of the queue
            Log.d(TAG, "new socket found");
            this.setSocket(newSocket);
        }
        else
        {
           waitForAcceptThread();
        }
    }

    private void waitForAcceptThread() throws ConnectException
    {
        Log.d(TAG, "accept thread running");
        // an accept thread was already called

        // was is successful?
        boolean found = false;
        int remainingTries = WAITING_TRIES;
        do
        {
            if (!this.socketList.isEmpty())
            {
                Log.d(TAG, "socket list not empty");
                // make first socket on waiting list to current socket
                this.setSocket(this.socketList.remove(0));
                found = true;
            }
            else
            {
                // wait
                try
                {
                    Log.d(TAG, "createSocket: socket list empty, wait/retry");
                    Thread.sleep(WAIT_LOOP_IN_MILLIS);
                }
                catch (InterruptedException e)
                {
                    // ignore
                }
            }
            remainingTries--;
        } while (!found && remainingTries > 0);

        if(!found){
            throw new ConnectException("Could not connect to peer");
        }
    }

    void close() throws IOException
    {
        super.close();

        if (this.srvSocket != null)
        {
            this.srvSocket.close();
        }
    }

    /**
     * A server can provide more than one connection - depending on client
     * attempts for connections. This method can be called, if a server channel with
     * multiple-flag was created. If not, an IOException is called immediately. If
     * so, the thread is stopped
     *
     * @throws IOException
     */
    void nextConnection() throws IOException
    {
        Log.d(TAG, "nextConnection called");
        if(!this.multiple)
        {
            String message = "multiple flag not set - no further connections";
            Log.d(TAG, message);
            throw new IOException(message);
        }

        if(this.srvSocket == null)
        {
            String message = "no open server socket, cannot create another connection";
            Log.d(TAG, message);
            throw new IOException(message);
        }

        // try to get next socket
        this.createSocket();
    }

    /**
     * This starts a thread which accepts connections on the
     * {@link #srvSocket} and adds the accepted sockets to the
     * {@link #socketList}
     */
    private void startAcceptThread()
    {
        Log.d(TAG, "startAcceptThread: starting accept thread");
        // create a new thread to collect other sockets
        this.acceptThread = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    Log.d(TAG, "AcceptThread: run: started");
                    while (multiple)
                    {
                        // loop will be broken when close called which closes srvSocket
                        socketList.add(srvSocket.accept());
                        Log.d(TAG, "AcceptThread: run: accepted new connection");
                    }
                }
                catch (IOException e)
                {
                    // leave loop
                }
                finally
                {
                    try
                    {
                        srvSocket.close();
                    }
                    catch (IOException e1)
                    {
                        // ignore
                    }
                    srvSocket = null; // remember invalid server socket
                }
                Log.d(TAG, "AcceptThread: run: ended");
            }
        };
        Log.d(TAG, "startAcceptThread: staring accept thread");
        this.acceptThread.start();
    }
}

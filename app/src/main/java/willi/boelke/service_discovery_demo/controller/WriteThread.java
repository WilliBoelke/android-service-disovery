package willi.boelke.service_discovery_demo.controller;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import willi.boelke.services.serviceConnection.ServiceConnection;
import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * Ths thread writes continuously messages to an InputStream
 * provided by a {@link ServiceConnection}.
 * <p>
 * This works hand in hand with the demo controllers and the {@link ControllerListener}
 * which will be required by this class.
 * <p>
 * This Thread iterates over the given list of connections and writes a message to
 * them containing a placeholder string and a counter to differentiate messages.
 * <p>
 * If a connection was closed/disconnected the thread will close this side of the connection (the socket)
 * as well as the Streams provided by that socket. The connection will then e removed from the list.
 * <p>
 * The given {@link ControllerListener} will be notified about closed connections.
 *
 * @param <T>
 *         An Object implementing {@link ServiceConnection}
 * @param <D>
 *         The type of device connected through the connection
 *
 * @author WilliBoelke
 */
public class WriteThread<T extends ServiceConnection, D> extends Thread
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();
    /**
     * The connections to write to
     */
    private final List<T> connections;
    private final ServiceDescription serviceDescription;
    private Thread thread;
    private final ControllerListener<T, D> listener;
    private boolean shouldRun = true;
    private boolean isRunning = false;


    public WriteThread(List<T> connections, ServiceDescription serviceDescription, ControllerListener<T, D> listener)
    {
        this.connections = connections;
        this.listener = listener;
        this.serviceDescription = serviceDescription;
    }

    @Override
    public void run()
    {
        this.isRunning = true;
        this.thread = Thread.currentThread();
        int counter = 0;
        while (shouldRun)
        {
            ArrayList<T> disconnectedConnections = new ArrayList<>();
            for (T connection : connections)
            {
                if (connection.isConnected() && !connection.isClosed())
                {
                    try
                    {
                        String msg = "Test message:" + counter +
                                "\nService: " + serviceDescription.getServiceName();
                        connection.getOutputStream().write(msg.getBytes());
                    }
                    catch (IOException e)
                    {
                        Log.i(TAG, "run: IOException while Writing to socket, closing connection");
                        disconnectedConnections.add(connection);
                    }
                }
                else
                {
                    Log.i(TAG, "run: disconnected, closing connection");
                    disconnectedConnections.add(connection);
                }
            }
            for (T disconnected : disconnectedConnections)
            {
                listener.onNewNotification("Lost connection " + disconnected);
                disconnected.close();
                listener.onConnectionLost(disconnected);
                connections.remove(disconnected);
            }

            try
            {
                synchronized (this)
                {
                    int timeOut = 1000;
                    this.wait(timeOut);
                }
                if (counter < Integer.MAX_VALUE)
                {
                    counter++;
                }
                else
                {
                    counter = 0;
                }
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }

    public void cancel()
    {
        Log.d(TAG, "cancel: stopping write thread");
        this.shouldRun = false;
        this.isRunning = false;
        this.thread.interrupt();
    }

    public boolean isRunning()
    {
        return this.isRunning;
    }
}

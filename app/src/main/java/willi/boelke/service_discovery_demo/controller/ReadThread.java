package willi.boelke.service_discovery_demo.controller;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import willi.boelke.services.serviceConnection.ServiceConnection;

/**
 * Generic reader for a number of {@link ServiceConnection}s T
 * as provided through the connections List<T> connections.
 *
 * Means this works with Bluetooth Connections aas well as with Wifi Connections.
 *
 * This thread will alter the contents of the provided connections List.
 * Thus the list should be a thread save collection. 
 * 
 * This thread will call the methods {@link ControllerListener#onConnectionLost(ServiceConnection)}
 * when one of the connections was closed and  {@link ControllerListener#onMessageChange(String)}
 * when a new message came through the Socket.
 *
 * The thread can be stopped by calling its {@link #cancel()} method.
 *
 * @param <T>
 *     The used implementation of the {@link ServiceConnection} interface.
 * @param <D>
 *     The matching remote device class, this can be ether the BluetoothDevice or the WifiDirectDevice
 *
 * @author WilliBoelke
 */
public class ReadThread<T extends ServiceConnection, D> extends Thread
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private final ControllerListener<T, D> listener;
    private boolean running = true;
    private Thread thread;
    private final CopyOnWriteArrayList<T> connections;

    public ReadThread(CopyOnWriteArrayList<T> connections, ControllerListener<T, D> listener){
        this.connections = connections;
        this.listener = listener; 
    }

    @Override
    public void run()
    {
        this.thread = Thread.currentThread();

        while (running)
        {
            ArrayList<T> disconnectedConnections = new ArrayList<>();
            for (int i = 0; i < connections.size(); i++)
            {
                T connection = connections.get(i);

                if (connection.isConnected())
                {
                    try
                    {
                        byte[] buffer = new byte[2048];
                        int bytes;
                        bytes = connection.getInputStream().read(buffer);
                        String incomingTransmission = new String(buffer, 0, bytes);
                        listener.onMessageChange(incomingTransmission);
                    }
                    catch (IOException e)
                    {
                        disconnectedConnections.add(connection);
                    }
                    catch (StringIndexOutOfBoundsException e)
                    {
                        // this may happen (happened once in all testing) on disconnect
                        // though maybe also in other cases.
                        // so lets just go one here
                        Log.e(TAG, "run: StringIndexOutOfBoundsException while reading from InputStream, probably disconnected");
                    }
                }
                else
                {
                    disconnectedConnections.add(connection);
                }
            }
            for (int i = 0; i < disconnectedConnections.size(); i++)
            {
                T connection = disconnectedConnections.get(i);
                connection.close();
                connections.remove(connection);
                listener.onConnectionLost(connection);
            }
        }
    }


    public void cancel()
    {
        try
        {
            this.running = false;
            this.thread.interrupt();
        }
        catch (NullPointerException e){
            // this.thread will be null if run wasn't yet called
            // this though should be checked before calling cancel as well
            Log.e(TAG, "cancel: thread was not started");
        }
    }

    public boolean isRunning()
    {
        return this.running;
    }
}
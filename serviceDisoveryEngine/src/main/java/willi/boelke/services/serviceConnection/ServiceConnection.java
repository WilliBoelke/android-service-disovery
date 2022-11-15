package willi.boelke.services.serviceConnection;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * A service connection contains the connected socket and
 * the corresponding service {@link ServiceDescription}.
 */
public interface ServiceConnection extends Closeable
{
    boolean isConnected();

    boolean isClosed();

    InputStream getInputStream() throws IOException;

    OutputStream getOutputStream() throws IOException;

    ServiceDescription getServiceDescription();

    String getRemoteDeviceAddress();

    void close();
}

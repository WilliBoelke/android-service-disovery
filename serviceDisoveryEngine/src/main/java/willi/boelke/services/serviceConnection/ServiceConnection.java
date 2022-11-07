package willi.boelke.services.serviceConnection;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import willi.boelke.services.serviceDiscovery.ServiceDescription;

public interface ServiceConnection extends Closeable
{
    boolean isConnected();
    InputStream getInputStream() throws IOException;
    OutputStream getOutputStream() throws IOException;
    ServiceDescription getServiceDescription();
    String getRemoteDeviceAddress();
    void close();
}

package willi.boelke.serviceDiscovery.bluetooth;


public class SdpException extends Exception {
    public SdpException() {
    }

    public SdpException(String message) {
        super(message);
    }

    public SdpException(String message, Throwable cause) {
        super(message, cause);
    }

    public SdpException(Throwable cause) {
        super(cause);
    }
}

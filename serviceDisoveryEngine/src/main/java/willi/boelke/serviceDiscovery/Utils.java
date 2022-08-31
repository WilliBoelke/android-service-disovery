package willi.boelke.serviceDiscovery;

import static android.net.wifi.p2p.WifiP2pManager.BUSY;
import static android.net.wifi.p2p.WifiP2pManager.ERROR;
import static android.net.wifi.p2p.WifiP2pManager.NO_SERVICE_REQUESTS;
import static android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED;

import android.bluetooth.BluetoothDevice;
import android.net.wifi.p2p.WifiP2pDevice;
import android.util.Log;

public class Utils
{
    /**
     * Classname for logging
     */
    private static final String TAG = Utils.class.getSimpleName();

    public static String getRemoteDeviceString(BluetoothDevice device)
    {
        return "DEVICE-B{ " + device.getName() + " | " + device.getAddress() + " }";
    }

    public static String getRemoteDeviceString(WifiP2pDevice device)
    {
        return "DEVICE-W{ " + device.deviceName + " | " + device.deviceAddress + " }";
    }

    public static void logReason(String msg, int arg0)
    {
        String reason;
        switch (arg0)
        {
            case ERROR:
                reason = "error";
                break;
            case NO_SERVICE_REQUESTS:
                reason = "no service requests";
                break;
            case BUSY:
                reason = "busy";
                break;
            case P2P_UNSUPPORTED:
                reason = "unsupported";
                break;
            default:
                reason = "unexpected error";
        }

        Log.e(TAG, msg +" reason : " + reason);
    }

}

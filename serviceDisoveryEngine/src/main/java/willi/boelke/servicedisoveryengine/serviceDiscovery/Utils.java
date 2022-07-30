package willi.boelke.servicedisoveryengine.serviceDiscovery;

import static android.net.wifi.p2p.WifiP2pManager.BUSY;
import static android.net.wifi.p2p.WifiP2pManager.ERROR;
import static android.net.wifi.p2p.WifiP2pManager.NO_SERVICE_REQUESTS;
import static android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED;

import android.bluetooth.BluetoothDevice;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

public class Utils
{

    /**
     * Classname for logging
     */
    private static final String TAG = Utils.class.getSimpleName();

    /**
     * This method reverses a UUID Bytewise
     *
     * This is a workaround for a problem which causes UUIDs
     * obtained with `fetchUuidsWithSdp()` to be in a little endian format
     * This problem is apparently not specific to a certain android version
     * since it only occurred on one of my devices running Android 8.1, the other one
     * (with the same version) didn't had this problem.
     *
     * This will be used on every discovered UUID when enabled in the
     * SdpBluetoothEngine`s configuration, sine the problem cant be predetermined
     * by any means i found.
     *
     * This Problem is mentioned in the the google Issue tracker
     * <a href="https://issuetracker.google.com/issues/37075233">...</a>
     * The code used here to reverse the UUID is stolen from the issues comments and can be found here
     * <a href="https://gist.github.com/masterjefferson/10922165432ec016a823e46c6eb382e6">...</a>
     * @param uuid
     * the UUID to be bytewise revered
     * @return
     * the revered uuid
     */
   public static UUID bytewiseReverseUuid(UUID uuid){
        ByteBuffer byteBuffer = ByteBuffer.allocate(16);
        byteBuffer
                .putLong(uuid.getLeastSignificantBits())
                .putLong(uuid.getMostSignificantBits());
        byteBuffer.rewind();
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        return new UUID(byteBuffer.getLong(), byteBuffer.getLong());
    }

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


    /**
     * Just trying out stuff
     * // todo remove
     */
    private void testUUIDs(){
        // UUID
        UUID test = UUID.fromString( "020012ac-4202-39b9-ec11-9af4ff5f3412");
        Log.d(TAG, "testUUIDs: " + test.toString());

        // Making it little endian
        ByteBuffer byteBuffer = ByteBuffer.allocate(16);
        byteBuffer
                .putLong(test.getLeastSignificantBits())
                .putLong(test.getMostSignificantBits());
        byteBuffer.rewind();
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        test = new UUID(byteBuffer.getLong(), byteBuffer.getLong());
        Log.d(TAG, "testUUIDs: little endian: " + test.toString());

        // Train to convert it to native order (Android big endian)
        ByteBuffer byteBufferTwo = ByteBuffer.allocate(16);
        byteBufferTwo
                .putLong(test.getLeastSignificantBits())
                .putLong(test.getMostSignificantBits());
        byteBufferTwo.rewind();
        byteBufferTwo.order(ByteOrder.nativeOrder());
        test = new UUID(byteBufferTwo.getLong(), byteBufferTwo.getLong());
        Log.d(TAG, "testUUIDs: native order endian: " + test.toString());

        if (ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN)) {
            System.out.println("testUUIDs Big-endian");
        } else {
            System.out.println("testUUIDs Little-endian");
        }

    }

}

package willi.boelke.services.testUtils;


import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

/**
 * <h1>DeviceRoleManager</h1>
 * The devices need to be defined {@link #getCurrentDeviceName()}
 * can be used to obtain the device names.
 * Also the Wifi and Bluetooth MAC addresses need to be specified
 * there is no way do get those in code (since Android 6)
 * <p>
 * This allows to execute different code / tests cases on 3 different devices
 * to test network functionalities and the behavior on actual android
 * android implementation and hardware.
 * It is used to test the service discovery for wifi direct and bluetooth
 * as well as the establishment on connections between the 3 devices.
 * <p>
 * <h2>Usage</h2>
 * The devices need to be specified here, the are distinct by their
 * 'device name' following the pattern [device] [manufacturer], this is not flawless
 * since it is not always unique. Though it is not possible to obtain
 * other unique device identifiers like the MAc addresses anymore.
 * <a href="https://developer.android.com/about/versions/marshmallow/android-6.0-changes#behavior-hardware-id">
 * Access to Hardware Identifier</a>
 * The names need to be given through the variables {@link #DEVICE_A}, {@link #DEVICE_B} and
 * {@link #DEVICE_C}.
 * <p>
 * This is also the reason why the MAC addresses (of wifi and bluetooth hardware) are
 * required to be specified manually in the variables {@link #MAC_A_BT}, {@link #MAC_B_BT},
 * {@link #MAC_C_BT}, {@link #MAC_A_WIFI}, {@link #MAC_B_WIFI} and {@link #MAC_C_WIFI}.
 * <p>
 * At runtime the current device name can be obtained by calling {@link }
 *
 * @author WilliBoelke
 */
public class DeviceRoleManager
{

    //
    //  ----------  the devices names ----------
    //

    public static final String DEVICE_A = "samsungSM-T580";
    public static final String DEVICE_B = "LENOVOLenovo TB-X304L";
    public static final String DEVICE_C = "DOOGEEY8";
    public static final String NOT_CONFIGURED = "NOT_CONFIGURED";


    //
    //  ----------  the mac addresses of the devices ----------
    //

    public static final String MAC_A_BT = "D0:7F:A0:D6:1C:9A";
    public static final String MAC_B_BT = "D0:F8:8C:2F:19:9F";
    public static final String MAC_C_BT = "20:19:08:15:56:13";

    public static final String MAC_A_WIFI = "";
    public static final String MAC_B_WIFI = "d2:f8:8c:32:19:9f";
    public static final String MAC_C_WIFI = "02:27:15:ba:be:40";

    /**
     * This golds the device name at runtime.
     */
    private static String runningDevice;

    /**
     * Returns a string containing the device name and manufacturer,
     * to distinct between devices at runtime
     *
     * @return a device name string
     */
    public static String getCurrentDeviceName()
    {
        //----------------------------------
        // NOTE : the actual mac address of
        // the local device would have been better
        // since then they could have been compared
        // more easily
        //----------------------------------
        return android.os.Build.MANUFACTURER + android.os.Build.MODEL;
    }

    /**
     * Determines which of the defined devices is running the tests
     */
    public static void determineTestRunner()
    {
        switch (getCurrentDeviceName())
        {
            case DEVICE_A:
                runningDevice = DEVICE_A;
                break;
            case DEVICE_B:
                runningDevice = DEVICE_B;
                break;
            case DEVICE_C:
                runningDevice = DEVICE_C;
                break;
            default:
                runningDevice = NOT_CONFIGURED;
        }
    }

    /**
     * Returns the device running the tests
     *
     * @return
     */
    public static String getTestRunner()
    {
        return runningDevice;
    }

    public static void printDeviceInfo(Context context)
    {
        //
        WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        Log.d("", "-------------------\n" +
                "device name = " + getCurrentDeviceName() + "\n" +
                "bt mac addr = " + BluetoothAdapter.getDefaultAdapter().getAddress() + "\n" +
                "wf mac addr = " + manager.getConnectionInfo().getMacAddress() + "\n" +
                "the mac addresses cant be obtained programmatically anymore (since android 6)" +
                "u need to find them manually");
    }
}

package willi.boelke.service_discovery_demo.view;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.util.ArrayList;
import java.util.List;

import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.databinding.ActivityMainBinding;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothServiceConnectionEngine;
import willi.boelke.services.serviceConnection.wifiDirectServiceConnection.WifiDirectConnectionEngine;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryVOne;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryVTwo;
import willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery.WifiDirectServiceDiscoveryEngine;


public class MainActivity extends AppCompatActivity
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();
    private static final int BT_AND_WIFI_PERMISSIONS = 124;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        willi.boelke.service_discovery_demo.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //--- nav bar ---//

        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_bluetooth_discovery,
                R.id.nav_wifi_discovery,
                R.id.nav_wifi_connect,
                R.id.nav_bluetooth_connect).build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);
        askForPermissions();
        notifyAndroidTenUsers();
        startEngines();
    }



    public void askForPermissions()
    {
        Log.d(TAG, "askForPermissions: checking permissions");
        List<String> permissions = new ArrayList<>();
        String message = "Demo App permissions missing:";
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Log.e(TAG, "askForPermissions: missing fine location permission");
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            message += "\nLocation access";
        }
        if (!permissions.isEmpty())
        {
            Log.e(TAG, "askForPermissions: missing permissions, requesting");
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            String[] params = permissions.toArray(new String[permissions.size()]);
            requestPermissions(params, BT_AND_WIFI_PERMISSIONS);
        }
        else
        {
            Log.d(TAG, "askForPermissions: has all permissions");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == BT_AND_WIFI_PERMISSIONS){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, "Thanks, permission granted, scotty start the engines", Toast.LENGTH_LONG).show();
                startEngines();
            }
            else{
                Toast.makeText(this, "Well okay - but it wont work then ", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void notifyAndroidTenUsers(){
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            //----------------------------------
            // NOTE : well in fact android 10 needs
            // users to enable their location service
            // to make bluetooth discovery work.
            //----------------------------------
            Toast.makeText(this, "Note: Android 10 users need to enable location service", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        WifiDirectServiceDiscoveryEngine.getInstance().stop();
        WifiDirectConnectionEngine.getInstance().stop();
        BluetoothServiceDiscoveryVOne.getInstance().stop();
        BluetoothServiceDiscoveryVTwo.getInstance().stop();
        BluetoothServiceConnectionEngine.getInstance().stop();
    }


    private void startEngines(){
        // Init the engine
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Toast.makeText(this, "Missing permission", Toast.LENGTH_LONG).show();
        }
        else
        {
            WifiDirectServiceDiscoveryEngine.getInstance().start(this);
            WifiDirectConnectionEngine.getInstance().start(this, WifiDirectServiceDiscoveryEngine.getInstance());
            BluetoothServiceDiscoveryVTwo.getInstance().start(this);
            BluetoothServiceConnectionEngine.getInstance().start(this, BluetoothServiceDiscoveryVTwo.getInstance());
        }
    }
}
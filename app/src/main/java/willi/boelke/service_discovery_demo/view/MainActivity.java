package willi.boelke.service_discovery_demo.view;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothServiceConnectionEngine;
import willi.boelke.services.serviceConnection.wifiDirectServiceConnection.WifiDirectConnectionEngine;
import willi.boelke.services.serviceDiscovery.ServiceDescription;
import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.databinding.ActivityMainBinding;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothDiscoveryEngine;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothDiscoveryVOne;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothDiscoveryVTwo;
import willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery.WifiDirectDiscoveryEngine;


public class MainActivity extends AppCompatActivity
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();
    private static final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
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
            requestPermissions(params, REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        }
        else{
            Log.d(TAG, "askForPermissions: has all permissions");
        }
    }


    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        WifiDirectDiscoveryEngine.getInstance().stop();
        WifiDirectConnectionEngine.getInstance().stop();
        BluetoothDiscoveryVOne.getInstance().stop();
        BluetoothDiscoveryVTwo.getInstance().stop();
        BluetoothServiceConnectionEngine.getInstance().stop();
    }
}
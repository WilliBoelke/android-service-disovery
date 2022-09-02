package willi.boelke.service_discovery_demo.view;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import willi.boelke.serviceDiscovery.serviceDescription.ServiceDescription;
import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.databinding.ActivityMainBinding;



public class MainActivity extends AppCompatActivity
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();
    private static final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;

    private ActivityMainBinding binding;

    private ServiceDescription descriptionForServiceOne;

    private ServiceDescription descriptionForServiceTwo;


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
        
        //--- init demo service descriptions ---//

        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        serviceAttributesTwo.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        descriptionForServiceOne = new ServiceDescription("Counting Service One", serviceAttributesOne);
        descriptionForServiceTwo = new ServiceDescription("Counting Service Two", serviceAttributesTwo);
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


    public ServiceDescription getDescriptionForServiceOne()
    {
        return descriptionForServiceOne;
    }

    public ServiceDescription getDescriptionForServiceTwo()
    {
        return descriptionForServiceTwo;
    }
}
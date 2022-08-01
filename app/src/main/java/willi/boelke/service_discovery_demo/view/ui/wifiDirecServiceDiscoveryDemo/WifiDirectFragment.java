package willi.boelke.service_discovery_demo.view.ui.wifiDirecServiceDiscoveryDemo;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import willi.boelke.service_discovery_demo.databinding.FragmentWifiDirectBinding;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceClient;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpClientServerInterfaces.SdpBluetoothServiceServer;
import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.SdpWifiEngine;


public class WifiDirectFragment extends Fragment implements SdpBluetoothServiceClient
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private FragmentWifiDirectBinding binding;

    private SdpWifiEngine wifiDirectEngine;


    //
    //  ----------  activity/fragment lifecycle ----------
    //

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {

        WifiDirectViewModel dashboardViewModel =
                new ViewModelProvider(this).get(WifiDirectViewModel.class);

        binding = FragmentWifiDirectBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
        }
        this.wifiDirectEngine = SdpWifiEngine.initialize(this.getContext());
        wifiDirectEngine.start();

        final Button discoveryBtn = binding.startWifiBtn;

        final Button startSdpOneBtn = binding.startDiscoveryOneBtn;
        final Button endSdpOneBtn = binding.endDiscoveryOneBtn;


        final Button startServiceOneBtn = binding.startServiceOneBtn;
        final Button endServiceOneBtn = binding.endServiceOneBtn;


        discoveryBtn.setOnClickListener(v ->
        {
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
                return;
            }
            SdpWifiEngine.getInstance().startDiscovery();
        });

        startServiceOneBtn.setOnClickListener(v ->
        {
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
                return;
            }
            SdpWifiEngine.getInstance().startSDPService("testService1", UUID.fromString("1be0643f-1d98-573b-97cd-ca98a65347dd"), new SdpBluetoothServiceServer()
            {
                @Override
                public void onClientConnected(SdpBluetoothConnection connection)
                {
                    Log.d(TAG, "onClientConnected: a client connected");
                }
            });
            SdpWifiEngine.getInstance().startSDPService("testService1", UUID.fromString("4be0643f-1d98-573b-97cd-ca98a65347dd"), new SdpBluetoothServiceServer()
            {
                @Override
                public void onClientConnected(SdpBluetoothConnection connection)
                {
                    Log.d(TAG, "onClientConnected: a client connected");
                }
            });
        });

        endServiceOneBtn.setOnClickListener((v) -> {
            SdpWifiEngine.getInstance().stopSDPService();
        });

        endSdpOneBtn.setOnClickListener((v) ->{
                    SdpWifiEngine.getInstance().stopSDPDiscovery();
                    SdpWifiEngine.getInstance().stopSDPDiscovery();
        });
        startSdpOneBtn.setOnClickListener((v)->{
            SdpWifiEngine.getInstance().startSDPDiscoveryForServiceWithUUID(UUID.fromString("1be0643f-1d98-573b-97cd-ca98a65347dd"), this);
        });



        return root;
    }


    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        this.wifiDirectEngine.stop();
        binding = null;
    }

    @Override
    public void onServiceDiscovered(String address, UUID serviceUUID)
    {
        this.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getActivity(), "discovered service on " + address , Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onConnectedToService(SdpBluetoothConnection connection)
    {

    }

    @Override
    public boolean shouldConnectTo(String address, UUID serviceUUID)
    {
        return true;
    }

    @Override
    public void onDevicesInRangeChange(ArrayList<BluetoothDevice> devices)
    {

    }
}


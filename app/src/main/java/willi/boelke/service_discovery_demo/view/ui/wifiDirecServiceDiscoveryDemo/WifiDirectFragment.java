package willi.boelke.service_discovery_demo.view.ui.wifiDirecServiceDiscoveryDemo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.HashMap;

import willi.boelke.service_discovery_demo.databinding.FragmentWifiDirectBinding;
import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirectServiceDiscovery.WifiDirectEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirectServiceDiscovery.WifiDirectService;


public class WifiDirectFragment extends Fragment
{

    private FragmentWifiDirectBinding binding;

    private WifiDirectEngine wifiDirectEngine;
    private WifiDirectService wifiDirectService;


    //
    //  ----------  activity/fragment lifecycle ----------
    //

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {

        WifiDirectViewModel dashboardViewModel =
                new ViewModelProvider(this).get(WifiDirectViewModel.class);

        binding = FragmentWifiDirectBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        //final TextView textView = binding.textDashboard;
        //dashboardViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        this.wifiDirectEngine = new WifiDirectEngine(this.getContext());
        HashMap<String, String> serviceRecords = new HashMap<>();
        serviceRecords.put("testRecord1" ,  "Das ist ein est record");
        wifiDirectService = new WifiDirectService("testService", "Das ISt ein testServvice" ,serviceRecords );


        return root;
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        wifiDirectEngine.stopServiceDiscovery();
        wifiDirectEngine.stopService();
        binding = null;
    }

    public void startWifi(View view)
    {
        this.wifiDirectEngine.startEngine();
    }

    public void startService(View view)
    {
        this.wifiDirectEngine.startService(wifiDirectService);
    }

    public void startServiceDiscovery(View view)
    {
        this.wifiDirectEngine.testDiscoverServices();
    }

    public void endService(View view)
    {
        this.wifiDirectEngine.stopService();
    }

    public void endServiceDiscovery(View view)
    {
        this.wifiDirectEngine.stopServiceDiscovery();
    }
}


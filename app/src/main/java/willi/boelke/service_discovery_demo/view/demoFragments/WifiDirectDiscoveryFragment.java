package willi.boelke.service_discovery_demo.view.demoFragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;

import willi.boelke.serviceDiscovery.serviceDescription.ServiceDescription;
import willi.boelke.serviceDiscovery.wifiDirect.sdpWifiDirectDiscovery.SdpWifiDirectDiscoveryEngine;
import willi.boelke.serviceDiscovery.wifiDirect.sdpWifiDirectDiscovery.WifiServiceDiscoveryListener;
import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.databinding.FragmentWifiDirectDiscoverBinding;
import willi.boelke.service_discovery_demo.view.MainActivity;
import willi.boelke.service_discovery_demo.view.listAdapters.ServiceListAdapter;

public class WifiDirectDiscoveryFragment extends Fragment implements WifiServiceDiscoveryListener
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private FragmentWifiDirectDiscoverBinding binding;

    private SdpWifiDirectDiscoveryEngine engine;

    private MainActivity mainActivity;
    private ListView discoveredServicesListView;
    private ServiceListAdapter serviceListAdapter;
    private ArrayList<ServiceDescription> discoveredServices =new ArrayList<>();
    private boolean notifyAboutAll = false;


    //
    //  ----------  activity/fragment lifecycle ----------
    //

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        binding = FragmentWifiDirectDiscoverBinding.inflate(inflater, container, false);

        View root = binding.getRoot();

        // Init the engine
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
        }
        else
        {
            this.engine = SdpWifiDirectDiscoveryEngine.getInstance();
            this.engine.start(this.getActivity().getApplicationContext());
            this.engine.registerDiscoverListener(this);
        }
        setupListView();
        setupClickListener();
        this.mainActivity = (MainActivity)this.getActivity();
        return root;
    }


    private void setupClickListener()
    {
        final Button discoveryBtn = binding.startWifiBtn;
        final Button startSdpOneBtn = binding.startDiscoveryOneBtn;
        final Button endSdpOneBtn = binding.endDiscoveryOneBtn;
        final Button startSdpTwoBtn = binding.startDiscoveryTwoBtn;
        final Button endSdpTwoBtn = binding.endDiscoveryTwoBtn;
        final Button endDiscoveryButton = binding.endDiscoveryButton;
        final Button discoverAllButton = binding.discoverAllBtn;
        discoverAllButton.setOnClickListener(this::onClickEventHandler);
        discoveryBtn.setOnClickListener(this::onClickEventHandler);
        startSdpOneBtn.setOnClickListener(this::onClickEventHandler);
        endSdpOneBtn.setOnClickListener(this::onClickEventHandler);
        startSdpTwoBtn.setOnClickListener(this::onClickEventHandler);
        endSdpTwoBtn.setOnClickListener(this::onClickEventHandler);
        endDiscoveryButton.setOnClickListener(this::onClickEventHandler);
    }

    private void onClickEventHandler(View view)
    {
        if(!this.engine.isRunning()){
            Toast.makeText(getActivity(), "Missing permission or wifi not working", Toast.LENGTH_LONG).show();
            return;
        }

        //--- clicks ---//

        if (binding.startWifiBtn.equals(view))
        {
            engine.startDiscovery();
            discoveredServices.clear();
            serviceListAdapter.notifyDataSetChanged();
        }
        if (binding.startDiscoveryOneBtn.equals(view))
        {
            engine.startSdpDiscoveryForService(mainActivity.getDescriptionForServiceOne());
            engine.startSdpService(mainActivity.getDescriptionForServiceOne());
        }
        else if (binding.endDiscoveryOneBtn.equals(view))
        {
            engine.stopSdpDiscovery(mainActivity.getDescriptionForServiceOne());
            engine.stopSdpService(mainActivity.getDescriptionForServiceOne());
        }
        else if (binding.startDiscoveryTwoBtn.equals(view))
        {
            engine.startSdpDiscoveryForService(mainActivity.getDescriptionForServiceTwo());
            engine.startSdpService(mainActivity.getDescriptionForServiceTwo());
        }
        else if (binding.endDiscoveryTwoBtn.equals(view))
        {
            engine.stopSdpDiscovery(mainActivity.getDescriptionForServiceTwo());
            engine.stopSdpService(mainActivity.getDescriptionForServiceTwo());
        }
        else if (binding.endDiscoveryButton.equals(view))
        {
            engine.stopDiscovery();
        }
        else if (binding.discoverAllBtn.equals(view))
        {
            this.notifyAboutAll = !notifyAboutAll;
            engine.notifyAboutEveryService(notifyAboutAll);
        }
    }

    private void setupListView()
    {
        discoveredServicesListView = binding.connectionsListView;
        serviceListAdapter = new ServiceListAdapter(getContext(), R.layout.recycler_card_service, discoveredServices);
        discoveredServicesListView.setAdapter(serviceListAdapter);
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        if(engine.isRunning()){
            this.engine.unregisterDiscoveryListener(this);
            this.engine.stopSdpDiscovery(mainActivity.getDescriptionForServiceOne());
            this.engine.stopSdpDiscovery(mainActivity.getDescriptionForServiceTwo());
            this.engine.stopSdpService(mainActivity.getDescriptionForServiceOne());
            this.engine.stopSdpService(mainActivity.getDescriptionForServiceTwo());
            this.engine.notifyAboutEveryService(false);
            this.engine.stop();
        }
        binding = null;
    }

    @Override
    public void onServiceDiscovered(WifiP2pDevice host, ServiceDescription description)
    {
        discoveredServices.add(description);
        serviceListAdapter.notifyDataSetChanged();
    }
}

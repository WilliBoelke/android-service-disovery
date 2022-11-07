package willi.boelke.service_discovery_demo.view.demoFragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.databinding.FragmentWifiDirectDiscoverBinding;
import willi.boelke.service_discovery_demo.view.listAdapters.ServiceListAdapter;
import willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery.WifiDirectDiscoveryEngine;

public class WifiDirectDiscoveryFragment extends Fragment
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private FragmentWifiDirectDiscoverBinding binding;

    private WifiDirectDiscoveryEngine engine;

    private WifiDirectDiscoveryViewModel model;


    //
    //  ----------  activity/fragment lifecycle ----------
    //

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        // Init the engine
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
        }
        else
        {
            this.engine = WifiDirectDiscoveryEngine.getInstance();
            this.engine.start(this.getActivity().getApplicationContext());
        }

        binding = FragmentWifiDirectDiscoverBinding.inflate(inflater, container, false);
        model = new ViewModelProvider(this).get(WifiDirectDiscoveryViewModel.class);

        View root = binding.getRoot();
        setupClickListener();
        notificationObserver();
        discoveryObserver();
        return root;
    }

    private void setupClickListener()
    {
        binding.startWifiBtn.setOnClickListener(this::onClickEventHandler);
        binding.startDiscoveryOneBtn.setOnClickListener(this::onClickEventHandler);
        binding.endDiscoveryOneBtn.setOnClickListener(this::onClickEventHandler);
        binding.startDiscoveryTwoBtn.setOnClickListener(this::onClickEventHandler);
        binding.endDiscoveryTwoBtn.setOnClickListener(this::onClickEventHandler);
        binding.endDiscoveryButton.setOnClickListener(this::onClickEventHandler);
        binding.discoverAllBtn.setOnClickListener(this::onClickEventHandler);
    }

    private void onClickEventHandler(View view)
    {
        if (!engine.isRunning())
        {
            Toast.makeText(getContext(), "Missing permission or bluetooth not supported", Toast.LENGTH_LONG).show();
            return;
        }

        //--- clicks ---//

        if (binding.startWifiBtn.equals(view))
        {
            model.startDiscovery();
        }
        if (binding.startDiscoveryOneBtn.equals(view))
        {
            model.startSearchServiceOne();
            model.startDiscovery();
        }
        else if (binding.endDiscoveryOneBtn.equals(view))
        {
            model.stopSearchServiceOne();
        }
        else if (binding.startDiscoveryTwoBtn.equals(view))
        {
            model.startSearchServiceTwo();
            model.startDiscovery();
        }
        else if (binding.endDiscoveryTwoBtn.equals(view))
        {
            model.stopSearchServiceTwo();
        }
        else if (binding.endDiscoveryButton.equals(view))
        {
            model.stopDiscovery();
        }
        else if (binding.discoverAllBtn.equals(view))
        {
            model.notifyAboutAllServices();
        }
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        model.stopSearchServiceTwo();
        model.stopSearchServiceOne();
        // just set that to default
        this.engine.notifyAboutAllServices(false);
        binding = null;
    }

    private void notificationObserver()
    {
        model.getLatestNotification().observe(this.getViewLifecycleOwner(), message -> Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show());
    }

    private void discoveryObserver()
    {
        model.getDiscoveredDevices().observe(this.getViewLifecycleOwner(), devicesInRange ->
        {
            ServiceListAdapter serviceListAdapter = new ServiceListAdapter(getContext(), R.layout.recycler_card_service, devicesInRange);
            binding.connectionsListView.setAdapter(serviceListAdapter);
        });
    }
}

package willi.boelke.service_discovery_demo.view.demoFragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.databinding.FragmentWifiDirectDiscoverBinding;
import willi.boelke.service_discovery_demo.view.listAdapters.ServiceListAdapter;
import willi.boelke.services.serviceDiscovery.wifiDirectServiceDiscovery.WifiDirectServiceDiscoveryEngine;

public class WifiDirectDiscoveryFragment extends Fragment
{

    private FragmentWifiDirectDiscoverBinding binding;

    private WifiDirectDiscoveryViewModel model;


    //
    //  ----------  activity/fragment lifecycle ----------
    //

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        binding = FragmentWifiDirectDiscoverBinding.inflate(inflater, container, false);
        model = new ViewModelProvider(this).get(WifiDirectDiscoveryViewModel.class);

        View root = binding.getRoot();
        setupClickListener();
        notificationObserver();
        discoveryObserver();
        discoverAllStateObserver();
        return root;
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        model.goInactive();
    }


    @Override
    public void onResume()
    {
        super.onResume();
        model.goActive();
    }


    //
    //  ---------- user integration ----------
    //

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
        if (!WifiDirectServiceDiscoveryEngine.getInstance().isRunning())
        {
            Toast.makeText(getContext(), "Missing permission or wifi not supported", Toast.LENGTH_LONG).show();
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
        }
        else if (binding.endDiscoveryOneBtn.equals(view))
        {
            model.stopSearchServiceOne();
        }
        else if (binding.startDiscoveryTwoBtn.equals(view))
        {
            model.startSearchServiceTwo();
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


    //
    //  ---------- observer ----------
    //

    private void notificationObserver()
    {
        model.getLatestNotification().observe(this.getViewLifecycleOwner(), message -> Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show());
    }

    private void discoveryObserver()
    {
        model.getDiscoveredDevices().observe(this.getViewLifecycleOwner(), devicesInRange ->
        {
            ServiceListAdapter serviceListAdapter = new ServiceListAdapter(getContext(), R.layout.list_item_service, devicesInRange);
            binding.connectionsListView.setAdapter(serviceListAdapter);
        });
    }

    private void discoverAllStateObserver()
    {
        model.getDiscoverAllState().observe(this.getViewLifecycleOwner(), notifyAboutAllServices ->
        {
            String message = notifyAboutAllServices ? "discover selected" : "discover all";
            binding.discoverAllBtn.setText(message);
        });
    }
}

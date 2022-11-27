package willi.boelke.service_discovery_demo.view.demoFragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;

import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.databinding.FragmentBluetoothDiscoverBinding;
import willi.boelke.service_discovery_demo.view.listAdapters.ServiceListAdapter;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryVTwo;

public class BluetoothDiscoveryFragment extends Fragment
{

    //------------Instance Variables------------

    private FragmentBluetoothDiscoverBinding binding;

    private ServiceListAdapter connectionListAdapter;

    private BluetoothDiscoveryViewModel model;


    //
    //  ----------  activity/fragment lifecycle ----------
    //

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        binding = FragmentBluetoothDiscoverBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        model = new ViewModelProvider(this).get(BluetoothDiscoveryViewModel.class);

        //--- setup views ---//

        setupClickListener();
        setupListView();

        //--- register observer ---//
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


    private void setupClickListener()
    {
        binding.startDiscoveryButton.setOnClickListener(this::onClickEventHandler);
        binding.startSdpOneButton.setOnClickListener(this::onClickEventHandler);
        binding.endSdpOneButton.setOnClickListener(this::onClickEventHandler);
        binding.startSdpTwoButton.setOnClickListener(this::onClickEventHandler);
        binding.endSdpTwoButton.setOnClickListener(this::onClickEventHandler);
        binding.refreshButton.setOnClickListener(this::onClickEventHandler);
        binding.discoverAllBtn.setOnClickListener(this::onClickEventHandler);
    }

    private void setupListView()
    {
        ListView discoveredServicesListView = binding.connectionListView;
        connectionListAdapter = new ServiceListAdapter(getContext(), R.layout.list_item_service, new ArrayList<>());
        discoveredServicesListView.setAdapter(connectionListAdapter);
    }

    private void onClickEventHandler(View view)
    {
        if (!BluetoothServiceDiscoveryVTwo.getInstance().isRunning())
        {
            Toast.makeText(getContext(), "Missing permission or bluetooth not supported", Toast.LENGTH_LONG).show();
            return;
        }

        if (binding.startDiscoveryButton.equals(view))
        {
            model.startDiscovery();
            connectionListAdapter.notifyDataSetChanged();
        }
        else if (binding.startSdpOneButton.equals(view))
        {
            model.startSearchServiceOne();
        }
        else if (binding.endSdpOneButton.equals(view))
        {
            model.stopSearchServiceOne();
        }
        else if (binding.startSdpTwoButton.equals(view))
        {
            model.startSearchServiceTwo();
        }
        else if (binding.endSdpTwoButton.equals(view))
        {
            model.stopSearchServiceTwo();
        }
        else if (binding.refreshButton.equals(view))
        {
            model.refreshServices();
        }
        else if (binding.discoverAllBtn.equals(view))
        {
            model.notifyAboutAllServices();
        }
    }


    private void notificationObserver()
    {
        model.getLatestNotification().observe(this.getViewLifecycleOwner(), message -> Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show());
    }

    private void discoveryObserver()
    {
        model.getDiscoveredDevices().observe(this.getViewLifecycleOwner(), devicesInRange ->
        {
            ServiceListAdapter serviceListAdapter = new ServiceListAdapter(getContext(), R.layout.list_item_service, devicesInRange);
            binding.connectionListView.setAdapter(serviceListAdapter);
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
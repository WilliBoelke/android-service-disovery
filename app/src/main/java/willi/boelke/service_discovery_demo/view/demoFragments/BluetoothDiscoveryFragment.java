package willi.boelke.service_discovery_demo.view.demoFragments;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.HashMap;

import willi.boelke.serviceDiscovery.bluetooth.sdpBluetoothDiscovery.BluetoothServiceDiscoveryListener;
import willi.boelke.serviceDiscovery.bluetooth.sdpBluetoothDiscovery.SdpBluetoothDiscoveryEngine;
import willi.boelke.serviceDiscovery.serviceDescription.ServiceDescription;
import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.controller.wifiDemoController.WifiDemoController;
import willi.boelke.service_discovery_demo.view.MainActivity;
import willi.boelke.service_discovery_demo.view.listAdapters.ServiceListAdapter;


public class BluetoothDiscoveryFragment extends Fragment implements BluetoothServiceDiscoveryListener
{

    //------------Instance Variables------------

    /**
     * The Log Tag
     */
    private final String TAG = this.getClass().getSimpleName();

    private willi.boelke.service_discovery_demo.databinding.FragmentBluetoothDiscoverBinding binding;

    private SdpBluetoothDiscoveryEngine engine;

    private boolean notifyAboutAll = false;

    private ArrayList<ServiceDescription> discoveredServices = new ArrayList<>();

    private ListView discoveredServicesListView;

    private ServiceListAdapter connectionListAdapter;
    private MainActivity mainActivity;

    //
    //  ----------  activity/fragment lifecycle ----------
    //

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        binding = willi.boelke.service_discovery_demo.databinding.FragmentBluetoothDiscoverBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        engine = SdpBluetoothDiscoveryEngine.getInstance();

        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        {
            Log.d(TAG, "onCreateView: starting engine");
            engine.start(this.getActivity().getApplicationContext());
            engine.registerDiscoverListener(this);
        }
        else
        {
            Log.e(TAG, "onCreateView: missing permissions");
        }


        this.mainActivity = (MainActivity) getActivity();

        setupClickListener();
        setupListView();
        return root;
    }


    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        if (engine.isRunning())
        {
            engine.unregisterDiscoveryListener(this);
            engine.stopDeviceDiscovery();
            engine.stopSDPDiscoveryForService(mainActivity.getDescriptionForServiceOne());
            engine.stopSDPDiscoveryForService(mainActivity.getDescriptionForServiceTwo());
            engine.notifyAboutAllServices(false);
        }
        binding = null;
    }



    private void setupClickListener()
    {
        final Button discoveryBtn = binding.startDiscoveryButton;
        final Button startSdpOneBtn = binding.startSdpOneButton;
        final Button endSdpOneBtn = binding.endSdpOneButton;
        final Button startSdpTwoBtn = binding.startSdpTwoButton;
        final Button endSdpTwoBtn = binding.endSdpTwoButton;
        final Button refreshBtn = binding.refreshButton;
        final Button discoverAllBtn = binding.discoverAllBtn;
        discoveryBtn.setOnClickListener(this::onClickEventHandler);
        startSdpOneBtn.setOnClickListener(this::onClickEventHandler);
        endSdpOneBtn.setOnClickListener(this::onClickEventHandler);
        startSdpTwoBtn.setOnClickListener(this::onClickEventHandler);
        endSdpTwoBtn.setOnClickListener(this::onClickEventHandler);
        endSdpTwoBtn.setOnClickListener(this::onClickEventHandler);
        discoverAllBtn.setOnClickListener(this::onClickEventHandler);
        endSdpTwoBtn.setOnClickListener(this::onClickEventHandler);
        refreshBtn.setOnClickListener(this::onClickEventHandler);
    }

    private void setupListView()
    {

        discoveredServicesListView = binding.connectionListView;
        connectionListAdapter = new ServiceListAdapter(getContext(), R.layout.recycler_card_service_connection, discoveredServices);
        discoveredServicesListView.setAdapter(connectionListAdapter);
    }

    private void onClickEventHandler(View view)
    {
        if (!engine.isRunning())
        {
            Toast.makeText(getContext(), "Missing permission or bluetooth not supported", Toast.LENGTH_LONG).show();
            return;
        }
        final Button discoveryBtn = binding.startDiscoveryButton;
        final Button startSdpOneBtn = binding.startSdpOneButton;
        final Button endSdpOneBtn = binding.endSdpOneButton;
        final Button startSdpTwoBtn = binding.startSdpTwoButton;
        final Button endSdpTwoBtn = binding.endSdpTwoButton;
        final Button refreshBtn = binding.refreshButton;
        final Button discoverAllBtn = binding.discoverAllBtn;

        if (discoveryBtn.equals(view))
        {
            engine.startDeviceDiscovery();
            discoveredServices.clear();
            connectionListAdapter.notifyDataSetChanged();
            discoveredServicesListView.setAdapter(connectionListAdapter);

        }
        else if (startSdpOneBtn.equals(view))
        {
            engine.startSDPDiscoveryForService(mainActivity.getDescriptionForServiceOne());
        }
        else if (endSdpOneBtn.equals(view))
        {
            engine.stopSDPDiscoveryForService(mainActivity.getDescriptionForServiceOne());
        }
        else if (startSdpTwoBtn.equals(view))
        {
            engine.startSDPDiscoveryForService(mainActivity.getDescriptionForServiceTwo());
        }
        else if (endSdpTwoBtn.equals(view))
        {
            engine.stopSDPDiscoveryForService(mainActivity.getDescriptionForServiceTwo());
        }
        else if (refreshBtn.equals(view))
        {
            engine.refreshNearbyServices();
        }
        else if (discoverAllBtn.equals(view))
        {
            this.notifyAboutAll = !notifyAboutAll;
            engine.notifyAboutAllServices(notifyAboutAll);
        }
    }


    //
    //  ----------  Listener ----------
    //

    @Override
    public void onServiceDiscovered(BluetoothDevice host, ServiceDescription description)
    {
        Toast.makeText(getContext(),
                "Service on { " +
                        host.getName() + ", " +
                        host.getAddress() + " }",
                Toast.LENGTH_LONG).show();

        discoveredServices.add(description);
        connectionListAdapter.notifyDataSetChanged();
        discoveredServicesListView.setAdapter(connectionListAdapter);
    }

    @Override
    public void onPeerDiscovered(BluetoothDevice device)
    {
        Toast.makeText(getContext(),
                "Discovered Peer { " +
                        device.getName() + ", " +
                        device.getAddress() + " }",
                Toast.LENGTH_LONG).show();
    }
}
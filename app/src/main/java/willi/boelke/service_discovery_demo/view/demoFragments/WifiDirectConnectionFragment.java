package willi.boelke.service_discovery_demo.view.demoFragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.databinding.FragmentWifiDirectConnectBinding;
import willi.boelke.service_discovery_demo.view.listAdapters.WifiConnectionListAdapter;
import willi.boelke.services.serviceConnection.wifiDirectServiceConnection.WifiDirectConnectionEngine;


public class WifiDirectConnectionFragment extends Fragment
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private FragmentWifiDirectConnectBinding binding;

    private WifiDirectConnectionEngine engine;

    private WifiDirectConnectionViewModel model;

    //
    //  ----------  activity/fragment lifecycle ----------
    //

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        binding = willi.boelke.service_discovery_demo.databinding.FragmentWifiDirectConnectBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
        }
        else
        {
            this.engine = WifiDirectConnectionEngine.getInstance();
            this.engine.start(this.getActivity().getApplicationContext());
        }

        model = new ViewModelProvider(this).get(WifiDirectConnectionViewModel.class);

        setupClickListener();
        setupMessageOneObserver();
        setupConnectionObserver();
        setupNotificationObserver();
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
            model.startServiceOne();
        }
        else if (binding.endDiscoveryOneBtn.equals(view))
        {
            model.stopServiceOne();
        }
        else if (binding.startDiscoveryTwoBtn.equals(view))
        {
            model.startServiceTwo();
        }
        else if (binding.endDiscoveryTwoBtn.equals(view))
        {
            model.stopServiceTwo();
        }
        else if (binding.endDiscoveryButton.equals(view))
        {
            model.stopDiscovery();
        }
    }


    //
    //  ---------- live data observers ----------
    //

    private void setupMessageOneObserver()
    {
        model.getLatestMessage().observe(this.getViewLifecycleOwner(), message -> binding.msgTextView.setText(message));
    }

    private void setupNotificationObserver()
    {
        model.getLatestNotification().observe(this.getViewLifecycleOwner(), message -> Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show());
    }

    private void setupConnectionObserver()
    {
        model.getOpenConnections().observe(this.getViewLifecycleOwner(), connections ->
        {

            ListView openConnectionsListView = binding.connectionsListView;
            WifiConnectionListAdapter connectionListAdapter = new WifiConnectionListAdapter(getContext(), R.layout.recycler_card_service_connection, connections);
            openConnectionsListView.setAdapter(connectionListAdapter);
        });
    }
}


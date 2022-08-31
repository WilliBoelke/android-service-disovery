package willi.boelke.service_discovery_demo.view.demoFragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.util.HashMap;

import willi.boelke.serviceDiscovery.serviceDescription.ServiceDescription;
import willi.boelke.serviceDiscovery.wifiDirect.sdpWifiEngine.SdpWifiEngine;
import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.controller.wifiDemoController.WifiDemoController;
import willi.boelke.service_discovery_demo.view.MainActivity;
import willi.boelke.service_discovery_demo.view.listAdapters.WifiConnectionListAdapter;


public class WifiDirectConnectionFragment extends Fragment
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private willi.boelke.service_discovery_demo.databinding.FragmentWifiDirectConnectBinding binding;

    private SdpWifiEngine engine;

    private TextView messageTextView;

    private MainActivity mainActivity;

    WifiDemoController wifiDemoControllerOne;
    WifiDemoController wifiDemoControllerTwo;

    //
    //  ----------  activity/fragment lifecycle ----------
    //

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        binding = willi.boelke.service_discovery_demo.databinding.FragmentWifiDirectConnectBinding.inflate(inflater, container, false);

        View root = binding.getRoot();

        // Init the engine
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
        }
        else
        {
            this.engine = SdpWifiEngine.getInstance();
            this.engine.start(this.getActivity().getApplicationContext());
        }
        this.mainActivity = (MainActivity) getActivity();

        this.wifiDemoControllerOne = new WifiDemoController(mainActivity.getDescriptionForServiceOne());
        this.wifiDemoControllerTwo = new WifiDemoController(mainActivity.getDescriptionForServiceTwo());
        this.messageTextView = binding.msgTextView;
        setupClickListener();
        setupMessageOneObserver();
        setupConnectionObserver();
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
            SdpWifiEngine.getInstance().startDiscovery();
        }
        if (binding.startDiscoveryOneBtn.equals(view))
        {
            this.wifiDemoControllerOne.startService();
        }
        else if (binding.endDiscoveryOneBtn.equals(view))
        {
            this.wifiDemoControllerOne.stopService();
        }
        else if (binding.startDiscoveryTwoBtn.equals(view))
        {
            this.wifiDemoControllerTwo.startService();
        }
        else if (binding.endDiscoveryTwoBtn.equals(view))
        {
            this.wifiDemoControllerTwo.stopService();
        }
        else if (binding.endDiscoveryButton.equals(view))
        {
            SdpWifiEngine.getInstance().stopDiscovery();
        }
    }


    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        if(engine.isRunning()){
            this.engine.stop();
        }
        binding = null;
    }

    //
    //  ---------- live data observers ----------
    //

    private void setupMessageOneObserver()
    {
        wifiDemoControllerOne.getCurrentMessage().observe(this.getViewLifecycleOwner(), message -> messageTextView.setText(message));
        wifiDemoControllerTwo.getCurrentMessage().observe(this.getViewLifecycleOwner(), message -> messageTextView.setText(message));
    }

  private void setupConnectionObserver(){
      wifiDemoControllerOne.getConnections().observe(this.getViewLifecycleOwner(), connections ->
      {

          ListView openConnectionsListView = binding.connectionsListView;
          WifiConnectionListAdapter connectionListAdapter = new WifiConnectionListAdapter(getContext(), R.layout.recycler_card_service_connection, connections);
          openConnectionsListView.setAdapter(connectionListAdapter);
      });

      wifiDemoControllerTwo.getConnections().observe(this.getViewLifecycleOwner(), connections ->
      {

          ListView openConnectionsListView = binding.connectionsListView;
          WifiConnectionListAdapter connectionListAdapter = new WifiConnectionListAdapter(getContext(), R.layout.recycler_card_service_connection, connections);
          openConnectionsListView.setAdapter(connectionListAdapter);
      });
  }
}


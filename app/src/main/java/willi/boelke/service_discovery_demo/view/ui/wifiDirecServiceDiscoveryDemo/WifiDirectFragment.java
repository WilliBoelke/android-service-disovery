package willi.boelke.service_discovery_demo.view.ui.wifiDirecServiceDiscoveryDemo;

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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;

import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.databinding.FragmentWifiDirectBinding;
import willi.boelke.service_discovery_demo.view.WifiConnectionListAdapter;
import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.SdpWifiConnection;
import willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirect.SdpWifiEngine;


public class WifiDirectFragment extends Fragment
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private FragmentWifiDirectBinding binding;

    private SdpWifiEngine wifiDirectEngine;

    private WifiDirectViewModel viewModel;

    private TextView messageTextView;

    //
    //  ----------  activity/fragment lifecycle ----------
    //

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {


        viewModel = new ViewModelProvider(this).get(WifiDirectViewModel.class);
        binding = FragmentWifiDirectBinding.inflate(inflater, container, false);

        View root = binding.getRoot();

        // Init the engine
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
        }
        else
        {
            this.wifiDirectEngine = SdpWifiEngine.initialize(this.getContext());
        }

        setupViews();
        setupMessageOneObserver();
        setupConnectionObserver();

        return root;
    }

    private void setupViews()
    {
        final Button discoveryBtn = binding.startWifiBtn;
        final Button startSdpOneBtn = binding.startDiscoveryOneBtn;
        final Button endSdpOneBtn = binding.endDiscoveryOneBtn;
        final Button startSdpTwoBtn = binding.startDiscoveryTwoBtn;
        final Button endSdpTwoBtn = binding.endDiscoveryTwoBtn;
        final Button endDiscoveryButton = binding.endDiscoveryButton;
        messageTextView = binding.msgTextView;


        discoveryBtn.setOnClickListener(v ->
        {
            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
                return;
            }
            SdpWifiEngine.getInstance().startDiscovery();
        });


        endDiscoveryButton.setOnClickListener(v ->
        {
            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
                return;
            }
            SdpWifiEngine.getInstance().stopDiscovery();
        });


        startSdpOneBtn.setOnClickListener((v) ->
        {
            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
                return;
            }
            viewModel.startServiceOne();
        });


        endSdpOneBtn.setOnClickListener((v) ->
        {
            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
                return;
            }
            viewModel.stopServiceOne();
        });


        startSdpTwoBtn.setOnClickListener((v) ->
        {
            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
                return;
            }
            viewModel.startServiceTwo();
        });


        endSdpTwoBtn.setOnClickListener((v) ->
        {
            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
                return;
            }
            viewModel.stopServiceTwo();
        });
    }


    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        {
            this.wifiDirectEngine.stop();
        }

        binding = null;
    }

    //
    //  ---------- live data observers ----------
    //

    private void setupMessageOneObserver()
    {
        viewModel.getCurrentMessageOne().observe(this.getViewLifecycleOwner(), new Observer<String>()
        {
            @Override
            public void onChanged(String message)
            {
                messageTextView.setText(message);
            }
        });

        viewModel.getCurrentMessageTwo().observe(this.getViewLifecycleOwner(), new Observer<String>()
        {
            @Override
            public void onChanged(String message)
            {
                messageTextView.setText(message);
            }
        });
    }

  private void setupConnectionObserver(){
      viewModel.getConnectionsOne().observe(this.getViewLifecycleOwner(), new Observer<ArrayList<SdpWifiConnection>>()
      {
          @Override
          public void onChanged(ArrayList<SdpWifiConnection> connections)
          {

              ListView openConnectionsListView = binding.connectionsListView;
              WifiConnectionListAdapter connectionListAdapter = new WifiConnectionListAdapter(getContext(), R.layout.recycler_card_connection, connections);
              openConnectionsListView.setAdapter(connectionListAdapter);
          }
      });

      viewModel.getConnectionsTwo().observe(this.getViewLifecycleOwner(), new Observer<ArrayList<SdpWifiConnection>>()
      {
          @Override
          public void onChanged(ArrayList<SdpWifiConnection> connections)
          {

              ListView openConnectionsListView = binding.connectionsListView;
              WifiConnectionListAdapter connectionListAdapter = new WifiConnectionListAdapter(getContext(), R.layout.recycler_card_connection, connections);
              openConnectionsListView.setAdapter(connectionListAdapter);
          }
      });
  }



}


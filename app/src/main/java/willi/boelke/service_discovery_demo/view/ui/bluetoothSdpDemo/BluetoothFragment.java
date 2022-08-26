package willi.boelke.service_discovery_demo.view.ui.bluetoothSdpDemo;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
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
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;

import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.databinding.FragmentBluetoothBinding;
import willi.boelke.service_discovery_demo.view.ConnectionListAdapter;
import willi.boelke.service_discovery_demo.view.DeviceListAdapter;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothConnection;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothEngine;


public class BluetoothFragment extends Fragment
{

    //------------Instance Variables------------

    /**
     * The Log Tag
     */
    private final String TAG = this.getClass().getSimpleName();

    private FragmentBluetoothBinding binding;

    private DeviceListAdapter deviceLisAdapter;
    private ConnectionListAdapter connectionListAdapter;

    private ArrayList<SdpBluetoothConnection> connections;
    private ArrayList<SdpBluetoothConnection> clientConnectionsOne;
    private ArrayList<SdpBluetoothConnection> serverConnectionsOne;
    private ArrayList<SdpBluetoothConnection> serverConnectionsTwo;
    private ArrayList<SdpBluetoothConnection> clientConnectionsTwo;


    private BluetoothViewModel bluetoothViewModel;

    private TextView messageTextView;
    //
    //  ----------  activity/fragment lifecycle ----------
    //

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
         bluetoothViewModel =
                new ViewModelProvider(this).get(BluetoothViewModel.class);

        binding = FragmentBluetoothBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        int MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;
        ActivityCompat.requestPermissions(this.getActivity(),
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
        // Using application context, since this context is gonna be stored in the singleton
        SdpBluetoothEngine.initialize(this.getActivity().getApplicationContext());
        SdpBluetoothEngine.getInstance().start();

        this.connections = new ArrayList<>();
        this.clientConnectionsOne = new ArrayList<>();
        this.clientConnectionsTwo = new ArrayList<>();
        this.serverConnectionsOne = new ArrayList<>();
        this.serverConnectionsTwo = new ArrayList<>();


        final Button discoverableBtn = binding.discoverableButton;
        final Button discoveryBtn = binding.startDiscoveryButton;

        final Button startSdpOneBtn  = binding.startSdpOneButton;
        final Button endSdpOneBtn = binding.endSdpOneButton;

        final Button startSdpTwoBtn  = binding.startSdpTwoButton;
        final Button endSdpTwoBtn = binding.endSdpTwoButton;

        final Button startServiceOneBtn  = binding.startServiceOneButton;
        final Button endServiceOneBtn = binding.endServiceOneButton;

        final Button startServiceTwoBtn = binding.startServiceTwoButton;
        final Button endServiceTwoBtn = binding.endServiceTwoButton;

        final Button refreshBtn = binding.refreshButton;

        messageTextView = binding.msgTextView;

        startSdpOneBtn.setOnClickListener(v -> bluetoothViewModel.startClientOne());

        startSdpTwoBtn.setOnClickListener(v -> bluetoothViewModel.startClientTwo());


        startServiceOneBtn.setOnClickListener(v -> bluetoothViewModel.startServiceOne());

        startServiceTwoBtn.setOnClickListener(v -> bluetoothViewModel.startServiceTwo());

        endServiceOneBtn.setOnClickListener(v -> bluetoothViewModel.stopServiceOne());

        endServiceTwoBtn.setOnClickListener(v -> bluetoothViewModel.stopServiceTwo());


        endSdpOneBtn.setOnClickListener(v -> bluetoothViewModel.endClientOne());

        endSdpTwoBtn.setOnClickListener(v -> bluetoothViewModel.endClientTwo());

        discoverableBtn.setOnClickListener(v -> bluetoothViewModel.enableDiscoverable());

        discoveryBtn.setOnClickListener(v -> bluetoothViewModel.starDiscovery());

        refreshBtn.setOnClickListener(v -> bluetoothViewModel.refreshServices());

        //Setup observers
        this.messageOneObserver();
        this.messageTwoObserver();
        this.connectionsObserver();
        this.notificationObserver();
        this.devicesInRangeObserver();
        return root;
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        SdpBluetoothEngine.getInstance().stop();
        binding = null;
    }


    //
    //  ---------- live data observers ----------
    //

    private void messageOneObserver()
    {
        bluetoothViewModel.getMessageServiceOne().observe(this.getViewLifecycleOwner(), new Observer<String>()
        {
            @Override
            public void onChanged(String message)
            {
                messageTextView.setText(message);
            }
        });
    }

    private void messageTwoObserver()
    {
        bluetoothViewModel.getMessageServiceTwo().observe(this.getViewLifecycleOwner(), new Observer<String>()
        {
            @Override
            public void onChanged(String message)
            {
                messageTextView.setText(message);
            }
        });
    }


    private void notificationObserver()
    {
        bluetoothViewModel.getNotificationOne().observe(this.getViewLifecycleOwner(), new Observer<String>()
        {
            @Override
            public void onChanged(String message)
            {
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            }
        });

        bluetoothViewModel.getNotificationTwo().observe(this.getViewLifecycleOwner(), new Observer<String>()
        {
            @Override
            public void onChanged(String message)
            {
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }


    private void connectionsObserver()
    {
        bluetoothViewModel.getClientOneConnections().observe(this.getViewLifecycleOwner(), new Observer<ArrayList<SdpBluetoothConnection>>()
        {
            @Override
            public void onChanged(ArrayList<SdpBluetoothConnection> newClientConnections)
            {
                clientConnectionsOne = newClientConnections;
                connections = bluetoothViewModel.mergeConnectionLists(newClientConnections, serverConnectionsTwo, serverConnectionsOne, clientConnectionsTwo);

                ListView openConnectionsListView = binding.connectionListView;
                connectionListAdapter = new ConnectionListAdapter(getContext(), R.layout.recycler_card_service_connection, connections);
                openConnectionsListView.setAdapter(connectionListAdapter);
            }
        });

        bluetoothViewModel.getClientTwoConnections().observe(this.getViewLifecycleOwner(), new Observer<ArrayList<SdpBluetoothConnection>>()
        {
            @Override
            public void onChanged(ArrayList<SdpBluetoothConnection> newClientConnections)
            {
                clientConnectionsTwo = newClientConnections;
                connections = bluetoothViewModel.mergeConnectionLists( newClientConnections, serverConnectionsTwo, serverConnectionsOne, clientConnectionsOne);
                ListView openConnectionsListView = binding.connectionListView;
                connectionListAdapter = new ConnectionListAdapter(getContext(), R.layout.recycler_card_service_connection, connections);
                openConnectionsListView.setAdapter(connectionListAdapter);
            }
        });


        bluetoothViewModel.getServerOneConnections().observe(this.getViewLifecycleOwner(), new Observer<ArrayList<SdpBluetoothConnection>>()
        {
            @Override
            public void onChanged(ArrayList<SdpBluetoothConnection> newServerConnections)
            {
                serverConnectionsOne = newServerConnections;
                connections = bluetoothViewModel.mergeConnectionLists(newServerConnections, serverConnectionsTwo, clientConnectionsOne, clientConnectionsTwo);
                ListView openConnectionsListView = binding.connectionListView;
                connectionListAdapter = new ConnectionListAdapter(getContext(), R.layout.recycler_card_service_connection, connections);
                openConnectionsListView.setAdapter(connectionListAdapter);
            }
        });

        bluetoothViewModel.getServerTwoConnections().observe(this.getViewLifecycleOwner(), new Observer<ArrayList<SdpBluetoothConnection>>()
        {
            @Override
            public void onChanged(ArrayList<SdpBluetoothConnection> newServerConnections)
            {
                serverConnectionsTwo = newServerConnections;
                connections = bluetoothViewModel.mergeConnectionLists(newServerConnections, serverConnectionsOne, clientConnectionsOne, clientConnectionsTwo);
                ListView openConnectionsListView = binding.connectionListView;
                connectionListAdapter = new ConnectionListAdapter(getContext(), R.layout.recycler_card_service_connection, connections);
                openConnectionsListView.setAdapter(connectionListAdapter);
            }
        });
    }

    private void devicesInRangeObserver()
    {
        bluetoothViewModel.getDevicesInRange().observe(this.getViewLifecycleOwner(), new Observer<ArrayList<BluetoothDevice>>()
        {
            @Override
            public void onChanged(ArrayList<BluetoothDevice> devicesInRange)
            {
                ListView devicesInRangeListView = binding.devicesInRange;
                deviceLisAdapter = new DeviceListAdapter(getContext(), R.layout.recycler_card_device, devicesInRange);
                devicesInRangeListView.setAdapter(deviceLisAdapter);
            }
        });
    }

}
package willi.boelke.service_discovery_demo.view.ui.bluetoothSdpDemo;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.UUID;

import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.databinding.FragmentBluetoothBinding;
import willi.boelke.service_discovery_demo.view.ConnectionListAdapter;
import willi.boelke.service_discovery_demo.view.DeviceListAdapter;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.SdpBluetoothEngine;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;


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

        SdpBluetoothEngine.initialize(this.getContext());
        SdpBluetoothEngine.getInstance().startEngine();

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

        messageTextView = binding.msgTextView;

        startSdpOneBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                bluetoothViewModel.startClientOne();
            }
        });

        startSdpTwoBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                bluetoothViewModel.startClientTwo();
            }
        });


        startServiceOneBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                bluetoothViewModel.startServiceOne();
            }
        });

        startServiceTwoBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                bluetoothViewModel.startServiceTwo();
            }
        });

        endServiceOneBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                bluetoothViewModel.stopServiceOne();
            }
        });

        endServiceTwoBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                bluetoothViewModel.stopServiceTwo();
            }
        });


        endSdpOneBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                bluetoothViewModel.endClientOne();
            }
        });

        endSdpTwoBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                bluetoothViewModel.endClientTwo();
            }
        });

        discoverableBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                bluetoothViewModel.enableDiscoverable();
            }
        });

        discoveryBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                bluetoothViewModel.starDiscovery();
            }
        });

        //Setup observers
        this.messageOneObserver();
        this.messageTwoObserver();
        this.connectionsObserver();
        this.devicesInRangeObserver();
        this.testUUIDs();
        return root;
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        SdpBluetoothEngine.getInstance().stopEngine();
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
                connectionListAdapter = new ConnectionListAdapter(getContext(), R.layout.recycler_card_connection, connections);
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
                connectionListAdapter = new ConnectionListAdapter(getContext(), R.layout.recycler_card_connection, connections);
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
                connectionListAdapter = new ConnectionListAdapter(getContext(), R.layout.recycler_card_connection, connections);
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
                connectionListAdapter = new ConnectionListAdapter(getContext(), R.layout.recycler_card_connection, connections);
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

    private void testUUIDs(){
        // UUID
        UUID test = UUID.fromString( "020012ac-4202-39b9-ec11-9af4ff5f3412");
        Log.d(TAG, "testUUIDs: " + test.toString());

        // Making it little endian
        ByteBuffer byteBuffer = ByteBuffer.allocate(16);
        byteBuffer
                .putLong(test.getLeastSignificantBits())
                .putLong(test.getMostSignificantBits());
        byteBuffer.rewind();
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        test = new UUID(byteBuffer.getLong(), byteBuffer.getLong());
        Log.d(TAG, "testUUIDs: little endian: " + test.toString());

        // Train to convert it to native order (Android big endian)
        ByteBuffer byteBufferTwo = ByteBuffer.allocate(16);
        byteBufferTwo
                .putLong(test.getLeastSignificantBits())
                .putLong(test.getMostSignificantBits());
        byteBufferTwo.rewind();
        byteBufferTwo.order(ByteOrder.nativeOrder());
        test = new UUID(byteBufferTwo.getLong(), byteBufferTwo.getLong());
        Log.d(TAG, "testUUIDs: native order endian: " + test.toString());

        if (ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN)) {
            System.out.println("testUUIDs Big-endian");
        } else {
            System.out.println("testUUIDs Little-endian");
        }

    }

}
package willi.boelke.service_discovery_demo.view.demoFragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import willi.boelke.serviceDiscovery.serviceDescription.ServiceDescription;
import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.controller.bluetoothDemoController.DemoClientController;
import willi.boelke.service_discovery_demo.controller.bluetoothDemoController.DemoServerController;
import willi.boelke.service_discovery_demo.databinding.FragmentBluetoothConnectBinding;
import willi.boelke.service_discovery_demo.view.MainActivity;
import willi.boelke.service_discovery_demo.view.listAdapters.ConnectionListAdapter;
import willi.boelke.service_discovery_demo.view.listAdapters.DeviceListAdapter;
import willi.boelke.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothConnection;
import willi.boelke.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothEngine;

/**
 * This fragment uses the {@link SdpBluetoothEngine}
 * to connect to remote devices / services on those devices
 *
 */
public class BluetoothConnectionFragment extends Fragment
{

    //------------Instance Variables------------

    /**
     * The Log Tag
     */
    private final String TAG = this.getClass().getSimpleName();
    private FragmentBluetoothConnectBinding binding;
    private DeviceListAdapter deviceLisAdapter;
    private ConnectionListAdapter connectionListAdapter;
    private ArrayList<SdpBluetoothConnection> connections = new ArrayList<>();
    private ArrayList<SdpBluetoothConnection> clientConnectionsOne = new ArrayList<>();
    private ArrayList<SdpBluetoothConnection> serverConnectionsOne = new ArrayList<>();
    private ArrayList<SdpBluetoothConnection> serverConnectionsTwo = new ArrayList<>();
    private ArrayList<SdpBluetoothConnection> clientConnectionsTwo = new ArrayList<>();

    private DemoClientController clientControllerOne;
    private DemoServerController serverControllerOne;
    private DemoClientController clientControllerTwo;
    private DemoServerController serverControllerTwo;

    private TextView messageTextView;
    private SdpBluetoothEngine engine;
    private MainActivity mainActivity;


    //
    //  ----------  activity/fragment lifecycle ----------
    //

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        binding = FragmentBluetoothConnectBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        {
            engine = SdpBluetoothEngine.getInstance();
            engine.start(this.getActivity().getApplicationContext());
        }

        //--- setup controllers ---//

       this.mainActivity = (MainActivity)getActivity();


        clientControllerOne = new DemoClientController(mainActivity.getDescriptionForServiceOne());
        clientControllerTwo = new DemoClientController(mainActivity.getDescriptionForServiceTwo());
        serverControllerOne = new DemoServerController(mainActivity.getDescriptionForServiceOne());
        serverControllerTwo = new DemoServerController(mainActivity.getDescriptionForServiceTwo());

        //--- setup views ---//

        this.setupClickListener();
        this.setupMessageTextView();

        //--- setup observers ---//

        this.messageObserver();
        this.connectionsObserver();
        this.notificationObserver();
        this.devicesInRangeObserver();

        return root;
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        if (engine.isRunning())
        {
            engine.stop();
        }

    }

    private void setupMessageTextView()
    {
        messageTextView = binding.msgTextView;
    }

    private void setupClickListener()
    {
        binding.discoverableButton.setOnClickListener(this::onClickEventHandler);
        binding.startDiscoveryButton.setOnClickListener(this::onClickEventHandler);
        binding.refreshButton.setOnClickListener(this::onClickEventHandler);
        binding.startServiceOneButton.setOnClickListener(this::onClickEventHandler);
        binding.startServiceTwoButton.setOnClickListener(this::onClickEventHandler);
        binding.endServiceOneButton.setOnClickListener(this::onClickEventHandler);
        binding.endServiceTwoButton.setOnClickListener(this::onClickEventHandler);
        binding.startSdpOneButton.setOnClickListener(this::onClickEventHandler);
        binding.startSdpTwoButton.setOnClickListener(this::onClickEventHandler);
        binding.endSdpOneButton.setOnClickListener(this::onClickEventHandler);
        binding.endSdpTwoButton.setOnClickListener(this::onClickEventHandler);
    }

    private void onClickEventHandler(View view)
    {
        if(!engine.isRunning()){

            return;
        }
        if (binding.discoverableButton.equals(view))
        {
            engine.startDiscoverable();
        }
        if (binding.startDiscoveryButton.equals(view))
        {
            engine.startDeviceDiscovery();
        }
        else if (binding.startServiceOneButton.equals(view))
        {
            this.serverControllerOne.startWriting();
            this.serverControllerOne.startService();
        }
        else if (binding.startServiceTwoButton.equals(view))
        {
            this.serverControllerTwo.startWriting();
            this.serverControllerTwo.startService();
        }
        else if (binding.endServiceOneButton.equals(view))
        {
            this.serverControllerOne.stopWriting();
            this.serverControllerOne.stopService();
        }
        else if (binding.endServiceTwoButton.equals(view))
        {
            this.serverControllerTwo.stopWriting();
            this.serverControllerTwo.stopService();
        }
        else if (binding.startSdpOneButton.equals(view))
        {
            this.clientControllerOne.startReading();
            this.clientControllerOne.startClient();
        }
        else if (binding.startSdpTwoButton.equals(view))
        {
            this.clientControllerTwo.startReading();
            this.clientControllerTwo.startClient();
        }
        else if (binding.endSdpOneButton.equals(view))
        {
            Log.e(TAG, "onClickEventHandler: ----end");
            this.clientControllerOne.endClient();
        }
        else if (binding.endSdpTwoButton.equals(view))
        {
            this.clientControllerTwo.endClient();
        }
        else if (binding.refreshButton.equals(view))
        {
            engine.refreshNearbyServices();
        }
    }

    //
    //  ---------- live data observers ----------
    //

    /**
     * registers observer on the clients controllers received messages
     * to get notified out new messages
     */
    private void messageObserver()
    {
        clientControllerTwo.getLatestMessage().observe(this.getViewLifecycleOwner(), message -> messageTextView.setText(message));
        clientControllerOne.getLatestMessage().observe(this.getViewLifecycleOwner(), message -> messageTextView.setText(message));
    }

    private void notificationObserver()
    {
        clientControllerOne.getLatestNotification().observe(this.getViewLifecycleOwner(), message -> Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show());
        clientControllerTwo.getLatestNotification().observe(this.getViewLifecycleOwner(), message -> Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show());
    }

    /**
     * Registers observers at the controllers to get
     * notified about new connections.
     */
    private void connectionsObserver()
    {
        clientControllerOne.getConnections().observe(this.getViewLifecycleOwner(), newClientConnections ->
        {
            clientConnectionsOne = newClientConnections;
            connections = mergeConnectionLists(newClientConnections, serverConnectionsTwo, serverConnectionsOne, clientConnectionsTwo);

            ListView openConnectionsListView = binding.connectionListView;
            connectionListAdapter = new ConnectionListAdapter(getContext(), R.layout.recycler_card_service_connection, connections);
            openConnectionsListView.setAdapter(connectionListAdapter);
        });

        clientControllerTwo.getConnections().observe(this.getViewLifecycleOwner(), newClientConnections ->
        {
            clientConnectionsTwo = newClientConnections;
            connections = mergeConnectionLists( newClientConnections, serverConnectionsTwo, serverConnectionsOne, clientConnectionsOne);
            ListView openConnectionsListView = binding.connectionListView;
            connectionListAdapter = new ConnectionListAdapter(getContext(), R.layout.recycler_card_service_connection, connections);
            openConnectionsListView.setAdapter(connectionListAdapter);
        });


        serverControllerOne.getConnections().observe(this.getViewLifecycleOwner(), newServerConnections ->
        {
            serverConnectionsOne = newServerConnections;
            connections = mergeConnectionLists(newServerConnections, serverConnectionsTwo, clientConnectionsOne, clientConnectionsTwo);
            ListView openConnectionsListView = binding.connectionListView;
            connectionListAdapter = new ConnectionListAdapter(getContext(), R.layout.recycler_card_service_connection, connections);
            openConnectionsListView.setAdapter(connectionListAdapter);
        });

        serverControllerTwo.getConnections().observe(this.getViewLifecycleOwner(), newServerConnections ->
        {
            serverConnectionsTwo = newServerConnections;
            connections = mergeConnectionLists(newServerConnections, serverConnectionsOne, clientConnectionsOne, clientConnectionsTwo);
            ListView openConnectionsListView = binding.connectionListView;
            connectionListAdapter = new ConnectionListAdapter(getContext(), R.layout.recycler_card_service_connection, connections);
            openConnectionsListView.setAdapter(connectionListAdapter);
        });
    }

    private void devicesInRangeObserver()
    {
        clientControllerOne.getDevicesInRange().observe(this.getViewLifecycleOwner(), devicesInRange ->
        {
            ListView devicesInRangeListView = binding.devicesInRange;
            deviceLisAdapter = new DeviceListAdapter(getContext(), R.layout.recycler_card_device, devicesInRange);
            devicesInRangeListView.setAdapter(deviceLisAdapter);
        });
    }

    private  ArrayList<SdpBluetoothConnection> mergeConnectionLists(ArrayList<SdpBluetoothConnection> listOne, ArrayList<SdpBluetoothConnection> listTwo, ArrayList<SdpBluetoothConnection> listThree, ArrayList<SdpBluetoothConnection> listFour)
    {
        Set<SdpBluetoothConnection> fooSet = new LinkedHashSet<>(listOne);
        fooSet.addAll(listTwo);
        fooSet.addAll(listThree);
        fooSet.addAll(listFour);
        return new ArrayList<>(fooSet);
    }
}
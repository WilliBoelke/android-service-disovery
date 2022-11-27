package willi.boelke.service_discovery_demo.view.demoFragments;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;

import willi.boelke.service_discovery_demo.R;
import willi.boelke.service_discovery_demo.databinding.FragmentBluetoothConnectBinding;
import willi.boelke.service_discovery_demo.view.listAdapters.BluetoothConnectionListAdapter;
import willi.boelke.service_discovery_demo.view.listAdapters.DeviceListAdapter;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothConnection;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothServiceConnectionEngine;

/**
 * This fragment uses the {@link BluetoothServiceConnectionEngine}
 * to connect to remote devices / services on those devices
 */
public class BluetoothConnectionFragment extends Fragment
{

    //------------Instance Variables------------

    private FragmentBluetoothConnectBinding binding;
    private DeviceListAdapter deviceListAdapter;
    private BluetoothConnectionListAdapter connectionListAdapter;
    private BluetoothConnectionViewModel model;

    private final ArrayList<BluetoothConnection> openConnections = new ArrayList<>();
    private final ArrayList<BluetoothDevice> devicesInRange = new ArrayList<>();


    //
    //  ----------  activity/fragment lifecycle ----------
    //

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        binding = FragmentBluetoothConnectBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        model = new ViewModelProvider(this).get(BluetoothConnectionViewModel.class);


        //--- setup views ---//

        this.setupClickListener();
        this.setupListViews();

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
        model.goInactive();
    }

    /**
     * Sets up the list view which display the
     * connections and discovered devices.
     * <p>
     * The Adapters will be stored in instance variables
     * to access and update them later on in {@link #devicesInRangeObserver()}
     * and {@link #connectionsObserver()}
     */
    private void setupListViews()
    {
        //--- connections ListView ---//
        connectionListAdapter = new BluetoothConnectionListAdapter(getContext(), R.layout.list_item_srv_conection, openConnections);
        binding.connectionListView.setAdapter(connectionListAdapter);

        //--- discovered devices ListView ---//
        deviceListAdapter = new DeviceListAdapter(getContext(), R.layout.list_item_device, devicesInRange);
        binding.devicesInRange.setAdapter(deviceListAdapter);
    }

    /**
     * Sets the click listener of all Buttons in this fragment to
     * {@link #onClickEventHandler(View)}.
     */
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

    /**
     * Handles all click events on button in this
     * view as set in {@link #setupClickListener()}
     */
    private void onClickEventHandler(View view)
    {
        if (!BluetoothServiceConnectionEngine.getInstance().isRunning())
        {
            return;
        }
        if (binding.discoverableButton.equals(view))
        {
            model.makeDiscoverable();
        }
        if (binding.startDiscoveryButton.equals(view))
        {
            model.starDiscovery();
        }
        else if (binding.startServiceOneButton.equals(view))
        {
            model.startServiceOne();
        }
        else if (binding.startServiceTwoButton.equals(view))
        {
            model.startServiceTwo();
        }
        else if (binding.endServiceOneButton.equals(view))
        {
            model.stopServiceOne();
        }
        else if (binding.endServiceTwoButton.equals(view))
        {
            model.stopServiceTwo();
        }
        else if (binding.startSdpOneButton.equals(view))
        {
            model.startClientOne();
        }
        else if (binding.startSdpTwoButton.equals(view))
        {
            model.startClientTwo();
        }
        else if (binding.endSdpOneButton.equals(view))
        {
            model.stopClientOne();
        }
        else if (binding.endSdpTwoButton.equals(view))
        {
            model.stopClientTwo();
        }
        else if (binding.refreshButton.equals(view))
        {
            model.refreshServices();
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
        model.getMessage().observe(this.getViewLifecycleOwner(), message -> binding.msgTextView.setText(message));
    }

    private void notificationObserver()
    {
        model.getNotification().observe(this.getViewLifecycleOwner(), message -> Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show());
    }

    /**
     * Registers observers at the controllers to get
     * notified about new connections.
     */
    private void connectionsObserver()
    {
        model.getOpenConnections().observe(this.getViewLifecycleOwner(), newConnections ->
        {
            this.openConnections.clear();
            this.openConnections.addAll(newConnections);
            connectionListAdapter.notifyDataSetChanged();
        });
    }

    private void devicesInRangeObserver()
    {
        model.getDiscoveredDevices().observe(this.getViewLifecycleOwner(), newDevices ->
        {
            this.devicesInRange.clear();
            this.devicesInRange.addAll(newDevices);
            deviceListAdapter.notifyDataSetChanged();
        });
    }
}
package willi.boelke.service_discovery_demo.view.demoFragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import willi.boelke.serviceDiscovery.serviceDescription.ServiceDescription;
import willi.boelke.serviceDiscovery.wifiDirect.sdpWifiDirectDiscovery.SdpWifiDirectDiscoveryEngine;
import willi.boelke.serviceDiscovery.wifiDirect.sdpWifiDirectDiscovery.WifiServiceDiscoveryListener;
import willi.boelke.service_discovery_demo.databinding.FragmentWifiDirectDiscoverBinding;
import willi.boelke.service_discovery_demo.view.MainActivity;

public class WifiDirectDiscoveryFragment extends Fragment implements WifiServiceDiscoveryListener
{
    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    private FragmentWifiDirectDiscoverBinding binding;

    private SdpWifiDirectDiscoveryEngine engine;

    private MainActivity mainActivity;


    //
    //  ----------  activity/fragment lifecycle ----------
    //

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        binding = FragmentWifiDirectDiscoverBinding.inflate(inflater, container, false);

        View root = binding.getRoot();

        // Init the engine
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Toast.makeText(getActivity(), "Missing permission", Toast.LENGTH_LONG).show();
        }
        else
        {
            this.engine = SdpWifiDirectDiscoveryEngine.getInstance();
            this.engine.start(this.getActivity().getApplicationContext());
            this.engine.registerDiscoverListener(this);
        }

        setupClickListener();
        this.mainActivity = (MainActivity)this.getActivity();
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
            SdpWifiDirectDiscoveryEngine.getInstance().startDiscovery();
        }
        if (binding.startDiscoveryOneBtn.equals(view))
        {
            SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(mainActivity.getDescriptionForServiceOne());
        }
        else if (binding.endDiscoveryOneBtn.equals(view))
        {
            SdpWifiDirectDiscoveryEngine.getInstance().stopSDPDiscovery(mainActivity.getDescriptionForServiceOne());
        }
        else if (binding.startDiscoveryTwoBtn.equals(view))
        {
            SdpWifiDirectDiscoveryEngine.getInstance().startSdpDiscoveryForService(mainActivity.getDescriptionForServiceTwo());
        }
        else if (binding.endDiscoveryTwoBtn.equals(view))
        {
            SdpWifiDirectDiscoveryEngine.getInstance().stopSDPDiscovery(mainActivity.getDescriptionForServiceTwo());
        }
        else if (binding.endDiscoveryButton.equals(view))
        {
            SdpWifiDirectDiscoveryEngine.getInstance().stopDiscovery();
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

    @Override
    public void onServiceDiscovered(WifiP2pDevice host, ServiceDescription description)
    {

    }
}

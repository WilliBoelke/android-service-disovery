package willi.boelke.service_discovery_demo.view.listAdapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import willi.boelke.service_discovery_demo.R;
import willi.boelke.serviceDiscovery.wifiDirect.sdpWifiEngine.SdpWifiConnection;


public class WifiConnectionListAdapter extends ArrayAdapter<SdpWifiConnection>
{

    private LayoutInflater mLayoutInflater;
    private ArrayList<SdpWifiConnection> connections;
    private int mViewResourceId;

    public WifiConnectionListAdapter(Context context, int tvResourceId, ArrayList<SdpWifiConnection> devices)
    {
        super(context, tvResourceId, devices);
        this.connections = devices;
        mLayoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mViewResourceId = tvResourceId;
    }

    public View getView(int position, View convertView, ViewGroup parent)
    {
        convertView = mLayoutInflater.inflate(mViewResourceId, null);

        SdpWifiConnection connection = connections.get(position);

        //Setup the name TextView
        TextView name = convertView.findViewById(R.id.service_name_tv);
        TextView description = convertView.findViewById(R.id.description_tv);
        TextView peerName = convertView.findViewById(R.id.name_tv);
        TextView peerAddress = convertView.findViewById(R.id.address_tv);
        TextView peerState = convertView.findViewById(R.id.peer_state_tv);

        name.setText(connection.getServiceDescription().getServiceRecord().get("service-name"));
        description.setText(connection.getServiceDescription().getServiceRecord().get("service-info"));
        peerAddress.setText(connection.getRemoteDeviceAddress());
        peerName.setText("");
        peerState.setText("");

        return convertView;
    }


}
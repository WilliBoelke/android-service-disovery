package willi.boelke.service_discovery_demo.view.listAdapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import willi.boelke.service_discovery_demo.R;
import willi.boelke.serviceDiscovery.bluetooth.sdpBluetoothEngine.SdpBluetoothConnection;


public class ConnectionListAdapter extends ArrayAdapter<SdpBluetoothConnection>
{

    private LayoutInflater mLayoutInflater;
    private ArrayList<SdpBluetoothConnection> connections;
    private int mViewResourceId;

    public ConnectionListAdapter(Context context, int tvResourceId, ArrayList<SdpBluetoothConnection> devices)
    {
        super(context, tvResourceId, devices);
        this.connections = devices;
        mLayoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mViewResourceId = tvResourceId;
    }

    public View getView(int position, View convertView, ViewGroup parent)
    {
        convertView = mLayoutInflater.inflate(mViewResourceId, null);

        SdpBluetoothConnection connection = connections.get(position);


        //Setup the name TextView
        TextView name = convertView.findViewById(R.id.service_name_tv);
        TextView description = convertView.findViewById(R.id.description_tv);
        TextView peerName = convertView.findViewById(R.id.name_tv);
        TextView peerAddress = convertView.findViewById(R.id.address_tv);
        TextView peerState = convertView.findViewById(R.id.peer_state_tv);

        name.setText(connection.getServiceDescription().getServiceName());
        description.setText(connection.getServiceDescription().getServiceRecord().get("service-info"));
        peerAddress.setText(connection.getRemoteDeviceAddress());
        peerName.setText(connection.getRemoteDevice().getName());
        if(connection.isServerPeer())
        {
            peerState.setText("as Client");
        }
        else{
            peerState.setText("as Server");
        }

        return convertView;
    }


}
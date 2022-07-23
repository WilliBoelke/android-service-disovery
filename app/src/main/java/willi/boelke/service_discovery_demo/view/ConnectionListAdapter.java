package willi.boelke.service_discovery_demo.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import willi.boelke.service_discovery_demo.R;
import willi.boelke.servicedisoveryengine.serviceDiscovery.bluetooth.sdpBluetoothConnection.SdpBluetoothConnection;


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
        TextView name = convertView.findViewById(R.id.device_name_tv);

        name.setText(connection.getRemoteDevice().getName() + " / " + connection.getServiceUUID());

        TextView address = convertView.findViewById(R.id.device_address_tv);
        address.setText(connection.getRemoteDeviceAddress());

        TextView isServer = convertView.findViewById(R.id.is_server_text_view);
        isServer.setText(Boolean.toString(connection.isServerPeer()));

        return convertView;
    }


}
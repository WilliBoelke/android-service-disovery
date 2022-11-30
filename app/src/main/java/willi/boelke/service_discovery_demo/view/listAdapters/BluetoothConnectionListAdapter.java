package willi.boelke.service_discovery_demo.view.listAdapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import willi.boelke.service_discovery_demo.R;
import willi.boelke.services.serviceConnection.bluetoothServiceConnection.BluetoothConnection;


public class BluetoothConnectionListAdapter extends ArrayAdapter<BluetoothConnection>
{

    private final LayoutInflater mLayoutInflater;
    private final ArrayList<BluetoothConnection> connections;
    private final int mViewResourceId;

    public BluetoothConnectionListAdapter(Context context, int tvResourceId, ArrayList<BluetoothConnection> devices)
    {
        super(context, tvResourceId, devices);
        this.connections = devices;
        mLayoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mViewResourceId = tvResourceId;
    }

    @SuppressLint("MissingPermission")
    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        convertView = mLayoutInflater.inflate(mViewResourceId, null);

        BluetoothConnection connection = connections.get(position);

        //Setup the name TextView
        TextView name = convertView.findViewById(R.id.service_tv);
        TextView serviceName = convertView.findViewById(R.id.service_name_tv);
        TextView description = convertView.findViewById(R.id.description_tv);
        TextView peerName = convertView.findViewById(R.id.name_tv);
        TextView peerAddress = convertView.findViewById(R.id.address_tv);
        TextView peerState = convertView.findViewById(R.id.peer_state_tv);
        TextView uuid = convertView.findViewById(R.id.uuid_tv);

        String srvInstance = connection.getServiceDescription().getInstanceName() + "." + connection.getServiceDescription().getServiceType();
        name.setText(srvInstance);
        description.setText(connection.getServiceDescription().getTxtRecord().get("info"));
        serviceName.setText(connection.getServiceDescription().getTxtRecord().get("name"));
        uuid.setText(connection.getServiceDescription().getServiceUuid().toString());
        peerAddress.setText(connection.getRemoteDeviceAddress());
        peerName.setText(connection.getRemoteDevice().getName());

        if (connection.isServerPeer())
        {
            peerState.setText(R.string.ConnectionType_Client);
        }
        else
        {
            peerState.setText(R.string.ConnectionType_Server);
        }

        return convertView;
    }
}
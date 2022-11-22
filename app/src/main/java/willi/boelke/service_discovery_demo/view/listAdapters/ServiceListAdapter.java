package willi.boelke.service_discovery_demo.view.listAdapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import willi.boelke.service_discovery_demo.R;
import willi.boelke.services.serviceDiscovery.ServiceDescription;


public class ServiceListAdapter extends ArrayAdapter<ServiceDescription>
{

    private final LayoutInflater mLayoutInflater;
    private final ArrayList<ServiceDescription> services;
    private final int mViewResourceId;

    public ServiceListAdapter(Context context, int tvResourceId, ArrayList<ServiceDescription> devices)
    {
        super(context, tvResourceId, devices);
        this.services = devices;
        mLayoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mViewResourceId = tvResourceId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        convertView = mLayoutInflater.inflate(mViewResourceId, null);

        ServiceDescription service = services.get(position);

        //Setup the name TextView
        TextView name = convertView.findViewById(R.id.service_name_tv);
        TextView description = convertView.findViewById(R.id.description_tv);
        String nameString = service.getServiceName() + " / " + service.getServiceUuid().toString();
        name.setText(nameString);

        if (service.getServiceRecord().size() > 0)
        {
            description.setText(service.getServiceRecord().toString());
        }
        else
        {
            description.setText("No record resolved");
        }

        return convertView;
    }
}
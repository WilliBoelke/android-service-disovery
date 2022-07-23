package willi.boelke.servicedisoveryengine.serviceDiscovery.wifiDirectServiceDiscovery;

import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class WifiDirectService
{
     final static String SERVICE_DESCRIPTION = "service-description";
     final static String SERVICE_NAME = "service-name";

    private String serviceName;

    private String serviceDescription;

    private HashMap<String, String> additionalServiceRecords;

    WifiP2pDnsSdServiceInfo serviceInfo;

    public WifiDirectService( String serviceName, String serviceDescription, HashMap<String, String> additionalServiceRecords){
        this.serviceName = serviceName;
        this.serviceDescription = serviceDescription;
        this.additionalServiceRecords = additionalServiceRecords;

        registerService();
    }

    private void registerService(){
        Map<String, String> serviceRecords = new HashMap<>();
        serviceRecords.put(SERVICE_DESCRIPTION, this.serviceDescription);
        serviceRecords.put(SERVICE_NAME, this.serviceName);
        serviceRecords.putAll(this.additionalServiceRecords);

         serviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance(serviceName, "_presence._tcp", serviceRecords);
    }


    public WifiP2pDnsSdServiceInfo getServiceInfo()
    {
        return serviceInfo;
    }

    public void onSuccess()
    {
        Log.d("Service " + this.serviceName, "Was made discoverable");
    }

    public void onFailure(int cause)
    {
        Log.d("Service " + this.serviceName, "was not  made discoverable due too " + cause);
    }

    public String getServiceName()
    {
        return  this.serviceName;
    }
}

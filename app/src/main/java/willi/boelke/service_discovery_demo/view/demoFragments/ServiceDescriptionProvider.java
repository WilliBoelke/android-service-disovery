package willi.boelke.service_discovery_demo.view.demoFragments;

import java.util.HashMap;

import willi.boelke.services.serviceDiscovery.ServiceDescription;

/**
 * This basically just initializes the descriptions used for the demo application
 * so the are on a single point and not scattered throughout the application.
 *
 * @author WilliBoelke
 */
public class ServiceDescriptionProvider
{
    private static ServiceDescription one;
    private static ServiceDescription two;

    private void ServiceDescription(){
        // private constructor
    }

    public static ServiceDescription getServiceDescriptionOne(){
        if(one == null){
            HashMap<String, String> serviceAttributesTwo = new HashMap<>();
            serviceAttributesTwo.put("name", "Counting Service one");
            serviceAttributesTwo.put("info", "This service counts upwards and sends a message containing this number to all clients");
            one = new ServiceDescription(getCurrentDeviceName(), serviceAttributesTwo, "_demoOne._tcp");
        }
        return one;
    }

    public static ServiceDescription getServiceDescriptionOTwo(){
        if(two == null){
            HashMap<String, String> serviceAttributesTwo = new HashMap<>();
            serviceAttributesTwo.put("name", "Counting Service two");
            serviceAttributesTwo.put("info", "This service counts upwards an sends a message containing this number to all clients");
            two = new ServiceDescription(getCurrentDeviceName(), serviceAttributesTwo, "_demoTwo._tcp");

            HashMap<String, String> txtRecord = new HashMap<>();
            txtRecord.put("name", "Example Service");
            txtRecord.put("port", "4242");
            ServiceDescription example = new ServiceDescription(
                    "Example Service",
                    txtRecord,
                    "_exampleSrv._tcp");

        }
       return two;
    }



    /**
     * Returns a string containing the device name and manufacturer,
     * to distinct between devices at runtime
     *
     * @return a device name string
     */
    private static String getCurrentDeviceName()
    {
        //----------------------------------
        // NOTE : the actual mac address of
        // the local device would have been better
        // since then they could have been compared
        // more easily
        //----------------------------------
        return android.os.Build.MANUFACTURER + android.os.Build.MODEL;
    }
}

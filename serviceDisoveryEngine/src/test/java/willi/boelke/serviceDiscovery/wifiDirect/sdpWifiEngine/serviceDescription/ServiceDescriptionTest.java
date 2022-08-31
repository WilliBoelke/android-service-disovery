package willi.boelke.serviceDiscovery.wifiDirect.sdpWifiEngine.serviceDescription;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.HashMap;

import willi.boelke.serviceDiscovery.serviceDescription.ServiceDescription;

/**
 * Unit tests for {@link ServiceDescription}
 *
 * @author WilliBoelke
 */
public class ServiceDescriptionTest
{

    @Test
    public void itShouldBeInitialized()
    {
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        serviceAttributesOne.put("service-name", "Test Service One");
        serviceAttributesOne.put("service-info", "This is a test service description");
        ServiceDescription descriptionForServiceOne = new ServiceDescription(serviceAttributesOne);
    }

    @Test
    public void itShouldGenerateDifferentUuidsBasedOnServiceAttributes()
    {
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("service-name", "Test Service One");
        serviceAttributesOne.put("service-info", "This is a test service description");
        serviceAttributesTwo.put("service-name", "Counting Service Two");
        serviceAttributesTwo.put("service-info", "This service counts upwards an sends a message containing this number to all clients");
        ServiceDescription descriptionForServiceOne = new ServiceDescription(serviceAttributesOne);
        ServiceDescription descriptionForServiceTwo = new ServiceDescription(serviceAttributesTwo);
        assertNotEquals(descriptionForServiceTwo.getServiceUuid(), descriptionForServiceOne.getServiceUuid());
    }

    @Test
    public void itShouldGenerateDifferentUuidsBasedOnServiceAttributeKeys()
    {
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("service-name", "Test Service One");
        serviceAttributesOne.put("service-info", "This is a test service description");
        serviceAttributesTwo.put("serviceName", "Counting Service One");
        serviceAttributesTwo.put("service-information", "This is a test service description");
        ServiceDescription descriptionForServiceOne = new ServiceDescription(serviceAttributesOne);
        ServiceDescription descriptionForServiceTwo = new ServiceDescription(serviceAttributesTwo);
        assertNotEquals(descriptionForServiceTwo.getServiceUuid(), descriptionForServiceOne.getServiceUuid());
    }


    @Test
    public void twoServicesWithSameAttributesShouldBeEqual()
    {
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("service-name", "Test Service One");
        serviceAttributesOne.put("service-info", "This is a test service description");
        serviceAttributesTwo.put("service-name", "Test Service One");
        serviceAttributesTwo.put("service-info", "This is a test service description");
        ServiceDescription descriptionForServiceOne = new ServiceDescription(serviceAttributesOne);
        ServiceDescription descriptionForServiceTwo = new ServiceDescription(serviceAttributesTwo);
        assertEquals(descriptionForServiceTwo.getServiceUuid(), descriptionForServiceOne.getServiceUuid());
    }
}
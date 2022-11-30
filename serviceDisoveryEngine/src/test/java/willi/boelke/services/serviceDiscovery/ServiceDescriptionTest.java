package willi.boelke.services.serviceDiscovery;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.HashMap;
import java.util.UUID;

/**
 * Unit tests for {@link ServiceDescription}
 *
 * @author WilliBoelke
 */
public class ServiceDescriptionTest
{

    @Test
    public void itShouldGenerateDifferentUuidsBasedOnServiceType()
    {
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("service-name", "Test Service One");
        serviceAttributesTwo.put("service-name", "Test Service One");
        ServiceDescription descriptionForServiceOne = new ServiceDescription(
                "Test Service One",
                serviceAttributesOne,
                "_testOne._tcp");
        ServiceDescription descriptionForServiceTwo = new ServiceDescription(
                "Test Service One",
                serviceAttributesTwo,
                "_testTwo._tcp");
        assertNotEquals(descriptionForServiceTwo.getServiceUuid(), descriptionForServiceOne.getServiceUuid());
    }


    @Test
    public void itShouldGenerateTheSameUUIdWhenInstanceIsDifferent()
    {
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("name", "Test Service One");
        serviceAttributesTwo.put("name", "Test Service One");
        ServiceDescription descriptionForServiceOne = new ServiceDescription(
                "Test Service Two",
                serviceAttributesOne,
                "_testOne._tcp");
        ServiceDescription descriptionForServiceTwo = new ServiceDescription(
                "Test Service One",
                serviceAttributesTwo,
                "_testOne._tcp");
        assertEquals(descriptionForServiceTwo.getServiceUuid(), descriptionForServiceOne.getServiceUuid());
    }

    @Test
    public void itShouldGenerateTheSameUUIdWhenAttributesAreDifferent()
    {
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("name", "Test Service One");
        serviceAttributesTwo.put("name", "Test Service Two 4242");
        ServiceDescription descriptionForServiceOne = new ServiceDescription(
                "Test Service One",
                serviceAttributesOne,
                "_testOne._tcp");
        ServiceDescription descriptionForServiceTwo = new ServiceDescription(
                "Test Service One",
                serviceAttributesTwo,
                "_testOne._tcp");
        assertEquals(descriptionForServiceTwo.getServiceUuid(), descriptionForServiceOne.getServiceUuid());
    }


    /**
     * Custom UUIDs can be set for the use of Bluetooth
     * these should override the uuid generated from the attributes
     */
    @Test
    public void itShouldOverwriteTheUuid()
    {
        HashMap<String, String> serviceAttributesOne = new HashMap<>();
        HashMap<String, String> serviceAttributesTwo = new HashMap<>();
        serviceAttributesOne.put("service-name", "Test Service One");
        serviceAttributesOne.put("service-info", "This is a test service description");
        serviceAttributesTwo.put("service-name", "Test Service One");
        serviceAttributesTwo.put("service-info", "This is a test service description");
        ServiceDescription descriptionForServiceOne = new ServiceDescription("Test Service One",
                serviceAttributesOne,
                "_testOne._tcp");
        ServiceDescription descriptionForServiceTwo = new ServiceDescription(
                "Test Service One",
                serviceAttributesTwo,
                "_testTwo._tcp");
        UUID uuid = UUID.fromString("e3d4932e-1016-4b95-8466-9f160ec1b553");
        descriptionForServiceTwo.overrideUuidForBluetooth(uuid);
        assertNotEquals(descriptionForServiceTwo.getServiceUuid(), descriptionForServiceOne.getServiceUuid());
        assertEquals(descriptionForServiceTwo.getServiceUuid(), uuid);
    }
}
package willi.boelke.servicedisoveryengine.serviceDiscovery.serviceDescription;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Unit tests for {@link ServiceDescription}
 *
 * @author WilliBoelke
 */
public class ServiceDescriptionTest
{


    @Test
    public void itShouldSaveTheName()
    {
        String name = "testName";
        ServiceDescription description = new ServiceDescription(name);
        assertEquals(name, description.getServiceRecord().get(ServiceDescription.SERVICE_NAME_KEY));
    }

    @Test
    public void itShouldGenerateAUuid()
    {
        String name = "testName";
        ServiceDescription description = new ServiceDescription(name);
        assertEquals("d7abccd4-0c20-31ae-80f2-55219fa39d9e", description.getServiceUuid().toString());
    }

    @Test
    public void itShouldGenerateDifferentUuidsBasedOnServiceAttributes()
    {
        String name = "testName";
        //description one
        ServiceDescription descriptionOne = new ServiceDescription(name);
        descriptionOne.addAttribute("anotherAttribute", "value");
        // description two with different attribute value
        ServiceDescription descriptionTwo = new ServiceDescription(name);
        descriptionTwo.addAttribute("anotherAttribute", "andAnotherValue");
        assertNotEquals(descriptionTwo.getServiceUuid(), descriptionOne.getServiceUuid());
    }


    @Test
    public void itShouldGenerateDifferentUuidsBasedOnServiceAttributeKeys()
    {
        String name = "testName";
        //description one
        ServiceDescription descriptionOne = new ServiceDescription(name);
        descriptionOne.addAttribute("anotherAttributeOne", "value");
        // description two with different attribute value
        ServiceDescription descriptionTwo = new ServiceDescription(name);
        descriptionTwo.addAttribute("anotherAttributeTwo", "value");
        assertNotEquals(descriptionTwo.getServiceUuid(), descriptionOne.getServiceUuid());
    }

    @Test
    public void itShouldGenerateDifferentUuidsBasedOnServiceAttributeValues()
    {
        String name = "testName";
        //description one
        ServiceDescription descriptionOne = new ServiceDescription(name);
        descriptionOne.addAttribute("anotherAttribute", "valueOne");
        // description two with different attribute value
        ServiceDescription descriptionTwo = new ServiceDescription(name);
        descriptionTwo.addAttribute("anotherAttribute", "valueTwo ");
        assertNotEquals(descriptionTwo.getServiceUuid(), descriptionOne.getServiceUuid());
    }



    @Test
    public void itShouldIgnoreAttributesWhenCustomUuidIsSet()
    {
        String name = "testName";
        String uuid = "d4abccd4-0c20-31ae-80f2-55219fa39d9e";
        ServiceDescription description = new ServiceDescription(name);
        description.addAttribute("attribute", "value");
        description.setCustomUUuid(UUID.fromString(uuid));
        assertEquals(uuid, description.getServiceUuid().toString());
    }

    @Test
    public void itShouldIgnoreFurtherAttributeChangesOnceTheUuidWasGenerated()
    {
        String name = "testName";
        String uuid = "d7abccd4-0c20-31ae-80f2-55219fa39d9e";
        ServiceDescription description = new ServiceDescription(name);
        assertEquals(uuid, description.getServiceUuid().toString());
        description.addAttribute("attribute", "value");
        assertEquals(uuid, description.getServiceUuid().toString());
    }

    @Test
    public void itShouldReverseUuid(){
        String name = "testName";
        String uuid = "d7abccd4-0c20-31ae-80f2-55219fa39d9e";
        ServiceDescription description = new ServiceDescription(name);
        description.setCustomUUuid(UUID.fromString(uuid));
        assertEquals("9e9da39f-2155-f280-ae31-200cd4ccabd7", description.getBytewiseReverseUuid().toString());
    }



}
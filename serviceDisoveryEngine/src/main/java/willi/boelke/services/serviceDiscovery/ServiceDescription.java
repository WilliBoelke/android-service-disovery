package willi.boelke.services.serviceDiscovery;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Describes a service.
 * The idea here is to make one "Service Description"
 * which can be applied for both Bluetooth and Wifi-Direct
 * (Bonjour mDNS-SD Services).
 * <h2>What is needed to describe a Service for both protocols in Android?</h2>
 * <h3>Bluetooth</h3>
 * Bluetooth requires a UUID and a service name t5o create a Bluetooth Server Socked
 * and to register with the local SDP server.
 * <h3>Bonjour / mDNS-SD</h3>
 * As (multicast) DNS-SD is strictly on DNS there are several things to be considered
 * for describing / advertising a service:
 * A (m) DNS-SD service is described through mainly two things :
 * <p>
 * A service instance name PTR record
 * A DNS-SD service requires a instance name.
 * This instance name "can" be assigned can the user and is
 * not required to be the same for each instance of and a domain
 * which for a link local application like this is structured as :
 * "_[instance]._[protocol]._[transport].local.", where
 * the instance field contains the name of the instance,
 * the protocol is the services protocol and the transport either
 * _udp or _tcp depending on the offered transport protocol.
 * See <a href="https://www.ietf.org/rfc/rfc6763.txt">RFC 6763</a> for more
 * information on this.
 * <h2>Connecting the two</h2>
 * As mentioned this class is aiming on connecting the two - so that for one
 * application, offering one (or several) service(s)
 * through both, Bluetooth ad WiFi Direct a single Service Description
 * can be applied which offers every necessary information to connect
 * to the service through either of the aforementioned techniques.
 *
 * <h3>Service name</h3>
 * The android API for WiFi direct does not offer complete access to the
 * SRV / PTR record as described in RFC 6763, alas a service name and type
 * can be specified. This equals the aforementioned instance name and domain.
 *
 * Also for Bluetooth a service name is required to be registered within the
 * local SPD server.
 *
 * This will be reflected in here through the {@link this.serviceName}
 * which ahs to be set on initialization.
 *
 * <h3>Service Domain / type</h3>
 * The service Type should be String contain the service
 * domain as _[protocol]._[transport].
 *
 * <h3>Service TXT Record</h3>
 * The Service TXT record is a list of key/value pairs.
 * For mDNS a limit of 8900 should not be exceeded.
 * And each key-value pair should not exceed 255 bytes, whereas
 * the key should (optimally) kept to a size of 9 bytes.
 * Please refer to <a href="https://www.ietf.org/rfc/rfc6763.txt">RFC 6763</a>
 * for further information.
 *
 * <h3>The UUID</h3>
 * As mentioned android requires a UUID for registering a service withing
 * the SDP server. This UUID can be generated as a name-based UUID
 * <a href="https://www.ietf.org/rfc/rfc4122.txt">RFC 4122</a>
 * by calculating MD5 Hash over the of the service type and the
 * contents of the TXT records.
 * <p>
 * <h2>Overwriting the UUID</h2>
 * As mentioned per default the UUID will be generated
 * through hashing.
 * To account for use cases where this method is not usable
 * (e.g. advertising services for and or looking for services to
 * other application which do not use UUIDs generated like that)
 * a custom UUID can be set using {@link #overrideUuidForBluetooth(UUID)}.
 * This is for the use with Bluetooth and cant be used for Wifi Direct.
 * Wifi-Direct will use the full Service Records and not the UUID.
 * Thus the UUID cant be resolved at the discovering side.
 * <p>
 * <h2>Equality</h2>
 * For service descriptions to be equal the service UUID needs to be the same
 * This is the only attribute that will be compared in {@link #equals(Object)}
 *
 * @author WilliBoelke
 */
public class ServiceDescription
{
    //
    //  ----------  instance variables ----------
    //

    /**
     * The Service attributes
     */
    private final Map<String, String> attributes;

    /**
     * The name of the service
     * This will be used to register it
     * either when creating the bluetooth server socket
     * or a bonjour / mDNS servers as instance name.
     * It does not have to be the same on all service instances
     */
    private final String serviceName;

    /**
     * A custom uuid, which - when set- will be used
     * instead of a generated one
     */
    private UUID serviceUuid;

    /**
     * The Bonjour / DNS Service type
     */
    private final String serviceType;

    //
    //  ---------- constructor and initialization ----------
    //

    /**
     * Public constructor
     *
     * @param serviceRecord
     *         The service record, this needs to contain at least one
     *         key - value
     */
    public ServiceDescription(String serviceName, Map<String, String> serviceRecord, String serviceType)
    {
        this.serviceName = serviceName;
        this.attributes = serviceRecord;
        this.serviceType = serviceType;
    }

    /**
     * A UUID set using this overrides the UUID generated from teh service records,
     * making it and the service records independent from each other.
     *
     * <h3>NOTE</h3>
     * ...that this only work for the Bluetooth service discovery and wont
     * work with WifiDirect
     * This is based on WiFi direct exchanging the TXT records, instance name and
     * service type, while Bluetooth will exchange only the UUID itself.
     *
     * @param uuid
     *         A custom UUId overriding the one generated from the Service records
     */
    public void overrideUuidForBluetooth(UUID uuid)
    {
        // it should be worth it to make wifi direct exchanging the UUID
        // when a custom UUID has been set, the custom UUID though should stay, so Bluetooth service
        // which don't use a UUID based on these service records can be found.
        // For now - its here in the comment.
        this.serviceUuid = uuid;
    }

    /**
     * Returns either the UUID set through {@link #overrideUuidForBluetooth(UUID)}
     * or a UUID generated from the services attributes.
     *
     * @return the services UUID
     *
     * @throws NullPointerException
     *         IF the attributes and the custom UUID are null
     */
    public UUID getServiceUuid()
    {
        if (this.serviceUuid == null)
        {
            //--- generating UUID from attributes ---//
            this.serviceUuid = getUuidForService( this.attributes, this.serviceType);
        }

        return this.serviceUuid;
    }

    /**
     * Returns the service type
     * @return
     * the service type of the service
     *
     */
    public String getServiceType(){
        return this.serviceType;
    }

    /**
     * Returns the Service records ad a Map object.
     * The map containing all key value pairs set through
     *
     * @return The service records Map
     */
    public Map<String, String> getServiceRecord()
    {
        return this.attributes;
    }

    /**
     * Generates a name based (type 3) UUID from a Map (service records)
     *
     * @param serviceRecord
     *         A Map containing key value pairs, describing a service
     *
     * @return A UUID generated from the map entries
     *
     * @throws NullPointerException
     *         If the given Map was empty
     */
    public static UUID getUuidForService( Map<String, String> serviceRecord, String serviceType)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(serviceType);
        for (Map.Entry<String, String> entry : serviceRecord.entrySet())
        {
            sb.append(entry.getKey());
            sb.append(entry.getValue());
        }
        return UUID.nameUUIDFromBytes(sb.toString().getBytes(StandardCharsets.UTF_8));
    }


    /**
     * This method reverses a UUID Bytewise
     * <p>
     * This is a workaround for a problem which causes UUIDs
     * obtained with `fetchUuidsWithSdp()` to be in a little endian format
     * This problem is apparently not specific to a certain android version
     * since it only occurred on one of my devices running Android 8.1, the other one
     * (with the same version) didn't had this problem.
     * <p>
     * This will be used on every discovered UUID when enabled in the
     * SdpBluetoothEngine`s configuration, sine the problem cant be predetermined
     * by any means i found.
     * <p>
     * <h2>References</h2>
     * This Problem is mentioned in the the
     * <a href="https://issuetracker.google.com/issues/37075233"> google Issue tracker</a>
     * The code used here to reverse the UUID is stolen from the issues comments and can be found
     * <a href="https://gist.github.com/masterjefferson/10922165432ec016a823e46c6eb382e6">here</a>
     *
     * @return the bytewise revered uuid
     */
    public UUID getBytewiseReverseUuid()
    {
        ByteBuffer byteBuffer = ByteBuffer.allocate(16);
        byteBuffer
                .putLong(this.getServiceUuid().getLeastSignificantBits())
                .putLong(this.getServiceUuid().getMostSignificantBits());
        byteBuffer.rewind();
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        return new UUID(byteBuffer.getLong(), byteBuffer.getLong());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        ServiceDescription that = (ServiceDescription) o;
        return this.getServiceUuid().equals(that.getServiceUuid());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.getServiceUuid(), serviceType);
    }

    @NonNull
    @Override
    public String toString()
    {
        return String.format("{|Name: %-20s|UUID: %-36s|Attr: %-10s|}",
                this.getServiceName(), this.getServiceUuid(), this.getServiceRecord());
    }

    /**
     * Getter for the service name
     *
     * @return the service name
     */
    public String getServiceName()
    {
        return this.serviceName;
    }
}

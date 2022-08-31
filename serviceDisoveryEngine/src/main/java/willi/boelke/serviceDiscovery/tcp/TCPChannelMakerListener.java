package willi.boelke.serviceDiscovery.tcp;

/**
 * Listener interface to listen on connection events on a {@link TCPChannelMaker}
 */
public interface TCPChannelMakerListener
{
     void onConnectionEstablished(TCPChannel channel);
     void onConnectionEstablishmentFailed(TCPChannel channel, String reason);
}

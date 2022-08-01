package willi.boelke.servicedisoveryengine.serviceDiscovery.tcp;

public interface TCPChannelMakerListener {
    public void onConnectionEstablished(TCPChannel channel);

    public void onConnectionEstablishmentFailed(TCPChannel channel, String reason);

}

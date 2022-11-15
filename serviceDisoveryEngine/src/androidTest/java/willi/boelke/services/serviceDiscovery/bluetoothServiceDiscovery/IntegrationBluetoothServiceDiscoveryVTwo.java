package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery;


import androidx.test.platform.app.InstrumentationRegistry;

/**
 * These tests {@link BluetoothServiceDiscoveryVTwo}
 * on actual hardware and with real connections.
 *
 * @author WilliBoelke
 */
public class IntegrationBluetoothServiceDiscoveryVTwo extends IntegrationBluetoothServiceDiscovery
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    @Override
    public void setup()
    {
        super.setup();
        BluetoothServiceDiscoveryVTwo.getInstance().start(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @Override
    public void teardown() throws NullPointerException
    {
        super.teardown();
        BluetoothServiceDiscoveryVTwo.getInstance().teardownEngine();
    }

    @Override
    public BluetoothServiceDiscoveryEngine getBluetoothDiscoveryImplementation()
    {
        return BluetoothServiceDiscoveryVTwo.getInstance();
    }
}

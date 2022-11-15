package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery;


import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;

/**
 * These tests {@link BluetoothServiceDiscoveryVOne}
 * on actual hardware and with real connections.
 *
 * @author WilliBoelke
 */
public class IntegrationBluetoothServiceDiscoveryVOne extends IntegrationBluetoothServiceDiscovery
{

    /**
     * Classname for logging
     */
    private final String TAG = this.getClass().getSimpleName();

    @Override
    public void setup()
    {
        super.setup();
        BluetoothServiceDiscoveryVOne.getInstance().start(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @After
    public void teardown() throws NullPointerException
    {
        super.teardown();
        BluetoothServiceDiscoveryVOne.getInstance().teardownEngine();
    }

    @Override
    public BluetoothServiceDiscoveryEngine getBluetoothDiscoveryImplementation()
    {
        return BluetoothServiceDiscoveryVOne.getInstance();
    }
}
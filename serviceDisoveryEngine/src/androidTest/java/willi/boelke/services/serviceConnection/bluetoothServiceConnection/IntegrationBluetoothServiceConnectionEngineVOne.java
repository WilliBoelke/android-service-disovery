package willi.boelke.services.serviceConnection.bluetoothServiceConnection;

import androidx.test.platform.app.InstrumentationRegistry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryVOne;

/**
 * These tests {@link BluetoothServiceConnectionEngine}
 * on actual hardware and with real connections.
 * As a discovery engine {@link BluetoothServiceDiscoveryVOne}
 * will be used.
 *
 * @author WilliBoelke
 */
public class IntegrationBluetoothServiceConnectionEngineVOne extends IntegrationBluetoothServiceConnectionEngine
{
    @Override
    public void setup()
    {
        super.setup();
        BluetoothServiceConnectionEngine.getInstance().start(InstrumentationRegistry.getInstrumentation().getTargetContext()
                , BluetoothServiceDiscoveryVOne.getInstance());
    }

    @Override
    public void teardown() throws NullPointerException, InvocationTargetException, IllegalAccessException, NoSuchMethodException
    {
        super.teardown();
        // tearing down discovery engine with reflections
        Method teardown = BluetoothServiceDiscoveryVOne.getInstance().getClass().getDeclaredMethod("teardownEngine");
        teardown.setAccessible(true);
        teardown.invoke(BluetoothServiceDiscoveryVOne.getInstance());
    }

    @Override
    protected BluetoothServiceConnectionEngine getConnectionEngine()
    {
        return BluetoothServiceConnectionEngine.getInstance();
    }
}
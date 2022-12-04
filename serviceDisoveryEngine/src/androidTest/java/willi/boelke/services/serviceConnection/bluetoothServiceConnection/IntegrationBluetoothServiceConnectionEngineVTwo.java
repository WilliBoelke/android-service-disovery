package willi.boelke.services.serviceConnection.bluetoothServiceConnection;

import androidx.test.platform.app.InstrumentationRegistry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryVOne;
import willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery.BluetoothServiceDiscoveryVTwo;

/**
 * These tests {@link BluetoothServiceConnectionEngine}
 * on actual hardware and with real connections.
 * As a discovery engine {@link BluetoothServiceDiscoveryVTwo}
 * will be used.
 *
 * @author WilliBoelke
 */
public class IntegrationBluetoothServiceConnectionEngineVTwo extends IntegrationBluetoothServiceConnectionEngine
{
    @Override
    public void setup()
    {
        super.setup();
        BluetoothServiceDiscoveryVTwo.getInstance().start(InstrumentationRegistry.getInstrumentation().getTargetContext());
        BluetoothServiceConnectionEngine.getInstance().start(InstrumentationRegistry.getInstrumentation().getTargetContext()
                , BluetoothServiceDiscoveryVTwo.getInstance());
    }

    @Override
    public void teardown() throws NullPointerException, InvocationTargetException, IllegalAccessException, NoSuchMethodException
    {
        super.teardown();
        // tearing down discovery engine with reflections
        Method teardown = BluetoothServiceDiscoveryVTwo.getInstance().getClass().getDeclaredMethod("teardownEngine");
        teardown.setAccessible(true);
        teardown.invoke(BluetoothServiceDiscoveryVTwo.getInstance());
    }

    @Override
    protected BluetoothServiceConnectionEngine getConnectionEngine()
    {
        return BluetoothServiceConnectionEngine.getInstance();
    }
}
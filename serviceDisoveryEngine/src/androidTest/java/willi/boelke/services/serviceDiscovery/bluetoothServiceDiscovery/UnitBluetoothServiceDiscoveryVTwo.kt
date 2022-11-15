package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery

import willi.boelke.services.testUtils.callPrivateFunc


class UnitBluetoothServiceDiscoveryVTwo : UnitBluetoothServiceDiscovery() {
    override fun teardown() {
        BluetoothServiceDiscoveryVTwo.getInstance().callPrivateFunc("teardownEngine")
    }

    override fun getDiscoveryEngine(): BluetoothServiceDiscoveryEngine {
        return BluetoothServiceDiscoveryVTwo.getInstance()
    }


    override fun specificSetup() {
        BluetoothServiceDiscoveryVTwo.getInstance().start(mockedContext, mockedBtAdapter)
        BluetoothServiceDiscoveryVTwo.getInstance().startDeviceDiscovery()
    }
}
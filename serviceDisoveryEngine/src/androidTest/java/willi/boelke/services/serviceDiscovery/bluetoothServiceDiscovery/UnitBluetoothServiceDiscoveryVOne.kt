package willi.boelke.services.serviceDiscovery.bluetoothServiceDiscovery

import willi.boelke.services.testUtils.callPrivateFunc

class UnitBluetoothServiceDiscoveryVOne : UnitBluetoothServiceDiscovery() {


    override fun specificSetup() {
        BluetoothServiceDiscoveryVOne.getInstance().start(mockedContext, mockedBtAdapter)
        BluetoothServiceDiscoveryVOne.getInstance().startDeviceDiscovery()
    }

    override fun teardown() {
        BluetoothServiceDiscoveryVOne.getInstance().callPrivateFunc("teardownEngine")
    }

    override fun getDiscoveryEngine(): BluetoothServiceDiscoveryEngine {
        return BluetoothServiceDiscoveryVOne.getInstance()
    }
}
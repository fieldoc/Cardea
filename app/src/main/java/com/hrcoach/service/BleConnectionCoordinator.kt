package com.hrcoach.service

import android.bluetooth.BluetoothDevice
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleConnectionCoordinator @Inject constructor(
    @ApplicationContext context: Context
) {
    private val manager = BleHrManager(context)

    val heartRate: StateFlow<Int> = manager.heartRate
    val isConnected: StateFlow<Boolean> = manager.isConnected
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = manager.discoveredDevices

    fun startScan(scanWindowMs: Long = 15_000L) {
        manager.startScan(scanWindowMs)
    }

    fun stopScan() {
        manager.stopScan()
    }

    fun connectToDevice(device: BluetoothDevice) {
        manager.connectToDevice(device)
    }

    fun connectToAddress(address: String): Boolean {
        return manager.connectToAddress(address)
    }

    fun disconnect() {
        manager.disconnect()
    }

    fun handoffConnectedDeviceAddress(fallbackAddress: String?): String? {
        manager.stopScan()
        return manager.currentDeviceAddress() ?: fallbackAddress
    }

    fun managerForWorkout(): BleHrManager = manager
}

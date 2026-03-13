package com.hrcoach.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BleHrManager(context: Context) {

    companion object {
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val DEFAULT_SCAN_WINDOW_MS = 15_000L
        private const val RECONNECT_DELAY_MS = 5_000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_BASE_DELAY_MS = 1_000L
        private val COOSPO_NAME_MARKERS = listOf("COOSPO", "H808")
        private val GENERIC_HR_NAME_MARKERS = listOf("HR", "HEART", "PULSE")

        fun isLikelyCoospoH808(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val normalized = name.uppercase()
            return COOSPO_NAME_MARKERS.any { marker -> normalized.contains(marker) }
        }

        private fun isLikelyHrName(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val normalized = name.uppercase()
            return GENERIC_HR_NAME_MARKERS.any { marker -> normalized.contains(marker) }
        }
    }

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateLock = Any()

    private var scanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanStopJob: Job? = null
    private var reconnectJob: Job? = null
    private var isScanning: Boolean = false
    private var shouldReconnect: Boolean = false
    private var lastDeviceAddress: String? = null
    private val deviceScores = mutableMapOf<String, Int>()
    private var reconnectAttempts: Int = 0

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    /** True once all reconnect attempts have been exhausted; cleared on a fresh connectToDevice call. */
    private val _connectionFailed = MutableStateFlow(false)
    val connectionFailed: StateFlow<Boolean> = _connectionFailed

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: result.scanRecord?.deviceName
            val hasHrService = result.scanRecord?.serviceUuids?.any { it.uuid == HR_SERVICE_UUID } == true
            val isCoospo = isLikelyCoospoH808(deviceName)
            val looksLikeHr = isLikelyHrName(deviceName)

            if (!hasHrService && !isCoospo && !looksLikeHr) return

            val score = when {
                isCoospo && hasHrService -> 300
                isCoospo -> 250
                hasHrService -> 200
                looksLikeHr -> 100
                else -> 0
            }
            upsertDiscoveredDevice(device, score)
        }

        override fun onScanFailed(errorCode: Int) {
            synchronized(stateLock) {
                isScanning = false
            }
        }
    }

    private fun upsertDiscoveredDevice(device: BluetoothDevice, score: Int) {
        synchronized(stateLock) {
            val address = device.address
            val existingDevices = _discoveredDevices.value.associateBy { it.address }.toMutableMap()
            existingDevices[address] = device
            deviceScores[address] = maxOf(score, deviceScores[address] ?: 0)
            _discoveredDevices.value = existingDevices.values
                .sortedByDescending { discovered -> deviceScores[discovered.address] ?: 0 }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(scanWindowMs: Long = DEFAULT_SCAN_WINDOW_MS) {
        synchronized(stateLock) {
            if (isScanning) return
        }
        if (bluetoothAdapter?.isEnabled != true) return

        val scannerToUse = bluetoothAdapter.bluetoothLeScanner ?: return
        synchronized(stateLock) {
            _discoveredDevices.value = emptyList()
            deviceScores.clear()
            scanner = scannerToUse
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val started = runCatching {
            scannerToUse.startScan(emptyList<ScanFilter>(), settings, scanCallback)
        }.isSuccess
        if (!started) {
            synchronized(stateLock) {
                isScanning = false
            }
            return
        }

        synchronized(stateLock) {
            isScanning = true
            scanStopJob?.cancel()
            scanStopJob = scope.launch {
                delay(scanWindowMs)
                stopScan()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val scannerToStop = synchronized(stateLock) {
            if (!isScanning) return
            isScanning = false
            scanStopJob?.cancel()
            scanStopJob = null
            scanner
        }
        scannerToStop?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        stopScan()
        _connectionFailed.value = false
        synchronized(stateLock) {
            shouldReconnect = true
            lastDeviceAddress = device.address
            reconnectAttempts = 0
            reconnectJob?.cancel()
            reconnectJob = null
            bluetoothGatt?.close()
            bluetoothGatt = null
        }
        val nextGatt = runCatching {
            device.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        }.getOrNull()
        synchronized(stateLock) {
            bluetoothGatt = nextGatt
            if (nextGatt == null) {
                shouldReconnect = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToAddress(address: String): Boolean {
        val device = runCatching { bluetoothAdapter?.getRemoteDevice(address) }.getOrNull()
            ?: return false
        connectToDevice(device)
        return true
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val gattToClose = synchronized(stateLock) {
            shouldReconnect = false
            reconnectJob?.cancel()
            reconnectJob = null
            val existing = bluetoothGatt
            bluetoothGatt = null
            existing
        }
        stopScan()
        gattToClose?.disconnect()
        gattToClose?.close()
        _isConnected.value = false
        _heartRate.value = 0
    }

    @SuppressLint("MissingPermission")
    private fun attemptReconnect() {
        var exhausted = false
        val reconnectParams: Pair<String, Int>? = synchronized(stateLock) {
            if (!shouldReconnect) return
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                shouldReconnect = false
                exhausted = true
                return@synchronized null
            }
            reconnectAttempts++
            val addr = lastDeviceAddress ?: return
            Pair(addr, reconnectAttempts)
        }
        if (exhausted) {
            _connectionFailed.value = true
            return
        }
        val (address, attempt) = reconnectParams ?: return

        val backoffMs = RECONNECT_BASE_DELAY_MS shl (attempt - 1) // 1s, 2s, 4s, 8s, 16s

        synchronized(stateLock) {
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(backoffMs)
                val device = bluetoothAdapter?.getRemoteDevice(address) ?: return@launch
                synchronized(stateLock) {
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
                val nextGatt = runCatching {
                    device.connectGatt(
                        appContext,
                        false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                }.getOrNull()
                synchronized(stateLock) {
                    bluetoothGatt = nextGatt
                    if (nextGatt == null) shouldReconnect = false
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                synchronized(stateLock) {
                    lastDeviceAddress = gatt.device?.address ?: lastDeviceAddress
                    reconnectJob?.cancel()
                    reconnectJob = null
                    reconnectAttempts = 0
                }
                _connectionFailed.value = false
                _isConnected.value = true
                gatt.discoverServices()
                appContext.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE).edit()
                    .putString("last_device_address", gatt.device?.address)
                    .putString("last_device_name", gatt.device?.name ?: "HR Monitor")
                    .putLong("last_connected_ms", System.currentTimeMillis())
                    .apply()
                return
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _isConnected.value = false
                _heartRate.value = 0
                attemptReconnect()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val measurement = gatt.getService(HR_SERVICE_UUID)
                ?.getCharacteristic(HR_MEASUREMENT_UUID)
                ?: gatt.services
                    .firstNotNullOfOrNull { service -> service.getCharacteristic(HR_MEASUREMENT_UUID) }
                ?: return
            gatt.setCharacteristicNotification(measurement, true)
            val descriptor = measurement.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG) ?: return
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid != HR_MEASUREMENT_UUID) return
            val value = characteristic.value ?: return
            if (value.size < 2) return

            val flags = value[0].toInt() and 0xFF
            val hr = if ((flags and 0x01) != 0) {
                if (value.size < 3) return
                (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
            } else {
                value[1].toInt() and 0xFF
            }
            _heartRate.value = hr
        }
    }

    fun currentDeviceAddress(): String? {
        return synchronized(stateLock) { lastDeviceAddress }
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}

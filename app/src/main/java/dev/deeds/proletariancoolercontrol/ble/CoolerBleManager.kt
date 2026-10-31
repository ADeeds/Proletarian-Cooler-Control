/*
 * Copyright 2026 deeds
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.deeds.proletariancoolercontrol.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

@SuppressLint("MissingPermission")
class CoolerBleManager(private val context: Context) {
    companion object {
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    private var gatt: BluetoothGatt? = null
    
    private val _connectionState = MutableStateFlow(BluetoothProfile.STATE_DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    private val _receivedData = MutableSharedFlow<ByteArray>(replay = 1, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val receivedData = _receivedData.asSharedFlow()

    private val packetBuffer = Collections.synchronizedList(mutableListOf<Byte>())

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices = _scannedDevices.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startScan() {
        val scanner = adapter.bluetoothLeScanner ?: return
        _scannedDevices.value = emptyList()
        scanner.startScan(scanCallback)
        // Stop scan after 10 seconds
        scope.launch {
            delay(10000)
            stopScan()
        }
    }

    fun stopScan() {
        adapter.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device.name?.contains("SYZ-ROCKY", ignoreCase = true) == true) {
                if (!_scannedDevices.value.contains(device)) {
                    _scannedDevices.value += device
                }
            }
        }
    }

    fun connect(device: BluetoothDevice): Boolean {
        stopScan()
        Log.d("CoolerBle", "Connecting to ${device.address}")
        val client = device.connectGatt(context, false, gattCallback)
        if (client == null) {
            Log.e("CoolerBle", "connectGatt returned null for ${device.address}")
            _isReady.value = false
            return false
        }
        gatt = client
        return true
    }

    fun getConnectedDevice(): BluetoothDevice? = gatt?.device

    fun disconnect() {
        android.util.Log.d("CoolerBle", "Disconnecting")
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = BluetoothProfile.STATE_DISCONNECTED
        _isReady.value = false
    }

    fun sendCommand(data: ByteArray) {
        val gatt = gatt ?: return
        val service = gatt.getService(CoolerProtocol.SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(CoolerProtocol.WRITE_CHAR_UUID) ?: return
        
        android.util.Log.d("CoolerBle", "Sending command: ${data.joinToString("") { "%02X ".format(it) }}")
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = data
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            android.util.Log.d("CoolerBle", "Connection state changed: $status -> $newState")
            _connectionState.value = newState
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                scope.launch {
                    delay(500) // Small delay before MTU request
                    gatt.requestMtu(512)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _isReady.value = false
                packetBuffer.clear()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            android.util.Log.d("CoolerBle", "MTU changed to $mtu, status: $status")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                enableNotifications(gatt)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == CoolerProtocol.NOTIFY_CHAR_UUID) {
                val data = characteristic.value ?: return
                android.util.Log.d("CoolerBle", "Data received (legacy): ${data.size} bytes")
                packetBuffer.addAll(data.toList())
                processBuffer()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == CoolerProtocol.NOTIFY_CHAR_UUID) {
                android.util.Log.d("CoolerBle", "Data received (API 33+): ${value.size} bytes")
                packetBuffer.addAll(value.toList())
                processBuffer()
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
             if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == CoolerProtocol.NOTIFY_CHAR_UUID) {
                val data = characteristic.value ?: return
                packetBuffer.addAll(data.toList())
                processBuffer()
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == CoolerProtocol.NOTIFY_CHAR_UUID) {
                packetBuffer.addAll(value.toList())
                processBuffer()
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid != CCCD_UUID) return
            Log.d("CoolerBle", "CCCD write status: $status")
            _isReady.value = status == BluetoothGatt.GATT_SUCCESS
            if (status != BluetoothGatt.GATT_SUCCESS) {
                gatt.disconnect()
            }
        }
    }

    private fun processBuffer() {
        synchronized(packetBuffer) {
            while (packetBuffer.size >= 7) {
                // Find header 55 AA
                var headerIdx = -1
                for (i in 0 until packetBuffer.size - 1) {
                    if (packetBuffer[i] == CoolerProtocol.HEADER_H && packetBuffer[i + 1] == CoolerProtocol.HEADER_L) {
                        headerIdx = i
                        break
                    }
                }

                if (headerIdx == -1) {
                    packetBuffer.clear()
                    return
                }
                if (headerIdx > 0) {
                    repeat(headerIdx) { packetBuffer.removeAt(0) }
                }

                if (packetBuffer.size < 7) return

                val payloadLen = ((packetBuffer[4].toInt() and 0xFF) shl 8) or (packetBuffer[5].toInt() and 0xFF)
                val fullSize = 7 + payloadLen

                if (packetBuffer.size >= fullSize) {
                    val frame = packetBuffer.take(fullSize).toByteArray()
                    repeat(fullSize) { packetBuffer.removeAt(0) }

                    if (CoolerProtocol.verifyChecksum(frame)) {
                        scope.launch {
                            _receivedData.emit(frame)
                        }
                    }
                } else {
                    break
                }
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(CoolerProtocol.SERVICE_UUID) ?: run {
            Log.e("CoolerBle", "Service ${CoolerProtocol.SERVICE_UUID} missing")
            gatt.disconnect()
            return
        }
        val characteristic = service.getCharacteristic(CoolerProtocol.NOTIFY_CHAR_UUID) ?: run {
            Log.e("CoolerBle", "Notify characteristic missing")
            gatt.disconnect()
            return
        }
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            Log.e("CoolerBle", "Failed to enable notifications for ${characteristic.uuid}")
            gatt.disconnect()
            return
        }
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            Log.e("CoolerBle", "CCCD descriptor missing for ${characteristic.uuid}")
            gatt.disconnect()
            return
        }
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (!gatt.writeDescriptor(descriptor)) {
            Log.e("CoolerBle", "Failed to write CCCD for ${characteristic.uuid}")
            gatt.disconnect()
        }
    }
}

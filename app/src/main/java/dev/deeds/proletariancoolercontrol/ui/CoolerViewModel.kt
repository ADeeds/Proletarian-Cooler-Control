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

package dev.deeds.proletariancoolercontrol.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.deeds.proletariancoolercontrol.ble.CoolerBleManager
import dev.deeds.proletariancoolercontrol.ble.CoolerProtocol
import dev.deeds.proletariancoolercontrol.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CoolerViewModel(application: Application) : AndroidViewModel(application) {
    private val bleManager = CoolerBleManager(application)
    private val prefs = application.getSharedPreferences("cooler_prefs", Context.MODE_PRIVATE)
    private var lastConnectedAddress = prefs.getString("last_device_address", null)
    private var shouldAttemptAutoReconnect = lastConnectedAddress != null
    private var userManuallyDisconnected = false
    
    private val _status = MutableStateFlow(CoolerStatus())
    val status = _status.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting = _isConnecting.asStateFlow()

    private val _scannedDevices = bleManager.scannedDevices
    val scannedDevices = _scannedDevices

    init {
        viewModelScope.launch {
            bleManager.isReady.collect { ready ->
                if (ready) {
                    _isConnecting.value = false
                    // Save last connected device for auto-reconnect
                    bleManager.getConnectedDevice()?.let { device ->
                        prefs.edit().putString("last_device_address", device.address).apply()
                        lastConnectedAddress = device.address
                    }
                    // Query status once services are discovered and notifications enabled
                    bleManager.sendCommand(CoolerProtocol.QUERY_PACKET)
                }
            }
        }

        viewModelScope.launch {
            scannedDevices.collect { devices ->
                if (shouldAttemptAutoReconnect && !userManuallyDisconnected && !status.value.isConnected) {
                    val device = devices.find { it.address == lastConnectedAddress }
                    if (device != null) {
                        shouldAttemptAutoReconnect = false
                        Toast.makeText(getApplication(), "Auto-reconnecting to last used cooler", Toast.LENGTH_SHORT).show()
                        connect(device)
                    }
                }
            }
        }

        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                _status.value = _status.value.copy(
                    isConnected = state == BluetoothProfile.STATE_CONNECTED,
                    isInitialized = if (state == BluetoothProfile.STATE_DISCONNECTED) false else _status.value.isInitialized
                )
                if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    _isConnecting.value = false
                }
            }
        }

        viewModelScope.launch {
            bleManager.receivedData.collect { data ->
                if (data.size >= 7) {
                    val payload = data.copyOfRange(6, data.size - 1)
                    val dps = CoolerProtocol.parseDataPoints(payload)
                    updateStatusFromDps(dps)
                }
            }
        }
    }

    private fun updateStatusFromDps(dps: Map<Byte, Int>) {
        var current = _status.value.copy(isInitialized = true)
        dps.forEach { (id, value) ->
            when (id) {
                CoolerProtocol.DP_POWER -> current = current.copy(powerOn = value == 1)
                CoolerProtocol.DP_Z1_CUR_TEMP -> current = current.copy(zone1CurrentTemp = value)
                CoolerProtocol.DP_Z1_TAR_TEMP -> current = current.copy(zone1TargetTemp = value)
                CoolerProtocol.DP_Z2_CUR_TEMP -> current = current.copy(zone2CurrentTemp = value)
                CoolerProtocol.DP_Z2_TAR_TEMP -> current = current.copy(zone2TargetTemp = value)
                CoolerProtocol.DP_TEMP_UNIT -> current = current.copy(tempUnit = TemperatureUnit.fromInt(value))
                CoolerProtocol.DP_BATTERY_PROT -> current = current.copy(batteryProtection = BatteryProtection.fromInt(value))
                CoolerProtocol.DP_OPERATING_MODE -> current = current.copy(operatingMode = OperatingMode.fromInt(value))
                CoolerProtocol.DP_ZONE_ENABLE -> current = current.copy(zoneEnable = ZoneEnable.fromInt(value))
                CoolerProtocol.DP_KEY_SOUND -> current = current.copy(keySoundMuted = value == 1)
                CoolerProtocol.DP_SCREEN_TIMEOUT -> current = current.copy(screenTimeout = ScreenTimeout.fromInt(value))
            }
        }
        _status.value = current
    }

    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()
    fun connect(device: BluetoothDevice) {
        shouldAttemptAutoReconnect = false
        _isConnecting.value = true
        bleManager.connect(device)
    }
    fun disconnect() {
        userManuallyDisconnected = true
        _isConnecting.value = false
        bleManager.disconnect()
    }

    fun setPower(on: Boolean) = sendDp(CoolerProtocol.DP_POWER, CoolerProtocol.TYPE_BOOL, if (on) 1 else 0)
    fun setZone1TargetTemp(temp: Int) = sendDp(CoolerProtocol.DP_Z1_TAR_TEMP, CoolerProtocol.TYPE_INT4, temp)
    fun setZone2TargetTemp(temp: Int) = sendDp(CoolerProtocol.DP_Z2_TAR_TEMP, CoolerProtocol.TYPE_INT4, temp)
    fun setTempUnit(unit: TemperatureUnit) = sendDp(CoolerProtocol.DP_TEMP_UNIT, CoolerProtocol.TYPE_ENUM, unit.value)
    fun setBatteryProtection(prot: BatteryProtection) = sendDp(CoolerProtocol.DP_BATTERY_PROT, CoolerProtocol.TYPE_ENUM, prot.value)
    fun setOperatingMode(mode: OperatingMode) = sendDp(CoolerProtocol.DP_OPERATING_MODE, CoolerProtocol.TYPE_ENUM, mode.value)
    fun setZoneEnable(enable: ZoneEnable) {
        val commandValue = when (enable) {
            ZoneEnable.LEFT_ONLY -> 2
            ZoneEnable.BOTH -> 1
            ZoneEnable.RIGHT_ONLY -> 0
        }
        sendDp(CoolerProtocol.DP_ZONE_ENABLE, CoolerProtocol.TYPE_ENUM, commandValue)
    }
    fun setKeySoundMuted(muted: Boolean) = sendDp(CoolerProtocol.DP_KEY_SOUND, CoolerProtocol.TYPE_BOOL, if (muted) 1 else 0)
    fun setScreenTimeout(timeout: ScreenTimeout) = sendDp(CoolerProtocol.DP_SCREEN_TIMEOUT, CoolerProtocol.TYPE_ENUM, timeout.value)

    private fun sendDp(id: Byte, type: Byte, value: Int) {
        val dpPayload = CoolerProtocol.buildDpPayload(id, type, value)
        val packet = CoolerProtocol.buildPacket(CoolerProtocol.CMD_SET_DP, dpPayload)
        bleManager.sendCommand(packet)
    }
}

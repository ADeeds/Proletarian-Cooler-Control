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

package dev.deeds.proletariancoolercontrol.model

data class CoolerStatus(
    val isConnected: Boolean = false,
    val isInitialized: Boolean = false,
    val powerOn: Boolean = false,
    val zone1CurrentTemp: Int = 0,
    val zone1TargetTemp: Int = 0,
    val zone2CurrentTemp: Int = 0,
    val zone2TargetTemp: Int = 0,
    val tempUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val batteryProtection: BatteryProtection = BatteryProtection.MEDIUM,
    val operatingMode: OperatingMode = OperatingMode.ECO,
    val zoneEnable: ZoneEnable = ZoneEnable.BOTH,
    val keySoundMuted: Boolean = false,
    val screenTimeout: ScreenTimeout = ScreenTimeout.ALWAYS_ON
)

enum class TemperatureUnit(val value: Int) {
    CELSIUS(0), FAHRENHEIT(1);
    companion object {
        fun fromInt(v: Int) = values().find { it.value == v } ?: CELSIUS
    }
}

enum class BatteryProtection(val value: Int, val displayName: String) {
    LOW(0, "Low"), MEDIUM(1, "Medium"), HIGH(2, "High");
    companion object {
        fun fromInt(v: Int) = entries.find { it.value == v } ?: MEDIUM
    }
}

enum class OperatingMode(val value: Int, val displayName: String) {
    ECO(0, "ECO"), MAX(1, "MAX");
    companion object {
        fun fromInt(v: Int) = entries.find { it.value == v } ?: ECO
    }
}

enum class ZoneEnable(val statusValue: Int, val displayName: String) {
    LEFT_ONLY(1, "Left Only"), RIGHT_ONLY(2, "Right Only"), BOTH(3, "Both");
    companion object {
        fun fromInt(v: Int): ZoneEnable {
            val found = entries.find { it.statusValue == v }
            if (found == null) {
                android.util.Log.w("CoolerModels", "Unknown ZoneEnable status value received: $v")
            }
            return found ?: BOTH
        }
    }
}

enum class ScreenTimeout(val value: Int, val displayName: String) {
    ALWAYS_ON(0, "Always On"), ONE_MINUTE(1, "1 Min"), THREE_MINUTES(2, "3 Mins");
    companion object {
        fun fromInt(v: Int) = entries.find { it.value == v } ?: ALWAYS_ON
    }
}

data class BleDevice(val name: String, val address: String)

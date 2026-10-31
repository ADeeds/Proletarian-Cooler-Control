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

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.deeds.proletariancoolercontrol.model.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoolerApp(viewModel: CoolerViewModel) {
    val status by viewModel.status.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val devices by viewModel.scannedDevices.collectAsState()

    var activeDialZone by remember { mutableIntStateOf(0) } // 0 = none, 1 = zone1, 2 = zone2

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (!status.isConnected) {
                ScannerScreen(
                    devices = devices,
                    isConnecting = isConnecting,
                    onScan = { viewModel.startScan() },
                    onConnect = { viewModel.connect(it) }
                )
            } else {
                DashboardScreen(
                    status = status,
                    onDisconnect = { viewModel.disconnect() },
                    onPowerToggle = { viewModel.setPower(!status.powerOn) },
                    onTempChange = { zone, delta ->
                        if (zone == 1) {
                            viewModel.setZone1TargetTemp(status.zone1TargetTemp + delta)
                        } else {
                            viewModel.setZone2TargetTemp(status.zone2TargetTemp + delta)
                        }
                    },
                    onLongPressTemp = { zone -> activeDialZone = zone },
                    onUnitToggle = {
                        viewModel.setTempUnit(if (status.tempUnit == TemperatureUnit.CELSIUS) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS)
                    },
                    onModeToggle = {
                        viewModel.setOperatingMode(if (status.operatingMode == OperatingMode.ECO) OperatingMode.MAX else OperatingMode.ECO)
                    },
                    onBatteryProtectionSelect = { viewModel.setBatteryProtection(it) },
                    onKeySoundToggle = { viewModel.setKeySoundMuted(!status.keySoundMuted) },
                    onScreenTimeoutSelect = { viewModel.setScreenTimeout(it) },
                    onZoneEnableSelect = { viewModel.setZoneEnable(it) }
                )
            }
        }

        if (activeDialZone != 0) {
            val initialTemp = if (activeDialZone == 1) status.zone1TargetTemp else status.zone2TargetTemp
            TempDialSheet(
                initialTemp = initialTemp,
                unit = if (status.tempUnit == TemperatureUnit.CELSIUS) "°C" else "°F",
                onDismiss = { activeDialZone = 0 },
                onTempConfirmed = { newTemp ->
                    if (activeDialZone == 1) viewModel.setZone1TargetTemp(newTemp)
                    else viewModel.setZone2TargetTemp(newTemp)
                }
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ScannerScreen(
    devices: List<BluetoothDevice>,
    isConnecting: Boolean,
    onScan: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit
) {
    LaunchedEffect(Unit) {
        onScan()
    }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Cooler Scanner", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (isConnecting) {
            CircularProgressIndicator()
            Text("Connecting...", modifier = Modifier.padding(top = 8.dp))
        } else {
            Button(onClick = onScan) {
                Text("Rescan")
            }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(devices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    onClick = { onConnect(device) }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(device.name ?: "Unknown Device", fontWeight = FontWeight.Bold)
                        Text(device.address, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    status: CoolerStatus,
    onDisconnect: () -> Unit,
    onPowerToggle: () -> Unit,
    onTempChange: (Int, Int) -> Unit,
    onLongPressTemp: (Int) -> Unit,
    onUnitToggle: () -> Unit,
    onModeToggle: () -> Unit,
    onBatteryProtectionSelect: (BatteryProtection) -> Unit,
    onKeySoundToggle: () -> Unit,
    onScreenTimeoutSelect: (ScreenTimeout) -> Unit,
    onZoneEnableSelect: (ZoneEnable) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cooler Status", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onDisconnect) { Text("Disconnect") }
        }

        Spacer(Modifier.height(16.dp))

        // Power Control
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Power", fontSize = 18.sp)
                Switch(checked = status.powerOn, onCheckedChange = { onPowerToggle() })
            }
        }

        Spacer(Modifier.height(16.dp))

        // Zones
        Row(Modifier.fillMaxWidth()) {
            val leftEnabled = status.zoneEnable == ZoneEnable.LEFT_ONLY || status.zoneEnable == ZoneEnable.BOTH
            val rightEnabled = status.zoneEnable == ZoneEnable.RIGHT_ONLY || status.zoneEnable == ZoneEnable.BOTH

            ZoneCard(
                title = "Zone 1 (Left)",
                current = status.zone1CurrentTemp,
                target = status.zone1TargetTemp,
                unit = if (status.tempUnit == TemperatureUnit.CELSIUS) "°C" else "°F",
                isEnabled = leftEnabled,
                isInitialized = status.isInitialized,
                onDelta = { onTempChange(1, it) },
                onLongClick = { onLongPressTemp(1) },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            ZoneCard(
                title = "Zone 2 (Right)",
                current = status.zone2CurrentTemp,
                target = status.zone2TargetTemp,
                unit = if (status.tempUnit == TemperatureUnit.CELSIUS) "°C" else "°F",
                isEnabled = rightEnabled,
                isInitialized = status.isInitialized,
                onDelta = { onTempChange(2, it) },
                onLongClick = { onLongPressTemp(2) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Settings
        Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        SettingRow("Temp Unit", status.tempUnit.name, status.isInitialized, onUnitToggle)
        SettingRow("Mode", status.operatingMode.displayName, status.isInitialized, onModeToggle)
        DropdownSettingRow(
            label = "Battery Prot",
            currentValue = status.batteryProtection.displayName,
            options = BatteryProtection.entries.map { it.displayName to it },
            isInitialized = status.isInitialized,
            onSelect = onBatteryProtectionSelect
        )
        SettingRow("Key Sound", if (status.keySoundMuted) "MUTED" else "ENABLED", status.isInitialized, onKeySoundToggle)
        DropdownSettingRow(
            label = "Screen Timeout",
            currentValue = status.screenTimeout.displayName,
            options = ScreenTimeout.entries.map { it.displayName to it },
            isInitialized = status.isInitialized,
            onSelect = onScreenTimeoutSelect
        )
        DropdownSettingRow(
            label = "Zones Enabled",
            currentValue = status.zoneEnable.displayName,
            options = ZoneEnable.entries.map { it.displayName to it },
            isInitialized = status.isInitialized,
            onSelect = onZoneEnableSelect
        )
    }
}

@Composable
fun <T> DropdownSettingRow(
    label: String,
    currentValue: String,
    options: List<Pair<String, T>>,
    isInitialized: Boolean,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingRow(label = label, value = currentValue, isInitialized = isInitialized, onClick = { if (isInitialized) expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (name, value) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoneCard(
    title: String,
    current: Int,
    target: Int,
    unit: String,
    isEnabled: Boolean,
    isInitialized: Boolean,
    onDelta: (Int) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .alpha(if (isEnabled) 1f else 0.7f)
            .combinedClickable(
                enabled = isEnabled && isInitialized,
                onClick = { /* fine to leave empty, buttons handle it */ },
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (isInitialized) {
                Text("$current$unit", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Text("Target: $target$unit", fontSize = 14.sp)
            } else {
                Spacer(Modifier.height(8.dp))
                SkeletonBox(width = 60.dp, height = 32.dp)
                Spacer(Modifier.height(8.dp))
                SkeletonBox(width = 80.dp, height = 14.dp)
            }
            Row {
                IconButton(onClick = { onDelta(-1) }, enabled = isEnabled && isInitialized) { Text("-", fontSize = 24.sp) }
                IconButton(onClick = { onDelta(1) }, enabled = isEnabled && isInitialized) { Text("+", fontSize = 24.sp) }
            }
        }
    }
}

@Composable
fun SettingRow(label: String, value: String, isInitialized: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = { if (isInitialized) onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontWeight = FontWeight.Medium)
            if (isInitialized) {
                Text(value, color = MaterialTheme.colorScheme.primary)
            } else {
                SkeletonBox(width = 80.dp, height = 18.dp)
            }
        }
    }
}

@Composable
fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    return this.alpha(alpha)
}

@Composable
fun SkeletonBox(width: Dp, height: Dp) {
    Box(
        modifier = Modifier
            .size(width, height)
            .shimmerEffect()
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempDialSheet(
    initialTemp: Int,
    unit: String,
    onDismiss: () -> Unit,
    onTempConfirmed: (Int) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(initialTemp.toFloat()) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Adjust Temperature", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Text("${sliderValue.roundToInt()}$unit", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(16.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onTempConfirmed(sliderValue.roundToInt()) },
                valueRange = if (unit == "°C") -20f..20f else -4f..68f,
                steps = if (unit == "°C") 40 else 72
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}

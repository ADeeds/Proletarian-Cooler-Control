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

import java.util.UUID

object CoolerProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
    val WRITE_CHAR_UUID: UUID = UUID.fromString("00001235-0000-1000-8000-00805f9b34fb")
    val NOTIFY_CHAR_UUID: UUID = UUID.fromString("00001236-0000-1000-8000-00805f9b34fb")

    const val HEADER_H = 0x55.toByte()
    const val HEADER_L = 0xAA.toByte()
    const val VERSION = 0x01.toByte()

    const val CMD_QUERY = 0x20.toByte()
    const val CMD_SET_DP = 0x21.toByte()

    // DP IDs
    const val DP_POWER = 0x01.toByte()
    const val DP_Z1_CUR_TEMP = 0x02.toByte()
    const val DP_Z1_TAR_TEMP = 0x03.toByte()
    const val DP_Z2_CUR_TEMP = 0x04.toByte()
    const val DP_Z2_TAR_TEMP = 0x05.toByte()
    const val DP_TEMP_UNIT = 0x06.toByte()
    const val DP_BATTERY_PROT = 0x07.toByte()
    const val DP_OPERATING_MODE = 0x0B.toByte()
    const val DP_ZONE_ENABLE = 0x12.toByte()
    const val DP_KEY_SOUND = 0x14.toByte()
    const val DP_SCREEN_TIMEOUT = 0x15.toByte()

    // DP Types
    const val TYPE_BOOL = 0x01.toByte()
    const val TYPE_INT4 = 0x02.toByte()
    const val TYPE_ENUM = 0x04.toByte()

    fun buildPacket(command: Byte, dpPayload: ByteArray): ByteArray {
        val payloadLen = dpPayload.size
        val packet = ByteArray(7 + payloadLen)
        packet[0] = HEADER_H
        packet[1] = HEADER_L
        packet[2] = VERSION
        packet[3] = command
        packet[4] = (payloadLen shr 8).toByte()
        packet[5] = (payloadLen and 0xFF).toByte()
        System.arraycopy(dpPayload, 0, packet, 6, payloadLen)
        packet[6 + payloadLen] = calculateChecksum(packet, 0, 6 + payloadLen)
        return packet
    }

    fun buildDpPayload(id: Byte, type: Byte, value: Int): ByteArray {
        return when (type) {
            TYPE_BOOL, TYPE_ENUM -> {
                byteArrayOf(id, type, 0x00, 0x01, value.toByte())
            }
            TYPE_INT4 -> {
                byteArrayOf(
                    id, type, 0x00, 0x04,
                    (value shr 24).toByte(),
                    (value shr 16).toByte(),
                    (value shr 8).toByte(),
                    value.toByte()
                )
            }
            else -> byteArrayOf()
        }
    }

    fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Byte {
        var sum = 0
        for (i in offset until offset + length) {
            sum += (data[i].toInt() and 0xFF)
        }
        return (sum and 0xFF).toByte()
    }

    fun verifyChecksum(data: ByteArray): Boolean {
        if (data.size < 7) return false
        val expected = data.last()
        val actual = calculateChecksum(data, 0, data.size - 1)
        return expected == actual
    }

    fun parseDataPoints(payload: ByteArray): Map<Byte, Int> {
        val results = mutableMapOf<Byte, Int>()
        var i = 0
        try {
            while (i + 4 <= payload.size) {
                val id = payload[i]
                val type = payload[i + 1]
                val len = ((payload[i + 2].toInt() and 0xFF) shl 8) or (payload[i + 3].toInt() and 0xFF)
                
                if (i + 4 + len > payload.size) {
                    android.util.Log.e("CoolerProtocol", "Invalid DP length: $len at index $i, total size: ${payload.size}")
                    break
                }
                
                val value: Int = when (type) {
                    TYPE_BOOL -> if (len >= 1) payload[i + 4].toInt() and 0xFF else 0
                    TYPE_ENUM -> if (len >= 1) payload[i + 4].toInt() and 0xFF else 0
                    TYPE_INT4 -> {
                        var v = 0
                        for (j in 0 until len.coerceAtMost(4)) {
                            v = (v shl 8) or (payload[i + 4 + j].toInt() and 0xFF)
                        }
                        // Handle signed 32-bit integer
                        if (len == 4 && (v and (1 shl 31)) != 0) {
                            (v or (0xFFFFFFFF.toLong() shl 32).toInt())
                        } else v
                    }
                    else -> 0
                }
                val rawValueHex = payload.copyOfRange(i + 4, i + 4 + len).joinToString("") { "%02X ".format(it) }
                android.util.Log.d("CoolerProtocol", "Parsed DP: ID=0x${"%02X".format(id)}, Type=0x${"%02X".format(type)}, Value=$value, Raw=[$rawValueHex]")
                results[id] = value
                i += 4 + len
            }
        } catch (e: Exception) {
            android.util.Log.e("CoolerProtocol", "Error parsing data points", e)
        }
        return results
    }

    val QUERY_PACKET = byteArrayOf(0x55.toByte(), 0xAA.toByte(), 0x01.toByte(), 0x20.toByte(), 0x00.toByte(), 0x00.toByte(), 0x20.toByte())
}

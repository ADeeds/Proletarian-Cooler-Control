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

package dev.deeds.proletariancoolercontrol

import dev.deeds.proletariancoolercontrol.ble.CoolerProtocol
import dev.deeds.proletariancoolercontrol.model.ZoneEnable
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoneEnableTest {

    @Test
    fun fromInt_mapsOfficialDp18Values() {
        assertEquals(ZoneEnable.BOTH_OFF, ZoneEnable.fromInt(0))
        assertEquals(ZoneEnable.LEFT_ONLY, ZoneEnable.fromInt(1))
        assertEquals(ZoneEnable.RIGHT_ONLY, ZoneEnable.fromInt(2))
        assertEquals(ZoneEnable.BOTH, ZoneEnable.fromInt(3))
        assertNull(ZoneEnable.fromInt(4))
    }

    @Test
    fun buildPacket_bothOn_usesValue3() {
        val payload = CoolerProtocol.buildDpPayload(
            CoolerProtocol.DP_ZONE_ENABLE,
            CoolerProtocol.TYPE_ENUM,
            ZoneEnable.BOTH.statusValue
        )
        val packet = CoolerProtocol.buildPacket(CoolerProtocol.CMD_SET_DP, payload)

        assertArrayEquals(
            byteArrayOf(
                0x55, 0xAA.toByte(), 0x01, 0x21, 0x00, 0x05,
                0x12, 0x04, 0x00, 0x01, 0x03, 0x40
            ),
            packet
        )
    }
}

# BougeRV Dual-Zone Cooler BLE Protocol

Reverse-engineered from HCI captures of the official Android application
(`com.caption.bougerv` 2.6.2) and cross-checked against the decompiled app.
Applies to the **SYZ-ROCKY3** series; tested on `SYZ-ROCKY3-50`.

This document is not affiliated with BougeRV. “BougeRV” and “Rocky” are
trademarks of their respective owners.

## Confidence

- **Verified:** GATT UUIDs, `0x55AA` framing, checksum, DP IDs, and the
  value mappings marked below.
- **Unverified:** the role of characteristic `0x1237`; see the note in the
  GATT section.

The protocol follows the Tuya / SYZ BLE MCU serial framing format over BLE.

---

## 1. Device discovery

- **Advertising name:** `SYZ-ROCKY3-50` (or `SYZ-ROCKY*`)
- **Transport:** Bluetooth Low Energy (BLE)

---

## 2. GATT profile

All communications occur under one custom GATT service.

**Service UUID:** `00001234-0000-1000-8000-00805f9b34fb`

| Characteristic UUID | Handle | Properties | Description |
|---|---|---|---|
| `00001235-0000-1000-8000-00805f9b34fb` | `0x000C` | Write Without Response | Command channel (app → cooler) |
| `00001236-0000-1000-8000-00805f9b34fb` | `0x0009` | Notify, Read | Status telemetry (cooler → app) |

> **Note on `0x1237`:** some GATT dumps show a `0x1237` characteristic.
> The official Rocky implementation (`OtaIceDeviceUtil`) does not use it;
> it writes `0x1235` and subscribes to `0x1236`. In the official app
> `0x1237` appears only as a generic fallback for other device families,
> so its role is unverified and it is not part of this protocol.

---

## 3. Frame format

Every command sent to `0x1235` and every notification received from `0x1236`
uses this layout:

```text
+-------------------+---------------+-------------------+--------------------+------------------------+-------------------+
| Header (2 bytes)  | Version (1B)  | Command (1B)      | Payload length (2B)| Data-point payload (N) | Checksum (1 byte) |
|   0x55   0xAA     |     0x01      |  0x20 / 0x21      |  Big-endian UInt16 |  DP header + value     | Sum modulo 256    |
+-------------------+---------------+-------------------+--------------------+------------------------+-------------------+
```

- `0x20` = query current status.
- `0x21` = set data point.
- Checksum = sum of every preceding byte, modulo 256.
- Payload length counts only the DP payload (not the header or checksum).

### Data-point layout

```text
+---------------+-----------------+--------------------+-------------------------+
|  DP ID (1B)   |  DP type (1B)   | Value length (2B)  |   Value data (N bytes)  |
| 1 byte        | 0x01/0x02/0x04  | Big-endian UInt16  | Big-endian byte array   |
+---------------+-----------------+--------------------+-------------------------+
```

Types:

- `0x01` — boolean/flag, 1-byte value (`0x00` or `0x01`).
- `0x02` — 4-byte big-endian integer.
- `0x04` — enum/option, 1-byte value.

---

## 4. Command construction example

Set **Zone 2 target temperature to 40°F**:

1. DP: ID `0x05`, type `0x02` (4-byte int), value `40` = `00 00 00 28`.
2. DP payload: `05 02 00 04 00 00 00 28` (length `0x0008`).
3. Frame:
   - Header `55 AA`, version `01`, command `21`
   - Length `00 08`
   - Payload `05 02 00 04 00 00 00 28`
   - Checksum `(0x55+0xAA+0x01+0x21+0x00+0x08+0x05+0x02+0x00+0x04+0x00+0x00+0x00+0x28) % 256 = 0x5C`
4. Bytes on the wire:

```text
55 AA 01 21 00 08 05 02 00 04 00 00 00 28 5C
```

---

## 5. Data-point registry

| DP ID | Type | Function | Values |
|---|---|---|---|
| `0x01` | `0x01` Bool | Power state | `0` = off, `1` = on |
| `0x02` | `0x02` Int4 | Zone 1 current temp | Read-only sensor temperature |
| `0x03` | `0x02` Int4 | Zone 1 target temp | Target for the left/main zone |
| `0x04` | `0x02` Int4 | Zone 2 current temp | Read-only sensor temperature |
| `0x05` | `0x02` Int4 | Zone 2 target temp | Target for the right/second zone |
| `0x06` | `0x04` Enum | Temperature unit | `0` = Celsius, `1` = Fahrenheit |
| `0x07` | `0x04` Enum | Battery protection | `0` = Low, `1` = Medium, `2` = High |
| `0x0B` | `0x04` Enum | Operating mode | `0` = ECO, `1` = MAX |
| `0x12` | `0x04` Enum | Zone enable | `0` = both off, `1` = left only, `2` = right only, `3` = both on |
| `0x14` | `0x01` Bool | Key sound / beep | `0` = sound on, `1` = muted |
| `0x15` | `0x04` Enum | Screen timeout | `0` = always on, `1` = 1 minute, `2` = 3 minutes |

Notes:

- **`0x12` is a bitmask:** bit 0 = left zone, bit 1 = right zone. Commands
  and status use the same encoding; there is no separate command mapping.
- **`0x15` status quirk:** the device reports DP `0x15` as type `0x01`
  (Bool) in status packets, but its values are `0`, `1`, and `2`, and
  commands should be sent with type `0x04` (Enum). Treat both types as
  carrying the same numeric value.
- **`0x14` polarity:** the official app treats `0` as the active/on state
  and `1` as muted/off.

---

## 6. Command reference

### Query status

```text
55 AA 01 20 00 00 20
```

### Set Zone 1 (left) target temperature

- 40°F: `55 AA 01 21 00 08 03 02 00 04 00 00 00 28 5A`
- 45°F: `55 AA 01 21 00 08 03 02 00 04 00 00 00 2D 5F`

### Set Zone 2 (right) target temperature

- 40°F: `55 AA 01 21 00 08 05 02 00 04 00 00 00 28 5C`
- 45°F: `55 AA 01 21 00 08 05 02 00 04 00 00 00 2D 61`

### Temperature unit

- Celsius: `55 AA 01 21 00 05 06 04 00 01 00 31`
- Fahrenheit: `55 AA 01 21 00 05 06 04 00 01 01 32`

### Zone enable

- Both off: `55 AA 01 21 00 05 12 04 00 01 00 3D`
- Left only: `55 AA 01 21 00 05 12 04 00 01 01 3E`
- Right only: `55 AA 01 21 00 05 12 04 00 01 02 3F`
- Both on: `55 AA 01 21 00 05 12 04 00 01 03 40`

### Screen timeout

- Always on: `55 AA 01 21 00 05 15 04 00 01 00 40`
- 1 minute: `55 AA 01 21 00 05 15 04 00 01 01 41`
- 3 minutes: `55 AA 01 21 00 05 15 04 00 01 02 42`

---

## 7. Python example

Illustrative example using `bleak`. Replace the MAC with your cooler’s
address. No scanner is needed if the address is known.

```python
import asyncio
from bleak import BleakClient

COOLER_MAC = "AA:BB:CC:DD:EE:FF"
WRITE_CHAR_UUID = "00001235-0000-1000-8000-00805f9b34fb"
NOTIFY_CHAR_UUID = "00001236-0000-1000-8000-00805f9b34fb"


def checksum(data: bytearray) -> int:
    return sum(data) & 0xFF


def set_temp_frame(zone: int, temp: int) -> bytes:
    dp_id = 0x03 if zone == 1 else 0x05
    frame = bytearray([
        0x55, 0xAA,       # header
        0x01,             # version
        0x21,             # set DP
        0x00, 0x08,       # payload length
        dp_id,
        0x02,             # 4-byte int
        0x00, 0x04,       # value length
        0x00, 0x00, 0x00, temp & 0xFF,
    ])
    frame.append(checksum(frame))
    return bytes(frame)


def notification_handler(sender, data: bytearray) -> None:
    if not data.startswith(b"\x55\xaa") or len(data) < 7:
        return

    payload = data[6:-1]
    index = 0
    while index + 4 <= len(payload):
        dp_id = payload[index]
        dp_type = payload[index + 1]
        dp_len = int.from_bytes(payload[index + 2:index + 4], "big")
        index += 4 + dp_len
        value_bytes = payload[index - dp_len:index]
        value = int.from_bytes(value_bytes, "big")
        print(f"DP 0x{dp_id:02X} type 0x{dp_type:02X}: {value}")


async def main() -> None:
    async with BleakClient(COOLER_MAC) as client:
        await client.start_notify(NOTIFY_CHAR_UUID, notification_handler)
        await client.write_gatt_char(
            WRITE_CHAR_UUID,
            bytes([0x55, 0xAA, 0x01, 0x20, 0x00, 0x00, 0x20]),
            response=False,
        )
        await asyncio.sleep(2)
        await client.write_gatt_char(
            WRITE_CHAR_UUID,
            set_temp_frame(zone=1, temp=40),
            response=False,
        )
        await asyncio.sleep(1)


if __name__ == "__main__":
    asyncio.run(main())
```

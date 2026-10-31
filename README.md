# PCC: ProletarianCoolerControl


PCC is a third party Android app that can be used for controlling various coolers with Bluetooth capability, such as the BougeRV Rocky v3.0. The app is not affiliated with any specific cooler company.

> [!NOTE]
> AI disclosure: This project is entirely vibecoded. No human involved. Only trust this code as much as you trust your least favorite AI inference provider.

## Why?

I bought a fancy expensive cooler. It had an app, which seemed kinda superfluous but I downloaded it anyways. The app took 200MB of storage and required my email address before it let me connect via Bluetooth to my cooler. I thought that was lame so I made my own. It's not as pretty, but it takes 99% less space and doesn't require email authentication so that you can locally control hardware that you own.

## Protocol

The reverse-engineered BLE protocol is documented in [docs/protocol.md](docs/protocol.md). Instead of using standard Bluetooth features like Gatt, it's a binary protocol similar to the [Tuya serial protocol](https://developer.tuya.com/en/docs/iot/tuya-cloud-universal-serial-port-access-protocol)

## Web controller

A browser-based alternative lives at [`web/index.html`](web/index.html). It uses
Web Bluetooth, so it needs a Chromium-based browser and a secure context
(HTTPS or localhost).

It is a **less bulletproof alternative** to the Android app: it has fewer
controls and guards, depends on browser BLE support, and is not built or
versioned as a release artifact.

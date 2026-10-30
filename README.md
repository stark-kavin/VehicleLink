# VehicleLink

A lean Android companion app for a connected two-wheeler. It keeps only what the
cluster actually needs and drops the server/analytics stack of the original app
(KOGO, Hero APIM, Mappls IoT, Netcore/Smartech, Hansel, Adobe, Dynatrace).

## What it does
- **Phone ↔ vehicle over BLE** (Nordic-UART). Phone is GATT central; the vehicle
  (e.g. ESP32) is the peripheral. Foreground service keeps the link alive.
- **Music controls** both ways: reads the active media session (Spotify, YT Music,
  …) for title/artist/state, and applies **NEXT / PREV / PLAY-PAUSE** sent from the
  handlebar buttons.
- **Turn-by-turn from the phone's Google Maps app.** Start directions in Google
  Maps as normal; VehicleLink catches the live guidance and streams the next
  maneuver + distance + road to the vehicle display.
- **On-device Google Maps** (Maps SDK for Android) in-app, replacing Mappls.

## How the Google Maps turn-by-turn capture works
Google Maps has no public "give me current guidance" API. While it navigates it
posts an *ongoing notification* carrying the next maneuver, distance and road.
`CompanionListenerService` (a `NotificationListenerService`) reads that
notification and `MapsNavParser` extracts the fields. The same notification-access
grant is also what authorizes `MediaSessionManager`, so one permission powers both
navigation capture and music control.

**Trade-offs / honesty:**
- The notification layout varies by locale and Maps version, so parsing is
  heuristic. The raw text is always forwarded too, so the cluster can fall back to
  showing it verbatim. Tune `MapsNavParser` against your locale.
- Maneuver *icons* aren't reliably exposed; we send text. If you need turn arrows,
  map the maneuver string to icons on the vehicle side, or move to the paid
  **Google Navigation SDK** for a fully in-app routed experience.
- This reads Google Maps' own output; it doesn't re-route or re-brand Google's
  data. It's the same mechanism aftermarket moto displays use.

## Setup
1. Open in Android Studio (Koala+), JDK 17.
2. Create an **API key** for *Maps SDK for Android* in Google Cloud Console.
3. `cp local.properties.sample local.properties` and set `MAPS_API_KEY=...`
   (restrict the key to your package + signing SHA-1).
4. Run on a device (BLE + media sessions don't work on the emulator).
5. In the app: **Enable notification access**, grant Bluetooth + Location, tap
   **Connect vehicle**.
6. Flash `firmware/esp32_vehiclelink.ino` to the vehicle controller (advertise a
   name starting `VEHICLE`).

## BLE protocol (newline-delimited UTF-8)
Phone → vehicle:
```
N|<maneuver>|<distance>|<road>     e.g.  N|Turn right|450 m|MG Road
NX                                 navigation ended
M|<1|0>|<title>|<artist>|<pos>|<dur>   1=playing, pos/dur in seconds
P|<ms>                             heartbeat
```
Vehicle → phone:
```
C|NEXT   C|PREV   C|PLAYPAUSE   C|PLAY   C|PAUSE
```

## Module map
| File | Role |
|---|---|
| `core/VehicleBus.kt` | in-memory hub wiring listener ↔ media ↔ BLE |
| `core/Protocol.kt` | line-protocol encode |
| `ble/BleVehicleService.kt` | foreground GATT central, scan/connect/write, inbound buttons |
| `listener/CompanionListenerService.kt` | notification listener (nav + media auth) |
| `nav/MapsNavParser.kt` | parse Google Maps guidance notification |
| `media/MediaHub.kt` | active media session read + transport control |
| `ui/VehicleLinkScreen.kt` | map + status + now-playing + nav card |
| `firmware/esp32_vehiclelink.ino` | reference peripheral |

## Not included (by design)
No app backend, accounts, telemetry upload, geofencing-as-a-service, or push
SDKs. Everything stays on the phone except BLE frames to your vehicle.

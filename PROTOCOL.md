# Hero Karizma XMR cluster BLE protocol (reverse-engineered)

Source: decompiled `com.customerapp.hero` — `ride_guide/BluetoothConnectionService`,
`views/fragments/rideGuide/SendDataToCluster`, `quickAction/NavigateFragment`,
`thorwrapper/rideguidewrapper/NavigationPacket`.

The module is a **Telit TIO (Terminal I/O) UART-over-BLE** bridge with credit-based
flow control. Device advertises name prefix `MCU` (e.g. `MCU01************`).

## GATT
Service `e837d9a2-9c49-4493-9547-8e6918a59ca8`:
| Purpose | UUID | Props |
|---|---|---|
| Data RX  (phone → cluster) | `64aecb40-849a-44f1-934f-addc4b316423` | write-no-response |
| Credits RX (phone → cluster) | `f535dd6e-7975-4abd-9719-491e38a81179` | write |
| Data TX  (cluster → phone) | `b792a4bb-db87-436a-9066-db63c5fb3f00` | notify |
| Credits TX (cluster → phone) | `36e8614b-2ded-45c1-9ad3-c9f59a01f21e` | indicate |

(Telit variant with base `…008025000000` also exists in the app: service FEFB,
RX 0001, TX 0002, RX-credits 0003, TX-credits 0004 — not used by this bike.)

## Handshake (TioState machine)
1. Connect → discoverServices → requestMtu.
2. Enable **INDICATE** on Credits-TX `36e8614b` (write CCCD = 0x0200).
3. On that descriptor-write ok → enable **NOTIFY** on Data-TX `b792a4bb` (CCCD = 0x0100).
4. On that ok → write **uint8 10** to Credits-RX `f535dd6e`  (grants the module 10 credits).
5. Link is live. Re-grant 10 credits periodically / when the module indicates.

## Payload framing
Every application packet is an ASCII (Windows-1252) string, wrapped `"\n" + body + "\n"`,
written to Data-RX `64aecb40` (write-no-response). First body char = packet type.

### Music  (type `9`)  — CONFIRMED, 48 bytes
```
\n 9 <song[20]> <artist/album[20]> 0 0 <play> <prog> <vol> \n
```
- song, artist: ASCII, truncated to 20 or right-padded with `~`. Default `Unknown~~~~~~~~~~~~~`.
- `00`: literal (uses `01` on session-destroy).
- play: `0` playing, `1` paused.
- prog: track progress as a single char. Steps 1..31 = char(1..31); unknown = `d`.
- vol (music-stream % → char): 0/1-5→d, 5-10→e, 10-15→f, 15-40→g, 40-50→h, 50-60→i,
  60-70→j, 70-80→k, 80-90→l, 90-95→m, 95-100→n.
- A separate 1 Hz updater rewrites byte offset 45 (prog) as the bar advances.

### Navigation  (type `1`) — CONFIRMED, 48-byte body (wrapped = 50)
16 fixed-width fields concatenated with NO separators (NavigationPacket.toPacketString):
```
id(1) binaryDir(1) distance(5) unit(1) etaHour(2) meridian(1) etaMinute(2) demeridian(1)
roundabout(2) noSignal(1) remainDistance(5) remainUnit(1) remainTime(4) nextBinaryDir(1)
secondRoundabout(2) roadName(18)
```
- id = `1`.
- binaryDir = maneuver letter via `setDirBinary`; roundabout = `B`; cancelled = `G`;
  finished = `H`. Lowercased when the turn is imminent (< 200 m).
- distance = 5 digits, left zero-padded (`00450`, `001.2`). **unit = `1` for metres, `k` for km.**
- eta = etaHour(2) + meridian(1, `A`/`P`) + etaMinute(2) + demeridian(1, **`M`**).
- roundabout = exit number `01`..`09` (from "Nth exit"), else `00`.
- noSignal = `1` when GPS lost, else `0`.
- remainDistance(5)/remainUnit(1)/remainTime(4)/nextBinaryDir(1) = trip remainders; `~`-filled when unknown.
- secondRoundabout(2); roadName(18), `~`-padded/truncated.
- Sent continuously (~1 Hz) while navigating; the cluster reads fixed byte offsets, so every
  width must be exact or fields after the mismatch are misread.

### Inbound (cluster → phone, on Data-TX notify)
Body byte[3] == `10` → accept ringing call; `15` → end/reject call.
(Handlebar buttons for calls; music NEXT/PREV/PLAYPAUSE arrive the same way — TODO map.)

## Status
- Handshake + Music: implemented in VehicleLink.
- Navigation + call-control: structure captured, field widths to validate on the cluster.

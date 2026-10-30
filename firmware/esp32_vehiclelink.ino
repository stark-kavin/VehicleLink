// VehicleLink — ESP32 peripheral reference sketch (Arduino / NimBLE-Arduino).
// Phone = GATT central; this device advertises the Nordic-UART service, receives
// nav + media frames, classifies the turn maneuver into an arrow, and sends
// handlebar button presses back.
//
// Install: Library Manager -> "NimBLE-Arduino" (h2zero).
// Frames (newline-delimited UTF-8, see VehicleLink Protocol.kt):
//   N|<maneuver>|<distance>|<road>     NX
//   M|<1|0>|<title>|<artist>|<posSec>|<durSec>
//   P|<ms>
// Send back:  C|NEXT  C|PREV  C|PLAYPAUSE

#include <NimBLEDevice.h>

#define SVC  "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define RX   "6e400002-b5a3-f393-e0a9-e50e24dcca9e"  // phone writes here
#define TX   "6e400003-b5a3-f393-e0a9-e50e24dcca9e"  // we notify here

// --- maneuver model -------------------------------------------------------
enum Maneuver {
  MAN_STRAIGHT, MAN_LEFT, MAN_RIGHT, MAN_SLIGHT_LEFT, MAN_SLIGHT_RIGHT,
  MAN_SHARP_LEFT, MAN_SHARP_RIGHT, MAN_UTURN, MAN_MERGE, MAN_ROUNDABOUT,
  MAN_RAMP, MAN_DESTINATION, MAN_NONE
};

struct NavState {
  Maneuver maneuver = MAN_NONE;
  String   arrow;        // unicode glyph for a simple display
  String   maneuverText; // raw, for a text row / fallback
  String   distance;     // "450 m"
  String   road;         // "MG Road"
  bool     active = false;
} nav;

struct MediaState {
  bool   playing = false;
  String title, artist;
  long   posSec = 0, durSec = 0;
} media;

static NimBLECharacteristic* txChar = nullptr;
static String rxBuf;

// Lowercase helper.
static String lc(const String& s) { String r = s; r.toLowerCase(); return r; }

// Map Google-Maps-style maneuver text -> maneuver + arrow glyph.
// Order matters: check the specific (slight/sharp/u-turn) before the generic.
static Maneuver classify(const String& textIn, String& arrowOut) {
  String t = lc(textIn);
  auto has = [&](const char* k) { return t.indexOf(k) >= 0; };

  if (has("u-turn") || has("u turn") || has("make a u")) { arrowOut = "↶"; return MAN_UTURN; }       // ↶
  if (has("roundabout") || has("rotary"))                { arrowOut = "↻"; return MAN_ROUNDABOUT; }  // ↻
  if (has("slight left"))   { arrowOut = "↖"; return MAN_SLIGHT_LEFT; }   // ↖
  if (has("slight right"))  { arrowOut = "↗"; return MAN_SLIGHT_RIGHT; }  // ↗
  if (has("sharp left"))    { arrowOut = "←"; return MAN_SHARP_LEFT; }    // ←
  if (has("sharp right"))   { arrowOut = "→"; return MAN_SHARP_RIGHT; }   // →
  if (has("keep left") || has("turn left") || has("left")) { arrowOut = "←"; return MAN_LEFT; }   // ←
  if (has("keep right")|| has("turn right")|| has("right")){ arrowOut = "→"; return MAN_RIGHT; }  // →
  if (has("merge"))         { arrowOut = "⤴"; return MAN_MERGE; }         // ⤴
  if (has("ramp") || has("exit") || has("take the")) { arrowOut = "↗"; return MAN_RAMP; }         // ↗
  if (has("destination") || has("arrive"))           { arrowOut = "⚑"; return MAN_DESTINATION; }  // ⚑
  if (has("head") || has("continue") || has("straight")) { arrowOut = "↑"; return MAN_STRAIGHT; } // ↑
  arrowOut = "↑"; return MAN_STRAIGHT;                                    // default ↑
}

// Split a UTF-8 line into up to maxp '|' fields.
static int split(const String& line, char sep, String* out, int maxp) {
  int n = 0, start = 0;
  for (int i = 0; i <= line.length() && n < maxp; i++) {
    if (i == line.length() || line[i] == sep) {
      out[n++] = line.substring(start, i);
      start = i + 1;
    }
  }
  return n;
}

// TODO: replace with your real cluster/OLED/CAN output.
static void renderNav() {
  Serial.printf("[NAV] %s  %s  %s  (%s)\n",
    nav.arrow.c_str(), nav.maneuverText.c_str(), nav.distance.c_str(), nav.road.c_str());
}
static void renderNavCleared() { Serial.println("[NAV] --- ended ---"); }
static void renderMedia() {
  Serial.printf("[MEDIA] %s  %s - %s  %ld/%lds\n",
    media.playing ? "▶" : "⎉", media.title.c_str(), media.artist.c_str(),
    media.posSec, media.durSec);
}

static void handleLine(const String& line) {
  if (line.startsWith("N|")) {
    String f[4]; int n = split(line, '|', f, 4);   // N | maneuver | distance | road
    nav.maneuverText = n > 1 ? f[1] : "";
    nav.distance     = n > 2 ? f[2] : "";
    nav.road         = n > 3 ? f[3] : "";
    String arrow; nav.maneuver = classify(nav.maneuverText, arrow);
    nav.arrow = arrow; nav.active = true;
    renderNav();
  } else if (line == "NX") {
    nav.active = false; nav.maneuver = MAN_NONE; nav.arrow = "";
    renderNavCleared();
  } else if (line.startsWith("M|")) {
    String f[6]; int n = split(line, '|', f, 6);
    media.playing = n > 1 && f[1] == "1";
    media.title   = n > 2 ? f[2] : "";
    media.artist  = n > 3 ? f[3] : "";
    media.posSec  = n > 4 ? f[4].toInt() : 0;
    media.durSec  = n > 5 ? f[5].toInt() : 0;
    renderMedia();
  } else if (line.startsWith("P|")) {
    // heartbeat: keep-alive / connection LED
  }
}

class RxCb : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* c) override {
    rxBuf += String(c->getValue().c_str());
    int nl;
    while ((nl = rxBuf.indexOf('\n')) >= 0) {
      handleLine(rxBuf.substring(0, nl));
      rxBuf = rxBuf.substring(nl + 1);
    }
  }
};

void sendCommand(const char* cmd) {           // call from your button handler
  if (txChar) { String s = String("C|") + cmd + "\n"; txChar->setValue(s.c_str()); txChar->notify(); }
}

// --- handlebar buttons (active-low, adjust pins) --------------------------
#define PIN_NEXT       25
#define PIN_PREV       26
#define PIN_PLAYPAUSE  27

static void pollButtons() {
  static uint32_t last = 0;
  if (millis() - last < 200) return;           // debounce
  if (digitalRead(PIN_NEXT) == LOW)      { sendCommand("NEXT");      last = millis(); }
  else if (digitalRead(PIN_PREV) == LOW) { sendCommand("PREV");      last = millis(); }
  else if (digitalRead(PIN_PLAYPAUSE)==LOW){ sendCommand("PLAYPAUSE"); last = millis(); }
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_NEXT, INPUT_PULLUP);
  pinMode(PIN_PREV, INPUT_PULLUP);
  pinMode(PIN_PLAYPAUSE, INPUT_PULLUP);

  NimBLEDevice::init("VEHICLE-01");            // name prefix must match DEVICE_NAME_PREFIX
  NimBLEServer* srv = NimBLEDevice::createServer();
  NimBLEService* svc = srv->createService(SVC);
  NimBLECharacteristic* rxc =
      svc->createCharacteristic(RX, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
  rxc->setCallbacks(new RxCb());
  txChar = svc->createCharacteristic(TX, NIMBLE_PROPERTY::NOTIFY);
  svc->start();
  NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
  adv->addServiceUUID(SVC);
  adv->start();
  Serial.println("VehicleLink peripheral advertising...");
}

void loop() {
  pollButtons();
  delay(10);
}

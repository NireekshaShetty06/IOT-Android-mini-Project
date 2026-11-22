# Smart Umbrella - IoT Rain Detection & Weather Monitoring System

An end-to-end IoT and Android weather monitoring system. The system combines an **ESP32 microcontroller** with a **DHT22 temperature & humidity sensor**, **FC-37 rain sensor**, **active buzzer**, **RGB status LED**, and an **I2C LCD display**, synchronizing live telemetry to **Firebase Realtime Database** with an Android client built in **Kotlin & Jetpack Compose**.

---

## 1. System Architecture

```
+----------------------------------------------------------------+
|                        ESP32 Firmware                          |
|  [Core 1] Sensor Sampling (40ms Rain, 2s DHT), Button & Buzzer |
|  [Core 0] FreeRTOS Background Task: Wi-Fi, NTP & Firebase REST |
+-------------------------------+--------------------------------+
                                | HTTPS (PATCH / PUT / GET)
                                v
+----------------------------------------------------------------+
|                   Firebase Realtime Database                   |
|  - /smartUmbrella/status      Live telemetry & actuator state  |
|  - /smartUmbrella/config      Remote thresholds & mute settings|
|  - /smartUmbrella/history     Persistent sensor timeseries     |
|  - /smartUmbrella/fcmTokens   Registered Android device tokens |
+-------------------------------+--------------------------------+
                                |
               +----------------+----------------+
               | Realtime Sync                   | DB Trigger
               v                                 v
+-------------------------------+ +------------------------------+
|     Android Application       | |   Firebase Cloud Functions   |
|  - Kotlin + Jetpack Compose   | |   - Listens on status change |
|  - StateFlow & Heartbeat Watch| |   - Broadcasts FCM on rain   |
|  - Outing Planner & History   | +------------------------------+
+-------------------------------+
```

---

## 2. Hardware Connections & Pinout

| Subsystem / Sensor | ESP32 GPIO | Electrical Specification / Details |
|---|---|---|
| **DHT22 (Temperature & Humidity)** | **GPIO 4** | 3.3V VCC, GND, 10k pull-up resistor on Data line |
| **FC-37 Rain Sensor (DO)** | **GPIO 27** | Active-LOW digital output from onboard LM393 comparator |
| **FC-37 Rain Sensor (AO)** | **GPIO 34** | Analog input (ADC1). Dry plate ~4095; Wet droplets < 3300 |
| **Active Buzzer** | **GPIO 25** | Active HIGH drive (300ms ON / 200ms OFF alert cadence) |
| **RGB LED (Red)** | **GPIO 18** | Common Cathode with 220Ω resistor (HIGH = ON) |
| **RGB LED (Green)** | **GPIO 14** | Common Cathode with 220Ω resistor (HIGH = ON) |
| **RGB LED (Blue)** | **GPIO 26** | Common Cathode with 220Ω resistor (HIGH = ON) |
| **Mute Push Button** | **GPIO 32** | Active-LOW with internal pull-up (`INPUT_PULLUP`) |
| **I2C LCD 16x2 (SDA)** | **GPIO 21** | 5V / 3.3V I2C Data (PCF8574 address `0x27`) |
| **I2C LCD 16x2 (SCL)** | **GPIO 22** | I2C Clock line |

> **Note**: Pin GPIO 34 is on ADC1 (safe for Wi-Fi use). Do NOT connect analog sensors to ADC2 pins while Wi-Fi is active.

---

## 3. Firebase Realtime Database Structure

### `/smartUmbrella/status` (Live Telemetry)
```json
{
  "deviceId": "umbrella_001",
  "temperature": 27.8,
  "humidity": 64.2,
  "dhtValid": true,
  "rainDetected": false,
  "rainAnalogValue": 3850,
  "rainDigitalValue": 1,
  "buzzerOn": false,
  "rgbStatus": "GREEN",
  "deviceOnline": true,
  "lastUpdated": 1700000000000
}
```

### `/smartUmbrella/config` (Remote Configuration)
```json
{
  "rainTriggerThreshold": 3300,
  "rainClearThreshold": 3600,
  "soundEnabled": true,
  "notificationsEnabled": true,
  "lastConfigUpdated": 1700000000000
}
```

### `/smartUmbrella/history/{timestamp}` (Timeseries Records)
```json
{
  "timestamp": 1700000000000,
  "temperature": 27.8,
  "humidity": 64.2,
  "rainAnalogValue": 3850
}
```

### Recommended Database Security Rules
```json
{
  "rules": {
    "smartUmbrella": {
      ".read": "auth != null",
      ".write": "auth != null"
    }
  }
}
```
*Android connects using anonymous authentication (`FirebaseAuth.getInstance().signInAnonymously()`), satisfying `auth != null` without hardcoding credentials.*

---

## 4. Software Setup & Build Instructions

### Android Application
1. **Requirements**: Android Studio Meerkat or newer, JDK 21 (configured in `gradle.properties`).
2. **Build debug APK**:
   ```powershell
   .\gradlew.bat assembleDebug
   ```
3. **Run Unit Tests**:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```

### ESP32 Firmware
1. **Canonical Sketch**: Located at `firmware/SmartUmbrella_Firebase_ESP32/SmartUmbrella_Firebase_ESP32.ino`.
2. **Arduino IDE / CLI Settings**:
   - Board: `ESP32 Dev Module` or `ESP32-WROOM-DA`
   - Upload Speed: `921600`
   - CPU Frequency: `240MHz`
   - Core Debug Level: `None` or `Info`
3. **Required Libraries**:
   - `LiquidCrystal I2C` (by Frank de Brabander)
   - `DHT sensor library` (by Adafruit)
   - `Adafruit Unified Sensor`
4. **Compile Command (via Arduino CLI)**:
   ```powershell
   arduino-cli compile --fqbn esp32:esp32:esp32da firmware\SmartUmbrella_Firebase_ESP32\SmartUmbrella_Firebase_ESP32.ino
   ```

### Serverless Cloud Functions (Optional for Background Push Notifications)
To receive alerts when the Android app is closed:
1. Navigate to `functions/`:
   ```bash
   cd functions
   npm install
   firebase deploy --only functions
   ```

---

## 5. Repository Structure Notes
- `firmware/SmartUmbrella_Firebase_ESP32.ino`: Canonical ESP32 firmware.
- `firmware/SmartUmbrella_Firebase_ESP32/`: Arduino IDE sketch folder containing canonical `.ino`.
- `Main Code`: Legacy sketch with duplicated/malformed content (deprecated; do not use as source of truth).
- `Rain Sensor`: Legacy standalone test sketch for the rain module (kept for standalone reference).

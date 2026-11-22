# Smart Umbrella IoT System & Android Application

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![ESP32](https://img.shields.io/badge/Hardware-ESP32-E7352C?logo=espressif&logoColor=white)](https://www.espressif.com/en/products/socs/esp32)
[![Firebase](https://img.shields.io/badge/Backend-Firebase%20RTDB-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)

An end-to-end Internet of Things (IoT) system and native Android application that combines real-time physical environmental sensing with predictive weather intelligence to keep users informed and prepared for rainfall.

---

## Architecture Overview

```
+-------------------------------------------------------------+
|                     ESP32 MICROCONTROLLER                   |
|  - DHT22 (Temp & Humidity)       - Active Buzzer (GPIO 25)  |
|  - FC-37 Rain Sensor (AO & DO)   - RGB Status LED           |
|  - I2C 16x2 LCD Display          - Push Button (Mute)       |
+------------------------------+------------------------------+
                               |
                   2.4 GHz Wi-Fi (HTTPS REST PUT)
                   NTP Epoch Time Synchronization
                               |
                               v
+-------------------------------------------------------------+
|                 FIREBASE REALTIME DATABASE                  |
|                 Path: /smartUmbrella/status                 |
|       - deviceOnline     - rainDetected   - temperature     |
|       - rainAnalogValue  - buzzerOn       - humidity        |
|       - lastUpdated      - rgbStatus      - deviceId        |
+------------------------------+------------------------------+
                               |
                     Realtime Value Listener
                     (Any Wi-Fi or Cellular Data)
                               |
                               v
+-------------------------------------------------------------+
|                 SMART UMBRELLA ANDROID APP                  |
|  - Jetpack Compose + Material 3 UI                          |
|  - Real-Time Sensor Dashboard & Historical Line Charts      |
|  - Smart Outing Planner (Open-Meteo Hourly Forecast & GPS)  |
|  - Dual Modes: Physical Device Mode & College Demo Mode     |
+-------------------------------------------------------------+
```

---

## Key Features

### 1. Physical Hardware & Sensing (ESP32)
* **Real-Time Environmental Telemetry**: Samples ambient temperature and relative humidity every 2 seconds via **DHT22**.
* **High-Precision Rain Detection**: Evaluates analog surface conductivity (**AO**) and digital threshold (**DO**) using smart hysteresis:
  * **Rain Alert**: Triggered when $AO < 3300$ or $DO == \text{LOW}$.
  * **Dry Reset**: Cleared when surface is wiped dry ($AO > 3600$).
* **Audible & Visual Alerts**:
  * Active Buzzer pulses on GPIO 25 during rain events ($300\text{ ms ON} / 200\text{ ms OFF}$).
  * RGB LED turns **RED** during rain and **GREEN** during dry/standby.
* **Alert Mute Toggle**: Debounced physical push button on GPIO 32 to silence the buzzer while maintaining visual and cloud notifications.
* **Local LCD Display**: 16x2 I2C LiquidCrystal display showing live temperature, humidity, and real-time rain status.
* **Reliable Wi-Fi & REST Sync**:
  * Authenticates using Firebase Email/Password REST API (`signInWithPassword`) with automatic token renewal.
  * Synchronizes real UTC epoch timestamps via NTP (`pool.ntp.org`).
  * Features a safe Wi-Fi reconnection state machine designed for mobile hotspot DHCP latency (avoids `E wifi:sta is connecting` errors).

### 2. Smart Umbrella Android Application
* **Modern Jetpack Compose UI**: Built with Material 3 design principles, custom themes, dark-mode styling, and smooth state animations.
* **Live Dashboard**:
  * Real-time metrics for Temperature, Humidity, Rain Level, and Buzzer status.
  * 30-second heartbeat monitor detecting online/offline ESP32 status.
  * 30-point interactive historical line charts for environmental trends.
  * Audio alert warnings and persistent rain alert event history.
* **Dual Operation Modes**:
  * **Physical Device Mode**: Streams live data directly from the physical ESP32 via Firebase Realtime Database.
  * **College Demo / Simulation Mode**: Built-in mock data loop with manual "Trigger Rain" buttons for viva presentations when campus Wi-Fi or hardware is unavailable.

### 3. Smart Outing Planner
* **Intelligent Recommendation Engine**: Tells users whether to take an umbrella for planned outdoor trips based on real weather forecasts:
  * `UMBRELLA_NOT_REQUIRED`: Clear/dry conditions ($<20\%$ rain probability).
  * `CARRY_JUST_IN_CASE`: Moderate or isolated showers ($20\%\text{--}50\%$ probability or light rain).
  * `UMBRELLA_RECOMMENDED`: High rain probability ($>50\%$), heavy rain, or storm weather codes.
* **Location Flexibility**:
  * One-tap GPS device location detection.
  * Manual destination address search powered by Open-Meteo Geocoding REST API.
* **Accurate Hourly Forecasts**:
  * Fetches real hourly precipitation probabilities (%), rain volume (mm), and WMO weather condition codes.
  * Robustly handles trips spanning across midnight, different dates, and timezone boundaries.
* **Hardware Context Separation**: Displays physical umbrella status (home sensor) independently from future destination weather.

---

## Hardware Pinout (ESP32 DevKit)

| Component | Pin / Bus | ESP32 GPIO | Description |
| :--- | :--- | :--- | :--- |
| **DHT22** | DATA | `GPIO 4` | Temperature & Humidity Sensor |
| **FC-37 Rain Sensor** | Digital Output (DO) | `GPIO 27` | Digital Rain Threshold Detection |
| **FC-37 Rain Sensor** | Analog Output (AO) | `GPIO 34` | Raw Analog Moisture Reading (ADC1) |
| **Active Buzzer** | Positive (+) | `GPIO 25` | Audible Rain Warning (Pulsed DC) |
| **RGB LED** | RED | `GPIO 18` | Rain Alert Indicator |
| **RGB LED** | GREEN | `GPIO 14` | Normal / Standby Indicator |
| **RGB LED** | BLUE | `GPIO 26` | Status Indicator |
| **Push Button** | Input | `GPIO 32` | Alert Mute Toggle (`INPUT_PULLUP`) |
| **I2C LCD 16x2** | SDA | `GPIO 21` | I2C Data Line (Address `0x27`) |
| **I2C LCD 16x2** | SCL | `GPIO 22` | I2C Clock Line (Address `0x27`) |

---

## Firebase Realtime Database Schema

Path: `/smartUmbrella/status`

```json
{
  "deviceId": "umbrella_001",
  "temperature": 27.4,
  "humidity": 65.2,
  "rainDetected": true,
  "rainAnalogValue": 2150,
  "rainDigitalValue": 0,
  "buzzerOn": true,
  "rgbStatus": "RED",
  "deviceOnline": true,
  "lastUpdated": 1732301845000
}
```

---

## Project Structure

```
IOT-Android-mini-Project/
├── app/                                    # Android Application (Kotlin, Jetpack Compose)
│   ├── src/main/
│   │   ├── AndroidManifest.xml             # Permissions & FCM Service Configuration
│   │   ├── java/com/smartumbrella/app/
│   │   │   ├── data/
│   │   │   │   ├── engine/                 # SmartRecommendationEngine for outing planner
│   │   │   │   ├── model/                  # Domain models (OutingPlan, Status, History)
│   │   │   │   └── repository/             # Firebase RTDB & Open-Meteo Weather Repositories
│   │   │   ├── service/                    # NotificationHelper & FCM Background Service
│   │   │   ├── ui/
│   │   │   │   ├── components/             # Reusable UI cards, charts, badges & banners
│   │   │   │   ├── navigation/             # AppNavHost, BottomNavigationBar & NavRoutes
│   │   │   │   ├── screens/                # Home, Planner, Sensors, Alerts, Settings
│   │   │   │   └── theme/                  # Material 3 Color palette, Theme & Typography
│   │   │   ├── util/                       # LocationHelper (GPS & Geocoding)
│   │   │   ├── viewmodel/                  # DashboardViewModel & OutingPlannerViewModel
│   │   │   └── MainActivity.kt             # Application entry point
│   │   └── res/                            # Vector drawables, themes, and XML resources
│   ├── build.gradle.kts                    # App-level Gradle dependencies
│   └── google-services.json                # Firebase configuration credentials
│
├── firmware/                               # ESP32 IoT Microcontroller Firmware
│   ├── SmartUmbrella_Firebase_ESP32.ino    # Unified Arduino C++ sketch
│   └── SmartUmbrella_Firebase_ESP32/       # Arduino IDE sketch folder
│       └── SmartUmbrella_Firebase_ESP32.ino
│
├── gradle/wrapper/                         # Gradle 8.9 wrapper binaries and properties
├── build.gradle.kts                        # Root Gradle build configuration
└── README.md                               # Project documentation
```

---

## Getting Started

### 1. Prerequisites
* **Android Studio**: Ladybug / Koala or newer with JDK 17 or 21.
* **Arduino IDE**: Version 2.0+ with **ESP32 by Espressif** board package (v3.0+).
* **Required Arduino Libraries**:
  * `LiquidCrystal I2C` (by Frank de Brabander)
  * `DHT sensor library` (by Adafruit)
  * `Adafruit Unified Sensor`

### 2. Microcontroller Setup (ESP32)
1. Open `firmware/SmartUmbrella_Firebase_ESP32/SmartUmbrella_Firebase_ESP32.ino` in Arduino IDE.
2. Update your credentials at the top of the file:
   ```cpp
   const char* WIFI_SSID              = "YOUR_2.4GHZ_WIFI_NAME";
   const char* WIFI_PASSWORD          = "YOUR_WIFI_PASSWORD";
   const char* FIREBASE_USER_EMAIL    = "your_user@example.com";
   const char* FIREBASE_USER_PASSWORD = "your_secure_password";
   ```
   > [!IMPORTANT]
   > Ensure your Wi-Fi router or phone mobile hotspot is set to the **2.4 GHz band** (ESP32 hardware does not support 5 GHz Wi-Fi).
3. Connect your ESP32 board via USB, select **Tools > Board > ESP32 Dev Module**, choose your COM port, and click **Upload**.
4. Open **Tools > Serial Monitor** (`115200` baud) to verify the boot beep, Wi-Fi connection, NTP time sync, and initial Firebase telemetry push.

### 3. Android App Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/NireekshaShetty06/IOT-Android-mini-Project.git
   ```
2. Open the project in **Android Studio**.
3. Let Gradle sync automatically (configured with Gradle 8.9 and Android Gradle Plugin 8.7.3).
4. Run the app on a physical Android device or emulator:
   ```bash
   ./gradlew assembleDebug
   ```
5. Navigate to **Settings** in the app to switch between **Physical Device Mode** (reads live ESP32 data) and **Simulation Mode** (demo/testing mode).

---

## Tech Stack

* **Mobile**: Kotlin 2.0, Jetpack Compose, Material 3, Navigation Compose, Coroutines, StateFlow, Retrofit2, OkHttp3.
* **Cloud & Backend**: Firebase Realtime Database, Firebase Authentication (REST API), Firebase Cloud Messaging.
* **APIs**: Open-Meteo Hourly Weather Forecast API, Open-Meteo Geocoding API.
* **Firmware / Hardware**: Arduino C++, ESP32 (Xtensa 32-bit LX6), FreeRTOS, HTTPClient, WiFiClientSecure, NTP Time.

---

## Contributors

* **Aishwarya Jain**
* **Nireeksha Shetty**

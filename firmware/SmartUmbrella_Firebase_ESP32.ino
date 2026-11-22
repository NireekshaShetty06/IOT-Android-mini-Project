/*
 * =====================================================================================
 * SMART UMBRELLA - FINAL UNIFIED ESP32 FIRMWARE
 * Built from original tested hardware baseline + FreeRTOS Network Task + Firebase REST
 * =====================================================================================
 * Hardware Pinout:
 * - DHT22 Sensor:       GPIO 4
 * - Rain Sensor (DO):   GPIO 27 (Active-LOW digital output from LM393)
 * - Rain Sensor (AO):   GPIO 34 (Analog input: Dry ~4095, Wet < 3300)
 * - Active Buzzer:      GPIO 25 (Active HIGH drive)
 * - RGB LED (Red):      GPIO 18
 * - RGB LED (Green):    GPIO 14
 * - RGB LED (Blue):     GPIO 26
 * - Push Button:        GPIO 32 (Mute toggle, Internal Pullup)
 * - I2C LCD 16x2:       SDA -> GPIO 21, SCL -> GPIO 22 (0x27)
 *
 * Firebase Realtime Database:
 * - Telemetry:   /smartUmbrella/status
 * - Config:      /smartUmbrella/config
 * - History:     /smartUmbrella/history
 * =====================================================================================
 */

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <DHT.h>
#include <time.h>
#include <sys/time.h>

// =====================================================================================
// 1. CONFIGURABLE CREDENTIALS
// =====================================================================================
// Replace with your 2.4 GHz Wi-Fi credentials (ESP32 only supports 2.4 GHz):
const char* WIFI_SSID               = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD           = "YOUR_WIFI_PASSWORD";

// Firebase Realtime Database Host (Asia Southeast 1):
const char* FIREBASE_HOST           = "https://smart-umbrella-1e4e4-default-rtdb.asia-southeast1.firebasedatabase.app";

// Firebase Web API Key:
const char* FIREBASE_API_KEY        = "AIzaSyDz-k0pxMTRMNPsJC7morSE98pfVDSAsPw";

// Firebase Authentication User Credentials (Email / Password):
const char* FIREBASE_USER_EMAIL     = "YOUR_FIREBASE_EMAIL";
const char* FIREBASE_USER_PASSWORD  = "YOUR_FIREBASE_PASSWORD";

// NTP Servers:
const char* NTP_SERVER_1            = "pool.ntp.org";
const char* NTP_SERVER_2            = "time.google.com";

// =====================================================================================
// 2. PIN DEFINITIONS (EXACT HARDWARE MATCH)
// =====================================================================================
#define PIN_DHT          4
#define DHTTYPE          DHT22

#define PIN_RAIN_DO      27
#define PIN_RAIN_AO      34

#define PIN_BUZZER       25

#define PIN_LED_RED      18      // Red on GPIO 18
#define PIN_LED_GREEN    14      // Green on GPIO 14
#define PIN_LED_BLUE     26      // Blue on GPIO 26

#define PIN_BUTTON       32

// =====================================================================================
// 3. BALANCED SENSITIVITY THRESHOLDS (SAFE DEFAULTS + REMOTE OVERRIDE)
// =====================================================================================
// Dry is ~4095. Droplets drop it to 2500-3200. Wiped dry goes to 3600+.
int rainTriggerThreshold = 3300; 
int rainClearThreshold   = 3600;
bool remoteSoundEnabled  = true;

const bool IS_COMMON_ANODE = false;

// Hardware Objects
LiquidCrystal_I2C lcd(0x27, 16, 2);
DHT dht(PIN_DHT, DHTTYPE);

// =====================================================================================
// 4. GLOBAL STATE VARIABLES & TIMERS
// =====================================================================================
float currentTemp = 0.0;
float currentHumidity = 0.0;
bool isDhtValid = false;

int rainRawValue = 4095;
int digitalRain = 1;
bool isRaining = false;
bool isAlertMuted = false;

// RGB State String for Firebase
String currentRgbStatus = "GREEN";

// Hardware Timing (Core 1)
unsigned long lastDHTReadTime = 0;
const unsigned long dhtInterval = 2000;

unsigned long lastRainCheckTime = 0;
const unsigned long rainCheckInterval = 40;

unsigned long lastBuzzerToggle = 0;
bool buzzerState = false;

int lastButtonReading = HIGH;
int buttonState = HIGH;
unsigned long lastDebounceTime = 0;
const unsigned long debounceDelay = 50;

// FreeRTOS Thread-Safe Telemetry Transfer Structure
struct TelemetrySnapshot {
  float temperature;
  float humidity;
  bool dhtValid;
  bool isRaining;
  int rainRawValue;
  int digitalRain;
  bool buzzerOn;
  char rgbStatus[8];
};

TelemetrySnapshot sharedTelemetry;
SemaphoreHandle_t telemetryMutex = NULL;
SemaphoreHandle_t telemetryNotifySem = NULL;

// Network State Variables (Owned exclusively by Core 0 network task)
bool wasWifiConnected = false;
bool isWifiConnecting = false;
unsigned long wifiConnectStartTime = 0;
const unsigned long wifiConnectTimeout = 20000; // 20s window
unsigned long lastWifiRetryTime = 0;
const unsigned long wifiRetryInterval = 15000;  // 15s cooldown

bool isTimeSynchronized = false;
unsigned long lastNtpRetryTime = 0;
const unsigned long ntpRetryInterval = 10000;

String firebaseIdToken = "";
unsigned long tokenAcquiredTime = 0;
unsigned long tokenExpiresInSec = 3600;
unsigned long lastAuthAttemptTime = 0;
const unsigned long authRetryInterval = 10000;

unsigned long lastSuccessfulHeartbeatTime = 0;
unsigned long lastConfigFetchTime = 0;

// =====================================================================================
// 5. HARDWARE CONTROL FUNCTIONS
// =====================================================================================
void setLEDColor(bool r, bool g, bool b) {
  if (IS_COMMON_ANODE) {
    r = !r;
    g = !g;
    b = !b;
  }
  digitalWrite(PIN_LED_RED,   r ? HIGH : LOW);
  digitalWrite(PIN_LED_GREEN, g ? HIGH : LOW);
  digitalWrite(PIN_LED_BLUE,  b ? HIGH : LOW);

  if (r && !g && !b) currentRgbStatus = "RED";
  else if (!r && g && !b) currentRgbStatus = "GREEN";
  else if (!r && !g && b) currentRgbStatus = "BLUE";
  else currentRgbStatus = "OFF";
}

void updateLCD() {
  lcd.setCursor(0, 0);
  lcd.print("T:");
  if (isDhtValid) {
    lcd.print((int)currentTemp);
    lcd.print((char)223);
    lcd.print("C H:");
    lcd.print((int)currentHumidity);
    lcd.print("%    ");
  } else {
    lcd.print("--");
    lcd.print((char)223);
    lcd.print("C H:--%    ");
  }

  lcd.setCursor(0, 1);
  if (isRaining) {
    if (isAlertMuted) {
      lcd.print("RAIN! [MUTED]   ");
    } else {
      lcd.print("RAIN! [ALERT]   ");
    }
  } else {
    lcd.print("STATUS: NO RAIN ");
  }
}

// =====================================================================================
// 6. NTP TIME SYNCHRONIZATION (Non-blocking, Core 0)
// =====================================================================================
bool checkTimeSync() {
  struct timeval tv;
  gettimeofday(&tv, NULL);
  if (tv.tv_sec >= 1700000000) {
    if (!isTimeSynchronized) {
      isTimeSynchronized = true;
      Serial.print("\n[NTP] Time synchronized successfully! Current UTC Epoch: ");
      Serial.println((unsigned long)tv.tv_sec);
    }
    return true;
  }
  isTimeSynchronized = false;
  return false;
}

void initNtpTimeNonBlocking() {
  Serial.println("[NTP] Initializing background NTP time synchronization...");
  configTime(0, 0, NTP_SERVER_1, NTP_SERVER_2);
  checkTimeSync();
}

unsigned long long getEpochMillis() {
  struct timeval tv;
  gettimeofday(&tv, NULL);
  if (tv.tv_sec < 1700000000) {
    return 0ULL;
  }
  return ((unsigned long long)tv.tv_sec * 1000ULL) + ((unsigned long long)(tv.tv_usec / 1000ULL));
}

String uint64ToString(unsigned long long value) {
  char buf[32];
  sprintf(buf, "%llu", value);
  return String(buf);
}

// =====================================================================================
// 7. FIREBASE AUTHENTICATION (EMAIL & PASSWORD REST API, Core 0)
// =====================================================================================
bool authenticateFirebase() {
  if (WiFi.status() != WL_CONNECTED) {
    return false;
  }

  Serial.println("[Auth] Authenticating with Firebase using Email/Password...");
  WiFiClientSecure client;
  client.setInsecure(); // Maintainable TLS endpoint connection
  HTTPClient http;

  String authUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + String(FIREBASE_API_KEY);
  http.begin(client, authUrl);
  http.addHeader("Content-Type", "application/json");

  String payload = "{\"email\":\"" + String(FIREBASE_USER_EMAIL) + "\",\"password\":\"" + String(FIREBASE_USER_PASSWORD) + "\",\"returnSecureToken\":true}";

  int httpCode = http.POST(payload);
  if (httpCode == 200) {
    String response = http.getString();

    // Canonical reviewed JSON offsets:
    int tokenKeyIdx = response.indexOf("\"idToken\": \"");
    int tokenStart = 0;
    if (tokenKeyIdx != -1) {
      tokenStart = tokenKeyIdx + 12;
    } else {
      tokenKeyIdx = response.indexOf("\"idToken\":\"");
      if (tokenKeyIdx != -1) tokenStart = tokenKeyIdx + 11;
    }

    if (tokenStart > 0) {
      int tokenEnd = response.indexOf("\"", tokenStart);
      if (tokenEnd != -1) {
        firebaseIdToken = response.substring(tokenStart, tokenEnd);
        tokenAcquiredTime = millis();

        int expKeyIdx = response.indexOf("\"expiresIn\": \"");
        if (expKeyIdx != -1) {
          int expStart = expKeyIdx + 14;
          int expEnd = response.indexOf("\"", expStart);
          if (expEnd != -1) tokenExpiresInSec = response.substring(expStart, expEnd).toInt();
        } else {
          expKeyIdx = response.indexOf("\"expiresIn\":\"");
          if (expKeyIdx != -1) {
            int expStart = expKeyIdx + 13;
            int expEnd = response.indexOf("\"", expStart);
            if (expEnd != -1) tokenExpiresInSec = response.substring(expStart, expEnd).toInt();
          }
        }

        Serial.println("[Auth] Authentication SUCCESS! ID token acquired.");
        http.end();
        client.stop();
        return true;
      }
    }
  }

  Serial.print("[Auth] Authentication FAILED. HTTP Code: ");
  Serial.println(httpCode);
  http.end();
  client.stop();
  return false;
}

bool ensureFirebaseAuth(unsigned long now) {
  if (WiFi.status() != WL_CONNECTED) return false;

  if (firebaseIdToken.length() == 0) {
    if (now - lastAuthAttemptTime >= authRetryInterval) {
      lastAuthAttemptTime = now;
      return authenticateFirebase();
    }
    return false;
  }

  unsigned long tokenAgeSec = (now - tokenAcquiredTime) / 1000;
  if (tokenAgeSec >= (tokenExpiresInSec - 300)) {
    Serial.println("[Auth] Token nearing expiration. Refreshing...");
    return authenticateFirebase();
  }

  return true;
}

// =====================================================================================
// 8. TELEMETRY UPLOAD & REMOTE CONFIG (PATCH REST API, Core 0)
// =====================================================================================
void pushTelemetryHttp(const TelemetrySnapshot& snap) {
  if (WiFi.status() != WL_CONNECTED) return;

  unsigned long long epoch = getEpochMillis();
  if (epoch == 0ULL) return; // Wait for NTP synchronization

  String epochStr = uint64ToString(epoch);

  String jsonPayload = "{";
  jsonPayload += "\"deviceId\":\"umbrella_001\",";
  jsonPayload += "\"temperature\":" + String(snap.temperature, 1) + ",";
  jsonPayload += "\"humidity\":" + String(snap.humidity, 1) + ",";
  jsonPayload += "\"dhtValid\":" + String(snap.dhtValid ? "true" : "false") + ",";
  jsonPayload += "\"rainDetected\":" + String(snap.isRaining ? "true" : "false") + ",";
  jsonPayload += "\"rainAnalogValue\":" + String(snap.rainRawValue) + ",";
  jsonPayload += "\"rainDigitalValue\":" + String(snap.digitalRain) + ",";
  jsonPayload += "\"buzzerOn\":" + String(snap.buzzerOn ? "true" : "false") + ",";
  jsonPayload += "\"rgbStatus\":\"" + String(snap.rgbStatus) + "\",";
  jsonPayload += "\"deviceOnline\":true,";
  jsonPayload += "\"lastUpdated\":" + epochStr;
  jsonPayload += "}";

  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;

  // Use PATCH to update status properties cleanly without erasing potential sibling keys
  String url = String(FIREBASE_HOST) + "/smartUmbrella/status.json?auth=" + firebaseIdToken;
  http.begin(client, url);
  http.addHeader("Content-Type", "application/json");

  int httpCode = http.sendRequest("PATCH", jsonPayload);

  if (httpCode == 200) {
    lastSuccessfulHeartbeatTime = millis();
    Serial.println("[Firebase] Telemetry synced (HTTP 200 OK) | LastUpdated: " + epochStr);
  } else if (httpCode == 401) {
    Serial.println("[Firebase] 401 Unauthorized. Renewing token.");
    firebaseIdToken = "";
  } else {
    Serial.print("[Firebase] Telemetry push returned HTTP Code: ");
    Serial.println(httpCode);
  }
  http.end();
  client.stop();
}

void fetchRemoteConfigHttp() {
  if (WiFi.status() != WL_CONNECTED || firebaseIdToken.length() == 0) return;

  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;

  String url = String(FIREBASE_HOST) + "/smartUmbrella/config.json?auth=" + firebaseIdToken;
  http.begin(client, url);

  int httpCode = http.GET();
  if (httpCode == 200) {
    String payload = http.getString();
    
    // Parse rainTriggerThreshold
    int trigIdx = payload.indexOf("\"rainTriggerThreshold\":");
    if (trigIdx != -1) {
      int valStart = trigIdx + 23;
      int valEnd = payload.indexOf(",", valStart);
      if (valEnd == -1) valEnd = payload.indexOf("}", valStart);
      if (valEnd != -1) {
        int parsed = payload.substring(valStart, valEnd).toInt();
        if (parsed >= 1000 && parsed <= 4000) {
          rainTriggerThreshold = parsed;
        }
      }
    }

    // Parse rainClearThreshold
    int clearIdx = payload.indexOf("\"rainClearThreshold\":");
    if (clearIdx != -1) {
      int valStart = clearIdx + 21;
      int valEnd = payload.indexOf(",", valStart);
      if (valEnd == -1) valEnd = payload.indexOf("}", valStart);
      if (valEnd != -1) {
        int parsed = payload.substring(valStart, valEnd).toInt();
        if (parsed > rainTriggerThreshold && parsed <= 4095) {
          rainClearThreshold = parsed;
        }
      }
    }

    // Parse soundEnabled
    int soundIdx = payload.indexOf("\"soundEnabled\":");
    if (soundIdx != -1) {
      int valStart = soundIdx + 15;
      if (payload.substring(valStart, valStart + 4) == "true") {
        remoteSoundEnabled = true;
      } else if (payload.substring(valStart, valStart + 5) == "false") {
        remoteSoundEnabled = false;
      }
    }

    Serial.println("[Config] Remote config fetched. Trigger: " + String(rainTriggerThreshold) + 
                   " | Clear: " + String(rainClearThreshold) + 
                   " | SoundEnabled: " + String(remoteSoundEnabled ? "YES" : "NO"));
  }
  http.end();
  client.stop();
}

// =====================================================================================
// 9. FREERTOS BACKGROUND NETWORK TASK (CORE 0)
// =====================================================================================
void networkWorkerTask(void* pvParameters) {
  Serial.println("[FreeRTOS] Background Network Task running on Core 0");

  WiFi.mode(WIFI_STA);
  Serial.print("[WiFi] Initiating connection to ");
  Serial.println(WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  isWifiConnecting = true;
  wifiConnectStartTime = millis();

  while (true) {
    unsigned long now = millis();
    wl_status_t status = WiFi.status();

    if (status == WL_CONNECTED) {
      isWifiConnecting = false;
      if (!wasWifiConnected) {
        wasWifiConnected = true;
        Serial.println("\n[WiFi] Connected successfully! IP: " + WiFi.localIP().toString());
        initNtpTimeNonBlocking();
        authenticateFirebase();
        fetchRemoteConfigHttp();
      }
    } else {
      if (wasWifiConnected) {
        wasWifiConnected = false;
        isTimeSynchronized = false;
        firebaseIdToken = "";
        Serial.println("\n[WiFi] Connection lost. Entering cooldown.");
        lastWifiRetryTime = now;
        isWifiConnecting = false;
      }

      if (isWifiConnecting) {
        if (now - wifiConnectStartTime >= wifiConnectTimeout) {
          Serial.println("\n[WiFi] Connection attempt timed out.");
          isWifiConnecting = false;
          lastWifiRetryTime = now;
        }
      } else {
        if (now - lastWifiRetryTime >= wifiRetryInterval) {
          if (status != WL_IDLE_STATUS) {
            lastWifiRetryTime = now;
            wifiConnectStartTime = now;
            isWifiConnecting = true;
            Serial.print("[WiFi] Re-initiating connection to ");
            Serial.println(WIFI_SSID);
            WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
          }
        }
      }
    }

    // Maintain NTP
    if (WiFi.status() == WL_CONNECTED && !isTimeSynchronized) {
      if (now - lastNtpRetryTime >= ntpRetryInterval) {
        lastNtpRetryTime = now;
        configTime(0, 0, NTP_SERVER_1, NTP_SERVER_2);
        checkTimeSync();
      }
    }

    // Maintain Auth
    if (WiFi.status() == WL_CONNECTED) {
      ensureFirebaseAuth(now);
    }

    // Wait for immediate telemetry notification or periodic heartbeat (every 3s)
    if (xSemaphoreTake(telemetryNotifySem, pdMS_TO_TICKS(500)) == pdTRUE || 
        (now - lastSuccessfulHeartbeatTime >= 3000)) {
      if (WiFi.status() == WL_CONNECTED && isTimeSynchronized && ensureFirebaseAuth(now)) {
        TelemetrySnapshot snap;
        if (xSemaphoreTake(telemetryMutex, pdMS_TO_TICKS(50)) == pdTRUE) {
          snap = sharedTelemetry;
          xSemaphoreGive(telemetryMutex);
          pushTelemetryHttp(snap);
        }
      }
    }

    // Periodic remote config fetch (every 60s)
    if (now - lastConfigFetchTime >= 60000) {
      lastConfigFetchTime = now;
      fetchRemoteConfigHttp();
    }

    vTaskDelay(pdMS_TO_TICKS(50));
  }
}

// =====================================================================================
// 10. SETUP (CORE 1)
// =====================================================================================
void setup() {
  Serial.begin(115200);
  delay(400);

  Serial.println("\n==========================================");
  Serial.println("  SMART UMBRELLA - BALANCED SENSITIVITY   ");
  Serial.println("  FreeRTOS Non-Blocking + Firebase Sync   ");
  Serial.println("==========================================");

  pinMode(PIN_RAIN_DO, INPUT);
  pinMode(PIN_RAIN_AO, INPUT);
  
  pinMode(PIN_BUZZER, OUTPUT);
  digitalWrite(PIN_BUZZER, LOW);

  pinMode(PIN_LED_RED, OUTPUT);
  pinMode(PIN_LED_GREEN, OUTPUT);
  pinMode(PIN_LED_BLUE, OUTPUT);
  setLEDColor(false, false, false);

  pinMode(PIN_BUTTON, INPUT_PULLUP);

  dht.begin();
  Wire.begin(21, 22);
  lcd.init();
  lcd.backlight();
  lcd.clear();

  lcd.setCursor(1, 0);
  lcd.print("SMART UMBRELLA");
  lcd.setCursor(2, 1);
  lcd.print("READY TO TEST");

  // Immediate confirmation beep on boot
  digitalWrite(PIN_BUZZER, HIGH);
  delay(120);
  digitalWrite(PIN_BUZZER, LOW);

  setLEDColor(false, true, false); // Green standby
  delay(1000);
  lcd.clear();

  // Create FreeRTOS synchronization primitives
  telemetryMutex = xSemaphoreCreateMutex();
  telemetryNotifySem = xSemaphoreCreateBinary();

  // Initialize shared telemetry state
  sharedTelemetry.temperature = 0.0;
  sharedTelemetry.humidity = 0.0;
  sharedTelemetry.dhtValid = false;
  sharedTelemetry.isRaining = false;
  sharedTelemetry.rainRawValue = 4095;
  sharedTelemetry.digitalRain = 1;
  sharedTelemetry.buzzerOn = false;
  strncpy(sharedTelemetry.rgbStatus, "GREEN", sizeof(sharedTelemetry.rgbStatus));

  // Spawn background network task pinned to Core 0
  xTaskCreatePinnedToCore(
    networkWorkerTask,
    "NetworkTask",
    8192,
    NULL,
    1,
    NULL,
    0
  );

  updateLCD();
  Serial.println(">> Watching live sensor values on Core 1...\n");
}

// =====================================================================================
// 11. MAIN LOOP (CORE 1 - 100% UNINTERRUPTED EXECUTION SPEED)
// =====================================================================================
void loop() {
  unsigned long now = millis();

  // 1. PUSH BUTTON CHECK (50ms debounce)
  int reading = digitalRead(PIN_BUTTON);
  if (reading != lastButtonReading) {
    lastDebounceTime = now;
  }
  if ((now - lastDebounceTime) > debounceDelay) {
    if (reading == LOW && buttonState == HIGH) {
      isAlertMuted = !isAlertMuted;
      Serial.print(">> BUTTON PRESSED: Mute = ");
      Serial.println(isAlertMuted ? "YES" : "NO");
      if (isAlertMuted) {
        digitalWrite(PIN_BUZZER, LOW);
        buzzerState = false;
      }
      updateLCD();
    }
    buttonState = reading;
  }
  lastButtonReading = reading;

  // 2. LIVE RAIN CHECK WITH SMART HYSTERESIS
  if (now - lastRainCheckTime >= rainCheckInterval) {
    lastRainCheckTime = now;

    digitalRain = digitalRead(PIN_RAIN_DO);
    rainRawValue = analogRead(PIN_RAIN_AO);

    bool previousRainState = isRaining;

    // Trigger rain when water drops arrive (< rainTriggerThreshold or DO == LOW):
    if (!isRaining && (digitalRain == LOW || rainRawValue < rainTriggerThreshold)) {
      isRaining = true;
    }
    // Clear rain only when wiped dry (> rainClearThreshold and DO == HIGH):
    else if (isRaining && (digitalRain == HIGH && rainRawValue > rainClearThreshold)) {
      isRaining = false;
    }

    // STATE TRANSITIONS
    if (isRaining != previousRainState) {
      if (isRaining) {
        setLEDColor(true, false, false);
        if (!isAlertMuted && remoteSoundEnabled) {
          digitalWrite(PIN_BUZZER, HIGH);
          buzzerState = true;
          lastBuzzerToggle = now;
        }
        Serial.println("\n*** RAIN EVENT DETECTED! AO Value: " + String(rainRawValue) + " ***");
      } else {
        digitalWrite(PIN_BUZZER, LOW);
        buzzerState = false;
        setLEDColor(false, true, false);
        isAlertMuted = false;
        Serial.println("\n*** PLATE DRY! AO Value: " + String(rainRawValue) + " ***");
      }
      updateLCD();

      // Trigger immediate background telemetry upload without blocking loop
      if (xSemaphoreTake(telemetryMutex, pdMS_TO_TICKS(10)) == pdTRUE) {
        sharedTelemetry.temperature = currentTemp;
        sharedTelemetry.humidity = currentHumidity;
        sharedTelemetry.dhtValid = isDhtValid;
        sharedTelemetry.isRaining = isRaining;
        sharedTelemetry.rainRawValue = rainRawValue;
        sharedTelemetry.digitalRain = digitalRain;
        sharedTelemetry.buzzerOn = buzzerState;
        strncpy(sharedTelemetry.rgbStatus, currentRgbStatus.c_str(), sizeof(sharedTelemetry.rgbStatus));
        xSemaphoreGive(telemetryMutex);
        xSemaphoreGive(telemetryNotifySem);
      }
    }
  }

  // 3. BUZZER CADENCE (300ms ON, 200ms OFF) - Uninterrupted by Wi-Fi/Network
  if (isRaining && !isAlertMuted && remoteSoundEnabled) {
    if (buzzerState && (now - lastBuzzerToggle >= 300)) {
      digitalWrite(PIN_BUZZER, LOW);
      buzzerState = false;
      lastBuzzerToggle = now;
    } else if (!buzzerState && (now - lastBuzzerToggle >= 200)) {
      digitalWrite(PIN_BUZZER, HIGH);
      buzzerState = true;
      lastBuzzerToggle = now;
    }
  } else {
    if (buzzerState) {
      digitalWrite(PIN_BUZZER, LOW);
      buzzerState = false;
    }
  }

  // 4. PERIODIC DHT22 SAMPLING (Every 2s)
  if (now - lastDHTReadTime >= dhtInterval) {
    lastDHTReadTime = now;

    float t = dht.readTemperature();
    float h = dht.readHumidity();

    if (!isnan(t) && !isnan(h)) {
      currentTemp = t;
      currentHumidity = h;
      isDhtValid = true;
    } else {
      isDhtValid = false;
      Serial.println("[DHT] Warning: Invalid sensor reading from DHT22.");
    }

    Serial.print("T: "); 
    if (isDhtValid) Serial.print(currentTemp, 1); else Serial.print("--");
    Serial.print(" C | H: "); 
    if (isDhtValid) Serial.print(currentHumidity, 1); else Serial.print("--");
    Serial.print(" % | Live Rain AO: "); Serial.print(rainRawValue);
    Serial.print(" | Status: "); Serial.println(isRaining ? "RAIN DETECTED!" : "NO RAIN");

    updateLCD();

    // Refresh shared telemetry state for periodic background heartbeat
    if (xSemaphoreTake(telemetryMutex, pdMS_TO_TICKS(10)) == pdTRUE) {
      sharedTelemetry.temperature = currentTemp;
      sharedTelemetry.humidity = currentHumidity;
      sharedTelemetry.dhtValid = isDhtValid;
      sharedTelemetry.isRaining = isRaining;
      sharedTelemetry.rainRawValue = rainRawValue;
      sharedTelemetry.digitalRain = digitalRain;
      sharedTelemetry.buzzerOn = buzzerState;
      strncpy(sharedTelemetry.rgbStatus, currentRgbStatus.c_str(), sizeof(sharedTelemetry.rgbStatus));
      xSemaphoreGive(telemetryMutex);
    }
  }

  delay(5); // Small yield for RTOS IDLE task
}

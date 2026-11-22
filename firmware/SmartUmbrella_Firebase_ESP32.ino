/*
 * =====================================================================================
 * SMART UMBRELLA - FINAL UNIFIED ESP32 FIRMWARE
 * Built from original tested hardware baseline + Firebase REST + NTP Time Synchronization
 * =====================================================================================
 * Hardware Pinout:
 * - DHT22 Sensor:       GPIO 4
 * - Rain Sensor (DO):   GPIO 27
 * - Rain Sensor (AO):   GPIO 34
 * - Active Buzzer:      GPIO 25
 * - RGB LED (Red):      GPIO 18
 * - RGB LED (Green):    GPIO 14
 * - RGB LED (Blue):     GPIO 26
 * - Push Button:        GPIO 32 (Mute toggle, Internal Pullup)
 * - I2C LCD 16x2:       SDA -> GPIO 21, SCL -> GPIO 22 (0x27)
 *
 * Firebase Realtime Database (/smartUmbrella/status):
 * - deviceId: "umbrella_001" (String)
 * - temperature: float
 * - humidity: float
 * - rainDetected: boolean
 * - rainAnalogValue: int
 * - rainDigitalValue: int
 * - buzzerOn: boolean
 * - rgbStatus: string ("GREEN", "RED", "BLUE", "OFF")
 * - deviceOnline: boolean
 * - lastUpdated: Unix epoch milliseconds (from NTP)
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
// 3. BALANCED SENSITIVITY THRESHOLDS (EXACT MATCH)
// =====================================================================================
// Dry is ~4095. Droplets drop it to 2500-3200. Wiped dry goes to 3600+.
const int RAIN_TRIGGER_THRESHOLD = 3300; 
const int RAIN_CLEAR_THRESHOLD   = 3600;

const bool IS_COMMON_ANODE = false;

// Hardware Objects
LiquidCrystal_I2C lcd(0x27, 16, 2);
DHT dht(PIN_DHT, DHTTYPE);

// =====================================================================================
// 4. GLOBAL STATE VARIABLES & TIMERS
// =====================================================================================
float currentTemp = 0.0;
float currentHumidity = 0.0;
int rainRawValue = 4095;
int digitalRain = 1;
bool isRaining = false;
bool isAlertMuted = false;

// RGB State String for Firebase
String currentRgbStatus = "GREEN";

// Hardware Timing
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

// Firebase Periodic Sync Timing
unsigned long lastFirebaseSync = 0;
const unsigned long firebaseSyncInterval = 3000; // Periodic sync every 3s

// Network & Safe Wi-Fi State Machine Variables
bool wasWifiConnected = false;
bool isWifiConnecting = false;
unsigned long wifiConnectStartTime = 0;
const unsigned long wifiConnectTimeout = 20000; // 20s window for DHCP lease from phone hotspot
unsigned long lastWifiRetryTime = 0;
const unsigned long wifiRetryInterval = 15000;  // 15s cooldown between new connection attempts

bool isTimeSynchronized = false;
unsigned long lastNtpRetryTime = 0;
const unsigned long ntpRetryInterval = 10000; // NTP clock retry every 10s

String firebaseIdToken = "";
unsigned long tokenAcquiredTime = 0;
unsigned long tokenExpiresInSec = 3600;
unsigned long lastAuthAttemptTime = 0;
const unsigned long authRetryInterval = 10000; // Firebase auth retry every 10s

// =====================================================================================
// 5. HARDWARE CONTROL FUNCTIONS (ORIGINAL TESTED CODE)
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
  lcd.print((int)currentTemp);
  lcd.print((char)223);
  lcd.print("C H:");
  lcd.print((int)currentHumidity);
  lcd.print("%    ");

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
// 6. NTP TIME SYNCHRONIZATION
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

void initNtpTime() {
  Serial.print("[NTP] Initializing NTP time synchronization...");
  configTime(0, 0, NTP_SERVER_1, NTP_SERVER_2);
  int attempts = 0;
  while (!checkTimeSync() && attempts < 15) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  if (!isTimeSynchronized) {
    Serial.println("\n[NTP] Time sync will continue in background.");
  }
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
// 7. FIREBASE AUTHENTICATION (EMAIL & PASSWORD REST API)
// =====================================================================================
bool authenticateFirebase() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[Auth] Cannot authenticate: Wi-Fi not connected.");
    return false;
  }

  Serial.println("[Auth] Authenticating with Firebase using Email/Password...");
  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;

  String authUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + String(FIREBASE_API_KEY);
  http.begin(client, authUrl);
  http.addHeader("Content-Type", "application/json");

  String payload = "{\"email\":\"" + String(FIREBASE_USER_EMAIL) + "\",\"password\":\"" + String(FIREBASE_USER_PASSWORD) + "\",\"returnSecureToken\":true}";

  int httpCode = http.POST(payload);
  if (httpCode == 200) {
    String response = http.getString();

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
        Serial.print("[Auth] Token expires in: ");
        Serial.print(tokenExpiresInSec);
        Serial.println(" seconds.");
        http.end();
        client.stop();
        return true;
      }
    }
  }

  Serial.print("[Auth] Authentication FAILED. HTTP Code: ");
  Serial.println(httpCode);
  if (httpCode > 0) {
    Serial.println("[Auth] Response: " + http.getString());
  } else {
    Serial.println("[Auth] Error: " + http.errorToString(httpCode));
  }
  http.end();
  client.stop();
  return false;
}

bool ensureFirebaseAuth(unsigned long now) {
  if (WiFi.status() != WL_CONNECTED) return false;

  if (firebaseIdToken.length() == 0) {
    if (now - lastAuthAttemptTime >= authRetryInterval) {
      lastAuthAttemptTime = now;
      Serial.println("[Auth] No active token. Retrying Firebase authentication...");
      return authenticateFirebase();
    }
    return false;
  }

  unsigned long tokenAgeSec = (now - tokenAcquiredTime) / 1000;
  if (tokenAgeSec >= (tokenExpiresInSec - 300)) {
    Serial.println("[Auth] Firebase ID token nearing expiration. Refreshing...");
    return authenticateFirebase();
  }

  return true;
}

// =====================================================================================
// 8. TELEMETRY UPLOAD (PUT REST API)
// =====================================================================================
void pushTelemetryToFirebase() {
  if (WiFi.status() != WL_CONNECTED) {
    return;
  }

  unsigned long now = millis();
  if (!ensureFirebaseAuth(now)) {
    return;
  }

  unsigned long long epoch = getEpochMillis();
  if (epoch == 0ULL) {
    return; // Wait for NTP synchronization
  }

  String epochStr = uint64ToString(epoch);

  String jsonPayload = "{";
  jsonPayload += "\"deviceId\":\"umbrella_001\",";
  jsonPayload += "\"temperature\":" + String(currentTemp, 1) + ",";
  jsonPayload += "\"humidity\":" + String(currentHumidity, 1) + ",";
  jsonPayload += "\"rainDetected\":" + String(isRaining ? "true" : "false") + ",";
  jsonPayload += "\"rainAnalogValue\":" + String(rainRawValue) + ",";
  jsonPayload += "\"rainDigitalValue\":" + String(digitalRain) + ",";
  jsonPayload += "\"buzzerOn\":" + String(buzzerState ? "true" : "false") + ",";
  jsonPayload += "\"rgbStatus\":\"" + currentRgbStatus + "\",";
  jsonPayload += "\"deviceOnline\":true,";
  jsonPayload += "\"lastUpdated\":" + epochStr;
  jsonPayload += "}";

  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;

  String url = String(FIREBASE_HOST) + "/smartUmbrella/status.json?auth=" + firebaseIdToken;
  http.begin(client, url);
  http.addHeader("Content-Type", "application/json");

  int httpCode = http.PUT(jsonPayload);

  if (httpCode == 200) {
    Serial.println("[Firebase] Telemetry pushed (HTTP 200 OK) | LastUpdated: " + epochStr);
  } else if (httpCode == 401) {
    Serial.println("[Firebase] Upload 401 Unauthorized. Refreshing token...");
    firebaseIdToken = "";
    authenticateFirebase();
  } else {
    Serial.print("[Firebase] Upload failed. HTTP Code: ");
    Serial.println(httpCode);
  }
  http.end();
  client.stop();

  // If the buzzer was active, refresh lastBuzzerToggle so network delay doesn't swallow buzzer cadence
  if (buzzerState) {
    lastBuzzerToggle = millis();
  }
}

// =====================================================================================
// 9. NETWORK SERVICES WATCHDOG (SAFE WI-FI RECONNECTION STATE MACHINE)
// =====================================================================================
void maintainNetworkServices(unsigned long now) {
  wl_status_t status = WiFi.status();

  if (status == WL_CONNECTED) {
    isWifiConnecting = false;
    if (!wasWifiConnected) {
      wasWifiConnected = true;
      Serial.println("\n[WiFi] Connection established! IP: " + WiFi.localIP().toString());
      lcd.setCursor(0, 0);
      lcd.print("WIFI CONNECTED  ");
      initNtpTime();
      authenticateFirebase();
      updateLCD();
    }
  } else {
    // If active connection was lost
    if (wasWifiConnected) {
      wasWifiConnected = false;
      isTimeSynchronized = false;
      firebaseIdToken = "";
      Serial.println("\n[WiFi] Connection lost.");
      lastWifiRetryTime = now;
      isWifiConnecting = false;
    }

    // If an existing connection attempt is still running
    if (isWifiConnecting) {
      if (now - wifiConnectStartTime >= wifiConnectTimeout) {
        Serial.println("\n[WiFi] Connection attempt timed out.");
        isWifiConnecting = false;
        lastWifiRetryTime = now; // Start cooldown before retrying
      } else {
        // Handshake/DHCP lease currently in progress: DO NOT call WiFi.begin() or WiFi.disconnect()
        return;
      }
    } else {
      // Cooldown check before initiating a new connection attempt
      if (now - lastWifiRetryTime >= wifiRetryInterval) {
        // Never call begin if the driver is already idle or associating
        if (status != WL_IDLE_STATUS) {
          lastWifiRetryTime = now;
          wifiConnectStartTime = now;
          isWifiConnecting = true;
          Serial.print("[WiFi] Initiating connection attempt to ");
          Serial.println(WIFI_SSID);
          WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
        }
      }
    }
    return;
  }

  // NTP synchronization check
  if (!isTimeSynchronized) {
    if (now - lastNtpRetryTime >= ntpRetryInterval) {
      lastNtpRetryTime = now;
      Serial.println("[NTP] Retrying clock sync...");
      configTime(0, 0, NTP_SERVER_1, NTP_SERVER_2);
      checkTimeSync();
    }
  }

  // Firebase auth check / token renewal
  ensureFirebaseAuth(now);
}

// =====================================================================================
// 10. SETUP
// =====================================================================================
void setup() {
  Serial.begin(115200);
  delay(400);

  Serial.println("\n==========================================");
  Serial.println("  SMART UMBRELLA - BALANCED SENSITIVITY   ");
  Serial.println("  Hardware Baseline + Firebase Telemetry  ");
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

  // Exact confirmation beep from working original code (Immediate feedback):
  digitalWrite(PIN_BUZZER, HIGH);
  delay(120);
  digitalWrite(PIN_BUZZER, LOW);

  setLEDColor(false, true, false); // Green standby
  delay(1000);
  lcd.clear();

  // Connect to Wi-Fi safely (Station Mode, single begin call)
  WiFi.mode(WIFI_STA);
  Serial.print("[WiFi] Connecting to ");
  Serial.println(WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  isWifiConnecting = true;
  wifiConnectStartTime = millis();

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 25) {
    delay(400);
    Serial.print(".");
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    wasWifiConnected = true;
    isWifiConnecting = false;
    Serial.println("\n[WiFi] Connected! IP: " + WiFi.localIP().toString());
    lcd.setCursor(0, 0);
    lcd.print("WIFI CONNECTED  ");
    lcd.setCursor(0, 1);
    lcd.print("INITIALIZING... ");
    initNtpTime();
    authenticateFirebase();
    delay(500);
    lcd.clear();
  } else {
    wasWifiConnected = false;
    Serial.println("\n[WiFi] Handshake in progress; continuing connection in background.");
  }

  updateLCD();
  Serial.println(">> Watching live sensor values...\n");
}

// =====================================================================================
// 11. LOOP (PRESERVES EXACT HARDWARE EXECUTION SPEED)
// =====================================================================================
void loop() {
  unsigned long now = millis();

  // 1. Maintain background Wi-Fi and Firebase services
  maintainNetworkServices(now);

  // 2. PUSH BUTTON CHECK (ORIGINAL LOGIC)
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

  // 3. LIVE RAIN CHECK WITH SMART HYSTERESIS (ORIGINAL LOGIC)
  if (now - lastRainCheckTime >= rainCheckInterval) {
    lastRainCheckTime = now;

    digitalRain = digitalRead(PIN_RAIN_DO);
    rainRawValue = analogRead(PIN_RAIN_AO);

    bool previousRainState = isRaining;

    // Trigger rain when water drops arrive:
    if (!isRaining && (digitalRain == LOW || rainRawValue < RAIN_TRIGGER_THRESHOLD)) {
      isRaining = true;
    }
    // Clear rain only when wiped dry:
    else if (isRaining && (digitalRain == HIGH && rainRawValue > RAIN_CLEAR_THRESHOLD)) {
      isRaining = false;
    }

    // STATE TRANSITIONS (ORIGINAL LOGIC)
    if (isRaining != previousRainState) {
      if (isRaining) {
        // RAIN DETECTED: Instant Red LED + Buzzer!
        setLEDColor(true, false, false);
        if (!isAlertMuted) {
          digitalWrite(PIN_BUZZER, HIGH);
          buzzerState = true;
          lastBuzzerToggle = now;
        }
        Serial.println("\n*** RAIN EVENT DETECTED! AO Value: " + String(rainRawValue) + " ***");
      } else {
        // DRY: Instant silence + Green LED!
        digitalWrite(PIN_BUZZER, LOW);
        buzzerState = false;
        setLEDColor(false, true, false);
        isAlertMuted = false;
        Serial.println("\n*** PLATE DRY! AO Value: " + String(rainRawValue) + " ***");
      }
      updateLCD();
      pushTelemetryToFirebase(); // Send immediate rain transition alert to Android App
    }
  }

  // 4. BUZZER CADENCE (300ms ON, 200ms OFF) (ORIGINAL LOGIC)
  if (isRaining && !isAlertMuted) {
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

  // 5. PERIODIC DHT22 TELEMETRY (Every 2s) (ORIGINAL LOGIC)
  if (now - lastDHTReadTime >= dhtInterval) {
    lastDHTReadTime = now;

    float t = dht.readTemperature();
    float h = dht.readHumidity();

    if (!isnan(t) && !isnan(h)) {
      currentTemp = t;
      currentHumidity = h;
    }

    Serial.print("T: "); Serial.print(currentTemp, 1);
    Serial.print(" C | H: "); Serial.print(currentHumidity, 1);
    Serial.print(" % | Live Rain AO: "); Serial.print(rainRawValue);
    Serial.print(" | Status: "); Serial.println(isRaining ? "RAIN DETECTED!" : "NO RAIN");

    updateLCD();
  }

  // 6. PERIODIC FIREBASE HEARTBEAT (Every 3s)
  if (now - lastFirebaseSync >= firebaseSyncInterval) {
    lastFirebaseSync = now;
    pushTelemetryToFirebase();
  }
}

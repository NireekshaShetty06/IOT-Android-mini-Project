/*
 * =====================================================================================
 * SMART UMBRELLA - FINAL UNIFIED ESP32 FIRMWARE
 * Firebase Realtime Database + REST Authentication + NTP Time Synchronization
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
 * Firebase Schema (/smartUmbrella/status):
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
// Replace with your 2.4 GHz Wi-Fi credentials:
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
// 2. PIN DEFINITIONS
// =====================================================================================
#define PIN_DHT          4
#define DHTTYPE          DHT22

#define PIN_RAIN_DO      27
#define PIN_RAIN_AO      34

#define PIN_BUZZER       25

#define PIN_LED_RED      18
#define PIN_LED_GREEN    14
#define PIN_LED_BLUE     26

#define PIN_BUTTON       32

// =====================================================================================
// 3. HARDWARE CONSTANTS & OBJECTS (EXACTLY ONE SET)
// =====================================================================================
const int RAIN_TRIGGER_THRESHOLD = 3300; 
const int RAIN_CLEAR_THRESHOLD   = 3600;

LiquidCrystal_I2C lcd(0x27, 16, 2);
DHT dht(PIN_DHT, DHTTYPE);

// =====================================================================================
// 4. GLOBAL STATE VARIABLES & TIMERS (EXACTLY ONE SET)
// =====================================================================================
// Live Sensor Values
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

unsigned long lastFirebaseSync = 0;
const unsigned long firebaseSyncInterval = 3000; // Periodic sync every 3 seconds

unsigned long lastBuzzerToggle = 0;
bool buzzerState = false;

int lastButtonReading = HIGH;
int buttonState = HIGH;
unsigned long lastDebounceTime = 0;
const unsigned long debounceDelay = 50;

// Network & Service Watchdogs
bool wasWifiConnected = false;
unsigned long lastWifiRetryTime = 0;
const unsigned long wifiRetryInterval = 10000; // Wi-Fi reconnect retry every 10s

bool isTimeSynchronized = false;
unsigned long lastNtpRetryTime = 0;
const unsigned long ntpRetryInterval = 10000; // NTP clock retry every 10s

String firebaseIdToken = "";
unsigned long tokenAcquiredTime = 0;
unsigned long tokenExpiresInSec = 3600;
unsigned long lastAuthAttemptTime = 0;
const unsigned long authRetryInterval = 10000; // Firebase auth retry every 10s

// =====================================================================================
// 5. HARDWARE CONTROL FUNCTIONS (EXACTLY ONE SET)
// =====================================================================================
void setLEDColor(bool r, bool g, bool b) {
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
// 6. NTP SYNCHRONIZATION
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
    Serial.println("\n[NTP] Time sync is taking longer; retrying in background.");
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
// 7. FIREBASE AUTHENTICATION (EMAIL & PASSWORD)
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

  unsigned long elapsedSec = (now - tokenAcquiredTime) / 1000;
  if (elapsedSec >= (tokenExpiresInSec - 300)) {
    if (now - lastAuthAttemptTime >= authRetryInterval) {
      lastAuthAttemptTime = now;
      Serial.println("[Auth] ID token approaching expiration. Renewing token...");
      return authenticateFirebase();
    }
  }
  return true;
}

// =====================================================================================
// 8. FIREBASE TELEMETRY UPLOAD (EXACTLY ONE FUNCTION)
// =====================================================================================
void pushTelemetryToFirebase() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[Firebase] Upload skipped: Wi-Fi disconnected.");
    return;
  }

  if (!checkTimeSync()) {
    Serial.println("[Firebase] Upload skipped: Valid NTP epoch time not yet acquired.");
    return;
  }

  unsigned long long epoch = getEpochMillis();
  if (epoch == 0ULL) {
    Serial.println("[Firebase] Upload skipped: Timestamp returned 0.");
    return;
  }

  unsigned long now = millis();
  if (!ensureFirebaseAuth(now) || firebaseIdToken.length() == 0) {
    Serial.println("[Firebase] Upload skipped: No valid authentication token.");
    return;
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
    Serial.print("[Firebase] Telemetry uploaded successfully (200 OK) | Timestamp: ");
    Serial.println(epochStr);
    http.end();
    client.stop();
  } else if (httpCode == 401) {
    Serial.println("[Firebase] Upload failed (401 Unauthorized). Re-authenticating...");
    http.end();
    client.stop();

    firebaseIdToken = "";
    if (authenticateFirebase()) {
      Serial.println("[Firebase] Retrying telemetry upload with renewed token...");
      WiFiClientSecure retryClient;
      retryClient.setInsecure();
      HTTPClient retryHttp;

      String retryUrl = String(FIREBASE_HOST) + "/smartUmbrella/status.json?auth=" + firebaseIdToken;
      retryHttp.begin(retryClient, retryUrl);
      retryHttp.addHeader("Content-Type", "application/json");

      int retryCode = retryHttp.PUT(jsonPayload);
      if (retryCode == 200) {
        Serial.println("[Firebase] Telemetry uploaded successfully after re-authentication (200 OK).");
      } else {
        Serial.print("[Firebase] Retry upload failed. HTTP Code: ");
        Serial.println(retryCode);
      }
      retryHttp.end();
      retryClient.stop();
    }
  } else {
    Serial.print("[Firebase] Upload failed. HTTP Code: ");
    Serial.println(httpCode);
    if (httpCode > 0) {
      Serial.println("[Firebase] Server Response: " + http.getString());
    } else {
      Serial.println("[Firebase] Error: " + http.errorToString(httpCode));
    }
    http.end();
    client.stop();
  }
}

// =====================================================================================
// 9. NETWORK SERVICES WATCHDOG
// =====================================================================================
void maintainNetworkServices(unsigned long now) {
  if (WiFi.status() == WL_CONNECTED) {
    if (!wasWifiConnected) {
      wasWifiConnected = true;
      Serial.println("\n[WiFi] Connection established! IP: " + WiFi.localIP().toString());
      lcd.setCursor(0, 0);
      lcd.print("WIFI CONNECTED  ");
      initNtpTime();
      authenticateFirebase();
    }
  } else {
    if (wasWifiConnected) {
      wasWifiConnected = false;
      isTimeSynchronized = false;
      firebaseIdToken = "";
      Serial.println("\n[WiFi] Connection lost. Reconnecting...");
      lcd.setCursor(0, 0);
      lcd.print("WIFI RECONNECT  ");
    }

    if (now - lastWifiRetryTime >= wifiRetryInterval) {
      lastWifiRetryTime = now;
      Serial.print("[WiFi] Attempting reconnection to ");
      Serial.println(WIFI_SSID);
      WiFi.disconnect();
      WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    }
    return;
  }

  if (!isTimeSynchronized) {
    if (now - lastNtpRetryTime >= ntpRetryInterval) {
      lastNtpRetryTime = now;
      Serial.println("[NTP] Retrying NTP clock synchronization...");
      configTime(0, 0, NTP_SERVER_1, NTP_SERVER_2);
      checkTimeSync();
    }
  }

  ensureFirebaseAuth(now);
}

// =====================================================================================
// 10. SETUP (EXACTLY ONE FUNCTION)
// =====================================================================================
void setup() {
  Serial.begin(115200);
  delay(400);

  Serial.println("\n==========================================");
  Serial.println("  SMART UMBRELLA - ESP32 IOT FIRMWARE     ");
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
  lcd.setCursor(0, 1);
  lcd.print("CONNECTING WIFI");

  Serial.print("[WiFi] Connecting to ");
  Serial.println(WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 25) {
    delay(500);
    Serial.print(".");
    attempts++;
  }

  lcd.clear();
  if (WiFi.status() == WL_CONNECTED) {
    wasWifiConnected = true;
    Serial.println("\n[WiFi] Connected! IP Address: " + WiFi.localIP().toString());
    lcd.setCursor(0, 0);
    lcd.print("WIFI CONNECTED");
    lcd.setCursor(0, 1);
    lcd.print("SYNCING TIME...");

    initNtpTime();

    lcd.setCursor(0, 1);
    lcd.print("AUTH FIREBASE...");
    authenticateFirebase();
  } else {
    wasWifiConnected = false;
    Serial.println("\n[WiFi] Initial connection timeout. Starting background reconnection...");
    lcd.setCursor(0, 0);
    lcd.print("WIFI OFFLINE");
    lcd.setCursor(0, 1);
    lcd.print("STANDALONE MODE");
  }

  digitalWrite(PIN_BUZZER, HIGH);
  delay(120);
  digitalWrite(PIN_BUZZER, LOW);

  setLEDColor(false, true, false);
  delay(1200);
  lcd.clear();
  Serial.println("[System] Initialization complete. Starting sensor monitoring loop...\n");
}

// =====================================================================================
// 11. LOOP (EXACTLY ONE FUNCTION)
// =====================================================================================
void loop() {
  unsigned long now = millis();

  maintainNetworkServices(now);

  int reading = digitalRead(PIN_BUTTON);
  if (reading != lastButtonReading) {
    lastDebounceTime = now;
  }
  if ((now - lastDebounceTime) > debounceDelay) {
    if (reading == LOW && buttonState == HIGH) {
      isAlertMuted = !isAlertMuted;
      Serial.print(">> [BUTTON] Pressed! Alert Mute = ");
      Serial.println(isAlertMuted ? "ACTIVE" : "DISABLED");
      if (isAlertMuted) {
        digitalWrite(PIN_BUZZER, LOW);
        buzzerState = false;
      }
      updateLCD();
    }
    buttonState = reading;
  }
  lastButtonReading = reading;

  if (now - lastRainCheckTime >= rainCheckInterval) {
    lastRainCheckTime = now;
    digitalRain = digitalRead(PIN_RAIN_DO);
    rainRawValue = analogRead(PIN_RAIN_AO);

    bool previousRainState = isRaining;

    if (!isRaining && (digitalRain == LOW || rainRawValue < RAIN_TRIGGER_THRESHOLD)) {
      isRaining = true;
    } else if (isRaining && (digitalRain == HIGH && rainRawValue > RAIN_CLEAR_THRESHOLD)) {
      isRaining = false;
    }

    if (isRaining != previousRainState) {
      if (isRaining) {
        setLEDColor(true, false, false);
        if (!isAlertMuted) {
          digitalWrite(PIN_BUZZER, HIGH);
          buzzerState = true;
          lastBuzzerToggle = now;
        }
        Serial.println("\n*** [RAIN EVENT] Rain detected! Analog: " + String(rainRawValue) + " ***");
      } else {
        digitalWrite(PIN_BUZZER, LOW);
        buzzerState = false;
        setLEDColor(false, true, false);
        isAlertMuted = false;
        Serial.println("\n*** [DRY EVENT] Surface cleared! Analog: " + String(rainRawValue) + " ***");
      }
      updateLCD();
      pushTelemetryToFirebase();
    }
  }

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
  }

  if (now - lastDHTReadTime >= dhtInterval) {
    lastDHTReadTime = now;
    float t = dht.readTemperature();
    float h = dht.readHumidity();

    if (!isnan(t) && !isnan(h)) {
      currentTemp = t;
      currentHumidity = h;
    }
    updateLCD();
  }

  if (now - lastFirebaseSync >= firebaseSyncInterval) {
    lastFirebaseSync = now;
    pushTelemetryToFirebase();
  }
}

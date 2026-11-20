/*
 * =====================================================================================
 * SMART UMBRELLA - ESP32 FIRMWARE WITH FIREBASE REALTIME DATABASE INTEGRATION
 * =====================================================================================
 * Hardware:
 * - ESP32-WROOM-DA
 * - DHT22 Temperature & Humidity Sensor (GPIO 4)
 * - FC-37 / YL-83 Rain Sensor (AO: GPIO 34, DO: GPIO 27)
 * - Active Buzzer (GPIO 25)
 * - RGB LED (Red: GPIO 18, Green: GPIO 14, Blue: GPIO 26)
 * - Push Button (GPIO 32)
 * - I2C LCD 16x2 (SDA: GPIO 21, SCL: GPIO 22)
 *
 * Firebase Schema matching the Android App:
 * /smartUmbrella/status:
 *   - deviceId: "umbrella_001"
 *   - temperature: float
 *   - humidity: float
 *   - rainDetected: boolean
 *   - rainAnalogValue: int
 *   - rainDigitalValue: int
 *   - buzzerOn: boolean
 *   - rgbStatus: string ("GREEN" / "RED" / "BLUE")
 *   - deviceOnline: boolean
 *   - lastUpdated: epoch milliseconds
 * =====================================================================================
 */

#include <WiFi.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <DHT.h>
#include <HTTPClient.h>

// ---------------- Wi-Fi & Firebase Credentials ----------------
// REPLACE WITH YOUR ACTUAL NETWORK AND FIREBASE DETAILS:
const char* WIFI_SSID       = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD   = "YOUR_WIFI_PASSWORD";
// e.g. "https://smart-umbrella-default-rtdb.firebaseio.com"
const char* FIREBASE_HOST   = "https://smart-umbrella-1e4e4-default-rtdb.asia-southeast1.firebasedatabase.app";
const char* FIREBASE_AUTH   = "YOUR_DATABASE_SECRET_OR_LEAVE_BLANK_IF_TEST_RULES";

// ---------------- Pin Definitions ----------------
#define PIN_DHT          4
#define DHTTYPE          DHT22

#define PIN_RAIN_DO      27
#define PIN_RAIN_AO      34

#define PIN_BUZZER       25

#define PIN_LED_RED      18
#define PIN_LED_GREEN    14
#define PIN_LED_BLUE     26

#define PIN_BUTTON       32

// ---------------- Rain Calibration ----------------
const int RAIN_TRIGGER_THRESHOLD = 3300; 
const int RAIN_CLEAR_THRESHOLD   = 3600;

LiquidCrystal_I2C lcd(0x27, 16, 2);
DHT dht(PIN_DHT, DHTTYPE);

float currentTemp = 0.0;
float currentHumidity = 0.0;
int rainRawValue = 4095;
int digitalRain = 1;
bool isRaining = false;
bool isAlertMuted = false;

unsigned long lastDHTReadTime = 0;
const unsigned long dhtInterval = 2000;

unsigned long lastRainCheckTime = 0;
const unsigned long rainCheckInterval = 40;

unsigned long lastFirebaseSync = 0;
const unsigned long firebaseSyncInterval = 3000; // Sync every 3 seconds

unsigned long lastBuzzerToggle = 0;
bool buzzerState = false;

int lastButtonReading = HIGH;
int buttonState = HIGH;
unsigned long lastDebounceTime = 0;
const unsigned long debounceDelay = 50;

String currentRgbStatus = "GREEN";

void setLEDColor(bool r, bool g, bool b) {
  digitalWrite(PIN_LED_RED,   r ? HIGH : LOW);
  digitalWrite(PIN_LED_GREEN, g ? HIGH : LOW);
  digitalWrite(PIN_LED_BLUE,  b ? HIGH : LOW);

  if (r && !g && !b) currentRgbStatus = "RED";
  else if (!r && g && !b) currentRgbStatus = "GREEN";
  else if (!r && !g && b) currentRgbStatus = "BLUE";
  else currentRgbStatus = "OFF";
}

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

  // Connect to Wi-Fi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20) {
    delay(500);
    Serial.print(".");
    attempts++;
  }

  lcd.clear();
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[WiFi] Connected! IP: " + WiFi.localIP().toString());
    lcd.setCursor(0, 0);
    lcd.print("WIFI CONNECTED");
    lcd.setCursor(0, 1);
    lcd.print("CLOUD SYNC READY");
  } else {
    Serial.println("\n[WiFi] Not connected. Running in offline standalone mode.");
    lcd.setCursor(0, 0);
    lcd.print("WIFI OFFLINE");
    lcd.setCursor(0, 1);
    lcd.print("STANDALONE MODE");
  }

  // Confirmation startup beep
  digitalWrite(PIN_BUZZER, HIGH);
  delay(120);
  digitalWrite(PIN_BUZZER, LOW);

  setLEDColor(false, true, false); // Green standby
  delay(1500);
  lcd.clear();
}

void loop() {
  unsigned long now = millis();

  // 1. PUSH BUTTON CHECK
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

  // 2. RAIN SENSOR HYSTERESIS
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
        setLEDColor(true, false, false); // RED
        if (!isAlertMuted) {
          digitalWrite(PIN_BUZZER, HIGH);
          buzzerState = true;
          lastBuzzerToggle = now;
        }
        Serial.println("\n*** RAIN EVENT! AO: " + String(rainRawValue) + " ***");
      } else {
        digitalWrite(PIN_BUZZER, LOW);
        buzzerState = false;
        setLEDColor(false, true, false); // GREEN
        isAlertMuted = false;
        Serial.println("\n*** DRY! AO: " + String(rainRawValue) + " ***");
      }
      updateLCD();
      // Push event immediately to Firebase on change
      pushTelemetryToFirebase();
    }
  }

  // 3. BUZZER CADENCE
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

  // 4. PERIODIC DHT22 TELEMETRY (Every 2s)
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

  // 5. PERIODIC CLOUD SYNC TO FIREBASE REALTIME DATABASE (Every 3s)
  if (now - lastFirebaseSync >= firebaseSyncInterval) {
    lastFirebaseSync = now;
    pushTelemetryToFirebase();
  }
}

void pushTelemetryToFirebase() {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  String url = String(FIREBASE_HOST) + "/smartUmbrella/status.json";
  http.begin(url);
  http.addHeader("Content-Type", "application/json");

  // Construct JSON Payload matching Android UmbrellaStatus data model:
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
  // Milliseconds epoch timestamp (approximate or synced)
  jsonPayload += "\"lastUpdated\":" + String(1795000000000ULL + millis());
  jsonPayload += "}";

  int httpCode = http.PUT(jsonPayload);
  if (httpCode > 0) {
    // 200 OK
    Serial.println("[Firebase] Telemetry pushed successfully: " + String(httpCode));
  } else {
    Serial.println("[Firebase] HTTP error: " + http.errorToString(httpCode));
  }
  http.end();
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


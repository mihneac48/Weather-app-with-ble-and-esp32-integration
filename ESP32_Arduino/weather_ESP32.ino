#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

const char* ssid = "HM2.4";
const char* password = "tintin12";

String oras_primit = "";

BLECharacteristic* rxCharacteristic;
BLECharacteristic* txCharacteristic;
bool deviceConnected = false;
unsigned long previous_ms = 0;
const int interval = 500;

#define SERVICE_UUID "01fad59e-bb28-4953-a6d7-43a842bb0f83"
#define CHARACTERISTIC_RX "9311449e-e41b-4a28-a584-eed29d9ddc63"
#define CHARACTERISTIC_TX "84eb6680-dac7-4444-9ab3-e0159e51d1a7"
#define LED 2

class MyCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pCharacteristic) override {
    oras_primit = String(pCharacteristic->getValue().c_str());
    if (oras_primit.length() > 0) {
      Serial.println("Oraș primit: " + oras_primit);
    }
  }
};

class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {
    deviceConnected = true;
  }
  void onDisconnect(BLEServer* pServer) override {
    deviceConnected = false;
  }
};

void trimiteEroareBLE(const String& mesaj) {
  Serial.println(mesaj);
  if (deviceConnected && txCharacteristic) {
    txCharacteristic->setValue(mesaj.c_str());
    txCharacteristic->notify();
  }
}

void conectareWiFi() {
  if (WiFi.status() == WL_CONNECTED) return;

  Serial.print("Conectare la WiFi");
  WiFi.begin(ssid, password);
  unsigned long startAttemptTime = millis();

  while (WiFi.status() != WL_CONNECTED && millis() - startAttemptTime < 10000) {
    delay(500);
    Serial.print(".");
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nConectat la WiFi!");
  } else {
    Serial.println("\nEroare la conectare WiFi.");
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(LED, OUTPUT);
  digitalWrite(LED, LOW);

  conectareWiFi();

  BLEDevice::init("ESP32-Vreme");
  BLEServer* pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService* pService = pServer->createService(SERVICE_UUID);

  rxCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_RX,
    BLECharacteristic::PROPERTY_WRITE);
  rxCharacteristic->setCallbacks(new MyCallbacks());

  txCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_TX,
    BLECharacteristic::PROPERTY_NOTIFY);
  txCharacteristic->addDescriptor(new BLE2902());

  pService->start();
  pServer->getAdvertising()->start();

  Serial.println("BLE pornit. Aștept conexiune...");
}

void loop() {
  unsigned long current_ms = millis();

  if (deviceConnected) {
    digitalWrite(LED, HIGH);
  } else {
    if (current_ms - previous_ms >= interval) {
      previous_ms = current_ms;
      digitalWrite(LED, !digitalRead(LED));
    }
  }

  if (WiFi.status() != WL_CONNECTED) {
    conectareWiFi();
  }

  if (oras_primit != "") {
    Serial.println("Cerere geocoding pentru oras: " + oras_primit);

    HTTPClient httpGeo;
    String urlGeo = "http://geocoding-api.open-meteo.com/v1/search?name=" + oras_primit + "&count=1";
    httpGeo.begin(urlGeo);
    httpGeo.setTimeout(10000);
    int httpCode = httpGeo.GET();

    if (httpCode > 0) {
      String payload = httpGeo.getString();

      DynamicJsonDocument docGeo(512);
      StaticJsonDocument<128> geoFilter;
      geoFilter["results"][0]["latitude"] = true;
      geoFilter["results"][0]["longitude"] = true;

      DeserializationError error = deserializeJson(docGeo, payload, DeserializationOption::Filter(geoFilter));

      if (!error && docGeo.containsKey("results") && docGeo["results"].size() > 0) {
        float lat = docGeo["results"][0]["latitude"];
        float lon = docGeo["results"][0]["longitude"];
        Serial.printf("Coordonate găsite: lat=%.6f, lon=%.6f\n", lat, lon);
        httpGeo.end();

        HTTPClient httpWeather;
        String urlWeather = "http://api.open-meteo.com/v1/forecast?latitude=" + String(lat, 6) + "&longitude=" + String(lon, 6) + "&current_weather=true";
        httpWeather.begin(urlWeather);
        httpWeather.setTimeout(10000);
        int httpCodeWeather = httpWeather.GET();

        if (httpCodeWeather > 0) {
          String weatherPayload = httpWeather.getString();

          DynamicJsonDocument docWeather(512);
          StaticJsonDocument<128> weatherFilter;
          weatherFilter["current_weather"]["temperature"] = true;
          weatherFilter["current_weather"]["windspeed"] = true;
          weatherFilter["current_weather"]["weathercode"] = true;

          DeserializationError errWeather = deserializeJson(docWeather, weatherPayload, DeserializationOption::Filter(weatherFilter));
          if (!errWeather && docWeather.containsKey("current_weather")) {
            float temp = docWeather["current_weather"]["temperature"];
            float wind = docWeather["current_weather"]["windspeed"];
            int code = docWeather["current_weather"]["weathercode"];

            String mesaj = "{\"t\":" + String(temp, 1) + ",\"v\":" + String(wind, 1) + "}";
            Serial.println("Trimitem catre telefon: " + mesaj);

            if (deviceConnected && txCharacteristic) {
              txCharacteristic->setValue(mesaj.c_str());
              txCharacteristic->notify();
            }
          } else {
            trimiteEroareBLE("Eroare parsare vreme");
          }
        } else {
          trimiteEroareBLE("Eroare HTTP vreme: " + String(httpCodeWeather));
        }
        httpWeather.end();

      } else {
        trimiteEroareBLE("Eroare: oraș negăsit sau eroare la parsare coordonate.");
        httpGeo.end();
      }
    } else {
      trimiteEroareBLE("Eroare HTTP geocoding: " + String(httpCode));
      httpGeo.end();
    }

    oras_primit = "";
  }

  delay(100);
}

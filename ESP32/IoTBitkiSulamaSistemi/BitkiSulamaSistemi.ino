#include <WiFi.h>
#include <WebServer.h>
#include <time.h>
#include <sys/time.h> // Saat ayarı için gerekli

// --- CONFIG ---
const char* apSSID = "ESP32_PUMP_AP";
const char* apPassword = "12345678";

const int relayPin = 33;
const int moisturePin = 34; // ADC1 kanalında olmalı (WiFi ile ADC2 çakışabilir)
const int adcMax = 4095;
// ----------------

WebServer server(80);

// ---- DURUMLAR ----
bool pumpState = false;
bool weeklyMode = false;

// ---- HAFTALIK PROGRAM ----
struct Schedule {
  int startH;
  int startM;
  int endH;
  int endM;
  bool active;
};

Schedule week[7]; // 0=Pzt ... 6=Paz (Java'da Pzt=0 yollamıştık)

// ---------- HTTP HANDLERLAR ----------

void handleRoot() {
  server.send(200, "text/html", "<h1>ESP32 Akilli Sulama Sistemi</h1>");
}

void handlePumpOn() {
  weeklyMode = false; // Manuel açınca otomatikten çıkar
  digitalWrite(relayPin, HIGH); // Rölen aktif YÜKSEK (High) tetikliyse
  pumpState = true;
  server.send(200, "text/plain", "OK");
}

void handlePumpOff() {
  weeklyMode = false;
  digitalWrite(relayPin, LOW);
  pumpState = false;
  server.send(200, "text/plain", "OK");
}

void handleWeeklyMode() {
  if (server.hasArg("state")) {
    weeklyMode = server.arg("state") == "1";
    Serial.println(weeklyMode ? "Haftalik Mod AKTIF" : "Haftalik Mod PASIF");
  }
  server.send(200, "text/plain", "OK");
}

// Android -> Günlük saat programı gönderir
void handleSchedule() {
  if (!server.hasArg("day") || !server.hasArg("start") || !server.hasArg("end")) {
    server.send(400, "text/plain", "Eksik parametre");
    return;
  }

  int day = server.arg("day").toInt(); // 1-7 arası gelir (Java koduna göre)
  String start = server.arg("start");
  String end = server.arg("end");

  // Dizi indeksi 0-6 arası olduğu için 1 eksiltiyoruz
  int idx = day - 1;
  if (idx < 0 || idx > 6) {
    server.send(400, "text/plain", "Gecersiz Gun");
    return;
  }

  Schedule &s = week[idx];
  s.startH = start.substring(0, 2).toInt();
  s.startM = start.substring(3, 5).toInt();
  s.endH   = end.substring(0, 2).toInt();
  s.endM   = end.substring(3, 5).toInt();
  s.active = true;

  Serial.printf("Program Ayarlandi: Gun %d => %02d:%02d - %02d:%02d\n",
                day, s.startH, s.startM, s.endH, s.endM);

  server.send(200, "text/plain", "OK");
}

// 🔴 YENİ: Telefondan saati alıp ESP'ye kaydeder
// Kullanım: http://192.168.4.1/setTime?timestamp=1700000000
void handleSetTime() {
  if (server.hasArg("timestamp")) {
    long ts = server.arg("timestamp").toInt();
    
    struct timeval tv;
    tv.tv_sec = ts;  // Telefondan gelen epoch zamanı
    tv.tv_usec = 0;
    settimeofday(&tv, NULL); // ESP32 saatini güncelle

    // GMT+3 ayarı (Türkiye)
    setenv("TZ", "GMT-3", 1);
    tzset();

    Serial.printf("Saat Guncellendi: %ld\n", ts);
    server.send(200, "text/plain", "OK");
  } else {
    server.send(400, "text/plain", "Timestamp yok");
  }
}

void handleStatus() {
  int raw = analogRead(moisturePin);
  
  // Nem Sensörü Mantığı:
  // Genelde: Kuru = 4095 (Max), Islak = 0 (Min)
  // Bu yüzden ters haritalama yapıyoruz:
  int pct = map(raw, 4095, 0, 0, 100); 
  pct = constrain(pct, 0, 100);

  String json = "{";
  json += "\"pump\":\"" + String(pumpState ? "ON" : "OFF") + "\",";
  json += "\"weekly\":\"" + String(weeklyMode ? "ON" : "OFF") + "\",";
  json += "\"moisture\":" + String(pct);
  json += "}";

  server.send(200, "application/json", json);
}

// ---------- SETUP ----------

void setup() {
  Serial.begin(115200);

  pinMode(relayPin, OUTPUT);
  digitalWrite(relayPin, LOW);
  pinMode(moisturePin, INPUT);

  // Haftalık diziyi temizle
  for (int i = 0; i < 7; i++) {
    week[i].active = false;
  }

  // Access Point Başlat
  WiFi.softAP(apSSID, apPassword);
  IPAddress IP = WiFi.softAPIP();
  Serial.print("AP Acildi. IP Adresi: ");
  Serial.println(IP);

  server.on("/", handleRoot);
  server.on("/pump/on", handlePumpOn);
  server.on("/pump/off", handlePumpOff);
  server.on("/weekly", handleWeeklyMode);
  server.on("/schedule", handleSchedule);
  server.on("/status", handleStatus);
  
  // Yeni saat ayar endpointi
  server.on("/setTime", handleSetTime);

  server.begin();
  Serial.println("HTTP Server basladi");
}

// ---------- LOOP ----------

void loop() {
  server.handleClient();

  // Otomatik mod kapalıysa döngüden çık
  if (!weeklyMode) return;

  struct tm timeinfo;
  // Eğer saat ayarlanmamışsa (1970 yılındaysa) programı çalıştırma
  if (!getLocalTime(&timeinfo)) return;
  if (timeinfo.tm_year < 100) return; // 2000 yılından önceyse çalıştırma

  // tm_wday: 0=Pazar, 1=Pazartesi ... 6=Cumartesi
  // Bizim dizimiz: 0=Pazartesi ... 6=Pazar
  int today = timeinfo.tm_wday; 
  int idx = (today == 0) ? 6 : (today - 1); // Pazarı sona at, 1 geri kaydır

  Schedule s = week[idx];
  
  // O gün için program yoksa çık
  if (!s.active) return;

  int h = timeinfo.tm_hour;
  int m = timeinfo.tm_min;

  // Şu anki saat, başlangıç ve bitiş arasında mı?
  // Basit mantık: (Saat > Başlangıç) VE (Saat < Bitiş)
  // Dakika hassasiyeti için dakikaya çevirip kıyaslamak en garantisidir:
  int currentMin = h * 60 + m;
  int startMin = s.startH * 60 + s.startM;
  int endMin = s.endH * 60 + s.endM;

  bool inRange = (currentMin >= startMin) && (currentMin < endMin);

  if (inRange && !pumpState) {
    digitalWrite(relayPin, HIGH);
    pumpState = true;
    Serial.println("Otomatik sulama BASLADI");
  } 
  else if (!inRange && pumpState) {
    digitalWrite(relayPin, LOW);
    pumpState = false;
    Serial.println("Otomatik sulama BITTI");
  }
}

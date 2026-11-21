# 🌱 IoT Tabanlı Akıllı Bitki Sulama Sistemi

ESP32 mikrodenetleyicisi ve özel geliştirilmiş yerel Android uygulaması (Java) ile çalışan, toprak nemini anlık takip eden ve haftalık zamanlama desteği sunan akıllı bitki sulama sistemi.

Sistem, harici bir internet bağlantısına ihtiyaç duymadan **ESP32 Access Point (AP)** modu üzerinden yerel ağ kurarak mobil uygulama ile doğrudan haberleşir.

---

## 🚀 Özellikler

* **Anlık Toprak Nemi Takibi:** Kapasitif toprak nem sensöründen alınan analog (ADC) veriler yüzdeye dönüştürülerek mobil uygulamada gerçek zamanlı (`1 sn` aralıklarla) görüntülenir.
* **Manuel Pompa Kontrolü:** Android uygulaması üzerinden su pompası tek tuşla uzaktan açılıp kapatılabilir.
* **Haftalık Otomatik Sulama Programı:** Haftanın 7 günü (Pazartesi–Pazar) için ayrı başlangıç ve bitiş saatleri tanımlanabilir.
* **Otomatik Saat Senkronizasyonu:** Harici bir RTC modülüne gerek kalmadan, mobil uygulama açıldığında telefonun sistem saati (Unix Timestamp) ESP32'ye gönderilerek dahili saat güncellenir.
* **Bağımsız Yerel Ağ (Access Point):** ESP32 kendi Wi-Fi ağını (`ESP32_PUMP_AP`) oluşturur; kurulum ve kullanım için internet erişimi gerekmez.

---

## 📂 Proje Dizin Yapısı

```text
IoT Tabanlı Akıllı Bitki Sulama Projesi/
├── Android-App/
│   └── IoTBitkiSulamaUygulama/       # Android Studio (Java) mobil uygulama kaynak kodları
├── ESP32/
│   └── IoTBitkiSulamaSistemi/
│       └── BitkiSulamaSistemi.ino    # ESP32 gömülü yazılım ve HTTP sunucu kodları
└── Docs/
    └── Bağlantı Tablosu.pdf          # Donanım pin bağlantıları ve güç dağıtım şeması
```

---

## 🛠️ Kullanılan Teknolojiler ve Donanımlar

### Donanım Bileşenleri
* **Mikrodenetleyici:** ESP32 Geliştirme Kartı
* **Sensör:** Kapasitif Toprak Nem Sensörü
* **Aktüatör & Kontrol:** 5V Röle Modülü ve Mini Su Pompası
* **Güç Yönetimi:** 12V Adaptör, Sigorta ve LM2596 Voltaj Düşürücü (Step-Down) Modül

### Yazılım
* **Gömülü Sistem:** C++ / Arduino IDE (`WiFi.h`, `WebServer.h`, `time.h`)
* **Mobil Uygulama:** Java, Android SDK (Min SDK 24 / Target SDK 36), Material Design bileşenleri

---

## 🔌 Donanım Bağlantı Şeması

Güç dağıtımı için **LM2596** modülü üzerinden 12V giriş gerilimi **5V** seviyesine düşürülerek ortak `5V BUS` ve `GND BUS` hatları oluşturulmuştur.

| Bileşen | Pin | Bağlandığı Yer |
| :--- | :--- | :--- |
| **Güç Girişi (12V)** | `+` / `–` | Sigorta üzerinden `LM2596 IN+` / `LM2596 IN–` |
| **LM2596 Çıkış** | `OUT+` (5V) | ESP32 `VIN`, Röle `VCC`, Pompa `+` |
| **LM2596 Çıkış** | `OUT–` (GND) | Ortak `GND BUS` hattı |
| **Toprak Nem Sensörü** | `VCC` / `GND` / `SIG` | ESP32 `3.3V` / Ortak `GND` / **GPIO 34** *(ADC1)* |
| **Röle Modülü** | `VCC` / `GND` / `IN` | `5V BUS` / Ortak `GND` / **GPIO 33** *(Kodda tanımlı)* |
| **Su Pompası** | `+` / `–` | `5V BUS` / Röle `COM` *(Röle `NO` ucu Ortak `GND`'ye bağlı)* |

> **Not:** `Docs/Bağlantı Tablosu.pdf` dosyasında röle tetik pini `GPIO23` olarak belirtilmiş olup, güncel `.ino` kaynak kodunda `relayPin = 33` olarak yapılandırılmıştır. Devre kurulumunda kod ile pin eşleşmesine dikkat ediniz.

---

## 🌐 ESP32 HTTP API Uç Noktaları

ESP32 varsayılan olarak **`192.168.4.1`** IP adresi ve **80** portu üzerinden aşağıdaki REST benzeri uç noktaları sunar:

| Metod | Endpoint | Açıklama |
| :--- | :--- | :--- |
| `GET` | `/` | Sistem başlık sayfasını döndürür. |
| `GET` | `/status` | Pompa durumu, haftalık mod durumu ve nem yüzdesini JSON formatında döner (`{"pump":"OFF","weekly":"OFF","moisture":45}`). |
| `GET` | `/pump/on` | Haftalık modu devre dışı bırakır ve pompayı manuel olarak açar. |
| `GET` | `/pump/off` | Haftalık modu devre dışı bırakır ve pompayı kapatır. |
| `GET` | `/weekly?state=1\|0` | Haftalık otomatik sulama modunu aktif (`1`) veya pasif (`0`) yapar. |
| `GET` | `/schedule?day=1..7&start=HH:MM&end=HH:MM` | Belirtilen gün (`1`: Pazartesi – `7`: Pazar) için sulama zaman aralığı tanımlar. |
| `GET` | `/setTime?timestamp=<epoch>` | Cihazın sistem saatini Unix Timestamp değerine göre ayarlar (`GMT+3`). |

---

## ⚙️ Kurulum ve Çalıştırma

### 1. ESP32 Yazılımının Yüklenmesi
1. `ESP32/IoTBitkiSulamaSistemi/BitkiSulamaSistemi.ino` dosyasını **Arduino IDE** ile açın.
2. Kart yöneticisinden **ESP32** kartını ve uygun COM portunu seçin.
3. Gerekirse kod içerisindeki Wi-Fi AP bilgilerini düzenleyin:
   ```cpp
   const char* apSSID = "ESP32_PUMP_AP";
   const char* apPassword = "12345678";
   ```
4. Kodu karta yükleyin.

### 2. Android Uygulamasının Kurulması
1. `Android-App/IoTBitkiSulamaUygulama` klasörünü **Android Studio** ile açın.
2. Gradle senkronizasyonunun tamamlanmasını bekleyin ve uygulamayı Android cihazınıza derleyip yükleyin.
3. Telefonunuzun Wi-Fi ayarlarından **`ESP32_PUMP_AP`** ağına (`12345678` şifresi ile) bağlanın.
4. Uygulamayı açtığınızda saat otomatik eşitlenecek ve sensör verileri ekrana gelecektir.

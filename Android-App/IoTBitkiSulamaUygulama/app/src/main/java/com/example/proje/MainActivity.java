package com.example.proje;

import androidx.appcompat.app.AppCompatActivity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    MaterialButton btnOn, btnOff;
    TextView txtPumpStatusBox, txtSensorValueBox, txtSoilPercent;
    ProgressBar progressBarSoil;
    SwitchMaterial switchWeekly;

    // ESP32 Access Point varsayılan IP adresi
    String espIP = "http://192.168.4.1";

    String[] start = new String[7];
    String[] end   = new String[7];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Arayüz elemanlarını tanımla
        btnOn = findViewById(R.id.btnOn);
        btnOff = findViewById(R.id.btnOff);
        txtPumpStatusBox = findViewById(R.id.txtPumpStatusBox);
        txtSensorValueBox = findViewById(R.id.txtSensorValueBox);
        txtSoilPercent = findViewById(R.id.txtSoilPercent);
        progressBarSoil = findViewById(R.id.progressBarSoil);
        switchWeekly = findViewById(R.id.switchWeekly);

        // Başlangıç değerleri (Varsayılan 09:00 - 10:00)
        for (int i = 0; i < 7; i++) {
            start[i] = "09:00";
            end[i]   = "10:00";
        }

        // --- YENİ EKLENEN KISIM: SAAT SENKRONİZASYONU ---
        // Uygulama açıldığında telefonun saatini ESP'ye gönderir.
        syncTime();
        // ------------------------------------------------

        // Buton Tıklamaları
        btnOn.setOnClickListener(v -> sendCommand(espIP + "/pump/on"));
        btnOff.setOnClickListener(v -> sendCommand(espIP + "/pump/off"));

        // Haftalık Mod Switch
        switchWeekly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) { // Kullanıcı eliyle değiştirdiyse işlem yap
                if (isChecked) {
                    showScheduleDialog();
                } else {
                    sendCommand(espIP + "/weekly?state=0");
                }
            }
        });

        // Sensör verilerini sürekli çekme döngüsü
        new Thread(() -> {
            while (!isFinishing()) {
                fetchStatus();
                try {
                    Thread.sleep(1000); // 1 saniyede bir veri çek
                } catch (Exception ignored) {}
            }
        }).start();
    }

    // ----------------------------------------------------------
    // YENİ FONKSİYON: Telefonun saatini ESP32'ye gönderir
    // ----------------------------------------------------------
    void syncTime() {
        new Thread(() -> {
            try {
                // Şu anki zamanı saniye cinsinden al (Unix Timestamp)
                long timestamp = System.currentTimeMillis() / 1000;

                String urlStr = espIP + "/setTime?timestamp=" + timestamp;
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(2000); // 2 saniye bekle, bağlanmazsa geç
                conn.getResponseCode();
                conn.disconnect();

                runOnUiThread(() -> Toast.makeText(this, "Saat Eşitlendi", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                // Hata olursa kullanıcıyı yorma, sessizce geç
                e.printStackTrace();
            }
        }).start();
    }

    // ---------- DIALOG (Zamanlayıcı Penceresi) ----------
    void showScheduleDialog() {
        Dialog d = new Dialog(this);
        d.setContentView(R.layout.dialog_schedule);

        // Dialog penceresini ekrana tam oturtmak için (Opsiyonel ama önerilir)
        if (d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        int[] sBtn = {R.id.monStart, R.id.tueStart, R.id.wedStart, R.id.thuStart,
                R.id.friStart, R.id.satStart, R.id.sunStart};
        int[] eBtn = {R.id.monEnd, R.id.tueEnd, R.id.wedEnd, R.id.thuEnd,
                R.id.friEnd, R.id.satEnd, R.id.sunEnd};

        for (int i = 0; i < 7; i++) {
            int day = i;
            // XML'de bunları Button yapmıştık, o yüzden Button olarak çekiyoruz
            Button bs = d.findViewById(sBtn[i]);
            Button be = d.findViewById(eBtn[i]);

            bs.setText(start[i]);
            be.setText(end[i]);

            bs.setOnClickListener(v -> pickTime(day, true, bs));
            be.setOnClickListener(v -> pickTime(day, false, be));
        }

        d.findViewById(R.id.btnSave).setOnClickListener(v -> {
            sendAllSchedules(); // Tüm saatleri ESP'ye yolla
            sendCommand(espIP + "/weekly?state=1"); // Otomatik modu aktif et
            d.dismiss();
            Toast.makeText(this, "Program Kaydedildi", Toast.LENGTH_SHORT).show();
        });

        d.setOnCancelListener(dialog -> {
            // İptal edilirse switch'i kapat
            switchWeekly.setChecked(false);
        });

        d.show();
    }

    void pickTime(int day, boolean isStart, Button btn) {
        // Mevcut saati butonun üzerindeki metinden alıp TimePicker'ı o saatte açalım
        String[] parts = btn.getText().toString().split(":");
        int currentHour = Integer.parseInt(parts[0]);
        int currentMin = Integer.parseInt(parts[1]);

        new TimePickerDialog(this, (view, h, m) -> {
            String t = String.format("%02d:%02d", h, m);
            if (isStart) start[day] = t;
            else end[day] = t;
            btn.setText(t);
        }, currentHour, currentMin, true).show();
    }

    void sendAllSchedules() {
        new Thread(() -> {
            try {
                for (int i = 0; i < 7; i++) {
                    // Java'da döngü 0-6 arası, ESP kodu günleri 1-7 (Pzt-Paz) bekliyor
                    String url = espIP + "/schedule?day=" + (i + 1)
                            + "&start=" + start[i]
                            + "&end=" + end[i];
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.getResponseCode();
                    c.disconnect();
                    Thread.sleep(100); // ESP'yi boğmamak için minik bir bekleme
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ---------- ESP İLETİŞİM (Basit Komutlar) ----------
    void sendCommand(String urlStr) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(3000);
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    // ---------- DURUM GÜNCELLEME (Sensör Verisi) ----------
    void fetchStatus() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(espIP + "/status").openConnection();
            conn.setConnectTimeout(2000); // Bağlantı koparsa uzun süre beklemesin

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            runOnUiThread(() -> updateUI(sb.toString()));

        } catch (Exception e) {
            runOnUiThread(() -> {
                txtPumpStatusBox.setText("BAĞLANTI YOK");
                txtPumpStatusBox.setBackgroundColor(Color.GRAY);
                txtSensorValueBox.setText("--");
            });
        }
    }

    void updateUI(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);

            String pump = json.getString("pump");
            if (pump.equals("ON")) {
                txtPumpStatusBox.setText("AÇIK");
                // Mavi arka plan (drawable klasöründe rounded_background_blue varsa onu kullan)
                // Yoksa geçici olarak renk kodu kullanabilirsin: setBackgroundColor(Color.BLUE)
                txtPumpStatusBox.setBackgroundResource(R.drawable.rounded_background_blue);
            } else {
                txtPumpStatusBox.setText("KAPALI");
                txtPumpStatusBox.setBackgroundResource(R.drawable.rounded_background_red);
            }

            // Haftalık mod durumuna göre switch'i güncelle
            // (Kullanıcı manuel açıp kapattığında switch'in senkron kalması için)
            String weekly = json.optString("weekly", "OFF");
            if (!switchWeekly.isPressed()) { // Sadece kullanıcı basmıyorken güncelle
                switchWeekly.setChecked(weekly.equals("ON"));
            }

            int moisture = json.getInt("moisture");
            progressBarSoil.setProgress(moisture);
            txtSoilPercent.setText("%" + moisture);
            txtSensorValueBox.setText(String.valueOf(moisture));

        } catch (Exception ignored) {}
    }
}

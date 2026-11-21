package com.example.proje;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper; // Bunu eklemeyi unutma
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Üstteki barı gizle (Tam ekran olsun)
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Handler içine "Looper.getMainLooper()" ekledik.
        // Bu, işlemin ana ekranda (UI Thread) yapılmasını garanti eder.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // Geri tuşuna basınca tekrar splash gelmesin diye activity'i öldür
        }, 2500);
    }
}
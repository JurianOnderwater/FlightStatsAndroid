package com.example.flightstats;

import android.app.Application;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

public class FlightStatsApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Apply Material You dynamic colors (wallpaper theming) across the entire app
        DynamicColors.applyToActivitiesIfAvailable(this);

        // Apply night mode preference on app start
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int themeMode = prefs.getInt("theme_mode", 2); // 0 = Light, 1 = Dark, 2 = System
        if (themeMode == 0) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (themeMode == 1) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}

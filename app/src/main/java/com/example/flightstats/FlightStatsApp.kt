package com.example.flightstats

import android.app.Application
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import android.preference.PreferenceManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FlightStatsApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Apply night mode preference on app start
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val themeMode = prefs.getInt("theme_mode", 2) // 0 = Light, 1 = Dark, 2 = System
        when (themeMode) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}

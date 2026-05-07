package com.example.flightstats;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class FlightStatsApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Apply Material You dynamic colors (wallpaper theming) across the entire app
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}

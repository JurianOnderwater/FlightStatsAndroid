package com.example.flightstats.di

import android.content.Context
import com.example.flightstats.data.AirportDao
import com.example.flightstats.data.AppDatabase
import com.example.flightstats.data.FlightDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideFlightDao(database: AppDatabase): FlightDao {
        return database.flightDao()
    }

    @Provides
    fun provideAirportDao(database: AppDatabase): AirportDao {
        return database.airportDao()
    }
}

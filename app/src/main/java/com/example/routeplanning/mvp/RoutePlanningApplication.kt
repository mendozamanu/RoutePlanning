package com.example.routeplanning.mvp

import android.app.Application
import com.example.routeplanning.BuildConfig
import com.google.android.libraries.places.api.Places
import java.util.Locale

class RoutePlanningApplication : Application() {
    val dependencies: MvpDependencies by lazy { MvpDependencies(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        val apiKey = BuildConfig.GOOGLE_MAPS_API
        if (apiKey.isNotBlank() && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(this, apiKey, Locale("es", "ES"))
        }
    }
}

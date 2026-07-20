package com.example.routeplanning.mvp

import android.content.Context
import com.example.routeplanning.BuildConfig
import com.example.routeplanning.mvp.data.cloud.FirebaseSavedCommuteDataSource
import com.example.routeplanning.mvp.data.cloud.SyncingSavedCommuteRepository
import com.example.routeplanning.mvp.data.location.AndroidCurrentLocationProvider
import com.example.routeplanning.mvp.data.local.RoutePlanningDatabase
import com.example.routeplanning.mvp.data.local.RoomSavedCommuteRepository
import com.example.routeplanning.mvp.data.remote.HttpJourneyRepository
import com.example.routeplanning.mvp.data.remote.HttpStopRepository
import com.example.routeplanning.mvp.domain.JourneyRepository
import com.example.routeplanning.mvp.domain.CurrentLocationProvider
import com.example.routeplanning.mvp.domain.SavedCommuteRepository
import com.example.routeplanning.mvp.domain.StopRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

class MvpDependencies(
    context: Context
) {
    private val database by lazy { RoutePlanningDatabase.create(context) }

    val savedCommuteRepository: SavedCommuteRepository by lazy {
        val local = RoomSavedCommuteRepository(database.savedCommuteDao())
        if (BuildConfig.FIREBASE_SYNC_ENABLED.toBoolean()) {
            SyncingSavedCommuteRepository(
                local = local,
                cloud = FirebaseSavedCommuteDataSource(
                    auth = Firebase.auth,
                    firestore = Firebase.firestore
                )
            )
        } else {
            local
        }
    }

    val journeyRepository: JourneyRepository by lazy {
        HttpJourneyRepository.create(
            baseUrl = BuildConfig.API_BASE_URL,
            allowCleartext = BuildConfig.DEBUG
        )
    }

    val stopRepository: StopRepository by lazy {
        HttpStopRepository.create(
            baseUrl = BuildConfig.API_BASE_URL,
            allowCleartext = BuildConfig.DEBUG
        )
    }

    val currentLocationProvider: CurrentLocationProvider by lazy {
        AndroidCurrentLocationProvider(context.applicationContext)
    }
}

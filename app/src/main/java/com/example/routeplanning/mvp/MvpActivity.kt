package com.example.routeplanning.mvp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.routeplanning.MainActivity
import com.example.routeplanning.mvp.ui.RoutePlanningApp
import com.example.routeplanning.mvp.ui.theme.RoutePlanningTheme

class MvpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dependencies = (application as RoutePlanningApplication).dependencies
        setContent {
            RoutePlanningTheme {
                RoutePlanningApp(
                    repository = dependencies.savedCommuteRepository,
                    journeyRepository = dependencies.journeyRepository,
                    currentLocationProvider = dependencies.currentLocationProvider,
                    onOpenLegacy = {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                )
            }
        }
    }
}

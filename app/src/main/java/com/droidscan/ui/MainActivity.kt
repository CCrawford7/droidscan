package com.droidscan.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.droidscan.DroidscanApp
import com.droidscan.shared.core.MetricSnapshot
import kotlinx.coroutines.flow.Flow

class MainActivity : ComponentActivity() {

    private lateinit var telemetryFlow: Flow<MetricSnapshot>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        telemetryFlow = DroidscanApp.instance.telemetryCollector.observeAll()

        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                DashboardScreen(telemetryFlow = telemetryFlow)
            }
        }
    }
}

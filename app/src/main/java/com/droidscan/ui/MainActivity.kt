package com.droidscan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.droidscan.DroidscanApp
import com.droidscan.shared.core.MetricSnapshot
import kotlinx.coroutines.flow.Flow

class MainActivity : ComponentActivity() {

    private lateinit var telemetryFlow: Flow<MetricSnapshot>

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permission results handled — re-run telemetry
        telemetryFlow = DroidscanApp.instance.telemetryCollector.observeAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()

        telemetryFlow = DroidscanApp.instance.telemetryCollector.observeAll()

        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                DashboardScreen(telemetryFlow = telemetryFlow)
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        }
    }
}

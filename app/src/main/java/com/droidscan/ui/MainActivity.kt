package com.droidscan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.droidscan.DroidscanApp
import com.droidscan.shared.core.MetricSnapshot
import com.droidscan.ui.theme.DroidscanTheme
import kotlinx.coroutines.flow.Flow

class MainActivity : ComponentActivity() {

    private lateinit var telemetryFlow: Flow<MetricSnapshot>

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        Log.i("DroidscanPerms", "Permission results: $grantResults")
        telemetryFlow = DroidscanApp.instance.telemetryCollector.observeAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.i("DroidscanPerms", "onCreate called, requesting permissions")
        requestRequiredPermissions()
        telemetryFlow = DroidscanApp.instance.telemetryCollector.observeAll()

        setContent {
            DroidscanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DashboardScreen(telemetryFlow = telemetryFlow)
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 33) {
            // API 33+: allCellInfo needs NEARBY_WIFI_DEVICES
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            // API 33+: allCellInfo needs location-level access — Android 16 in
            // practice requires FINE, not just coarse, or cell info stays empty.
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                // request coarse alongside so the "while in use" dialog offers both
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            // API 34+: need READ_SIM_STATE for subscription info (not a compile constant in SDK 35)
            if (Build.VERSION.SDK_INT >= 34) {
                val readSimState = "android.permission.READ_SIM_STATE"
                if (ContextCompat.checkSelfPermission(this, readSimState)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    permissions.add(readSimState)
                } else {
                    Log.i("DroidscanPerms", "READ_SIM_STATE already granted")
                }
            }
        } else {
            // API 32 and below: request READ_PHONE_STATE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_PHONE_STATE)
            }
        }
        if (permissions.isNotEmpty()) {
            Log.i("DroidscanPerms", "Requesting permissions: $permissions")
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        } else {
            Log.i("DroidscanPerms", "All permissions already granted")
        }
    }
}

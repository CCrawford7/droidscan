package com.droidscan.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.droidscan.DroidscanApp
import com.droidscan.TelemetryService
import com.droidscan.shared.core.MetricSnapshot
import com.droidscan.ui.theme.DroidscanTheme
import kotlinx.coroutines.flow.Flow

class MainActivity : ComponentActivity() {

    private lateinit var telemetryFlow: Flow<MetricSnapshot>

    private val specs = droidscanPermissionSpecs()

    /** Permissions currently held, as Compose state so the gate recomposes. */
    private var granted by mutableStateOf<Set<String>>(emptySet())
    private var rationaleDismissed by mutableStateOf(false)
    private var telemetryStarted = false

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        Log.i("DroidscanPerms", "Permission results: $grantResults")
        refreshGranted()
        // Once the run permission set is settled, kick the foreground service so
        // collection survives backgrounding.
        startTelemetryIfSettled()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Single shared telemetry flow (owned by DroidscanApp) — the service and
        // every activity consume the same liveSnapshot, so there's one collector.
        telemetryFlow = DroidscanApp.instance.liveSnapshot
        refreshGranted()
        startTelemetryIfSettled()

        setContent {
            DroidscanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Nothing is requested until the user has seen why. The old
                    // flow fired bare system dialogs from onCreate, before any
                    // UI existed to explain them.
                    if (!allGranted() && !rationaleDismissed) {
                        PermissionsScreen(
                            specs = specs,
                            granted = granted,
                            onGrant = {
                                val missing = specs.missingPermissions(granted)
                                if (missing.isEmpty()) {
                                    rationaleDismissed = true
                                } else {
                                    Log.i("DroidscanPerms", "Requesting: $missing")
                                    requestPermissionsLauncher.launch(missing.toTypedArray())
                                }
                            },
                            onSkip = {
                                Log.i("DroidscanPerms", "Rationale skipped by user")
                                rationaleDismissed = true
                                startTelemetryIfSettled()
                            }
                        )
                    } else {
                        DashboardScreen(telemetryFlow = telemetryFlow)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have toggled a permission in system settings and come back.
        refreshGranted()
        startTelemetryIfSettled()
    }

    private fun allGranted(): Boolean =
        specs.all { spec -> spec.permissions.all { it in granted } }

    private fun refreshGranted() {
        granted = specs
            .flatMap { it.permissions }
            .filter {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
            .toSet()
    }

    /**
     * Start collection once the permission gate is resolved — granted outright,
     * or explicitly skipped. Best-effort either way: the collector degrades
     * gracefully when a source is unavailable.
     */
    private fun startTelemetryIfSettled() {
        if (telemetryStarted) return
        if (allGranted() || rationaleDismissed) {
            telemetryStarted = true
            TelemetryService.start(applicationContext)
        }
    }
}

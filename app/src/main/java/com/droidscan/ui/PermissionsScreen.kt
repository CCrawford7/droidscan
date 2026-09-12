package com.droidscan.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidscan.ui.theme.*

/**
 * One user-facing permission request. Several [permissions] can back a single
 * row — Android wants FINE and COARSE location together but the user thinks of
 * that as one thing ("Location"), so it reads as one row.
 */
data class PermissionSpec(
    val permissions: List<String>,
    val label: String,
    val glyph: String,
    val rationale: String,
    val reassurance: String? = null
)

/**
 * The runtime set droidscan needs, ordered by how much the user will balk at it.
 * Rationale copy follows the rule: name the capability that sounds alarming,
 * then immediately say what the app actually does with it.
 *
 * Only genuinely requestable (dangerous) permissions belong here.
 * `READ_SIM_STATE` and `READ_PRECISE_PHONE_STATE` are declared in the manifest
 * but are privileged: they appear in neither the install nor the runtime
 * permission set, so the platform never shows a dialog for them and
 * `checkSelfPermission` returns DENIED permanently. Listing them would make
 * "grant everything" unreachable and trap the user on the rationale screen.
 * The collectors already degrade gracefully when they're absent.
 */
fun droidscanPermissionSpecs(): List<PermissionSpec> {
    val specs = mutableListOf(
        PermissionSpec(
            permissions = listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            label = "Location",
            glyph = "◎",
            rationale = "To read your serving cell, signal strength and network " +
                "type. Android treats cell and Wi-Fi details as location data, so " +
                "this is required before any radio reading is available.",
            reassurance = "Stays on this device — droidscan never uploads it."
        )
    )

    if (Build.VERSION.SDK_INT >= 33) {
        specs += PermissionSpec(
            permissions = listOf(Manifest.permission.NEARBY_WIFI_DEVICES),
            label = "Nearby devices",
            glyph = "◈",
            rationale = "To list nearby Wi-Fi networks and their signal strength."
        )
    }

    // READ_PHONE_STATE is only declared up to API 32 (see AndroidManifest) and
    // only there is it a dangerous permission worth asking the user for. On
    // 33+ the install-granted READ_BASIC_PHONE_STATE covers what's available.
    if (Build.VERSION.SDK_INT <= 32) {
        specs += PermissionSpec(
            permissions = listOf(Manifest.permission.READ_PHONE_STATE),
            label = "Phone state",
            // U+FE0E forces text presentation — bare U+260E renders as a
            // full-colour emoji telephone, which clashes with the palette.
            glyph = "☎︎",
            rationale = "To read your SIM state and network registration.",
            reassurance = "droidscan never places, answers or records calls."
        )
    }

    if (Build.VERSION.SDK_INT >= 33) {
        specs += PermissionSpec(
            permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
            label = "Notifications",
            glyph = "◔",
            rationale = "To show the live telemetry card while collecting in the " +
                "background."
        )
    }
    return specs
}

/** Permissions still missing, flattened for a single system request. */
fun List<PermissionSpec>.missingPermissions(granted: Set<String>): List<String> =
    flatMap { it.permissions }.filterNot { it in granted }

/**
 * Pre-permission rationale. Shown instead of firing bare system dialogs from
 * onCreate — the user should know why before the OS asks. Modelled on the
 * NetMonster pattern: per-permission GRANTED badge + one plain-English reason.
 */
@Composable
fun PermissionsScreen(
    specs: List<PermissionSpec>,
    granted: Set<String>,
    onGrant: () -> Unit,
    onSkip: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(TerminalBackground)) {

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "PERMISSIONS REQUIRED",
                color = TerminalAccentBlue,
                fontFamily = TerminalHeading,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.04.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "These are essential to make droidscan read your radio. " +
                    "Nothing is sent off the device.",
                color = TerminalTextSecondary,
                fontFamily = TerminalBody,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(10.dp))

            specs.forEach { spec ->
                PermissionRow(spec, spec.permissions.all { it in granted })
            }
            Spacer(Modifier.height(16.dp))
        }

        // Pinned footer: one primary action, plus an escape hatch so the user is
        // never trapped on this screen.
        Surface(color = TerminalSurface) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Button(
                    onClick = onGrant,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TerminalAccentBlue,
                        contentColor = TerminalBackground
                    )
                ) {
                    Text(
                        "GRANT ACCESS",
                        fontFamily = TerminalHeading,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.08.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Not now",
                    color = TerminalTextSecondary,
                    fontFamily = TerminalBody,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSkip)
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(spec: PermissionSpec, isGranted: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
        Box(
            Modifier
                .size(42.dp)
                .background(TerminalSurfaceVariant, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                spec.glyph,
                color = if (isGranted) TerminalAccentGreen else TerminalTextSecondary,
                fontSize = 17.sp
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    spec.label,
                    color = TerminalTextPrimary,
                    fontFamily = TerminalHeading,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isGranted) {
                    Spacer(Modifier.width(8.dp))
                    Chip("GRANTED", TerminalAccentGreen)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                spec.rationale,
                color = TerminalTextSecondary,
                fontFamily = TerminalBody,
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )
            spec.reassurance?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    it,
                    color = TerminalAccentBlue,
                    fontFamily = TerminalBody,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

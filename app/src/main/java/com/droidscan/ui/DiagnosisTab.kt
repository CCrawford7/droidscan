package com.droidscan.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidscan.diagnostics.*
import com.droidscan.shared.core.MetricSnapshot
import com.droidscan.ui.theme.*

// ─── Diagnosis ────────────────────────────────────────────
// Runs the :diagnostics engine over the live snapshot and renders the
// original product promise: present symptoms → tiered resolution steps →
// ranked community evidence, all plain-English and fully offline.

private val ValueText = FontFamily.Monospace
private val LabelText = FontFamily.SansSerif

/** Loads the corpus and constructs the engine once. Null on any asset/parse error. */
private fun loadDiagnosticEngine(context: Context): DiagnosticEngine? = try {
    val json = context.assets.open("corpus/ee_corpus.json").bufferedReader().use { it.readText() }
    DiagnosticEngine.fromCorpusJson(json)
} catch (t: Throwable) {
    null
}

private fun Severity.color(): Color = when (this) {
    Severity.HIGH -> TerminalAccentRed
    Severity.MEDIUM -> TerminalAccentYellow
    Severity.LOW -> TerminalAccentGreen
}

private fun ResolutionTier.label(): String = when (this) {
    ResolutionTier.USER_FIXABLE -> "USER-FIXABLE"
    ResolutionTier.DEEP_SETTINGS -> "DEEP SETTINGS"
    ResolutionTier.CARRIER_SIDE -> "CARRIER-SIDE"
}

private fun ResolutionTier.color(): Color = when (this) {
    ResolutionTier.USER_FIXABLE -> TerminalAccentGreen
    ResolutionTier.DEEP_SETTINGS -> TerminalAccentCyan
    ResolutionTier.CARRIER_SIDE -> TerminalAccentRed
}

@Composable
fun DiagnosisTab(snapshot: MetricSnapshot, padding: Modifier) {
    val context = LocalContext.current
    val engine = remember { loadDiagnosticEngine(context) }

    Column(
        Modifier
            .fillMaxSize()
            .then(padding)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (engine == null) {
            TierCard("DIAGNOSIS") {
                Text(
                    "Diagnostic corpus unavailable. Rebuild or reinstall the app to restore it.",
                    color = TerminalTextSecondary,
                    fontFamily = LabelText,
                    fontSize = 12.sp
                )
            }
            return@Column
        }

        val result = remember(snapshot) { engine.diagnose(snapshot) }
        val present = result.symptoms.filter { it.present }

        SummaryCard(present.size, result.matches.size)

        if (present.isEmpty()) {
            TierCard("FINDINGS") {
                Text(
                    "No issues detected from the current telemetry.",
                    color = TerminalAccentGreen,
                    fontFamily = LabelText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "If a problem persists, re-run this while it is actively happening so the engine can see it.",
                    color = TerminalTextSecondary,
                    fontFamily = LabelText,
                    fontSize = 11.sp
                )
            }
        } else {
            present.sortedByDescending { it.severity.ordinal }.forEach { symptom ->
                SymptomCard(symptom)
            }
        }

        if (result.steps.isNotEmpty()) StepsCard(result.steps)
        if (result.matches.isNotEmpty()) MatchesCard(result.matches, context)
    }
}

@Composable
private fun SummaryCard(issueCount: Int, matchCount: Int) {
    TierCard("DIAGNOSIS") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (issueCount == 0) "ALL CLEAR" else "$issueCount ISSUE${if (issueCount > 1) "S" else ""}",
                color = if (issueCount == 0) TerminalAccentGreen else TerminalAccentRed,
                fontFamily = ValueText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "$matchCount community match${if (matchCount != 1) "es" else ""}",
                color = TerminalTextSecondary,
                fontFamily = ValueText,
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Plain-English triage from the live telemetry — no server, no account.",
            color = TerminalTextSecondary,
            fontFamily = LabelText,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SymptomCard(symptom: DiagnosticSymptom) {
    TierCard(
        title = symptom.label.uppercase(),
        liveReadout = symptom.severity.name,
        liveColor = symptom.severity.color()
    ) {
        symptom.detail?.let {
            Text(
                it,
                color = TerminalTextPrimary,
                fontFamily = LabelText,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun StepsCard(steps: List<RecommendationStep>) {
    TierCard("RESOLUTION PLAN") {
        steps.forEach { step ->
            Row(verticalAlignment = Alignment.Top) {
                Chip(step.tier.label(), step.tier.color())
                Spacer(Modifier.width(8.dp))
                Text(
                    step.text,
                    color = TerminalTextPrimary,
                    fontFamily = LabelText,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MatchesCard(matches: List<CorpusMatch>, context: Context) {
    TierCard("COMMUNITY EVIDENCE") {
        matches.forEach { match ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { openThread(context, match.url) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        match.title,
                        color = TerminalTextPrimary,
                        fontFamily = LabelText,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        match.category,
                        color = TerminalTextSecondary,
                        fontFamily = LabelText,
                        fontSize = 10.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Chip(
                    if (match.isSolved) "SOLVED" else "OPEN",
                    if (match.isSolved) TerminalAccentGreen else TerminalAccentYellow
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap a thread to open it in your browser (EE Community).",
            color = TerminalTextSecondary,
            fontFamily = LabelText,
            fontSize = 10.sp
        )
    }
}

private fun openThread(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        // No browser / invalid URL — swallow; the row is informational.
    }
}

package com.droidscan.diagnostics

import com.droidscan.shared.core.MetricSnapshot

/**
 * Maps a [MetricSnapshot] to the full symptom list, one [DiagnosticSymptom]
 * per catalog entry. `present` is true exactly when the catalog [condition]
 * produced a detail line, so the UI can show "found" vs "checked, clear".
 */
object SymptomExtractor {

    fun extract(snapshot: MetricSnapshot): List<DiagnosticSymptom> =
        SymptomCatalog.specs.map { spec ->
            val detail = spec.condition(snapshot)
            DiagnosticSymptom(
                id = spec.id,
                label = spec.label,
                severity = spec.severity,
                present = detail != null,
                detail = detail
            )
        }

    fun present(snapshot: MetricSnapshot): List<DiagnosticSymptom> =
        extract(snapshot).filter { it.present }
}

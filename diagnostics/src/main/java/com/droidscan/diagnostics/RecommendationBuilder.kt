package com.droidscan.diagnostics

/**
 * Turns present symptoms into a plain-English, tiered resolution plan — the
 * original product's core promise: *user-fixable under 60 seconds*, *guided
 * deep-settings redirect*, or *carrier-side* (escalate with a cheat-sheet).
 *
 * Steps are deterministic and grounded in what the telemetry actually showed;
 * `glossaryId` links a step to the in-app metric glossary where one exists.
 */
object RecommendationBuilder {

    fun build(
        present: List<DiagnosticSymptom>,
        matches: List<CorpusMatch>
    ): List<RecommendationStep> {
        val steps = mutableListOf<RecommendationStep>()

        present.sortedByDescending { it.severity.ordinal }.forEach { symptom ->
            steps += stepsFor(symptom.id)
        }

        // Community evidence is the escalation signal: if the matched threads
        // are all unresolved, this pattern is carrier-side more often than not.
        if (matches.isNotEmpty() && matches.none { it.isSolved }) {
            steps += step(
                "Matching community threads are largely unresolved — escalate to your carrier using the report export.",
                ResolutionTier.CARRIER_SIDE
            )
        }

        return steps.distinctBy { it.text }
    }

    private fun step(text: String, tier: ResolutionTier, glossaryId: String? = null) =
        RecommendationStep(text, tier, glossaryId)

    private fun stepsFor(id: String): List<RecommendationStep> = when (id) {

        "weakSignal" -> listOf(
            step("Move near a window or outdoors — RSRP recovers quickly with line-of-sight to the mast.", ResolutionTier.USER_FIXABLE, "rsrp"),
            step("Toggle Airplane Mode for ~10 s to force a re-acquire on the nearest cell.", ResolutionTier.USER_FIXABLE),
            step("Confirm the preferred network type includes your carrier's bands.", ResolutionTier.DEEP_SETTINGS, "preferredNetworkType"),
            step("If it persists, check your carrier's coverage checker — sustained weak signal is a coverage/mast issue.", ResolutionTier.CARRIER_SIDE)
        )

        "poorSignalQuality" -> listOf(
            step("RSRQ tracks interference — move away from metal frames, thick walls, or a crowded room.", ResolutionTier.USER_FIXABLE, "rsrq"),
            step("Disable unused radios (Wi-Fi/Bluetooth scanning) to cut local interference.", ResolutionTier.DEEP_SETTINGS),
            step("Persistent poor RSRQ at strong RSRP points to sector congestion — carrier-side.", ResolutionTier.CARRIER_SIDE)
        )

        "poorSinr" -> listOf(
            step("Low SINR is a noisy channel — reposition the device and retest.", ResolutionTier.USER_FIXABLE, "sinr"),
            step("Sustained low SINR despite strong RSRP is congestion or a faulty mast sector.", ResolutionTier.CARRIER_SIDE)
        )

        "noData" -> listOf(
            step("Toggle Airplane Mode to clear a stuck data session (PDP context).", ResolutionTier.USER_FIXABLE, "dataNetworkType"),
            step("Check the APN against your carrier's published settings.", ResolutionTier.DEEP_SETTINGS),
            step("If the APN is correct and other devices work, ask your carrier to reset your data profile.", ResolutionTier.CARRIER_SIDE)
        )

        "noService" -> listOf(
            step("Toggle Airplane Mode; if no change, power-cycle and re-seat the SIM.", ResolutionTier.USER_FIXABLE, "dataNetworkType"),
            step("Dial *#*#4636#*#* → Phone information → confirm the radio is on and the network type is correct.", ResolutionTier.DEEP_SETTINGS),
            step("Persistent 'Not registered on network' with a valid SIM is usually carrier provisioning.", ResolutionTier.CARRIER_SIDE)
        )

        "simNotProvisioned" -> listOf(
            step("Re-seat the SIM, or re-download the eSIM profile.", ResolutionTier.USER_FIXABLE, "simProvisioning"),
            step("The SIM/eSIM needs (re)activation by the carrier.", ResolutionTier.CARRIER_SIDE)
        )

        "imsNotRegistered" -> listOf(
            step("Toggle VoLTE and Wi-Fi Calling off and on, then reboot.", ResolutionTier.USER_FIXABLE, "imsRegistered"),
            step("Confirm VoLTE and Wi-Fi Calling are enabled in call settings.", ResolutionTier.DEEP_SETTINGS, "volteEnabled"),
            step("IMS registration is carrier-provisioned; a persistent NOT REGISTERED needs a carrier IMS reset.", ResolutionTier.CARRIER_SIDE)
        )

        "volteOff" -> listOf(
            step("Enable VoLTE in Settings → Connections → Mobile networks → VoLTE calls.", ResolutionTier.DEEP_SETTINGS, "volteEnabled")
        )

        "wifiCallingDown" -> listOf(
            step("Toggle Wi-Fi Calling off/on and confirm Wi-Fi is connected and has internet.", ResolutionTier.USER_FIXABLE, "wifiCallingStatus"),
            step("Set the calling preference to 'Wi-Fi preferred'.", ResolutionTier.DEEP_SETTINGS, "wifiCallingStatus"),
            step("Some carriers gate Wi-Fi Calling by account type — confirm it is enabled on your plan.", ResolutionTier.CARRIER_SIDE)
        )

        "roamingIssue" -> listOf(
            step("Enable 'Data roaming' in Settings → Mobile networks.", ResolutionTier.DEEP_SETTINGS, "dataRoamingEnabled"),
            step("If enabled but data still fails, confirm your plan includes roaming and the add-on is active.", ResolutionTier.CARRIER_SIDE)
        )

        "highLatency" -> listOf(
            step("Latency above 300 ms is often congestion — retest off-peak or on Wi-Fi.", ResolutionTier.USER_FIXABLE),
            step("Sustained high latency on a strong signal points to carrier routing/congestion.", ResolutionTier.CARRIER_SIDE)
        )

        "memoryPressure" -> listOf(
            step("Close or force-stop memory-hungry background apps.", ResolutionTier.USER_FIXABLE, "memoryPressure"),
            step("Review 'Running services' in Developer Options.", ResolutionTier.DEEP_SETTINGS, "memoryPressure")
        )

        "thermalThrottling" -> listOf(
            step("Move the device out of direct sun or off a hot surface — throttling lifts as it cools.", ResolutionTier.USER_FIXABLE, "throttling")
        )

        "lowBattery" -> listOf(
            step("Charge the device — low battery can force power-saving radio modes.", ResolutionTier.USER_FIXABLE, "batteryLevel")
        )

        "storageLow" -> listOf(
            step("Free internal storage — critically low space degrades apps and caches.", ResolutionTier.USER_FIXABLE, "storageUsed")
        )

        else -> emptyList()
    }
}

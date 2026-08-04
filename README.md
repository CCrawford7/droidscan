# droidscan — Android Network Diagnostic Assistant

An Android network-diagnostics app that reads cellular/radio, SIM, power, thermal,
storage, CPU/memory, and network-config telemetry from the device and renders it in
a terminal-styled dashboard. Includes an educational glossary, optional **Power Mode**
via Shizuku for kernel-level data, and a file-backed metric history for trend analysis.

> **Status note:** v1.0 baseline (telemetry viewer). The longer-term product goal is
> an *AI-assisted* diagnostic engine that matches device telemetry against a real-world
> troubleshooting corpus (see `datasets/` and `docs/ARCHITECTURE.md`).

## Modules

| Module | Purpose |
|---|---|
| `:shared-core` | Data model (`MetricSnapshot` + sub-snapshots), thresholds, `CircularBuffer`, `MetricHistoryStore` (file-backed history), glossary asset. Pure Kotlin + kotlinx.serialization. |
| `:telemetry-collector` | Android library. Collectors for radio, thermal, storage, power, processes, network config, SIM, system info; `TelemetryCollector` orchestration; `ShizukuDetector`. |
| `:app` | Jetpack Compose UI (`DashboardScreen`, 5 tabs), `DroidscanApp` wiring, runtime permission flow, dark terminal theme. |

## Building

**Prerequisite: JDK 17.** The Gradle wrapper is 8.9 and AGP 8.7.3 — they do **not**
support running on the default JDK 26 of some systems (the build fails with a cryptic
`26.0.1` error).

```bash
# Point Gradle at a JDK 17 installation
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk      # adjust to your install

# Compile
./gradlew :app:assembleDebug

# Unit tests
./gradlew :shared-core:testDebugUnitTest :telemetry-collector:testDebugUnitTest
```

If your `JAVA_HOME` is already a JDK 17, you can omit the export. An alternative to
pinning JDK 17 is upgrading the Gradle wrapper to a version compatible with your JDK —
but pinning JDK 17 is the tested, supported path today.

## Permissions map

| Permission | Why | Notes |
|---|---|---|
| `READ_PHONE_STATE` | cellular/signal data | `maxSdkVersion=32` — replaced by newer perms on 33+ |
| `READ_BASIC_PHONE_STATE` | non-sensitive phone state (API 33+) | |
| `READ_SIM_STATE` | subscription/SIM info | requested on API 34+ |
| `NEARBY_WIFI_DEVICES` | `allCellInfo` (API 33+) | runtime-requested |
| `ACCESS_COARSE_LOCATION` | `allCellInfo` requires location-level access on API 33+ | runtime-requested; without it radio data is silently empty |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | network config, WiFi SSID/RSSI | normal |
| `BATTERY_STATS` | battery properties | declared; no special path used |
| `moe.shizuku.manager.permission.API_V23` | optional Power Mode | unused if Shizuku not present |

## Architecture (summary)

```
Android telemetry APIs ──> Collectors ──> MetricSnapshot (@Serializable) ──> Flow (1s)
                                        │                                     │
                                        └──> CircularBuffer(60) <─────────────┘
                                        └──> MetricHistoryStore (JSONL, ~1h)
```
See `docs/ARCHITECTURE.md` for the full picture and the AI-diagnostic-engine spike.

## Privacy

All telemetry is collected **on-device only** — nothing is transmitted, and
`android:allowBackup="false"` prevents cloud backup of the metric history.
Sensitive identifiers are truncated (ICCID last-4, IMSI MCC-MNC prefix only).
See `docs/PRIVACY.md`.

## Current status / roadmap

- **P0 (done):** threshold colouring correctness, instantaneous CPU load, storage
  external-used fix, location permission for `allCellInfo`, status-bar inset fix,
  dynamic RAT label, logged fallbacks.
- **P1 (done):** NR RSRQ/SINR, WiFi RSSI, latency probe, file-backed history store,
  Shizuku service-bound check.
- **P2 (UI rework):** charts/trends, stale/error states, accessibility — being addressed separately.
- **P3 (in progress):** unit tests (23 passing), README, privacy doc, architecture spike.
- **Future:** AI diagnostic engine matching telemetry to the EE troubleshooting corpus.

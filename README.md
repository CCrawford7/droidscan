# droidscan — Android Network Diagnostic Assistant

> A terminal-styled diagnostic dashboard that reads **cellular/radio, SIM, power, thermal,
> storage, CPU/memory, and network-config telemetry** straight off your Android device —
> including **per-SIM radio data** for dual-SIM phones.

[![Latest release](https://img.shields.io/github/v/release/CCrawford7/droidscan?label=latest%20release&color=brightgreen)](https://github.com/CCrawford7/droidscan/releases/latest)
[![Download APK](https://img.shields.io/badge/APK-download%20latest-blueviolet)](https://github.com/CCrawford7/droidscan/releases/latest/download/droidscan.apk)
[![Android](https://img.shields.io/badge/Android-10%2B%20(API%2029+)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/studio/releases/platforms)

---

## 📥 Download & install

**Latest build:** [`droidscan.apk`](https://github.com/CCrawford7/droidscan/releases/latest/download/droidscan.apk)
(v1.0.0 · 2026-08-04)

Each tagged release ships a debug-signed APK attached as a **GitHub Release asset**.
The link above always points at the newest release. Older builds are under
[Releases](https://github.com/CCrawford7/droidscan/releases).

**Install steps:**
1. Download the APK on your phone (or transfer it from a computer).
2. If prompted, allow your browser / file manager to "Install unknown apps".
3. Open it and grant the requested runtime permissions (location, nearby devices).

> **Note:** the published APK is **debug-signed** (build tools auto-sign with the debug
> key) so it installs cleanly for side-loading. It is *not* Play-Store signed and will
> show "Untrusted developer" on first install — that's expected for a side-loaded dev build.

---

## What it does

- **Dual-SIM aware** — reads per-subscription identity, capabilities, and signal for
  *every* provisioned SIM (DSDS supported; N SIMs if the device allows).
- **Per-SIM radio** — VoLTE, WiFi Calling, IMS, NR-NSA, call state, data network type,
  and registered-cell RSRP read via `createForSubscriptionId()` — SIM 2's card reflects
  SIM 2's radio, not the default sub's.
- **Live signal metrics** — RSRP / RSRQ / SINR, active band, cell ID, TAC, RAT, roaming —
  with sentinel (`Int.MAX_VALUE`) and physical-range validation so only real values display.
- **5-tab dashboard** — Overview · Radio · System · Power · SIM, in a dark terminal theme.
- **Optional Power Mode** — deeper kernel/process data via [Shizuku](https://shizuku.rikka.app/)
  (service-bound, detected via real reflection — no root required).
- **Metric history** — file-backed 1-hour history (1 s cadence) for trend analysis.
- **Educational glossary** — tap any metric for a plain-English explanation.

## Screenshots

*Coming soon.* The dashboard renders in a dark terminal theme across Overview / Radio /
System / Power / SIM tabs.

---

## Modules

| Module | Purpose |
|---|---|
| `:shared-core` | Data model (`MetricSnapshot` + sub-snapshots), thresholds + signal validation, `CircularBuffer`, `MetricHistoryStore` (file-backed history), glossary asset. Pure Kotlin + kotlinx.serialization. |
| `:telemetry-collector` | Android library. Collectors for radio, thermal, storage, power, processes, network config, SIM, system info; `TelemetryCollector` orchestration; `ShizukuDetector`. |
| `:app` | Jetpack Compose UI (`DashboardScreen`, 5 tabs), `DroidscanApp` wiring, runtime permission flow, dark terminal theme. |

## Building from source

**Prerequisite: JDK 17.** Gradle 8.9 + AGP 8.7.3 do **not** support running on the default
JDK 26 of some systems (the build fails with a cryptic `26.0.1` error).

```bash
# Point Gradle at a JDK 17 installation
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk      # adjust to your install

# Compile the debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug

# Run unit tests (28 passing)
./gradlew :shared-core:testDebugUnitTest :telemetry-collector:testDebugUnitTest
```

The CI workflow (`.github/workflows/build-apk.yml`) does exactly this on every tagged
release, so the published APK is always a clean CI build.

## Permissions map

| Permission | Why | Notes |
|---|---|---|
| `READ_PHONE_STATE` | cellular/signal data | `maxSdkVersion=32` — replaced by newer perms on 33+ |
| `READ_BASIC_PHONE_STATE` | non-sensitive phone state (API 33+) | |
| `READ_SIM_STATE` | subscription/SIM info | requested on API 34+ |
| `NEARBY_WIFI_DEVICES` | `allCellInfo` (API 33+) | runtime-requested |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | `allCellInfo` requires location-level access on API 33+; Android 16 needs **FINE** in practice | runtime-requested; without it radio data is silently empty |
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
`android:allowBackup="false"` prevents cloud backup of the metric history. Sensitive
identifiers are truncated (ICCID last-4, IMSI MCC-MNC prefix only). See `docs/PRIVACY.md`.

## Status / roadmap

- **P0 (done):** threshold colouring correctness, instantaneous CPU load, storage
  external-used fix, location permission for `allCellInfo`, status-bar inset fix,
  dynamic RAT label, logged fallbacks.
- **P1 (done):** NR RSRQ/SINR, WiFi RSSI, latency probe, file-backed history store,
  Shizuku service-bound check, per-SIM capabilities + disabled-SIM detection,
  signal sentinel/range validation, dual-source signal fallback.
- **P2 (UI rework):** charts/trends, stale/error states, accessibility — being addressed separately.
- **P3 (in progress):** unit tests (28 passing), this README, privacy doc, architecture spike.
- **Future:** AI diagnostic engine matching telemetry to a real-world troubleshooting corpus.

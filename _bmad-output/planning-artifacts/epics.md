---
stepsCompleted:
  - "01-validate-prerequisites"
  - "02-design-epics"
  - "party-mode-anti-consensus-club"
  - "03-create-stories"
inputDocuments:
  - "_bmad-output/planning-artifacts/briefs/brief-droidscan-2026-07-25/brief.md"
  - "_bmad-output/planning-artifacts/briefs/brief-droidscan-2026-07-25/addendum.md"
  - "_bmad-output/planning-artifacts/architecture/arch-droidscan-2026-07-25/ARCHITECTURE-SPINE.md"
  - "top_100_most_asked_ee_queries.json"
  - "top_100_unresolved_ee_queries.json"
---

# droidscan (working title) — Epic Breakdown

## Overview

droidscan is **btop/htop for Android phones** — a high-density, real-time system health dashboard. It surfaces everything about a device's current state in one place: radio/cellular, SIM/eSIM provisioning, network configuration, storage, thermal zones, power consumption, and running processes. The scraped EE Community forum data was a development reference for understanding common problem categories but is **not bundled or queried at runtime**.

**v1.0 scope is Phase 1 only:** collect and display system metrics with explanatory tooltips. No diagnostic analysis engine. No LLM layer. No feedback pipeline. Those are documented as deferred epics for later phases.

## v1.0 — System Dashboard & Metric Glossary

### Dashboard Sections

| Section | Data Sources | What It Shows |
|---|---|---|
| **Radio / Cellular** | TelephonyManager, SignalStrength, CellIdentity | RSRP, RSRQ, SINR, band, cell ID, IMS state, data type, roaming, operator, ePDG tunnel |
| **SIM / eSIM** | SubscriptionManager, UiccCardInfo | Active subscriptions, eSIM profiles, provisioning state, ICCID (masked), multi-SIM config |
| **Network Config** | ConnectivityManager, LinkProperties, WifiManager | IP addresses, DNS, VPN status, hotspot state, APN in use, WiFi vs mobile active |
| **Storage** | StorageStatsManager, StatFs | Internal used/free, system vs user breakdown, external/SD, top consumers |
| **Thermal** | HardwarePropertiesManager, PowerManager (Power Mode: kernel zones) | Per-zone temperatures, throttling status, trend indicator |
| **Power / Battery** | BatteryManager, PowerProfile | Current drain, charge rate, battery health/capacity/temp, per-process power, BT/eSIM/hotspot overhead estimates |
| **Processes / CPU / Memory** | ActivityManager, ProcessStats, /proc | Per-core load, top processes by CPU/RAM, memory pressure, swap, kernel wakelocks (Power Mode) |
| **System Overview** | Build, SELinux, PackageManager | Device model, OS version, build, security patch, SELinux state, uptime |

### Functional Requirements — v1.0

**FR1 — Radio Telemetry:** Read and display live radio metrics — RSRP, RSRQ, SINR, cell ID, band(s), IMS registration state, ePDG tunnel health, data network type, roaming status, operator name. Updated at 1s intervals.

**FR2 — Thermal & CPU Telemetry:** Read and display per-core CPU load and thermal zone temperatures. Standard Mode via BatteryManager / HardwarePropertiesManager. Power Mode via kernel `/sys/class/thermal/`. Thermal at 5s intervals, CPU at 1s.

**FR3 — Process & Socket Telemetry:** Read and display top processes by CPU/memory usage. Power Mode also reads `/proc/net/tcp`, `/proc/net/udp`, and kernel wakelocks for deeper visibility.

**FR4 — SIM / eSIM Status:** Read and display active subscription info per SIM slot — carrier name, provisioning state, eSIM profile list, roaming status per subscription, ICCID (masked for privacy). Power Mode may access additional RIL-level detail via dumpsys.

**FR5 — Network Configuration:** Read and display current network config — IP addresses (v4/v6), DNS servers, active network (WiFi vs mobile data), VPN status, hotspot/tethering state, APN in use.

**FR6 — Storage Metrics:** Read and display internal storage used/free, system vs user data breakdown, external/SD card stats, and top consumers by size.

**FR7 — Power & Battery Metrics:** Read and display battery level, health, temperature, charge rate (mA), estimated time remaining, current drain. Estimate per-process power consumption. Surface comparative drain impact of eSIM vs Bluetooth vs hotspot tethering.

**FR8 — System Overview:** Read and display device model, Android version, build number, security patch level, kernel version, SELinux status, uptime.

**FR9 — High-Density Terminal Dashboard:** Render all metrics in a btop-inspired real-time layout using Jetpack Compose Canvas — grid layouts, sparklines for trending values, colour-coded gauges, compact information-dense design. Updates push via Kotlin Flow.

**FR10 — Standard Mode:** Collect all available metrics via Play Store-compliant Android APIs (TelephonyManager, BatteryManager, StorageStatsManager, ActivityManager, ConnectivityManager, etc.). Works on any unmodified device.

**FR11 — Shizuku Power Mode:** When Shizuku is available via Wireless ADB, unlock additional metrics — kernel thermal zones, `/proc/net` socket stats, dumpsys telemetry, kernel wakelocks. No root required.

**FR12 — Graceful Shizuku Fallback:** Detect Shizuku at runtime. If unavailable, operate entirely in Standard Mode and hide Power Mode UI indicators.

**FR13 — Metric Tooltips & Glossary:** Tap any metric to see a plain-English explanation — what it measures, what the current value means, typical healthy range, and common causes of abnormal values.

**FR14 — Status Colouring:** Each metric displays a colour indicator (green/yellow/red) based on threshold ranges — e.g., RSRP > −100 dBm green, > −120 dBm yellow, ≤ −120 dBm red. Thresholds are preset for v1.0.

### Non-Functional Requirements — v1.0

**NFR1:** Dashboard render latency ≤100ms from metric emission to display update.

**NFR2:** App cold start to first metric display ≤3 seconds.

**NFR3 (amended v0.9):** No persistent telemetry storage → superseded. An encrypted, file-backed history store persists ~1 h of 1s snapshots (`metrics_history.jsonl`) to power trend charts and the diagnostic engine; the 60 s in-memory ring buffer remains for live sparklines.

**NFR4 (amended v0.9):** No analytics, no crash reporting, no telemetry *transmission* — still held. The one exception is a single on-device latency probe (a TCP connect to a well-known host) surfaced as a metric; no telemetry ever leaves the device.

**NFR5 (amended v0.9):** APK must not exceed 15MB → superseded. The debug build ships at ~26 MB; the R8 `release` build is ~2.3 MB. Size is a distribution concern, not a v1.0 gate.

**NFR6:** No ML/embedding/vector DB runtime dependencies.

**NFR7:** Carrier-agnostic — works on any Android device with any carrier, in any country, without modification.

**NFR8:** Min SDK 29 (Android 10), target API 35 (Android 15) — raised for the Android 16 `allCellInfo`/FINE-location behaviour.

**NFR9:** Shizuku integration is optional — app must be fully functional without it.

### Architecture Requirements — v1.0

**AR01:** Native Android (Kotlin). Kotlin 2.0+ with Compose compiler. Gradle Kotlin DSL. Target API 31+, min SDK 29.

**AR02:** 3-module architecture for v1.0:
  - `:shared-core` — metric data classes, threshold definitions, glossary entries
  - `:telemetry-collector` — all metric collection (Standard + Shizuku) via Kotlin Flow
  - `:dashboard-ui` — Compose Canvas dashboard, tooltip views, settings
  Dependency: `:dashboard-ui` → `:telemetry-collector` → `:shared-core`
  *(Modules deferred: `:analysis-engine`, `:llm-reasoner`)*

**AR03:** Telemetry collection via Kotlin `Flow` on `Dispatchers.IO`. Radio/CPU at 1s intervals, thermal/storage at 5s intervals, battery/power on change or 10s. In-memory ring buffer `CircularBuffer<MetricSnapshot>(size=60)` for sparkline rendering. No disk writes.

**AR04:** Dashboard UI via Jetpack Compose `Canvas` composable — custom `drawText`, `drawLine`, `drawRect` for terminal-style layout. Sections organised as scrollable or tabbed views. Tooltips via Material 3 popup/dialog.

**AR05:** Shizuku via API 13+ with `ShizukuProvider.ping()` detection at startup. Power Mode reads go through `ShizukuShell` wrapper (`Shizuku.newProcess()`). Standard Mode always works without Shizuku installed.

**AR06:** Threshold configuration embedded as compile-time constants for v1.0. Future: hot-reloadable config file.

**AR07:** CI pipeline — GitHub Actions: lint + test on PR, build APK on merge to main.

**AR08:** Key dependencies — `androidx.compose.ui` (Canvas), `shizuku:api:13+` (optional Power Mode), `kotlinx-serialization-json` (structured config). No ML, no network, no vector DB deps.

## Deferred Epics (Post-v1.0)

### Epic 2 — Diagnostic Analysis Engine
A lightweight heuristic layer that correlates metric patterns to suggest likely root causes. Decision-tree based, works entirely offline. Informed by patterns observed in the scraped forum corpus. No ML dependencies.

### Epic 3 — LLM Accessibility Layer
BYOK LLM integration for non-power users. Translates natural-language questions ("why are my texts not sending?") into plain-English guidance with OEM-specific deep links. API-driven; user provides their own key.

### Epic 4 — Feedback & Accuracy Pipeline
Structured feedback loop where users confirm whether suggested fixes resolved their issue. Data used to refine detection thresholds and diagnostic tree branches over time.

## FR Coverage Map — v1.0

FR1:  Epic 1 — Radio telemetry
FR2:  Epic 1 — Thermal/CPU telemetry
FR3:  Epic 1 — Process & socket telemetry
FR4:  Epic 1 — SIM/eSIM status
FR5:  Epic 1 — Network configuration
FR6:  Epic 1 — Storage metrics
FR7:  Epic 1 — Power & battery metrics
FR8:  Epic 1 — System overview
FR9:  Epic 1 — Terminal dashboard display
FR10: Epic 1 — Standard Mode
FR11: Epic 1 — Shizuku Power Mode
FR12: Epic 1 — Graceful Shizuku fallback
FR13: Epic 1 — Metric tooltips & glossary
FR14: Epic 1 — Status colouring

## Epic List — v1.0

### Epic 1: Real-Time System Health Dashboard
droidscan is btop for Android — a high-density real-time dashboard displaying everything about your phone's current state: radio, SIM, network, storage, thermal, power, and processes. Every metric has a plain-English tooltip explaining what it means. Standard Mode works on any device; Shizuku Power Mode unlocks deeper telemetry for those who want it.

**FRs covered:** FR1–FR14

## Epic 1: Real-Time System Health Dashboard

**Goal:** droidscan is btop for Android — a high-density real-time dashboard displaying everything about your phone's current state: radio, SIM, network, storage, thermal, power, and processes. Every metric has a plain-English tooltip explaining what it means. Standard Mode works on any device; Shizuku Power Mode unlocks deeper telemetry for those who want it.

**FRs covered:** FR1–FR14

### Story 1.1: Project Scaffold & Shared Core

As a developer,
I want a compilable Android project with the 3-module structure, build config, and shared data types in place,
So that feature code can be written against a stable, tested foundation from day one.

**Acceptance Criteria:**

**Given** the project is initialised
**When** I run `./gradlew assembleDebug`
**Then** the build succeeds with zero errors

**Given** the module structure
**When** I inspect `settings.gradle.kts`
**Then** the modules `:shared-core`, `:telemetry-collector`, and `:dashboard-ui` are declared with `:dashboard-ui` depending on `:telemetry-collector` and `:telemetry-collector` depending on `:shared-core`

**Given** `:shared-core`
**When** I review its source
**Then** it contains data classes for all metric types: `RadioSnapshot`, `ThermalSnapshot`, `StorageSnapshot`, `PowerSnapshot`, `ProcessSnapshot`, `NetworkConfigSnapshot`, `SimStatusSnapshot`, `SystemInfoSnapshot`
**And** it contains threshold constants and colour-range enums used for status colouring

**Given** the CI workflow
**When** a PR is opened
**Then** GitHub Actions runs `./gradlew lint` and `./gradlew test`
**And** on merge to main, a debug APK is built

**Given** the Kotlin configuration
**When** I check `build.gradle.kts`
**Then** Kotlin 2.0+ with Compose compiler plugin is configured, target SDK is 31+, min SDK is 29, and compile SDK is 35

### Story 1.2: Standard Telemetry Collector

As a developer,
I want a `:telemetry-collector` module that gathers all system metrics via public Android APIs and exposes them as Kotlin Flows,
So that the dashboard UI can subscribe to live data without knowing where it comes from.

**Acceptance Criteria:**

**Given** the `:telemetry-collector` module
**When** I inspect the public API
**Then** `TelemetryCollector` exposes one Flow per metric section: `observeRadio(): Flow<RadioSnapshot>`, `observeThermal(): Flow<ThermalSnapshot>`, `observeStorage(): Flow<StorageSnapshot>`, `observePower(): Flow<PowerSnapshot>`, `observeProcesses(): Flow<ProcessSnapshot>`, `observeNetworkConfig(): Flow<NetworkConfigSnapshot>`, `observeSimStatus(): Flow<SimStatusSnapshot>`, and `observeSystemInfo(): Flow<SystemInfoSnapshot>`

**Given** the collection intervals
**When** radio and CPU metrics are collected
**Then** they emit at 1-second intervals

**Given** the collection intervals
**When** thermal and storage metrics are collected
**Then** they emit at 5-second intervals

**Given** the collection intervals
**When** battery and power metrics are collected
**Then** they emit on state change or at 10-second intervals, whichever is sooner

**Given** all metric collection
**When** data is gathered
**Then** all Flow emission runs on `Dispatchers.IO` and never blocks the main thread

**Given** the in-memory buffer
**When** a snapshot is emitted
**Then** it is stored in a `CircularBuffer<MetricSnapshot>(size=60)` in `:telemetry-collector` for sparkline rendering
**And** no data is ever written to disk

**Given** the module is compiled
**When** run on a device with API 29+
**Then** all flows emit valid data (no `null` or default values for available metrics)

### Story 1.3: Dashboard Shell & Canvas Framework

As a user,
I want to open the app and see a terminal-style dashboard with live metric sections,
So that I can start monitoring my device's health immediately.

**Acceptance Criteria:**

**Given** the `:dashboard-ui` module
**When** the app launches
**Then** `DashboardScreen` composable subscribes to all 8 metric Flows from `:telemetry-collector`

**Given** the dashboard layout
**When** displayed
**Then** it is scrollable with sections for Radio, SIM, Network, Storage, Thermal, Power, Processes, and System Overview

**Given** each section renders on a Jetpack Compose `Canvas`
**When** metrics emit
**Then** values are drawn using custom `drawText`, `drawLine`, and `drawRect` in a terminal-inspired aesthetic (monospace-style text, grid alignment, compact density)

**Given** a metric with a ring buffer (RSRP, CPU load, temperature, etc.)
**When** displayed
**Then** a sparkline shows the last 60 data points trending in real time

**Given** a metric with threshold-based colouring
**When** displayed
**Then** it renders a colour-coded gauge (green/yellow/red) alongside the value

**Given** the app structure
**When** launched
**Then** a bottom navigation bar presents at least two tabs: Dashboard and Settings

**Given** the app is cold-started on a device with API 29+
**When** the app reaches first metric display
**Then** elapsed time from launch is ≤3 seconds

### Story 1.4: Radio & Network Section

As a user,
I want to see my live cellular signal metrics and current network configuration on the dashboard,
So that I can understand my connectivity quality at a glance.

**Acceptance Criteria:**

**Given** the Radio section on the dashboard
**When** metrics are flowing
**Then** it displays RSRP, RSRQ, SINR, active band(s), cell ID (masked for privacy), IMS registration state, data network type (NR/LTE/HSPA/EDGE), roaming status, operator name, and ePDG tunnel health
**And** all values update at 1-second intervals

**Given** the Network Config section on the dashboard
**When** displayed
**Then** it shows IP addresses (v4 and v6), DNS servers, active network type (WiFi vs mobile data), VPN status and label, hotspot/tethering state, and APN name in use

**Given** each metric in both sections
**When** displayed
**Then** it shows inline colour coding (green/yellow/red) based on configured thresholds

**Given** the RSRP metric
**When** displayed
**Then** a sparkline shows the last 60 samples trending in real time

### Story 1.5: SIM, Storage & System Sections

As a user,
I want to see my SIM/eSIM status, storage usage, and device system info on the dashboard,
So that I can quickly check my provisioning, available space, and OS details without digging through Android settings.

**Acceptance Criteria:**

**Given** the SIM section on the dashboard
**When** displayed
**Then** it shows per-SIM-slot: carrier name, roaming status, eSIM profile count, provisioning state, and ICCID (masked to last 4 digits)

**Given** the Storage section on the dashboard
**When** displayed
**Then** it shows internal storage used and free, system vs user data breakdown, external/SD card status and usage, and the top 3 consumers by size
**And** values update at 5-second intervals

**Given** the storage metrics
**When** free space is below configured thresholds
**Then** status colouring is applied (green when plenty free, yellow when moderate, red when critically low)

**Given** the System Overview section on the dashboard
**When** displayed
**Then** it shows device model, Android version, build number, security patch level, kernel version, SELinux status, and uptime

### Story 1.6: Thermal, Power & Process Sections

As a user,
I want to see thermal zones, battery status, and running processes on the dashboard,
So that I can identify overheating, power drain, and which apps are consuming resources.

**Acceptance Criteria:**

**Given** the Thermal section on the dashboard
**When** displayed
**Then** it shows per-zone temperatures, throttling status (active/inactive), and a trend indicator (rising/stable/falling)
**And** values update at 5-second intervals
**And** a sparkline shows the last 60 temperature samples trending in real time

**Given** the thermal status colouring
**When** a zone is within normal range
**Then** it is displayed in green
**When** a zone is elevated but not throttling
**Then** it is displayed in yellow
**When** a zone is actively throttling
**Then** it is displayed in red

**Given** the Power & Battery section on the dashboard
**When** displayed
**Then** it shows battery level %, battery health (good/overheat/dead/etc.), battery temperature, charge rate (mA), estimated time remaining, and current drain estimate
**And** values update on state change or at 10-second intervals

**Given** a per-process power breakdown is available
**When** displayed
**Then** it shows estimated power consumption per process

**Given** eSIM, Bluetooth, or hotspot is active
**When** displayed
**Then** comparative power overhead estimates for each active feature are shown

**Given** the battery health status
**When** battery health is good
**Then** it is displayed in green
**When** battery health indicates attention (overheat, cold, degraded)
**Then** it is displayed in red

**Given** the Process section on the dashboard
**When** displayed
**Then** it shows per-core CPU load %, the top 5 processes by CPU usage, the top 5 processes by memory usage, and a memory pressure indicator
**And** values update at 1-second intervals

### Story 1.7: Metric Tooltips & Glossary

As a user,
I want to tap any metric on the dashboard and see a plain-English explanation,
So that I can understand what the number means and whether I should be concerned.

**Acceptance Criteria:**

**Given** any metric displayed on the dashboard
**When** I tap its label or value
**Then** a tooltip popup or dialog opens with an explanation

**Given** the tooltip content
**When** displayed
**Then** it contains: what the metric measures, the current value in context, the typical healthy range, and common causes of abnormal values

**Given** all 8 dashboard sections
**When** I tap any metric across Radio, SIM, Network, Storage, Thermal, Power, Processes, or System Overview
**Then** a tooltip is available for every displayed metric

**Given** the glossary data source
**When** the app builds
**Then** tooltip content is loaded from a bundled JSON file in `:shared-core`

**Given** the tooltip UI
**When** displayed
**Then** it uses a Material 3 dialog or popup component, not a Canvas-drawn overlay

### Story 1.8: Status Colouring

As a user,
I want to see at a glance whether each metric is in a healthy range,
So that I don't have to know the thresholds to know if something is wrong.

**Acceptance Criteria:**

**Given** every metric on the dashboard
**When** displayed
**Then** it shows a colour indicator: green (good), yellow (caution), or red (critical)

**Given** the threshold definitions
**When** the app compiles
**Then** per-metric thresholds are defined as constants in `:shared-core`
**And** they cover all displayed metrics across all 8 sections

**Given** a metric's value changes
**When** it crosses a threshold boundary
**Then** the colour indicator updates reactively without manual refresh

**Given** the RSRP metric
**When** RSRP > −100 dBm
**Then** colour is green
**When** RSRP > −120 dBm and ≤ −100 dBm
**Then** colour is yellow
**When** RSRP ≤ −120 dBm
**Then** colour is red

**Given** the battery level metric
**When** level > 20%
**Then** colour is green
**When** level > 5% and ≤ 20%
**Then** colour is yellow
**When** level ≤ 5%
**Then** colour is red

### Story 1.9: Shizuku Power Mode Foundation

As a power user,
I want the app to detect Shizuku and unlock deeper telemetry,
So that I can see kernel-level thermal data and wakelocks without rooting my phone.

**Acceptance Criteria:**

**Given** the Shizuku API integration
**When** the app starts
**Then** it checks for Shizuku availability via `ShizukuProvider.ping()` (API 13+)

**Given** Shizuku is available
**When** permission is requested and granted
**Then** Power Mode activates and additional metrics become available

**Given** the `ShizukuShell` wrapper
**When** Power Mode commands need to run
**Then** they execute via `Shizuku.newProcess()` and the wrapper handles output parsing and error states

**Given** Power Mode is active
**When** thermal data is collected
**Then** per-zone kernel temperatures are read from `/sys/class/thermal/thermal_zone*/temp`
**And** these replace or augment the Standard Mode BatteryManager thermal readings

**Given** Power Mode is active
**When** system data is collected
**Then** kernel wakelock information is read from `/proc/wakelocks` or `/d/wakeup_sources`

**Given** Shizuku is not installed or permission is denied
**When** the app runs
**Then** it operates entirely in Standard Mode with no errors, no empty UI sections, and no confusing state

**Given** Power Mode is inactive
**When** the dashboard renders
**Then** Shizuku-only UI indicators are hidden and the user is not prompted about missing features

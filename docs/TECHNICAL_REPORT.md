# droidscan — Technical Report

**Date:** 2026-09-12
**Version under review:** `versionName 0.9.0`, `versionCode 3`, branch `0.9.1`
**Commit:** `73648dc` (`fix(ui): stop rendering unknown telemetry as zero`) + uncommitted manifest/doc corrections
**Method:** static analysis of the repository, full unit-test execution, `dumpsys`/`pm` probes against a physical test device (OnePlus/OPPO `CPH2709`, Android 15), and an on-device UI audit of eight comparable applications.

---

## 1. Executive summary

droidscan is a single-developer Android telemetry dashboard that reads radio, SIM, power, thermal, storage and process data off a device and renders it in a terminal-styled Compose UI. It is **feature-complete against its own v1.0 scope** and the telemetry layer is genuinely well engineered — 80 unit tests, all passing; OEM-specific instrument quirks handled with real fallbacks; and a privacy posture that is unusually disciplined for the category.

Three findings dominate the current state, and none of them are about the app's core competence:

1. **The published APK is a debug build.** CI runs `assembleDebug` and attaches the result to GitHub Releases. The shipped artifact is 26 MB and carries `android:debuggable="true"`, which on any device with ADB enabled permits `run-as com.droidscan.app` to read the private data directory — including the persisted metric history containing IMSI prefix, ICCID last-4, MSISDN and serving-cell identifiers.
2. **There is no shippable release path.** The `release` build type enables R8 but declares no `signingConfig`, so it emits `app-release-unsigned.apk`. The 2.3 MB optimised build cannot be distributed, and the 26 MB debug build is the only artifact that can.
3. **`PRIVACY.md` is factually wrong about background collection.** It states telemetry is collected only while foregrounded; the app runs a `dataSync` foreground service and the README advertises background collection as a feature. This is a Play Data Safety exposure, not a documentation nit.

A fourth, more strategic finding: **"Pro Features" is a debug toggle with no purchase path.** `DroidscanApp.proFeaturesEnabled` is a `MutableStateFlow(false)` flipped by a switch in Settings, and the project declares no billing dependency. The app currently has a paywall and no way to pay.

The strategic risk that prompted this report — dependence on Shizuku — is real but **materially overestimated**. Section 7 measures the coupling and finds it shallow, isolated behind a three-transport facade, and non-fatal when absent.

---

## 2. System overview

### 2.1 Module graph

```
:app ──> :telemetry-collector ──> :shared-core
```

Dependency direction is one-way and enforced by Gradle module boundaries. There are no cycles and no `:shared-core` → Android dependencies (it is pure Kotlin + `kotlinx.serialization`, which is why it is cheaply unit-testable).

| Module | Files | Lines | Tests | Role |
|---|---:|---:|---:|---|
| `:app` | 10 | 3,549 | 0 | Compose UI, service, DI wiring, permissions |
| `:telemetry-collector` | 22 | 3,180 | 28 | Android collectors, orchestration, privilege bridges |
| `:shared-core` | 15 | 1,487 | 52 | Data model, thresholds, buffers, history store, glossary |
| **Total** | **47** | **8,216** | **80** | |

### 2.2 Data flow

```
Device APIs (TelephonyManager, BatteryManager, ActivityManager,
            ConnectivityManager, SubscriptionManager, /proc, /sys)
   │
   ▼
Collectors — attach(...) once, then read(): *Snapshot per tick
   │  Radio, Thermal, Storage, Power, Process, NetworkConfig, SimStatus, SystemInfo
   ▼
TelemetryCollector.observeAll()  ──  1 s loop on Dispatchers.IO
   │
   ▼
MetricSnapshot (@Serializable envelope, all 8 sub-snapshots)
   ├── emit ──> Flow<MetricSnapshot> ──> Compose UI (single collector, shared by service + activity)
   ├── add  ──> CircularBuffer(60)      (in-memory, current session)
   └── append ─> MetricHistoryStore      (JSONL, ~3,600 entries ≈ 1 h)
```

The **envelope pattern** is the load-bearing design decision: one serialisable object per tick means persistence, export, and any future diagnostic engine all consume the same shape.

### 2.3 Collector inventory

| Collector | Standard Mode source | Power Mode delta |
|---|---|---|
| `RadioCollector` | `getAllCellInfo()`, `getSignalStrength()`, `CarrierConfigManager`, `Settings.Global` | none wired |
| `SimStatusCollector` | `SubscriptionManager`, per-sub `createForSubscriptionId()` | none wired |
| `ImsCollector` | `ImsMmTelManager` via reflection | **field is `--` without privilege** |
| `PowerCollector` | `BatteryManager`, `PowerProfile` | none |
| `ThermalCollector` | `HardwarePropertiesManager` | ✅ `/sys/class/thermal` |
| `ProcessCollector` | `ActivityManager` | ✅ per-UID CPU, kernel wakelocks |
| `StorageWearCollector` | `StatFs`, `StorageStatsManager` | ✅ eMMC/UFS wear |
| `StorageCollector` | `StatFs`, `StorageStatsManager` | none |
| `NetworkConfigCollector` | `ConnectivityManager`, `LinkProperties`, `WifiManager` | none |
| `GnssCollector` | `GnssStatus.Callback` | none |
| `SystemInfoCollector` | `Build`, `SystemClock` | none |

---

## 3. Feature inventory

Verified present in source and, where marked, observed working on the test device.

### 3.1 Working

- **Dual-SIM aware telemetry** — per-subscription identity, capabilities and registered-cell signal via `createForSubscriptionId()`. Includes deliberate disabled-SIM detection so a deactivated slot does not borrow the default subscription's identity.
- **Signal metrics** — RSRP/RSRQ/SINR (LTE + NR CSI/SS variants), band, PCI, TAC, CI, EARFCN/NRARFCN, bandwidth, derived downlink frequency.
- **Sentinel and range validation** — all raw values pass through shared validators that reject `Int.MAX_VALUE` and physically impossible readings. This exists because OPPO/OnePlus hardware returns the sentinel rather than a null.
- **Dual-source signal fallback** — `getAllCellInfo()` primary, consolidated `getSignalStrength()` backfill, merged per-metric rather than all-or-nothing.
- **5-tab dashboard** — Overview · Signal · System · Power · SIM.
- **Activity-Ring Overview** — concentric per-SIM signal rings, battery ring, RAM bar; tap-to-glossary with live plain-language explainers.
- **Trend charts** — 30-minute RSRP/SINR/CPU/RAM/battery/thermal from the history store.
- **Foreground collection service** — `dataSync` type, survives backgrounding.
- **Diagnostic report export** — share full snapshot as text.
- **GNSS** — per-constellation visibility, in-fix count, C/N₀.
- **Metric glossary** — 30 KB asset, 39 entries, tap-any-metric.
- **Pre-permission rationale screen** — added this cycle; explains each capability in plain English before any system dialog fires.
- **Telemetry liveness strip** — distinguishes "collecting" from "stalled N s" with a restart affordance.

### 3.2 Incomplete or gated

- **Power Mode (thermal zones, wakelocks, storage wear)** — implemented and functional, gated behind the Pro switch, requires Shizuku or root.
- **IMS registration depth** — `ImsCollector` is written but returns `--` for registration state/transport without `READ_PRECISE_PHONE_STATE`. It does not currently route through Shizuku (Section 7.4).
- **Signal tab list treatment, chart axes, semantic colour ramp** — scoped as UI fixes 4–6, not started (`docs/UI_FIX_PLAN.md`).

### 3.3 Absent

- **Any diagnostic/AI engine.** The original product vision (match telemetry to a corpus of real-world network faults) has zero implementation. The corpus exists; the engine does not.
- **Instrumentation tests.** `androidTest` count is 0.

---

## 4. Quality and verification

### 4.1 Test execution (this review)

```
./gradlew :shared-core:testDebugUnitTest :telemetry-collector:testDebugUnitTest
  → BUILD SUCCESSFUL
  → 80 tests, 0 failures, 0 errors, 0 skipped
```

| Suite | Tests |
|---|---:|
| `BandTableTest` | 12 |
| `SignalPresentationTest` | 11 |
| `ProcessCollectorTest` | 9 |
| `ThresholdsTest` | 7 |
| `EncryptedHistoryStoreTest` | 6 |
| `MetricHistoryStoreTest` | 6 |
| `ThermalCollectorTest` | 5 |
| `SignalValuesTest` | 5 |
| `CircularBufferTest` | 5 |
| `StorageWearCollectorTest` | 4 |
| `RootShellBridgeTest` | 4 |
| `HistoryForChartsTest` | 3 |
| `TelemetryCollectorTest` | 3 |

**README claims 71 tests. Actual is 80.** The count drifted and the doc did not follow.

### 4.2 Coverage character

The suite is weighted toward **pure logic** — band tables, threshold evaluation, signal validation, buffers, history storage. This is the right place to spend unit tests, and the `ShizukuDetector.setAvailableOverride()` hook means the degraded path is testable without the dependency present.

The uncovered surface is everything that needs a device or a screen:
- **Zero instrumentation tests** across all three modules.
- `DashboardScreen.kt` (1,486 lines) is untested.
- No visual regression or screenshot testing.
- Collector behaviour against real hardware is validated manually on one device.

### 4.3 Device validation

Real-device evidence exists for the hard cases and is documented in commit messages:
- Bottom-nav/gesture-inset collision measured at 42 px and fixed (`7fd514a`).
- Colour-emoji glyph leaking into a monochrome UI, fixed with U+FE0E (`aa3450b`).
- Null-coerced-to-zero false alarms eliminated (`73648dc`).
- OPPO/OnePlus `MAX_VALUE` sentinel behaviour handled in the radio fallback chain.

**Validation breadth is one device, one OEM, one Android version.** The code is full of OEM-conditional fallbacks that have never been exercised on Samsung, Pixel or Xiaomi hardware.

---

## 5. Build, release and distribution

### 5.1 Build configuration

| Setting | Value |
|---|---|
| `compileSdk` / `targetSdk` | 35 |
| `minSdk` | 29 (Android 10) |
| AGP / Gradle / Kotlin | 8.7.3 / 8.9 / 2.1.0 |
| JDK | **17 required** — Gradle 8.9 + AGP 8.7.3 fail on JDK 26 with a cryptic error |
| Compose BOM | 2024.12.01 |

Build types: `debug`, `release` (R8, no signing config), `staging` (`.test` suffix, amber icon, for side-by-side installs).

### 5.2 The release pipeline, as it actually behaves

```yaml
- run: ./gradlew :app:assembleDebug          # ← builds DEBUG
- run: cp app/build/outputs/apk/debug/app-debug.apk dist/droidscan.apk
- uses: softprops/action-gh-release@v2
  with: { files: dist/droidscan.apk }
```

Triggers on `v*` tags and `workflow_dispatch`. Stable asset name gives a permanent `releases/latest/download/droidscan.apk` URL.

### 5.3 Artifact measurements

| Artifact | Size | Signed | Debuggable | Distributable |
|---|---:|---|---|---|
| `app-debug.apk` | **26 MB** | debug key | **`android:debuggable="true"`** | ← this is what ships |
| `app-staging.apk` | 23 MB | debug key | yes | no (test build) |
| `app-release-unsigned.apk` | **2.3 MB** | **no** | no | **no** |

The debug build is **11× larger** than the R8-optimised release build, and the release build cannot be installed on a device as-is.

---

## 6. Permissions and privacy posture

### 6.1 Declared permissions and their real protection levels

Measured on the test device via `dumpsys package permissions`:

| Permission | Protection level | Grantable by user? |
|---|---|---|
| `READ_BASIC_PHONE_STATE` | `normal` | install-granted, no prompt |
| `READ_PHONE_STATE` | `dangerous` (`maxSdkVersion=32`) | yes, ≤ API 32 only |
| `ACCESS_FINE_LOCATION` / `COARSE` | `dangerous` | yes |
| `NEARBY_WIFI_DEVICES` | `dangerous` (API 33+) | yes |
| `POST_NOTIFICATIONS` | `dangerous` (API 33+) | yes |
| `READ_PRECISE_PHONE_STATE` | **`signature` / `privileged`** | **never** |

**`READ_SIM_STATE` was declared in the manifest and does not exist on the platform at all** — zero occurrences in `pm list permissions`, absent from `/system/etc/permissions`, not granted to `com.android.shell`. It has been removed this cycle; the declaration was a no-op that implied a capability the app does not have.

### 6.2 The dependency that actually matters

Cell identity does **not** require a privileged permission. RSRP/RSRQ/SINR, band, PCI, TAC, CI and EARFCN all come from `TelephonyManager.getAllCellInfo()`, which requires **location** — a one-tap runtime grant. This was misdiagnosed in an earlier draft of the UI fix plan and is corrected there.

The genuine privileged delta is narrow: **IMS registration state and transport**, plus the kernel-level reads already gated as Pro.

### 6.3 Privacy safeguards in place

- **No telemetry transmission.** No analytics, no crash reporting, no upload path in the codebase.
- **`android:allowBackup="false"`** — history excluded from Auto Backup and device migration.
- **Truncated identifiers** — ICCID last-4 only; IMSI MCC-MNC prefix only (`[310-XXXX-XXXX]`).
- **Encrypted history store** — `EncryptedHistoryStore` with dedicated tests.
- **No location recorded** — location is requested solely because `allCellInfo` demands it.

### 6.4 Privacy posture defects

- **`PRIVACY.md` contradicts the implementation on background collection.** The doc says "Telemetry is collected only while the app is foregrounded"; `TelemetryService` is a `dataSync` foreground service and the README advertises background collection as a headline feature. Whichever is intended, the doc and the code currently disagree — and this document feeds the Play Data Safety declaration.
- **`PRIVACY.md` says coarse location**; the manifest requests `ACCESS_FINE_LOCATION` and the README notes Android 16 requires FINE in practice. The privacy disclosure understates the actual request.

---

## 7. Privilege model and the Shizuku dependency

### 7.1 What Shizuku actually provides

Shizuku does not grant permissions. It starts a process running as **UID 2000 (adb shell)**. The entire elevated surface droidscan uses is:

```kotlin
// ShizukuTelemetryService.runCommand
ProcessBuilder("/system/bin/sh", "-c", cmd).start()
```

Two operations, total: `runCommand(cmd)` and `readFile(path)`. There is no binder wrapping of Android APIs, no `ShizukuBinderWrapper`, no hidden-API IPC. The 14 `rikka.shizuku.*` call sites exist purely to solve the *bootstrap* problem — establishing that shell-UID process.

### 7.2 Why it works

`com.android.shell` holds `READ_PRECISE_PHONE_STATE` by default (`granted=true`). Routing through Shizuku succeeds because the call is made *as* a package that legitimately owns the permission. It is borrowed identity, not a bypass.

### 7.3 Can the dependency be removed?

No — the constraint is platform-level. UID 2000 requires **ADB or root**; there is no third path. A self-built bootstrap would mean shipping an in-app ADB client (RSA keypair, ADB wire protocol, wireless-debugging pairing) — roughly 2–4k lines — and would still require the user to enable Developer Options, turn on Wireless Debugging and enter a pairing code. Same friction, same reboot fragility (wireless debugging does not survive restart), worse UX, and the maintenance burden becomes ours.

### 7.4 Coupling assessment — low risk

```
ShizukuDetector          ← the only seam
  ├── RootShellBridge    (root: zero setup, survives reboot)
  ├── ShizukuBridge      (Shizuku: permission + bound UserService)
  └── (none)             → Standard Mode, full graceful degradation
```

- Every collector degrades to Standard Mode when `isAvailable()` is false. Nothing crashes without Shizuku.
- `dev.rikka.shizuku:api` is **`compileOnly`** — not shipped. Only the small `provider` shim is bundled.
- Maven Central artifacts persist independently of the app's maintenance status.

**The realistic failure mode is "no new Shizuku installs on future Android", not "droidscan breaks."**

The true risk is commercial, not technical: Pro's value proposition currently rests on the narrow privileged slice, and that slice is not even wired up (Section 7.5).

### 7.5 Outstanding gap

`RadioCollector`, `SimStatusCollector` and `ImsCollector` are exactly the collectors that need privileged data and are exactly the three that **do not** route through Shizuku — while `ThermalCollector`, `ProcessCollector` and `StorageWearCollector` do. Pro currently promises IMS depth it does not deliver even when Shizuku is running. The UI labels these rows honestly ("Requires Shizuku"), so nothing misleads the user, but the feature is unimplemented.

---

## 8. SWOT analysis

### Strengths

| # | Strength | Evidence |
|---|---|---|
| S1 | **Clean, enforced module layering** | `:app → :telemetry-collector → :shared-core`, no cycles, `:shared-core` free of Android deps |
| S2 | **Real test discipline** | 80 tests, 0 failures, run in CI on every release; concentrated where logic lives |
| S3 | **Hardware-quirk engineering** | Sentinel rejection, dual-source signal fallback, disabled-SIM detection — behaviour derived from failures observed on real hardware, not theory |
| S4 | **Honest unknown handling** | `null` ≠ `0` end-to-end; unknown renders `--` rather than a false alarm |
| S5 | **Graceful degradation as architecture** | Three-transport facade; `setAvailableOverride()` makes the degraded path testable |
| S6 | **Strong privacy baseline** | No transmission, `allowBackup=false`, truncated identifiers, encrypted history store |
| S7 | **Accessibility treated as a feature** | TalkBack content descriptions, merged semantics, spoken status words, live-region discipline |
| S8 | **Documentation culture** | Four substantive docs, evidence-linked fix plans, incident post-mortems |
| S9 | **Genuine domain depth** | Per-subscription radio isolation, IMS/VoLTE/WiFi-Calling modelling, band tables with DL frequency derivation |
| S10 | **Intellectual honesty in versioning** | Deliberately re-based from `v1.2.0` to `0.9.0` rather than claiming unearned MVP status |

### Weaknesses

| # | Weakness | Evidence |
|---|---|---|
| W1 | **Shipped artifact is a debug build** | CI builds `assembleDebug`; merged manifest has `android:debuggable="true"` |
| W2 | **No signing configuration** | `release` block has R8 but no `signingConfig` → `app-release-unsigned.apk`; no shippable release exists |
| W3 | **26 MB download, 11× the optimised size** | Debug 26 MB vs release 2.3 MB |
| W4 | **`PRIVACY.md` contradicts the code on background collection** | Doc says foreground-only; `TelemetryService` is a `dataSync` foreground service |
| W5 | **"Pro Features" has no purchase path** | `MutableStateFlow(false)` + Settings switch; no billing dependency in `libs.versions.toml` |
| W6 | **Zero instrumentation tests** | `androidTest` file count is 0; 1,486-line `DashboardScreen.kt` untested |
| W7 | **Single-device validation** | All OEM fallbacks verified on one OnePlus/OPPO unit only |
| W8 | **God-object UI file** | `DashboardScreen.kt` at 1,486 lines holds five tabs, settings, glossary and shared components |
| W9 | **Spec drift unmanaged** | NFR3 ("no persistent storage") and NFR4 ("no network calls") both violated in practice; NFR5 (≤15 MB) exceeded by 73% |
| W10 | **README carries factual errors** | "71 passing" tests (actual 80); `READ_SIM_STATE` "requested on API 34+"; `NEARBY_WIFI_DEVICES` cited as the reason for `allCellInfo` |
| W11 | **Version identity is confusing** | `versionName 0.9.0`, branch `0.9.1`, tags `v1.0.0`–`v1.2.0` in git history |
| W12 | **Dev artifacts in the repository** | 30 files under `_bmad/`, plus `datasets/` and two Python scrapers |
| W13 | **No crash or field diagnostics** | Deliberate (NFR4), but means zero visibility into failures on other people's devices |

### Opportunities

| # | Opportunity | Basis |
|---|---|---|
| O1 | **Ship a signed, minified release — 26 MB → ~2.3 MB** | The optimised build already exists; it needs only a `signingConfig` |
| O2 | **Unlock Play Store distribution** | Requires W1+W2 resolved and W4 corrected; unlocks the entire mainstream channel |
| O3 | **Reposition Pro off the privileged slice** | Build the paid tier on history depth, trends, export, alerting — all Standard-Mode-capable — making Shizuku a bonus rather than a dependency |
| O4 | **Implement the diagnostic engine** | The original vision; corpus (200 threads) and glossary (39 entries) already exist and are already ID-aligned |
| O5 | **Complete the UI audit fixes 4–6** | Scoped with evidence in `UI_FIX_PLAN.md`; the colour-ramp fix also unblocks the Signal redesign |
| O6 | **Add instrumentation tests** | Closes the largest verification gap for modest effort |
| O7 | **Breadth-test the OEM fallbacks** | Every `catch` block keyed to OEM behaviour is an untested assumption on non-OPPO hardware |
| O8 | **Wire the three telephony collectors through Shizuku** | Turns existing `--` rows into real values; the plumbing is already built |
| O9 | **Productise the liveness/staleness UX** | Already better than most competitors; a differentiator worth surfacing |

### Threats

| # | Threat | Severity | Basis |
|---|---|---|---|
| T1 | **Shizuku deprecation or Android hardening** | Medium | Mitigated by the facade and graceful degradation; the commercial risk (O3) is the real exposure |
| T2 | **Platform tightening on `allCellInfo` / location** | High | Android 16 already requires FINE in practice; further restriction would gut the Signal tab |
| T3 | **Play policy scrutiny of phone-state + location** | High | Any distribution attempt invites review; W4 makes the Data Safety declaration currently inaccurate |
| T4 | **Debug-signed APK in the wild** | High | `run-as` access to a private directory containing IMSI prefix, ICCID last-4, MSISDN, cell IDs and 1 h of location-correlated history |
| T5 | **Competitive pressure on size/UX** | Medium | 26 MB sideload against apps that install in one tap from Play |
| T6 | **Bus factor of one** | High | 8,216 lines, four docs, a release pipeline and an unbuilt product vision, all single-maintainer |
| T7 | **Documentation drift compounding** | Medium | Three of four docs already contain stale or contradictory claims |
| T8 | **Trust damage from a privacy-doc error** | High | For a privacy-positioned tool, an inaccurate disclosure is a reputational class of bug, not a typo |

---

## 9. Stated plans, and their status

Four independent roadmaps exist. They do not fully agree, which is itself a finding.

### 9.1 `README.md` — status/roadmap

| Phase | Stated | Actual |
|---|---|---|
| P0 | threshold colouring, CPU load, storage fix, location perm, inset fix, RAT label | **done** |
| P1 | NR RSRQ/SINR, WiFi RSSI, latency probe, history store, Shizuku check, per-SIM caps, validation, dual-source fallback | **done** |
| P2 | charts ✅, stale/error states ✅, accessibility ✅ | **done** |
| P3 | unit tests, README, privacy doc, architecture spike | **done** |
| Future | AI diagnostic engine against a troubleshooting corpus | **not started** |

### 9.2 `docs/ARCHITECTURE.md` — evolution roadmap

1. **v1.1** — expose `MetricHistoryStore` trends in UI; export/share diagnostic dump → *trends and export are both done; this item is complete ahead of schedule.*
2. **v1.2** — build `:diagnostics`: symptom extractor + corpus index + matcher → *not started.*
3. **v1.3** — recommendation UI ("Diagnosis" tab) wired to glossary + corpus → *not started.*
4. **v1.4** — optional LLM-assisted diagnosis grounded on the corpus → *not started.*

Includes a concrete data model (`DiagnosticSymptom`, `DiagnosticResult`, `CorpusMatch`) and a four-stage pipeline design.

### 9.3 `_bmad-output/planning-artifacts/epics.md`

Defines v1.0 as **Phase 1 only** — collect and display metrics with explanatory tooltips — and explicitly defers the diagnostic engine, LLM layer and feedback pipeline. Enumerates FR1–FR14 and NFR1–NFR6.

**Reality check against NFRs:**

| NFR | Requirement | Status |
|---|---|---|
| NFR1 | render latency ≤ 100 ms | unverified, no measurement |
| NFR2 | cold start to first metric ≤ 3 s | unverified, no measurement |
| NFR3 | **no persistent telemetry storage** | **violated** — `MetricHistoryStore` persists ~1 h by design |
| NFR4 | **no analytics, no crash reporting, no network calls** | **violated** — latency probe makes a network call |
| NFR5 | **APK ≤ 15 MB** | **violated** — 26 MB debug (73% over) |
| NFR6 | no ML/vector runtime deps | met |

NFR3 and NFR4 were superseded by product decisions (trend charts need history; a latency probe needs a network call) but the spec was never amended, so the project's own contract now contradicts its shipped behaviour.

### 9.4 `docs/UI_FIX_PLAN.md` — competitive audit follow-up

| # | Fix | Status |
|---|---|---|
| 1 | bottom nav collides with gesture bar | **done**, device-verified (`7fd514a`) |
| 2 | no in-app permission rationale | **done**, device-verified (`227aa3a`, `aa3450b`) |
| 3 | unknown rendered as zero | **done** (`73648dc`) |
| 4 | Signal tab lacks scannable-list treatment | not started |
| 5 | trend charts have no scale/legend/event markers | not started |
| 6 | colour ramp used inconsistently | not started |

Order was set as 1→2→3→4→5→6; current recommendation is **6 before 4**, since the Signal redesign needs a defined semantic ramp to reference.

---

## 10. Recommendations

### P0 — Correctness and exposure (do first, small, independent)

| Ref | Action | Fixes |
|---|---|---|
| A1 | Add a `signingConfig` and make CI build + publish the **release** variant | W1, W2, W3, T4 |
| A2 | Correct `PRIVACY.md`'s background-collection and location claims to match the code | W4, T3, T8 |
| A3 | Amend NFR3/NFR4/NFR5 in `epics.md` to reflect delivered behaviour | W9 |
| A4 | Fix the README permissions table and test count | W10 |

`A1` alone converts a 26 MB debuggable sideload into a 2.3 MB signed build and removes the `run-as` exposure — the single highest-value change available.

### P1 — Structural

| Ref | Action | Fixes |
|---|---|---|
| B1 | Decide whether Pro is a product or a debug switch. If product: add Play Billing. If debug: rename it and stop calling it Pro. | W5 |
| B2 | Reprovision Pro's value onto Standard-Mode data — history, trends, export, alerting | O3, T1 |
| B3 | Wire `RadioCollector`, `SimStatusCollector`, `ImsCollector` through `ShizukuDetector`, or remove the claim | 7.5, O8 |
| B4 | Split `DashboardScreen.kt` by tab | W8 |

### P2 — Growth

| Ref | Action |
|---|---|
| C1 | Complete UI fixes 6 → 4 → 5 |
| C2 | Add instrumentation tests for the dashboard and collectors |
| C3 | Breadth-test OEM fallbacks on non-OPPO hardware |
| C4 | Build the `:diagnostics` engine per the architecture spike |

---

## 11. Appendix — verification commands

```bash
# Full test suite (JDK 17 required)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :shared-core:testDebugUnitTest :telemetry-collector:testDebugUnitTest

# Test totals from XML reports
python3 -c "import glob,xml.etree.ElementTree as ET; \
  [print(ET.parse(p).getroot().get('tests'), ET.parse(p).getroot().get('name')) \
   for p in glob.glob('**/test-results/**/TEST-*.xml', recursive=True)]"

# Protection levels of the phone-state family
adb shell dumpsys package permissions | grep -A3 "READ_BASIC_PHONE_STATE\|READ_PRECISE_PHONE_STATE"

# Confirm the shipped artifact is debuggable
grep -o 'android:debuggable="[^"]*"' \
  app/build/intermediates/merged_manifest/debug/*/AndroidManifest.xml

# What the shell UID (and therefore Shizuku) actually holds
adb shell dumpsys package com.android.shell | grep -E "READ_PRECISE_PHONE_STATE|READ_BASIC_PHONE_STATE"

# Artifact sizes
find . -name "*.apk" -exec ls -lh {} \;
```

---

*Report compiled from repository state at commit `73648dc` plus uncommitted corrections to `AndroidManifest.xml` and `docs/UI_FIX_PLAN.md`. Test results are from a local run on 2026-09-12; device observations are from a single physical OnePlus/OPPO `CPH2709` running Android 15.*

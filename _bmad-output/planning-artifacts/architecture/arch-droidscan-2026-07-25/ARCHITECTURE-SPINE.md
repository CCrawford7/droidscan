---
title: "droidscan v1.0 — Architecture Spine"
status: draft
created: 2026-07-25
updated: 2026-07-25
version: 0.1
altitude: system
applies_to: "droidscan Android application"
---

# droidscan Architecture Spine

## Design Paradigm

**Modular layered architecture with event-driven telemetry flow.** Each layer owns a single responsibility and communicates via defined interfaces (Kotlin interfaces + coroutine Flows). The UI layer never touches telemetry hardware directly; the knowledge engine never imports Android SDK types. Dependencies point inward: UI → Reasoner → (Knowledge Engine + Telemetry Collector). This lets us test, swap, or stub any layer independently.

```
┌─────────────────────────────────────────────────────────┐
│                    Dashboard UI                          │
│  (Jetpack Compose + Canvas terminal renderer)            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐ │
│  │ btop Display  │ │ Diag Report  │ │ Settings / Config │ │
│  │ (Canvas)      │ │ (Compose)    │ │ (Compose)        │ │
│  └──────┬───────┘ └──────┬───────┘ └──────────────────┘ │
└─────────┼─────────────────┼──────────────────────────────┘
          │                 │
          ▼                 ▼
┌─────────────────────────────────────────────────────────┐
│                   LLM Reasoner                           │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐ │
│  │ Anomaly       │ │ LLM Client   │ │ Tri-Tier          │ │
│  │ Detector      │ │ (API)        │ │ Post-Processor   │ │
│  └──────┬───────┘ └──────┬───────┘ └──────────────────┘ │
└─────────┼─────────────────┼──────────────────────────────┘
          │                 │
          ▼                 ▼
┌──────────────────┐ ┌────────────────────────────────────┐
│ Knowledge Engine  │ │        Telemetry Collector         │
│ (SQLite + vss)    │ │  (Kotlin Flow polling loop)        │
│ ┌──────────────┐ │ │ ┌────────────┐ ┌────────────────┐  │
│ │ Corpus Store  │ │ │ │ Standard   │ │ Power Mode     │  │
│ │ (embeddings)  │ │ │ │ Telephony  │ │ (Shizuku)      │  │
│ │ + vector      │ │ │ │ Manager    │ │ /proc, dumpsys │  │
│ │ search        │ │ │ │ Flow       │ │ Flow           │  │
│ └──────────────┘ │ │ └────────────┘ └────────────────┘  │
└──────────────────┘ └────────────────────────────────────┘
```

## Inherited Invariants

*(No parent spine — this is the root architecture for droidscan v1.0.)*

## Architecture Decisions

### AD-001: Native Android (Kotlin)

| Field | Value |
|---|---|
| **Binds** | All implementation — Kotlin language, Android SDK, Jetpack toolchain |
| **Prevents** | Cross-platform frameworks (Flutter, React Native, Kotlin Multiplatform for UI) |
| **Rule** | Target API 31+ (Android 12), min SDK 29 (Android 10) for TelephonyManager API coverage. Compile with Kotlin 2.0+. Gradle with Kotlin DSL. |
| **Rationale** | `TelephonyManager.getSignalStrength()`, `CellSignalStrength`, `TelephonyManager.isImsRegistered()`, Shizuku API — all require direct Android SDK access. Kotlin's Flow is the natural fit for continuous telemetry streams. Compose provides Canvas-level control for the custom terminal UI. |

### AD-002: Modular 5-Module Architecture

| Field | Value |
|---|---|
| **Binds** | Package structure, dependency graph, CI build order |
| **Prevents** | Monolithic single-module structure; circular dependencies between layers |
| **Rule** | Modules: `:shared-core` (base types, telemetry data classes, corpus schemas), `:telemetry-collector` (Android SDK + Shizuku), `:knowledge-engine` (corpus storage, vector search — pure JVM, no Android deps), `:llm-reasoner` (anomaly detection, LLM client, tri-tier post-processor), `:dashboard-ui` (Compose UI, Canvas renderer, bottom nav). Dependency: `:dashboard-ui` → `:llm-reasoner` → `:knowledge-engine`, `:telemetry-collector`; `:knowledge-engine` and `:telemetry-collector` depend on `:shared-core` only. |
| **Rationale** | Clean separation lets us swap the LLM provider, change the vector DB, or stub telemetry for testing without touching other layers. |

### AD-003: SQLite + sqlite-vss for Knowledge Engine

| Field | Value |
|---|---|
| **Binds** | Vector database technology; corpus import pipeline; embedding model format |
| **Prevents** | Running a separate vector DB service; using ChromaDB (15MB+ dependency) |
| **Rule** | Embedding model: `all-MiniLM-L6-v2` (ONNX format, ~23MB) bundled in APK assets. Vector search: SQLite with `sqlite-vss` extension compiled for Android (ARM64). Corpus schema: `threads` table (id, title, category, url, views, replies, is_solved, date_posted), `embeddings` table (thread_id, embedding_blob, model_version). Search: cosine similarity via vss0 virtual table. |
| **Rationale** | Android already ships SQLite; `sqlite-vss` adds ~2MB to APK vs 15MB+ for ChromaDB. On-device vector search is fast enough for 200–2000 thread corpus (sub-10ms queries). |

### AD-004: API-Based LLM (GPT-4o-mini / Claude Haiku)

| Field | Value |
|---|---|
| **Binds** | Network permission requirement; API key management; latency budget; privacy model |
| **Prevents** | On-device model in v1.0 (deferred to v1.1) |
| **Rule** | LLM client abstracts provider behind `LlmProvider` interface. v1.0 ships with OpenAI-compatible adapter (works with GPT-4o-mini, Claude API via OpenAI-compatible proxy, or any OpenAI-compatible endpoint). User provides their own API key (bring-your-own-key model). Request: telemetry context window + top-3 matched corpus threads. Response: structured JSON with `{ tier, issue_category, title, steps[], cheat_sheet_text, matched_threads[] }`. Timeout: 15s. |
| **Rationale** | API-based lets us iterate on prompt engineering without app store updates. BYOK avoids hosting costs and privacy liability. Latency concern mitigated by: 15s timeout fits within 30s budget; streaming response shown progressively. |

### AD-005: Jetpack Compose + Canvas Terminal Dashboard

| Field | Value |
|---|---|
| **Binds** | UI technology; rendering approach for real-time display |
| **Prevents** | WebView-based UI; Unity/GameEngine rendering |
| **Rule** | Two UI surfaces: (1) Terminal dashboard uses Jetpack Compose `Canvas` composable — custom drawText, drawLine, drawRect for sparklines, gauges, and grid layouts. (2) Diagnostic reports, settings, and cheat-sheet views use standard Material 3 Compose components. Navigation: bottom nav bar with 3 tabs — Dashboard, Diagnostics, Settings. |
| **Rationale** | Canvas composable gives pixel-level control for the btop aesthetic without pulling in a game engine. Material 3 gives polished UX for non-terminal screens. Single rendering thread — telemetry updates via `LaunchedEffect` keyed on flow emission. |

### AD-006: Kotlin Flow Telemetry Pipeline

| Field | Value |
|---|---|
| **Binds** | Telemetry collection architecture; data freshness; threading model |
| **Prevents** | Callback-based telemetry collection; polling via Handler/postDelayed |
| **Rule** | `TelemetryCollector` interface exposes `fun observe(): Flow<TelemetrySnapshot>`. Implementation uses `flow { while(active) { emit(snapshot()); delay(intervalMs) } }` on `Dispatchers.IO`. Radio metrics: 1s interval. Thermal/CPU: 5s interval. In-memory ring buffer: `CircularBuffer<TelemetrySnapshot>(size=60)` held in `:llm-reasoner` for context window. Buffer not persisted to disk. |
| **Rationale** | Flow is lifecycle-aware (repeatOnLifecycle in UI). Structured concurrency via coroutine scope tied to app lifecycle. Ring buffer avoids unbounded memory and gives the LLM reasoner enough context (60s at 1s intervals) to detect transient vs persistent issues. |

### AD-007: Shizuku Power Mode as Optional Enhancement

| Field | Value |
|---|---|
| **Binds** | Power mode detection; fallback behavior; permission model |
| **Prevents** | Requiring root access for any feature |
| **Rule** | Detect Shizuku availability at runtime via `ShizukuProvider.ping()` (Shizuku API 13+). If unavailable: Standard Mode only, Power Mode UI elements hidden. If available: request permission via Shizuku API. Power Mode adds: (1) kernel thermal zone reads from `/sys/class/thermal/thermal_zone*/temp`, (2) process socket stats from `/proc/net/tcp` via shell, (3) `dumpsys telephony.registry` snippets for RIL-level detail. All Power Mode reads go through `ShizukuShell` wrapper that runs commands via `Shizuku.newProcess()`. |
| **Rationale** | Shizuku is well-established (50M+ downloads), requires no root, and is Play Store compliant. Wireless ADB setup is a one-time friction the power-user audience will accept. |

### AD-008: Structured LLM Output with Deterministic Post-Processing

| Field | Value |
|---|---|
| **Binds** | LLM response format; client-side validation; Tier-2 deep link mapping |
| **Prevents** | Displaying raw LLM text to users; prompt injection surfacing in UI |
| **Rule** | LLM returns JSON only (enforced via `response_format={ "type": "json_object" }`). App-side `TriTierProcessor`: validates JSON schema, checks tier is 1/2/3, maps Tier-2 steps to OEM-specific `Intent` deep links (e.g., `Settings.ACTION_NETWORK_OPERATOR_SETTINGS`), formats Tier-3 cheat sheet as markdown. If LLM response fails validation → fallback: "Unable to classify — here are the most similar forum threads" with top-3 corpus matches shown directly. |
| **Rationale** | Structured output prevents hallucinated instructions reaching the user. OEM deep link mapping is deterministic and maintained in a config file — no LLM involvement in generating Android intents. The corpus fallback ensures the app is useful even when the LLM is unreachable. |

## Deferred

| Item | Why Deferred | Revisit Condition |
|---|---|---|
| On-device LLM (Gemma 2B / Phi-3-mini) | APK size, model download UX, quality gap vs API models | When API latency >8s consistently OR user surveys show privacy concern is a blocker |
| Multi-carrier corpus ingestion | v1.0 scope limits to EE Community data | v1.1 planning cycle |
| Historical telemetry persistence | No persistent storage in v1.0 (ephemeral ring buffer only) | When user demand for trend analysis is validated |
| Crowd-sourced anonymous telemetry | Privacy/compliance overhead not justified for v1.0 | v1.2+ depending on adoption |
| iOS support | Native Android APIs are a hard prerequisite | When cross-platform corpus format is standardised + dedicated iOS team |
| Network speed test / throughput measurement | Separate concern from radio telemetry; adds complexity to permissions model | v1.1 feature consideration |

## Open Questions

1. **LLM API key management:** Does the user configure a key in-app, or do we offer a pre-configured proxy endpoint with rate limiting? [ASSUMPTION: BYOK with optional pre-configured proxy for trial mode (5 free diagnoses/day via a shared rate-limited endpoint).]
2. **Corpus update mechanism:** How does the corpus get updated when new forum threads are scraped? Bundled in APK? Downloaded at first launch? [ASSUMPTION: Bundled in APK assets for v1.0; downloadable corpus update as v1.1 feature.]
3. **Crash reporting / analytics:** Opt-in only? What telemetry is collected about the app's own performance? [ASSUMPTION: No analytics in v1.0. Pure local tool.]

## Diagrams

### Module Dependency Graph

```
:dashboard-ui
    │
    ▼
:llm-reasoner
    │
    ├────────────────────┐
    ▼                    ▼
:knowledge-engine    :telemetry-collector
    │                    │
    └──────┬─────────────┘
           ▼
     :shared-core
```

### Telemetry Data Flow

```
TelephonyManager          Shizuku (Power Mode)
      │                        │
      ▼                        ▼
:telemetry-collector ─── merge ───→ Flow<TelemetrySnapshot>
                                          │
                                    ┌─────┴──────┐
                                    ▼            ▼
                              Dashboard UI   Anomaly Detector
                              (Canvas)           │
                                                  ▼
                                           LLM Reasoner
                                                  │
                                                  ▼
                                         Tri-Tier Processor
                                                  │
                                                  ▼
                                   ┌──────────────┼──────────────┐
                                   ▼              ▼              ▼
                              Tier 1:        Tier 2:        Tier 3:
                           Quick Fixes    OEM Settings    Cheat Sheet
```

## Seed (v1.0 Cold Start)

- **Build system:** Gradle with Kotlin DSL, version catalog (`libs.versions.toml`)
- **Min SDK:** 29 (Android 10) | **Target SDK:** 34 (Android 14) | **Compile SDK:** 35
- **Kotlin:** 2.0+ with Compose compiler plugin
- **Key dependencies:** `androidx.compose.ui` (Canvas), `shizuku:api:13+`, `sqlite-vss-android:0.1+`, `okhttp3` (LLM API client), `kotlinx-serialization-json` (LLM response parsing)
- **CI:** GitHub Actions — lint + test on PR, build APK on merge to main
- **Corpus:** 200 EE Community threads embedded via `all-MiniLM-L6-v2`, stored in SQLite + vss, bundled in APK `assets/`

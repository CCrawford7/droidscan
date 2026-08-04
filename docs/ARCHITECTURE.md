# droidscan — Architecture & AI-Engine Spike

## 1. Current architecture

Three modules with a clean dependency direction:

```
:app ──> :telemetry-collector ──> :shared-core
```

### Data flow

```
Device telemetry APIs
   │  (TelephonyManager, BatteryManager, ActivityManager,
   │   ConnectivityManager, SubscriptionManager, /proc, /sys)
   ▼
Collectors (Radio, Thermal, Storage, Power, Process,
   NetworkConfig, SimStatus, SystemInfo)
   │  each: attach(...) then read(): *Snapshot
   ▼
TelemetryCollector.observeAll()
   │  1s loop on Dispatchers.IO
   ▼
MetricSnapshot (@Serializable, single envelope)
   ├── emit → Flow<MetricSnapshot> → Compose UI
   ├── add  → CircularBuffer<MetricSnapshot>(60)   (in-memory, current session)
   └── append → MetricHistoryStore (JSONL file, ~3600 entries ≈ 1h)
```

### Key design decisions

- **Envelope pattern:** every tick builds one `MetricSnapshot` containing all eight
  sub-snapshots. Simple to serialize, persist, and later ship to a diagnostic engine.
- **Attach/read lifecycle:** collectors hold system-service references injected by
  `DroidscanApp`; `read()` is synchronous and cheap enough for the 1s cadence.
- **Optional privilege:** `ShizukuDetector` gates "Power Mode" (kernel thermal zones,
  wakelocks). Power Mode is only advertised when the Shizuku binder is live **and**
  permission is granted (service-bound check, not classpath presence).
- **Persistence is additive:** the history store never blocks or throws into the
  telemetry loop (best-effort, synchronized, pruned).

## 2. The vision gap

The stated product goal is *AI-assisted mobile-network diagnostics* — match a user's
telemetry to known issues from a real-world corpus and suggest fixes.

**What exists:** the telemetry layer above, plus an unused corpus:
- `datasets/top_100_most_asked_ee_queries.json` (100 high-view EE threads)
- `datasets/top_100_unresolved_ee_queries.json` (100 unresolved threads)
- `shared-core/src/main/assets/glossary.json` (39 educational metric entries)

**What is missing:** every component of the diagnostic engine. This doc is the spike.

## 3. AI diagnostic engine — spike design

### 3.1 Pipeline

```
MetricSnapshot (or user-entered symptom)
   │
   ▼
Symptom extractor ──> normalized symptom vector
   │                     (e.g. {network: "5G", lowRSRP: true, noData: true})
   ▼
Corpus matcher ──> ranked candidate threads (from datasets/*.json)
   │                 keyword + category + severity scoring
   ▼
Recommendation builder ──> ranked troubleshooting steps + glossary links
   │
   ▼
Rendered in UI as "Diagnosis" tab / dialog
```

### 3.2 Components

1. **Symptom extractor** (`:diagnostics` new module or `:shared-core`)
   - Maps snapshot fields to discrete symptoms: `lowRsrp`, `noImsRegistration`,
     `volteOff`, `wifiCallUnavailable`, `highLatency`, `memoryPressure`, etc.
   - Each symptom maps to canonical terms used in the corpus (title/category keywords).
2. **Corpus index**
   - Preprocess `datasets/*.json` into an in-app index (keyword → thread).
   - No network required at runtime; ship the index as an asset (e.g. a built JSON).
3. **Matcher**
   - Score threads by (a) symptom-term overlap, (b) category match, (c) whether the
     thread is solved (prefer solved).
   - Deterministic v1 (BM25-style on titles/keywords); LLM-assisted v2.
4. **Recommendation builder**
   - Map matched threads to concrete steps; link to glossary entries
     (glossary IDs already align with metric IDs, e.g. `rsrp`, `volteEnabled`).
   - Show confidence = match score.

### 3.3 Data model additions

```kotlin
@Serializable
data class DiagnosticSymptom(val id: String, val present: Boolean, val severity: Int)

@Serializable
data class DiagnosticResult(
    val symptoms: List<DiagnosticSymptom>,
    val matches: List<CorpusMatch>,   // ranked
    val recommendedSteps: List<String>,
    val generatedAtMs: Long
)
```

### 3.4 Integration points

- `TelemetryCollector.history(n)` already provides the time-series the extractor needs.
- Glossary ids should be kept in sync with symptom ids (see P2 glossary-sync work).
- New module `:diagnostics` depends on `:shared-core` and the corpus assets.

### 3.5 MCP (later)

Not needed to build v1 of the engine (it's an in-process matcher). A telemetry MCP
server makes sense later if external agents should query device state. Defer.

## 4. Evolution roadmap

1. **v1.1:** expose `MetricHistoryStore` trends in UI; export/share diagnostic dump.
2. **v1.2:** build `:diagnostics` — symptom extractor + corpus index + matcher.
3. **v1.3:** recommendation UI ("Diagnosis" tab) wired to glossary + corpus.
4. **v1.4:** optional LLM-assisted diagnosis using the EE corpus as grounding.

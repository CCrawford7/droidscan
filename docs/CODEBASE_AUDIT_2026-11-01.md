# droidscan — Codebase Audit, Efficacy Assessment & Evolution Plan

**Date:** 2026-11-01
**Branch:** `0.9.1` @ `73648dc` (plus correct, uncommitted manifest/doc fixes)
**Method:** full-source read of all 47 Kotlin files (8,216 lines) across `:app`,
`:telemetry-collector`, `:shared-core`; corpus + glossary inspection; baseline
build + unit-test execution; confirmation of a live test device (`CPH2709`, Android 15).

This document **supersedes** the day-to-day status in `TECHNICAL_REPORT.md`
(2026-09-12). The technical findings there remain accurate — the report was
correct and thorough — so this audit does not repeat them. It answers the three
questions that report left open: *does the app actually solve its stated
problem, who should build what's missing, and in what order.*

---

## 1. What the problem actually is

The product goal (from `docs/project-context.md`, the brief, and
`docs/ARCHITECTURE.md`) is not "a telemetry dashboard". It is:

> **An engineer-grade, real-time radio/system telemetry engine *paired with a
> diagnostic reasoning layer* that translates cryptic radio numbers into a
> plain-English resolution plan** — matching live device telemetry against a
> corpus of real-world network faults (200 scraped EE Community threads) and
> tiering each finding into *user-fixable / guided deep-settings / carrier-side*.

The dashboard is the **means**; the diagnostic reasoning is the **point**.

## 2. Efficacy verdict

| Layer | Status | Verdict |
|---|---|---|
| Telemetry collection (11 collectors, dual-SIM, sentinel/range validation, dual-source fallback) | **complete** | Best-in-class for a single-developer tool. Strong, tested (80 unit tests), hardware-quirk-hardened. |
| Presentation (5-tab terminal UI, rings, trends, glossary, accessibility) | **complete, minor polish outstanding** | Solid. UI fixes 4–6 remain (scannable Signal list, chart axes, colour ramp). |
| Persistence / export (encrypted history, report dump) | **complete** | Done and disciplined. |
| **Diagnostic engine** (symptom extractor → corpus matcher → recommendation builder) | **0% implemented** | The corpus (200 threads) and glossary (46 ids) exist but are unused. |
| **LLM layer** (grounded diagnosis) | **0%** | Deferred to v1.4; fine to defer. |

**Efficacy ≈ 50%.** The app currently solves "show me the real numbers" — the
half of the problem the competition also solves. It does not yet solve "tell me
what's wrong and what to do", which is the entire differentiation and the
reason the corpus was scraped in the first place. Every `docs/*.md` and the
roadmap agree this is the gap; none of them have started it.

The single highest-leverage act of "improvement and evolution" is therefore to
**build the `:diagnostics` engine** (architecture spike `docs/ARCHITECTURE.md`
§3 already specifies the pipeline and data model).

## 3. Subagent selection

The referenced catalog (aitmpl.com/agents, "600+ AI Agents for Claude Code") is
a JS-rendered page — its list cannot be statically enumerated, but this
repository already ships the catalog's agent definitions under
`.claude/agents/*.md`, and those are the agents available here. The mapping
below is therefore *against the agents this repo actually has installed*.

| Agent | Role in droidscan's evolution | When |
|---|---|---|
| **data-analyst** | Analyse the 200-thread EE corpus → canonical symptom taxonomy + title-keyword → symptom mapping (the input that makes matching non-random). | Phase 1, in parallel with engine scaffolding. |
| **backend-architect** | Nail the `:diagnostics` contract (module boundary, `DiagnosticSymptom`/`CorpusMatch`/`DiagnosticResult` shapes, matcher scoring) against the existing `:shared-core` layering. | Phase 1 design gate. |
| **architect-reviewer** | Verify the new module honours the one-way `:app → :telemetry-collector → :shared-core` direction and doesn't drag Android deps into pure logic. | Phase 2 review gate. |
| **code-reviewer** | Adversarial review of the engine + UI before merge (correctness, null-handling parity with the `-- ≠ 0` convention). | Phase 2 review gate. |
| **test-engineer** | Unit tests for extractor/matcher; close the **zero-instrumentation-test** gap (the report's W6). | Phase 2, after engine lands. |
| **ui-ux-designer** | The "Diagnosis" tab and UI fixes 4–6 (colour ramp → scannable Signal list → chart axes). | Phase 3. |
| **documentation-expert** | Correct `PRIVACY.md` (background-collection + location claims), README test count/permissions, `epics.md` NFR3/4/5. | Phase 0 (already scoped, low-risk). |
| **security-auditor** | Release signing + privacy posture (report A1/A2/T4). | Phase 4 — **needs the owner's keystore**; can't be done unattended. |
| **debugger** | Device-specific issues surfaced by the deploy loop. | On demand. |

**Note on "frontend-developer":** the catalog's frontend agent targets
React/Vue/Angular. droidscan's UI is Jetpack Compose (Kotlin), so UI *implementation*
is done by the primary agent (me) and *design review* by `ui-ux-designer` —
the web-stack agent is not the right executor here.

## 4. Execution plan (what "begin working on it" means)

Ordered by (value × safety), committed and pushed to `origin/0.9.1` at every
step, with an APK deploy to the device at each user-visible milestone:

1. **Phase 0 — clean baseline.** Commit the pending correct manifest/doc work
   (`READ_SIM_STATE` removal note, `UI_FIX_PLAN.md` follow-up, `TECHNICAL_REPORT.md`).
   Fix the toolchain pointer (SDK path) locally.
2. **Phase 0 — doc correctness.** `PRIVACY.md` background/location claims,
   README test count + permissions table, `epics.md` NFR3/4/5. *(documentation-expert scope)*
3. **Phase 1 — `:diagnostics` engine.** New pure-Kotlin module: symptom
   extractor, corpus asset (built from `datasets/*.json`), deterministic
   matcher, recommendation builder — each with unit tests. *(data-analyst feeds
   the taxonomy; backend-architect gates the contract.)*
4. **Phase 2 — Diagnosis UI.** A "Diagnosis" tab/sheet that runs the engine on
   the live `MetricSnapshot` and renders ranked findings + plain-English steps,
   tiered per the original vision. Deploy + verify on device.
5. **Phase 3 — UI fixes 6 → 4 → 5** per `UI_FIX_PLAN.md`, then `DashboardScreen.kt`
   tab-split (W8).
6. **Phase 4 — distribution** (deferred on the owner's keystore): signed release,
   CI `assembleRelease`, Data-Safety alignment.

The first two phases are unambiguous and begin immediately below.

# Design Prompt — droidscan v1 UI (for app-developer assessment)

> **To:** the agent implementing droidscan v1 (Jetpack Compose + Material 3)
> **From:** UX planning (workspace `ux-droidscan-2026-08-04`)
> **Purpose:** assessment copy — read the direction, verify feasibility against the current Compose build, and respond with concerns/questions before implementation locks.

---

## 1. Product intent (why this design exists)

droidscan is an **engineer-grade Android system health dashboard** — "btop for mobile." It replaces the hostile `*#*#4636#*#*` diagnostic screen with a **live, at-a-glance, trustworthy** view of signal, power, system, and SIM state. The register is **diagnostic instrument**, not consumer fitness: no gamification, no ads, no gatekeeping, no simplified "4 bars."

**Two named users the design must serve:**

- **Alex, field engineer** — troubleshooting a client's device at a site. Opens droidscan, confirms "weak signal + IMS failure" in **<10 seconds**, screenshots Overview + Radio for a ticket.
- **Sam, power user** — stress-testing a phone mid-game. Watches **thermal throttling and CPU in real time**, correlates throttling banner + 42°C + a hot process, decides a fix without blaming the carrier.

**Scope:** v1 only — five tabs (Overview, Radio, System, Power, SIM), glossary, settings/permissions, empty states. **No** LLM/diagnostic surfaces.

---

## 2. The chosen direction (what you are assessing)

The UX run explored three directions (Terminal Purist / Diagnostic Cockpit / Signal Clarity). The owner chose a **blend**:

> **Direction C (Signal Clarity) visual language × the tiered-list structure.**

- **Visual language (C):** deep telecom navy, a single cyan accent, sans uppercase section labels + mono values, airy rounded tier cards, calm confidence.
- **Structure (tiered list):** the Overview reads as **SIGNAL / POWER / SYSTEM** status-tiered metric lists — and the **same tier-card pattern applies to all five tabs**.

This is a **reskin + hierarchy pass over the existing `DashboardScreen.kt` content** — no metrics are added or removed. Every row in the mocks traces to a field the current build already collects. Where a tab renders a state (permission denied, throttling, no SIM, Shizuku absent), it is explicitly specced below.

**Visual references — open these before assessing:**

- `.working/key-tabs-signal-clarity.html` — all five tabs in the chosen blend (the canonical mock)
- `.working/color-themes.html` — the 5-palette board; **Signal Clarity** (4th panel) is the chosen register
- `.working/directions.html` — the 3-direction board; **C · Signal Clarity** (3rd column) is the chosen personality

---

## 3. Token specification

### 3.1 Color tokens (replaces the GitHub-dark palette in `Theme.kt`)

| Semantic | Hex | Replaces | Notes |
|---|---|---|---|
| `bg` | `#0B1220` | `TerminalBackground #0D1117` | Screen canvas |
| `surface` | `#101A2E` | `TerminalSurface #161B22` | Tier-card fill |
| `raised` | `#16233C` | `TerminalSurfaceVariant #21262D` | Elevated surfaces, tab-bar scrim |
| `border` | `#1E2D4A` | `TerminalBorder #30363D` | Card/track hairlines |
| `ink` | `#EAF1FA` | `TerminalTextPrimary #C9D1D9` | Primary text (values) |
| `ink2` | `#8CA0BE` | `TerminalTextSecondary #8B949E` | Labels, secondary text |
| `accent` | `#22D3EE` | `TerminalAccentBlue #58A6FF` | The ONE cyan — tier labels, brand, expand affordance, active tab |
| `ok` | `#34D399` | `TerminalAccentGreen #4CAF50` | Threshold tier: green |
| `warn` | `#FBBF24` | `TerminalAccentYellow #FFC107` | Threshold tier: yellow |
| `crit` | `#F87171` | `TerminalAccentRed #F44336` | Threshold tier: red + crisis |
| `live` | `#60A5FA` | `TerminalAccentCyan #39D2C0` | "streaming/live" affordance, Power Mode badge |
| `peak` (thermal only) | `>70°C crit · >50°C warn` | `TerminalAccentOrange #FF8800` | Peak-temp color ramping in Power thermal tiers |

**Rules:** `ok/warn/crit` must hold contrast against both `bg` and `surface`. `accent` is reserved — never used for status; status is `ok/warn/crit`. Tints for chips/banners derive from the status color at ~10–14% alpha.

### 3.2 Typography

| Role | Face | Size / weight | Spacing | Color |
|---|---|---|---|---|
| Tier label (uppercase) | Sans | 11sp · 600 | 0.09em | `accent` |
| Row label | Sans | 12sp · 400 | — | `ink2` |
| Row value | Mono | 13sp · 600 | — | `ink` |
| Dual cell label | Sans | 11sp | — | `ink2` |
| Dual cell value | Mono | 12.5sp · 600 | — | `ink` |
| Live readout (tier header right) | Mono | 10sp | — | `live` or `ink2` |
| Brand wordmark | Mono | 17sp · 700 | — | `accent` |
| Banner title / subtitle | Sans | 12.5sp·600 / 10.5sp | — | crit-tinted / `ink2` |
| Chip text | Sans | 9sp · 600 | 0.03em | status color |
| Expand affordance | Mono | 10sp | — | `accent` |

**Mapping note:** the current build sets *everything* in mono (`FontFamily.Monospace`). The design splits it — **labels sans, values mono**. This is the single biggest typographic change.

### 3.3 Shape & spacing

| Token | Value |
|---|---|
| Tier-card radius | 14dp |
| Tier-card padding | 12dp top / 14dp horizontal / 14dp bottom |
| Screen horizontal padding | 14dp |
| Gap between tiers | 12dp |
| Row rhythm (vertical gap between rows) | 9dp |
| Dual-pair grid | 2 cols, 9dp row / 14dp col gap |
| Gauge track | 6dp tall, 3dp radius, `border` track |
| Status dot | 7dp circle |
| Signal bars | 4dp wide, `warn`-colored active fills, `border` inactive |
| Banner | 12dp radius, 10/14dp padding |
| Chip | 5dp radius, 2/6dp padding |
| Tab touch target | ≥44dp tall |

---

## 4. Component system

The tier card is the **load-bearing container**. It replaces `MetricCard` as the unit of layout everywhere.

### 4.1 TierCard (replaces `MetricCard`)
- Fill `surface`, 1dp `border` hairline, 14dp radius, internal padding per §3.3.
- **Header:** uppercase sans label (cyan) + optional right-aligned **live readout** (mono, `live` for "streaming" indicator, or a status value like `42°C`).
- No drop shadow, no elevation — hierarchy comes from tone and typography, never shadow.
- Optional hairline `border` divider above the last row when a summary line is emphasized (e.g. Peak overall in Power).

### 4.2 MetricRow (the existing `MetricRow`, re-skinned)
- Label left (`ink2` sans 12sp), value right (mono 13sp 600 `ink`).
- **Status dot** (7dp) immediately before the value, colored by threshold tier (`ok/warn/crit`). Dot is decorative — **the value text is the status** (a11y: dot alone never conveys state).
- Full-row tap → glossary bottom sheet (unchanged interaction; visual changes only).

### 4.3 DualPair (the existing `DualMetricRow`)
- 2-col grid; label above value; value ellipsizes on overflow.
- Status dots allowed per value.

### 4.4 Gauge (`BarGauge`)
- Rounded 6dp track in `border`; fill color = status tier of the ratio.
- Optional label row above (sans `ink2`) — value may ride in the header live-readout instead.

### 4.5 SignalBars (`SignalStrengthBar`)
- 5 bars; filled count per RSRP tier (existing logic); active bars `ok` (≥4) / `warn` (3) / `crit` (≤2); inactive `border`.
- RAT label (`NR`/`LTE`/`3G`/`2G`/`WiFi`/`SIG`, mono 9sp `ink2`) + live dBm value in the bar color.

### 4.6 CrisisBanner (Power tab, top)
- Fill `crit` @ 10%, 1dp `crit` @ 32% border, 12dp radius.
- Warning glyph + **title** (`Thermal throttling active`) + **subtitle** (`Peak 52.1°C — CPU cores hottest`).
- Rendered only when `thermal.isThrottling == true`. **Top of Power only**; Overview carries a compact THROTTLING chip instead (see §4.7).

### 4.7 Chip (status / capability)
- 9sp semibold, status color text on status @ ~12% tint, 5dp radius.
- Variants: `THROTTLING` (crit), `POWER MODE` / `SHIZUKU` (live), `PROVISIONED` (ok).
- Used: Power Mode badge in header, throttling chip in Overview's Power tier, Shizuku status in System/Overview, provisioning in SIM.

### 4.8 Expand affordance (raw sensors)
- Mono 10sp cyan: `▼ Show all N raw sensors` / `▲ Hide raw sensors`. Toggles the raw sensor rows (existing behavior).

### 4.9 App header
- Brand wordmark mono 17sp cyan, right: **Power Mode badge** (when Shizuku live) + device model caption (mono 11sp `ink2`).
- **Gear icon** (top-right, 48dp target) → Settings (permissions + Shizuku status).

### 4.10 Tab bar
- 5 tabs, active = cyan, inactive `ink2`, 10sp semibold, ≥44dp tall, top indicator (2dp cyan underline) on the active tab. Background `raised` scrim, top hairline `border`.

### 4.11 Glossary bottom sheet (replaces the current `AlertDialog`)
- `ModalBottomSheet`, swipe-to-dismiss (decision already made — do **not** revert to AlertDialog).
- Title: term (bold, cyan). Sub: full acronym (`REFERENCE SIGNAL RECEIVED POWER`).
- Structured sections with uppercase sub-labels: `WHAT IT MEASURES` · `CURRENT CONTEXT` (status dot + current value + plain-language) · `HEALTHY RANGE` · `COMMON CAUSES`.

### 4.12 Empty / degraded states
- **Location denied (Radio):** title `Location required for cell metrics` (warn), body explains Radio needs location to read cell identity, primary pill `Open Settings` (accent fill, dark text). Tiers other than cellular signal still render.
- **No SIM (SIM tab):** `No SIM cards detected`, single row.
- **Shizuku unavailable (Power tab):** a MODE tier shows `Standard Mode — no kernel data`; **raw sensors, process list, and wakelocks are hidden**. No nag banner — Shizuku guidance lives in Settings only.

---

## 5. Per-tab structure (canonical, from the mocks)

| Tab | Tiers (top → bottom) |
|---|---|
| **Overview** | SIGNAL · POWER · SYSTEM |
| **Radio** | CELLULAR SIGNAL · SERVING NETWORK · IMS / REGISTRATION · NETWORK CONFIG |
| **System** | CPU · MEMORY · WAKELOCKS (Power Mode) · STORAGE · DEVICE |
| **Power** | [CrisisBanner] · BATTERY · RADIO / SENSORS · THERMAL · MODE · TOP PROCESSES |
| **SIM** | SIM 1 · EE · IDENTITY · CAPABILITIES · CURRENT CONNECTION (+ No-SIM state) |

Open IA question for you (§7): the current Overview also carries SUBSCRIBER and STORAGE cards; in the chosen three-tier Overview they are parked on Radio/SIM/System. Weigh in on whether they return.

---

## 6. Do's and don'ts

**Do**
- Mono **only** for values; labels are sans.
- Status always as **text value + dot**, never dot alone.
- Status color at the periphery (dot, bar fill, chip, banner) — keep `accent` out of status duty.
- Live values update **in place**; no spinners on the steady-state dashboard.
- Calm motion, no flourish; scroll only.
- Honor large font scale and TalkBack: every metric row announces label + value + status.
- ≥44dp touch targets on tabs and tappable rows.

**Don't**
- Revert to `── SECTION ──` mono rules / all-mono aesthetic (that is direction A, rejected).
- Light theme in v1.
- Gamify, streak, encourage; no marketing copy (strings come from the journeys, voice-checked).
- Hide status behind color alone.
- Nag banners for Shizuku; ads; "4 bars" oversimplification.

---

## 7. What I need from you (assessment checklist)

Answer each with **approve / adjust + reason / concern**, plus anything not covered:

1. **Feasibility** — do these tokens/components map cleanly onto the existing `Theme.kt` color scheme and `MetricComponents.kt`? Confirm the rename table in §3.1 is complete.
2. **Performance** — live telemetry recomposes the tier lists each snapshot. Any recomposition-scope or Canvas risks you foresee (sparklines, signal bars, gauge)?
3. **Accessibility** — contrast of `ok/warn/crit` against `bg` and `surface`; TalkBack labels on MetricRow/DualPair; behavior at largest font scale (truncation risk on DualPair values, banner, chips).
4. **State coverage** — are §4.12's empty/degraded states sufficient for v1? Anything in the collectors that produces a state we haven't specced (e.g. null sensor data, throttling with no zones)?
5. **IA fit** — does every tab's content fit the tier structure without awkward leftovers? Specifically weigh in on the **Overview SUBSCRIBER/STORAGE** question in §5.
6. **Glossary** — confirming `ModalBottomSheet` over the existing `AlertDialog` is acceptable to implement.
7. **Open questions** — anything the developer needs answered before starting (font/sp scale confirmation, tab-bar M3 NavigationBar vs custom, etc.).

Deliverable: a response to this checklist. Once resolved, the UX spines (`DESIGN.md`, `EXPERIENCE.md`) finalize against your answers.

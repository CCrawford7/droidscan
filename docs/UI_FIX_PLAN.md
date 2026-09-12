# UI Fix Plan — competitive audit follow-up

Source: on-device audit of 8 apps (2026-09-12), captured via ADB
(`screencap` + `uiautomator dump`) at 420dpi. Reference apps and the
specific pattern each contributes:

| App | Pattern worth taking |
|---|---|
| Network Survey | Redundant RSSI encoding (number + colour + bar), header meta-strip, segmented view toggle, blank≠zero |
| NetMonster | Per-permission `GRANTED` badges + "name the scary capability, then defuse it" copy |
| Speedtest | One hero element per screen |
| Windy | Always-visible legend with numeric ticks; scrubber annotated with data density |
| Shortstop | Written empty states; floating nav pill |

## Fix 1 — Bottom nav collides with the system gesture bar  `[BUG]`
**Evidence:** `TabBar` labels sit at y=881–902dp on a 905.9dp screen (4dp
clearance). `dumpsys window` reports `overrideNonDecorInsets=[0,120][0,42]`
(16dp bottom inset). The gesture pill renders on top of the "System" label.
`enableEdgeToEdge()` is enabled but nothing applies `navigationBars`.
**Fix:** pad the TabBar *content* while letting its background fill the inset.
**Files:** `ui/DashboardScreen.kt` (`TabBar`)

## Fix 2 — No in-app permission rationale
**Evidence:** `MainActivity.onCreate` fires the system permission dialogs
before `setContent` — the user gets bare OS prompts with no explanation.
NetMonster's screen shows the pattern: icon + name + `GRANTED` badge +
one-line plain-English reason, then a single full-width CTA.
**Fix:** pre-permission rationale screen; request only on explicit tap.
**Files:** `ui/MainActivity.kt`, new `ui/PermissionsScreen.kt`

## Fix 3 — Unknown rendered as zero
**Evidence:** with no SIM the Overview shows `0 mA`, `0.0°C`, `0h 0m`, `0%`.
Absent data is not a measurement of zero. Network Survey leaves absent
satellite fields blank rather than printing 0.
**Fix:** distinguish null from 0 end-to-end; render "—" for unknown.
**Files:** UI value formatters + `shared-core` snapshot fields

## Fix 4 — Signal tab lacks the scannable-list treatment
**Evidence:** Network Survey's Wi-Fi list encodes each RSSI three ways
(number, colour, bar) with right-aligned values, a sort chip, a scan
counter and a live dot. droidscan renders flat label/value rows.
**Fix:** colour ramp + bar meter + right-aligned value column + meta strip.
**Files:** `ui/DashboardScreen.kt` (`SignalTab`), `ui/MetricComponents.kt`

## Fix 5 — Trend charts have no scale
**Evidence:** Windy keeps its colour ramp on screen with numeric ticks;
droidscan's RSRP/SINR trends show a line with no axis, no legend and no
event markers.
**Fix:** numeric y-axis ticks + threshold legend; mark handovers/drops.
**Files:** `ui/TrendCharts.kt`

## Fix 6 — Colour ramp used inconsistently
**Evidence:** `0%` battery renders red, `0%` RAM renders green. The cyan
accent doubles as brand and as "active". No single documented scale.
**Fix:** one semantic scale (ok/warn/crit/unknown), applied to values.
**Files:** `ui/theme/Theme.kt` + call sites

## Order
1 → 2 → 3 → 4 → 5 → 6. Fixes 1–3 are small and independently shippable;
4 is a redesign and should land behind review.

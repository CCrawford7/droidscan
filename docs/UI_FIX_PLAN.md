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

## Status

| # | State | Commit |
|---|---|---|
| 1 | done, verified on device | `7fd514a` |
| 2 | done, verified on device | `227aa3a`, `aa3450b` |
| 3 | done, verified on device | this commit |
| 4 | not started | — |
| 5 | not started | — |
| 6 | not started | — |

### Incident found while implementing fix 2

The first cut of the permission gate required *every* spec in
`droidscanPermissionSpecs()` to be granted before the dashboard would render.
`READ_SIM_STATE` and `READ_PRECISE_PHONE_STATE` are declared in the manifest
but are **privileged**, not runtime, permissions — they appear in neither the
install nor the runtime permission set, so `checkSelfPermission` returns DENIED
permanently and `requestPermissions` ignores them. That made "all granted"
unreachable and would have trapped every user on the rationale screen forever.

These two are now excluded from the request list, and `MainActivity` filters
specs through `isRuntimeRequestable()` (dangerous protection level) as a
guard — logging loudly if anything is ever dropped, so the failure can't hide
again.

A follow-up probe (2026-09-12) showed the two are **not the same kind of
thing**, and the original note above was wrong to treat them alike:

* `READ_PRECISE_PHONE_STATE` is real. `pm list permissions -f` reports it as
  `package:android`, i.e. declared by the framework itself. It is not
  user-grantable, but the **shell** package holds it by default —
  `dumpsys package com.android.shell` reports `granted=true`. That is the
  entire reason a Shizuku-routed call succeeds: Shizuku executes as uid 2000
  (`com.android.shell`), which legitimately owns the permission. It is not a
  bypass, it is borrowed identity.
* `READ_SIM_STATE` is a **ghost**. It appears nowhere in `pm list permissions`,
  is absent from `/system/etc/permissions`, and is not in the shell package's
  grant list either. No such permission exists on this platform, so the
  manifest line is a no-op. A declaration for a permission that does not exist
  cannot ever help, and it misleads anyone reading the manifest into thinking
  the app can ask for SIM state. Deleted.

**Consequence for the design.** Protection levels are AOSP-defined and
therefore identical on every OEM — this is not a OnePlus quirk. But the
practical reach of that ceiling is far narrower than it first looks, and an
earlier draft of this note overstated it:

* `READ_PRECISE_PHONE_STATE` is `prot=signature|privileged`
  (`dumpsys package permissions`), so it is unreachable in Standard Mode — but
  it only gates the **IMS** path: `ImsMmTelManager` registration
  state/transport/settings, and the `isImsRegistered` reflection.
* It does **not** gate cell identity. RSRP/RSRQ/SINR, band, PCI, TAC, CI,
  EARFCN, bandwidth and derived DL frequency all come from
  `TelephonyManager.getAllCellInfo()`, which requires **location** — a
  one-tap runtime permission.
* `READ_BASIC_PHONE_STATE` is `prot=normal` and install-granted (verified
  `granted=true` on `com.droidscan.app`), so network type and call state need
  no prompt at all.

The gap Shizuku actually closes is therefore **IMS registration detail plus
the kernel-level reads already gated as Pro** (thermal zones, wakelocks,
storage wear, per-UID CPU) — not "accurate readings" in general, and
specifically not cell identity. `RadioCollector`, `SimStatusCollector` and
`ImsCollector` currently return null for the privileged IMS fields on API 34+
and lean on CarrierConfig fallbacks; routing them through Shizuku would turn
those `--` into real values. Until then the UI must keep labelling that
section as requiring Shizuku (it does — `DashboardScreen` lines 576, 768),
and no Standard-Mode copy should promise IMS registration state.

---
title: "droidscan *(working title)* — Android Network Diagnostic Engine"
status: reviewed
created: 2026-07-25
updated: 2026-07-25
version: 0.3
author: X1
---

# Product Brief: droidscan *(working title)*

## Executive Summary

droidscan is an **engineer-grade, real-time system and radio telemetry engine for Android**, paired with an LLM diagnostic reasoning layer. It bridges the gap between cryptic, low-level Android radio numbers (−112 dBm RSRP, SINR −4 dB, IMS NOT_REGISTERED, ePDG tunnel drops) and real-world user problems (dropped calls, silent missed calls, "4 bars of 5G but 0 data speed," RCS stuck on connecting, border roaming lockouts).

Think **btop / htop for mobile networks and radio protocols** — but with an LLM layer that translates raw telemetry into a plain-English resolution plan in under 30 seconds.

The engine ingests a curated corpus of real-world carrier forum threads (starting with EE Community Forum's top 100 most-asked and top 100 unresolved issues), vectorizes them, and matches live device telemetry against known issue patterns. Every diagnostic report categorises the problem into one of three tiers: user-fixable under 60 seconds, guided deep-settings redirect, or carrier-side issue requiring a verbatim Tech Support Cheat Sheet.

## The Problem

Mobile network diagnostics on Android are broken at every layer:

- **The Android Network Settings UI** is too simplified — it shows "4 bars of 5G" but hides band congestion, RSRQ noise, IMS registration status, and handover history. Power users and field engineers get zero actionable information.
- **Ping / traceroute tools** only confirm a packet dropped — they cannot explain *why* (radio interference vs. background socket leak vs. thermal throttling).
- **Carrier support apps** are gatekept, bloated with ads, and default to generic "turn it off and on again" deflection scripts. They are not carrier-agnostic.
- **`*#*#4636#*#*` testing menus** dump dense, hostile diagnostic text with no explanation, no action plan, and no historical tracking.
- **Forums like EE Community** contain the actual solutions — 100s of millions of views across threads about exact problems — but there is no tool that maps live telemetry to this corpus.

The consequence: millions of users escalate fixable problems to carrier support, endure long hold times, or simply live with broken connectivity. Field engineers waste hours on problems a 30-second diagnostic would identify.

## The Solution

droidscan is a standalone Android application (with optional Shizuku Power Mode) that:

1. **Reads live radio telemetry** — RSRP, RSRQ, SINR, cell ID, band (n71, B41, etc.), IMS registration state, ePDG tunnel health, thermal zones, CPU core loads, socket states — continuously, in real time.
2. **Vector-matches anomalies** against a curated corpus of real-world forum threads and carrier protocol specs to identify the known issue pattern.
3. **Generates a Tri-Tier resolution plan:**
   - **Tier 1 — User-Fixable:** Quick fixes under 60 seconds (toggle airplane mode, clear APN cache, reset network stack).
   - **Tier 2 — Guided Settings Redirect:** Deep navigation into OEM-specific menus (disabling MIUI/OneUI battery optimisations, forcing LTE-only mode, resetting IMS).
   - **Tier 3 — Carrier / Tech Support Required:** Issues that cannot be fixed on-device — generates a verbatim Tech Support Cheat Sheet to read to carrier agents.
4. **Displays a high-density real-time dashboard** (terminal-style, btop-inspired) showing active bands, thermal zones, core loads, and socket states — refreshed continuously.

The app operates in two modes:
- **Standard Mode:** Play Store compliant, uses native Android `TelephonyManager`, `NetworkStatsManager`, `BatteryManager`.
- **Power Mode (Shizuku):** Unlocks root-like process listing, kernel thermal zones, and socket-level bandwidth tracking via Wireless ADB — no rooting required.

## What Makes This Different

| Existing Tool | Limitation | How droidscan Fixes It |
|---|---|---|
| Android Network Settings UI | Dumbed-down; hides real metrics | Raw radio telemetry (RSRP, RSRQ, SINR, cell ID, handoffs) in real time |
| Ping / Traceroute | Packet drop only, no root cause | Correlates network performance with CPU, thermal, and radio health |
| Carrier Support Apps | Bloated, gatekept, carrier-specific | Carrier-agnostic. Zero-bullshit diagnosis. |
| `*#*#4636#*#*` Menu | Dense text dump, no guidance | LLM reasoning → 30-second plain-English plan |
| Forum threads (EE, etc.) | Contains the answer but no tool queries it | Vector-matched corpus links telemetry → known issues |

## Who This Serves

**Primary — Tech Enthusiasts & Power Users (40%):** People who want high-density, real-time terminal metrics on their phone. They understand what RSRP means but lack a tool that surfaces it beautifully and connects it to resolutions. They are the early adopters and evangelists.

**Primary — Field Engineers & IT Admins (30%):** Professionals troubleshooting remote connectivity, SIM/eSIM provisioning, or signal handoffs on client devices. They need carrier-agnostic, scriptable diagnostics they can trust.

**Secondary — Everyday Frustrated Subscribers (30%):** Users experiencing dropped calls, dead zones, RCS activation loops, or roaming glitches. They do not want a terminal — they want a candid, step-by-step fix or an exact script to read to support. The LLM layer serves this group directly.

## Success Criteria

1. **Diagnostic accuracy:** ≥85% of generated Tier-1/Tier-2 resolutions resolve the user's issue on first attempt (measured via in-app feedback).
2. **Time-to-resolution:** Median time from app open to resolution plan ≤30 seconds.
3. **Corpus coverage:** Knowledge engine covers ≥90% of the top 100 most-viewed EE Community network threads with vector-matched patterns.
4. **User satisfaction:** ≥4.0 star rating on Play Store after 90 days.
5. **Carrier-agnostic:** Works on EE, Vodafone, O2, Three, and at least 3 non-UK carriers without modification.

## Scope

### In — v1.0
- Real-time radio telemetry display (RSRP, RSRQ, SINR, cell ID, band, IMS state)
- Standard Mode (Play Store compliant) using native Android APIs
- Knowledge engine with EE Community corpus pre-loaded
- LLM diagnostic layer generating Tri-Tier resolutions
- btop-inspired terminal dashboard UI
- Tech Support Cheat Sheet export (plain text / PDF)
- Shizuku Power Mode (Wireless ADB, no root)

### Explicitly Out — v1.0
- iOS support
- Carrier-side integration (API access to carrier OSS/BSS)
- On-device fine-tuning of the LLM
- Crowd-sourced telemetry / anonymised data sharing
- Non-EE forum corpus ingestion (v1.1 candidate)
- Network speed test / throughput measurement (bandwidth measurement is a separate concern from telemetry)

## Vision

In 2–3 years, droidscan becomes the **definitive open diagnostic layer for mobile networks** — the first tool a user reaches for when their phone acts up, before they call support, before they Google, before they factory reset.

It grows to support:
- Multi-carrier corpus ingestion (automatically scrapes and indexes every major carrier forum)
- Community-contributed diagnostic patterns and resolution scripts
- Integration with MDM platforms for enterprise fleet network health monitoring
- Historical telemetry trending ("your signal at home degraded 40% over the last 3 months — here's the pattern")
- OEM-specific diagnostic modules (Samsung Members integration, Pixel Diagnostics, etc.)

droidscan does not replace carrier support — it makes carrier support *effective* on the first call, because the user arrives with a precise diagnostic report and a Tier-3 cheat sheet the agent cannot deflect.

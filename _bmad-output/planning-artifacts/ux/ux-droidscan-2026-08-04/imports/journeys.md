# Key Flows — Named Protagonist Journeys

## Flow 1 — Field check (Alex, field engineer, client site)

**Context:** Alex is troubleshooting a client's Samsung Galaxy on EE. The client reports "calls drop in the basement." Alex needs RSRP, IMS registration, and band info in under 10 seconds.

1. Alex opens droidscan on the client's device (cold start).
2. Overview tab loads with live metrics — signal section shows RSRP −112 dBm (yellow dot), IMS `NOT_REGISTERED` (red).
3. Alex taps **Radio** tab for full cellular detail: RSRQ −14 dB, SINR 2 dB, band n78, cell ID 12345678.
4. Alex taps **RSRP** row — glossary bottom sheet opens: healthy range, current value context ("Weak — may cause dropped calls indoors").
5. Alex screenshots Overview + Radio tabs for the ticket.
6. **Climax:** Alex confirms the issue is weak signal + IMS failure, not a device fault — actionable diagnosis in 10 seconds without dialing *#*#4636#*#*.

**Failure path:** Location permission denied → Radio section shows "Location required for cell metrics" with link to Settings.

---

## Flow 2 — Thermal watch (Sam, power user, gaming session)

**Context:** Sam is stress-testing a new phone while gaming. They want to watch thermal throttling and CPU load in real time with Shizuku Power Mode enabled.

1. Sam opens droidscan; header shows device model + "Power Mode" badge (Shizuku active).
2. Sam taps **Power** tab — red throttling banner at top: "Thermal throttling active."
3. Battery gauge shows 67%, temperature 42°C (yellow), CPU avg 78%.
4. Sam expands **Raw thermal sensors** — sees skin, battery, GPU zone temps.
5. Sam scrolls to **Top processes** — `com.game.example` at 34% CPU.
6. Sam taps **Battery temperature** — glossary explains healthy range and throttling triggers.
7. **Climax:** Sam correlates throttling banner + 42°C + high CPU process — decides to reduce game settings, not blame the carrier.

**Failure path:** Shizuku unavailable → Power tab hides raw sensors and process list; shows "Standard Mode — connect Shizuku for deeper metrics" in Settings only, not as a nag banner.

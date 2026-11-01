# droidscan — Privacy & Data Handling

**Effective date:** 2026-08-04

## What this app collects

droidscan reads device telemetry to help diagnose mobile-network problems. All data is
collected **on the device only** and **is never transmitted** to any server — with one
explicit exception, the opt-in speed test described below. There is no account, no
analytics SDK, and no background upload of telemetry.

| Data | Examples | Purpose |
|---|---|---|
| Radio/cellular | RSRP, RSRQ, SINR, bands, cell ID, TAC, network PLMN, roaming state | signal diagnostics |
| Subscriber (SIM) | home PLMN, carrier name, ICCID (last 4 digits), IMSI (MCC-MNC prefix only), MSISDN | SIM provisioning status |
| Network config | IP addresses, DNS, WiFi SSID, WiFi RSSI, APN/carrier label, VPN/hotspot state, probe latency | connectivity diagnostics |
| Device | model, Android version, build, kernel, security patch, uptime, resolution | device context |
| Power/thermal | battery %, health, temperature, charge/drain, thermal zones | thermal & power diagnostics |
| CPU/memory/storage | per-core load, RAM pressure, storage used/free, top apps | system health |

## Where it lives

- The in-app dashboard holds only the current snapshot and a 60-entry in-memory ring buffer.
- A **metric history file** (`metrics_history.jsonl`, up to ~3600 snapshots ≈ 1 hour) is
  written to the app's private `filesDir`. It is not readable by other apps.
- `android:allowBackup="false"` — metric history is **not** included in Android Auto Backup
  or device-to-device migration.

## Privacy safeguards

- **Truncated identifiers only:** ICCID shows the last 4 digits; IMSI shows only the
  MCC-MNC prefix (e.g. `[310-XXXX-XXXX]`); MSISDN is only displayed if the platform exposes it.
- **No location used for positioning.** Location access (fine in practice on Android 16)
  is requested solely because Android requires location-level access for `allCellInfo` on
  API 33+; the app does not use GPS or record the device's position.
- **Background collection via a foreground service.** Telemetry is collected while the app
  is in the foreground *and* while backgrounded, via a `dataSync` foreground service (visible
  in the notification shade). All collection remains on-device; nothing is transmitted.
- **Speed test is opt-in and disclosed.** The on-demand speed test (Signal tab) downloads
  ~10 MB and uploads ~1 MB to public test endpoints (Cloudflare for download; a VitalPing
  upload worker for upload) when you tap "Run test". It transmits nothing else, and runs
  only on your explicit tap — never automatically.

## Google Play Data Safety (for the declaration)

Expected data types to declare: *Fine location* (for `allCellInfo`),
*Network connections*, *Device or other IDs* (carrier/IMEI-adjacent fields are
*not* read; only SIM identifiers). All are collected on-device, not shared,
and not used for advertising.

## Future risk considerations

If the app later syncs telemetry to a server (e.g. for the AI diagnostic engine):
1. Minimize PII — do not collect IMSI/MSISDN/ICCID beyond what diagnosis needs.
2. Obtain consent and provide data deletion.
3. Re-enable backup only with `dataExtractionRules` excluding telemetry.
4. For EEA users, a GDPR-compliant lawful basis (consent or legitimate interest)
   and a data-processing addendum with any infrastructure provider is required.

*This document is informational and not legal advice.*

# Addendum — Technical Depth & Downstream Context

## Radio Telemetry Surface (Standard Mode)

Via Android `TelephonyManager` (API 31+):

| Metric | API / Source | Example Value |
|---|---|---|
| RSRP | `TelephonyManager.getSignalStrength().getCellSignalStrengths()` | −112 dBm |
| RSRQ | `CellSignalStrengthLte.getRsrq()` | −14 dB |
| SINR | `CellSignalStrengthLte.getRssnr()` | −4 dB |
| Active band | `CellIdentityLte.getBands()` / NR equivalents | n71, B41, n78 |
| Cell ID | `CellIdentity.getCid()` / `getTac()` | 12345678 |
| IMS registration | `TelephonyManager.isImsRegistered()` | boolean |
| Data network type | `TelephonyManager.getDataNetworkType()` | NR, LTE, HSPA, EDGE |
| Roaming status | `TelephonyManager.isNetworkRoaming()` | boolean |
| Operator | `TelephonyManager.getNetworkOperatorName()` | "EE", "Vodafone UK" |

## Power Mode Extras (Shizuku / Wireless ADB)

| Capability | Source | Use Case |
|---|---|---|
| Kernel thermal zones | `/sys/class/thermal/thermal_zone*/temp` | CPU throttling causing radio drops |
| Process socket stats | `/proc/net/tcp`, `/proc/net/udp`, `ss` via shell | Background app data leak detection |
| Full process list | `ps` / `top` via shell | Identify misbehaving apps consuming radio |
| Radio HAL direct | `lshal` / `dumpsys` snippets | Low-level RIL interrogation |
| ePDG tunnel | IMS APN + dumpsys ConnectivityService | VoWiFi handover failure root cause |

## Knowledge Engine Architecture (v1.0)

```
Forum JSON → embedding model → vector DB (sqlite-vss / Chroma)
                                    ↑
Live telemetry → feature extractor → vector query → top-K matches
                                    ↓
                           LLM reasoner → resolution plan
```

- Corpus: 200 threads (100 most-asked + 100 unresolved) from EE Community
- Embedding: `all-MiniLM-L6-v2` or `gte-small` (runs on-device, ~80MB)
- Vector DB: SQLite with `sqlite-vss` extension or ChromaDB (embedded mode)
- LLM: API-based (GPT-4o-mini / Claude Haiku) for v1.0, on-device (Gemma 2B / Phi-3) as stretch goal

## Tri-Tier Resolution Engine

### Tier 1 — User-Fixable (<60s)
Toggle patterns: airplane mode, data off/on, reboot, clear APN, re-select network, SIM re-seat, WiFi calling toggle, VoLTE toggle, RCS force stop + clear cache.

### Tier 2 — Guided Settings Redirect
OEM-specific deep links or nav paths:
- Samsung: `Settings > Connections > Mobile Networks > Network Mode > LTE/3G/2G`
- Xiaomi/OneUI: disable battery optimisation for IMS services
- Pixel: `Settings > Network & Internet > SIMs > Access Point Names > Reset to default`
- Force LTE-only via `*#*#4636#*#*` → Set preferred network type

### Tier 3 — Carrier Cheat Sheet
Generated text including:
- Exact symptoms + device telemetry snapshot
- IMS registration state, RCS provisioning status, ePDG tunnel state
- Known carrier-side fix references (from forum matched threads)
- Script to read to support: *"I need you to check IMS registration on my line and re-trigger the RCS provisioning flag"*
- Thread URL references from the corpus

## Scraper Pipeline (Data Source)

Already built at `/home/x1/proj/droidscan/ee_network_scraper.py`:
- Playwright + BeautifulSoup targeting EE Community Forum
- Filters for network/mobile-broadband relevance via board selection + 80+ title keywords
- Sort by view count to capture highest-impact issues
- Outputs structured JSON with: rank, title, category, url, views, replies, is_solved, date_posted, scraped_at

Future: multi-carrier scraper for Vodafone, O2, Three, T-Mobile DE, Verizon, etc.

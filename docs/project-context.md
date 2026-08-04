---
name: "droidscan (working title)"
status: active
created: 2026-07-25
---

> **ⓘ Currency note (2026-08-04):** This file describes the original *scraper-era*
> project vision (EE forum corpus, Python scrapers). The codebase has since evolved
> into an Android telemetry app. For the current picture see **[README.md](../README.md)**
> (build, modules, permissions) and **[docs/ARCHITECTURE.md](./ARCHITECTURE.md)**.
> This page is retained as the product-vision historical context and the EE-dataset
> documentation.

# droidscan *(working title)* — Android Network Diagnostic Assistant

## What We Have

### Scrapers
- `ee_network_scraper.py` — Playwright+BS4 scraper targeting EE Community Forum boards related to mobile network experiences and mobile broadband devices. Scrapes by view count, filters for network-relevant threads via keyword matching and board selection.
- `ee_forum_scraper.py` — Original general-purpose scraper (all boards, unfiltered).

### Datasets
- `top_100_most_asked_ee_queries.json` — 100 highest-view-count threads about EE mobile network issues. Schema: rank, title, category, url, views, replies, is_solved, date_posted, scraped_at. Categories: SIM cards & Porting (18), Mobile Network discussions (18), Broadband & Landline (17), Mobile Data (15), Mobile Broadband (11), Mobile Services (9), Roaming (8), Apple Devices (2), Android Devices (2).
- `top_100_unresolved_ee_queries.json` — 100 highest-view-count threads with NO accepted solution. Same schema. Zero solved threads in dataset.

### Domain
Mobile network troubleshooting topics covered:
- Signal/coverage issues (poor reception, no service, dead zones)
- Mobile data connectivity (APN settings, 4G/5G not working, slow data)
- WiFi Calling / VoLTE problems
- SMS/MMS send failures
- Call quality and dropouts
- eSIM/SIM provisioning and activation
- Roaming connectivity
- Mobile broadband devices (4G/5G WiFi routers, Mini WiFi, hotspots)
- Network outages and errors

## Goal
Build an AI-powered Android diagnostic engine that uses real-world EE forum data as its knowledge base to help users troubleshoot mobile network problems on their Android devices. The system should be able to:
1. Match user-reported symptoms to known issues from the forum corpus
2. Suggest proven troubleshooting steps (especially from resolved threads)
3. Identify patterns in unresolved issues for diagnostic improvement

## Technology Context
- Python-based scraper using Playwright + BeautifulSoup
- JSON datasets ready for LLM ingestion
- BMAD v6.10.0 for project management and workflow


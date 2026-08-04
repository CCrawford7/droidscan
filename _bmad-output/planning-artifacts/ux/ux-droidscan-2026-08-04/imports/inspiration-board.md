# Inspiration Board — droidscan v1 UI/UX

Reference products and patterns for design exploration. Saved to `imports/` as qualitative guidance (no screenshots bundled).

## Lift from (positive references)

| Reference | What to lift | Apply to droidscan |
|---|---|---|
| **btop / htop** | Dense grid layout, color-coded resource bars, monospace values, minimal chrome | Terminal Purist direction; sparklines, bar gauges, section rules |
| **Grafana mobile** | Card sections, sans labels + mono values, status dots, dark theme hierarchy | Diagnostic Cockpit direction; mixed typography |
| **Network Signal Guru** | Signal metrics front-and-center, band/cell ID context, engineer-friendly density | Radio tab layout, RSRP/RSRQ/SINR prominence |
| **Material 3 dark** | NavigationBar, bottom sheets, elevation tokens, 48dp touch targets | Navigation chrome, glossary sheet, settings |
| **Datadog mobile** | Left-border status accents on cards, "at a glance" overview | Overview tab health summary cards |

## Reject (anti-patterns)

| Reference | What to reject | Why |
|---|---|---|
| **Android *#*#4636#*#*** | Wall of unlabeled text, no hierarchy, no actions | Hostile; exactly what droidscan replaces |
| **Carrier support apps** | Ads, gatekeeping, oversimplified "4 bars" | Misleading; hides real metrics |
| **Generic fitness/wellness apps** | Gamification, streaks, encouragement copy | Wrong register for diagnostic tool |
| **Light-mode-first dashboards** | White backgrounds, soft pastels | v1 is dark-only; metrics read better on dark |

## Concern scan (product-specific)

- **Density vs legibility** — must work at default and large font scale
- **Dark-only** — no light theme in v1; optimize contrast for OLED
- **Monospace legibility** — reserve mono for values; labels can be sans
- **TalkBack** — every metric row needs label + value + status announced
- **Permission UX** — honest empty states when location/phone denied
- **Shizuku fallback** — Power Mode indicators hidden when unavailable; no confusing prompts
- **Real-time feel** — sparklines and live values; no loading spinners on steady-state dashboard
- **Future Diagnostics tab** — IA must not paint us into a corner

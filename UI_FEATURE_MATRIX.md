# Thermal Sentinel — Feature Matrix

What each screen actually does **in this build**, not what it is intended to do
eventually. The engine phase (V1) is implemented; the network and security phases
(V2/V3) are still UI only, and the matrix says so on every affected row.

Status vocabulary:

| Status | Meaning |
|---|---|
| **Live** | Backed by the engine: real platform reads, local persistence, or persisted preferences. |
| **Live (partial)** | The data-bearing part is live; some surfaces on the screen are still placeholders, named in the notes. |
| **UI only** | The screen renders, navigation works, and no engine is behind it. |
| **Static** | Content only (principles, build metadata); nothing to compute. |

| Screen | Feature surface | Status in this build | Notes |
|---|---|---|---|
| Dashboard | Monitoring control + latest thermal/battery sample + Quick Access | Live (partial) | Live: start/stop/resume, alert state, latest sample, freshness. Placeholders: "Risk snapshot" rows report that the correlation engine does not exist yet; network/security rows state "not implemented". |
| Thermal Monitor | Battery temperature, platform thermal status, headroom, 24 h trend, recorded events, active rules | Live | Temperature, status and headroom come from the collectors; the chart draws raw samples and shows gaps as gaps; events are read from the local event table. |
| Battery & Charging | Charge, voltage, current, health, cycle count, capacity level, charging sessions | Live | Unsupported platform values render their absence reason ("Unavailable on this device") rather than `0`. |
| Alert Rules | Warning/critical thresholds, hysteresis, cooldown, charging rule, platform bands | Live | Rules are persisted in DataStore and evaluated by the running engine; invalid combinations are clamped and explained. |
| Thermal History | 24 h raw, 7 d hourly averages, 30 d daily averages, min/max/avg, time above threshold, event timeline, CSV export | Live | Longer ranges read compacted buckets and are labelled as averages; time above threshold is "not derivable" for those ranges instead of being estimated. |
| Network Protection | VPN lifecycle, traffic visibility, per-app policy | UI only | No `VpnService` exists. The screen states that protection is off. |
| Firewall Rules | Precedence, category rules, explicit override | UI only | — |
| DNS Protection | Resolver state, blocklists, cache/attribution | UI only | No DNS engine, no `VpnService`. |
| Privacy Monitor | Tracking signals, usage correlation, privacy alerts | UI only | Usage correlation needs a `PACKAGE_USAGE_STATS` grant that is not requested in this build. |
| App Activity | Package inventory, risk state, activity correlation | UI only | — |
| Security Scanner | Evidence, confidence, risk assessment | UI only | — |
| Security Timeline | Correlated local event stream | UI only | The thermal event timeline is real and lives on Thermal Monitor. |
| Reports & Export | Report surfaces and export formats | Live (partial) | Live: CSV export of stored samples, via the app cache and the system share sheet. UI only: JSON, PDF and the combined report bundle. |
| Quick Access | Battery/network/display settings intents | UI only | Rows are disabled and label the intent they will use. |
| Diagnostics | Runtime readiness checks | Live | 11 checks; "cannot be verified" (for OEM background limits) is reported as `UNKNOWN`, never as a pass. |
| Settings | Appearance preferences + engine position | Live | Theme mode, dynamic colour, AMOLED and accent are persisted in DataStore. Monitoring thresholds live on Alert Rules. |
| Widgets | SYSTEM / THEMED / TRANSPARENT variants | Live | A real Glance provider reads the same local history; the selected style is persisted and applied. The widget says "monitoring is off" rather than showing a stale temperature. |
| About | Product principles, monetisation position, build metadata | Static | Version comes from `BuildConfig`. |

## Not screens, but part of the app

| Component | Status | Notes |
|---|---|---|
| Monitoring foreground service | Live | `specialUse` type, `START_NOT_STICKY`, user-started only, stop reason recorded. |
| Local history database | Live | Room v1 with a 7 d / 90 d / 400 d retention ladder; no destructive migration. |
| Alert notifications | Live | Two channels; monitoring continues when notifications are blocked, and Diagnostics says the notification is hidden. |
| CSV export | Live | Sample table only; the window is clamped to raw retention and the result says so. |
| Cloud sync, analytics, accounts | Not present | Not implemented by design, not merely disabled. |

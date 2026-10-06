# Thermal Sentinel

Private, local-first Android monitoring for **battery temperature and platform
thermal state**. No account, no cloud, no ads, no analytics.

This repository now contains a **working V1 monitoring engine** in addition to the
Compose UI: a foreground service that samples the battery and thermal APIs, a local
history database with a retention ladder, a hysteresis-aware alert engine, a
diagnostics surface, CSV export and a real home-screen widget.

> **Build status: verified in CI.** `./gradlew testDebugUnitTest assembleDebug
> assembleRelease lintDebug` passes in
> [`.github/workflows/ci.yml`](.github/workflows/ci.yml): 88 JVM unit tests, the
> debug build, the minified release build and lint (`abortOnError = true`).
> Instrumented tests are written but not yet part of CI, because they need a
> device or emulator.

| | |
|---|---|
| Namespace (R/BuildConfig) | `com.thermalsentinel` |
| applicationId | `com.thermalsentinel.ui` (debug variant adds `.debug`) |
| Gradle project name | `ThermalSentinelUi` |
| minSdk / targetSdk / compileSdk | 31 / 36 / 37 |
| AGP / Gradle / Kotlin | 9.4.0 / 9.6.0 / 2.4.10 |
| Compose BOM | 2026.09.00 |
| Room / KSP | 2.8.5 / 2.3.12 |
| Glance | 1.2.0 |
| JDK | 17 |

## What is real in this build

| Area | Status |
|---|---|
| Monitoring foreground service (`specialUse`, `START_NOT_STICKY`, user-started only) | Implemented |
| Adaptive sampling (5–90 s by state) with a stated reason for every interval | Implemented |
| Battery collector (sticky broadcast + `BatteryManager` properties, API-gated extras) | Implemented |
| Thermal collector (status, listener, headroom) | Implemented |
| Room v1 history: raw / hourly / daily retention ladder, charging sessions, event timeline | Implemented |
| Alert engine: hysteresis, cooldown, charging rule, platform-status bands | Implemented |
| Alert + ongoing notifications, two channels, permission-aware | Implemented |
| Dashboard, Thermal, Battery, History, Alert Rules, Diagnostics, Widgets screens | Wired to the engine |
| CSV export through the app cache and the system share sheet | Implemented |
| Glance home-screen widget, pushed on new samples (≤1/min) | Implemented |
| DataStore-persisted alert rules, monitoring intent and widget style | Implemented |
| Appearance preferences (theme, dynamic colour, AMOLED, accent) | Implemented |

## What is **not** in this build

| Area | State |
|---|---|
| Network Protection: local VPN, per-app policy, traffic attribution | Not implemented — the screens say so; no `VpnService` exists |
| DNS Protection: resolvers, blocklists, attribution | Not implemented |
| Privacy Monitor, App Activity, Security Timeline, Security Scanner | Not implemented |
| Reports: JSON, PDF, combined diagnostic bundle | Not implemented (CSV export of thermal samples is) |
| Quick Access settings intents | Rows are disabled and labelled |
| Boot-time start of monitoring | Deliberately absent; monitoring resumes when the app is opened |
| Brightness control, app force-stop, thermal-engine overrides | Deliberately absent, permanently |

## Honesty rules the code enforces

These are not aspirations; each one has an implementation consequence.

1. **A missing measurement is never rendered as `0`.** `Reading<T>` is
   `Present` or `Absent(reason)`, and every screen renders the reason
   ("Unavailable on this device", "Reported value not plausible", "Not measured").
2. **Battery temperature is never called "device temperature".** They are different
   measurements and the copy says so.
3. **A stopped engine never looks active.** Monitoring intent and service state are
   two separate facts; when Android kills the service the Dashboard says
   "stopped by Android" and offers to resume.
4. **"Cannot be verified" is a valid answer.** OEM background restrictions are
   reported as `UNKNOWN`, not as a green tick.
5. **Nothing is uploaded.** There is no analytics SDK, no remote config and no
   network dependency in the app.

## Where the engine lives

```
app/src/main/java/com/thermalsentinel/
  ThermalSentinelApp.kt          builds the graph; never starts monitoring
  engine/
    AppGraph.kt                  manual dependency graph shared by service, widget and UI
    domain/                      pure Kotlin: Reading, snapshots, platform mapping, formatting
    collector/                   the only code that touches Android sensors
    monitoring/                  sampling policy, assembly, history maths, retention
    alert/                       rules, state machine, notification publisher
    data/                        Room v1 (entities, DAOs, database) + DataStore repositories
    service/                     foreground service, sampling loop, start/stop controller
    diagnostics/                 11 runtime readiness checks
    export/                      RFC 4180 CSV + share intent
    widget/                      Glance widget + throttled updater
  ui/
    live/                        repository-backed ViewModels
    components/ThermalHistoryChart.kt   gap-honest chart
    screens/Screens.kt           all screens (live and placeholder)
    data/                        appearance preferences, WidgetStyle contract
```

The domain layer never imports `android.*`, which is why the alert engine, the
sampling policy and the history maths are unit-tested on the JVM.

The full contract — schema, sampling table, alert semantics, service lifecycle,
failure states and the test matrix — is in
[`THERMAL_SENTINEL_CORE_ENGINE_SPEC.md`](THERMAL_SENTINEL_CORE_ENGINE_SPEC.md).
Per-screen status is in [`UI_FEATURE_MATRIX.md`](UI_FEATURE_MATRIX.md).

## Product position

- No ads, no advertising SDKs, no remote analytics, no accounts.
- If monetised, a **one-time Pro unlock** only. Play Billing is not implemented here.
- Automatic brightness reduction stays **outside** the monitoring engine; it would
  need its own explicit `Settings.System.canWrite()` authorisation flow.
- The monitoring service uses the `specialUse` foreground-service type and will
  need a written Play Store justification before release.

## Build and test

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug
```

### Tests

**88 JVM unit tests** (15 UI contract tests + 73 engine tests):

| Class | Tests | Covers |
|---|---|---|
| `AlertEngineTest` | 14 | Escalation, jump to critical, hysteresis hold, cooldown vs. escalation, reminders, cleared-is-always-announced, absent-data hold, status bands, charging rule, `since` tracking |
| `ThermalAlertRulesTest` | 9 | Every `RuleViolation`, `clamped()` validity, charging thresholds, lowest triggered band |
| `PlatformValuesTest` | 14 | Unsupported sentinels, `0 mA` / `0 °C` implausibility, voltage and percent bounds, `NaN` headroom, unknown status levels |
| `SamplingPolicyTest` | 11 | Branch priority, charging vs. low battery, unknown status is not elevated, 5 s floor |
| `HistoryMathTest` | 9 | Segment splitting, holes, min/max/avg over present values only, threshold time, rate classification |
| `ReadingTest` | 6 | `Present`/`Absent` semantics, covariance, labels |
| `ThermalCsvTest` | 6 | Header contract, empty fields for absent values, locale-independent decimals, quoting, timestamps |
| `RetentionPolicyTest` | 4 | Bucket alignment, cut-offs, ladder ordering, divide-by-zero guard |
| `EngineContractParsingTest`, `UiPreferencesParsingTest`, `RouteContractTest` | 15 | Storage-value round trips, fallbacks, routes ↔ drawer consistency |

**2 instrumented smoke tests** (`NavigationSmokeTest`, need a device or emulator):
the app launches into the Dashboard and the drawer reaches Alert Rules.
Instrumented tests for the service lifecycle and the Room database are planned,
not written.

## Gradle wrapper

The Gradle 9.6.0 wrapper is committed: `gradlew`, `gradlew.bat`,
`gradle/wrapper/gradle-wrapper.jar` and `gradle/wrapper/gradle-wrapper.properties`.
The jar's SHA-256 (`497c8c2a…a9c7`) matches the wrapper JAR checksum published for
9.6.0 at <https://gradle.org/release-checksums/>, and the properties file pins the
9.6.0 `-bin` distribution checksum.

The scripts and jar were taken from the `v9.6.0` tag of `gradle/gradle`, not
generated with `gradle wrapper`. If you prefer a locally generated wrapper, run
`gradle wrapper --gradle-version 9.6.0` in an **empty folder** (a locally installed
older Gradle would otherwise try to apply AGP) and copy the files over.

## Known follow-ups

- Add the planned instrumentation tests for the service lifecycle and Room to CI
  (they need a device or emulator, so they run locally today).
- Add a Room migration test before the schema version is ever bumped.
- Split `Screens.kt` per screen (optional; it is deliberately one file for now).
- Confirm the `androidx.core:core-splashscreen` pin (1.0.1) before release.
- Finalise `applicationId` before the first Play upload (it is intentionally
  unchanged so the installed identity does not move in this phase).

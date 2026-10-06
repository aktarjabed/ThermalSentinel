# Thermal Sentinel — Core Engine Specification

Status: **implemented (v0.2.0-engine), build-unverified in this environment.**
Scope: the V1 monitoring engine only — battery temperature, platform thermal status and
headroom, charge state, history, alerts, the foreground service, diagnostics, CSV export
and the home-screen widget.
Explicitly out of scope: the local VPN, DNS filtering, the security scanner and any cloud
component. Those are specified separately (see `UI_FEATURE_MATRIX.md`).

This document is the contract the code is written against. Where the code and this document
disagree, the code in `app/src/main/java/com/thermalsentinel/engine/` is authoritative and
this document must be corrected.

---

## 1. Non-negotiable constraints

| # | Constraint | Where it is enforced |
|---|---|---|
| C1 | No root, no hidden APIs, no `adb`-only tricks. | Only public platform APIs are referenced (`BatteryManager`, `PowerManager`, `ACTION_BATTERY_CHANGED`). |
| C2 | No claim of a capability whose engine or privilege is absent. | `Reading.Absent` reasons are rendered instead of values; screens state "not implemented in this build". |
| C3 | A missing measurement is never rendered as `0` (or as `-1`, or as "Good"). | `Reading` + `PlatformValues` plausibility rules; UI helpers in `Screens.kt`. |
| C4 | Battery temperature is never labelled "device temperature". | Copy review; `BatterySnapshot` naming rule. |
| C5 | No forced brightness, no force-stop, no thermal-engine override. | Only notification + row-level intents are produced. |
| C6 | Local-first: no analytics, no upload, no account. | No network dependency is declared; the CSV is written to the app cache and shared by the user. |
| C7 | "VPN unavailable" must never render as "Protection active". | Network screens are placeholders that state the module is not implemented. |

---

## 2. Package structure

```
com.thermalsentinel
├── ThermalSentinelApp.kt            Application; builds the graph, never starts monitoring
├── engine
│   ├── AppGraph.kt                  manual dependency graph (no DI framework)
│   ├── domain/                      pure Kotlin, no android.* imports
│   │   ├── Reading.kt               Reading<T> = Present | Absent(reason); AbsenceReason
│   │   ├── BatterySnapshot.kt       battery + power-source + health enums and snapshot
│   │   ├── DeviceSample.kt          one coherent measurement + SamplingMetadata
│   │   ├── PlatformValues.kt        platform → domain mapping and plausibility rules
│   │   ├── ThermalStatusBand.kt     mirror of PowerManager.THERMAL_STATUS_* (frozen storage)
│   │   ├── ThermalEventType.kt      timeline event vocabulary
│   │   └── ThermalFormatting.kt     one place for "38.4 °C" / "—" / durations
│   ├── collector/                   the only place that touches Android sensors
│   │   ├── BatteryCollector.kt      sticky broadcast + BatteryManager properties
│   │   └── ThermalCollector.kt      PowerManager status + headroom, both listeners
│   ├── monitoring/                  policy, no platform access
│   │   ├── SamplingPolicy.kt        adaptive interval decision
│   │   ├── SampleAssembler.kt       request + snapshots → DeviceSample
│   │   ├── HistoryMath.kt           series math, gaps, rate classification
│   │   └── RetentionPolicy.kt       7 d raw / 90 d hourly / 400 d daily ladder
│   ├── alert/
│   │   ├── ThermalAlertRules.kt     persisted rule set + validation + clamping
│   │   ├── AlertEngine.kt           pure state machine (hysteresis + cooldown)
│   │   └── AlertNotificationPublisher.kt  channels, notifications, permission handling
│   ├── data/                        Room + DataStore
│   │   ├── Entities.kt, Daos.kt, EngineDatabase.kt
│   │   ├── SampleMappers.kt         entity ⇄ domain, absence-aware both ways
│   │   ├── ThermalHistoryRepository.kt    writes, queries, retention maintenance
│   │   └── EngineSettingsRepository.kt    user rules, monitoring intent, alert state
│   ├── service/
│   │   ├── ThermalMonitorService.kt foreground service (specialUse, not sticky)
│   │   ├── MonitoringCoordinator.kt sampling loop + alert pipeline
│   │   └── MonitoringController.kt  the only start/stop entry point
│   ├── diagnostics/DiagnosticsEngine.kt   11 runtime readiness checks
│   ├── export/{ThermalCsv,ThermalExportService}.kt
│   └── widget/ThermalSentinelWidget.kt    Glance widget + throttled updater
└── ui
    ├── live/ThermalViewModels.kt, MonitoringViewModel.kt   repository-backed state
    ├── components/ThermalHistoryChart.kt                  gap-honest chart
    └── screens/Screens.kt                                 navigation targets
```

Dependency direction is strictly one-way: `ui → engine → domain`. The domain layer never
imports `android.*`, which is what makes the alert engine, policy and maths JVM-testable.

---

## 3. Domain model

### 3.1 `Reading<T>` — availability as a first-class value

```kotlin
sealed interface Reading<out T> {
    data class Present<T>(val value: T) : Reading<T>
    data class Absent(val reason: AbsenceReason) : Reading<Nothing>
}
```

| Reason | Meaning | Typical source |
|---|---|---|
| `NOT_REPORTED_BY_PLATFORM` | The API exists and was called; this device did not include the value. | A broadcast extra the OEM omits. |
| `UNSUPPORTED_ON_THIS_DEVICE` | The hardware/fuel gauge does not expose the property. | `getIntProperty` → `Integer.MIN_VALUE`, headroom → `NaN`. |
| `REPORTED_VALUE_IMPLAUSIBLE` | A value arrived that cannot be physically true for a running device. | `0 mA` while discharging, `0` deci-°C temperature. |
| `NOT_COLLECTED_YET` | Nothing has been collected in this process. | First frame after launch. |
| `NOT_RECORDED` | The value was absent at sample time and only the absence was stored. | Reading history back from Room. |

`NOT_RECORDED` is what makes history honest: a nullable column loads back as an explicit
absence, so a stored `0 °C` can never be confused with "we did not measure".

### 3.2 `DeviceSample`

```kotlin
data class DeviceSample(
    val timestampMillis: Long,
    val battery: BatterySnapshot,
    val thermal: ThermalSnapshot,
    val sampling: SamplingMetadata
)
```

`SamplingMetadata` carries the reason the sample exists, the requested interval, the actual
interval and a `isGap` flag computed with `GAP_FACTOR = 2.5`. A sample is flagged as a gap
when the actual interval exceeds 2.5× the requested interval; the recorder then writes a
`GAP_DETECTED` event so the chart can explain the hole rather than interpolate it.

### 3.3 Plausibility rules (`PlatformValues`)

| Signal | Rule | Result |
|---|---|---|
| `EXTRA_TEMPERATURE` | outside −30…120 °C, or exactly 0 deci-°C | `REPORTED_VALUE_IMPLAUSIBLE` |
| `EXTRA_VOLTAGE` | outside 1…20 000 mV | `REPORTED_VALUE_IMPLAUSIBLE` |
| `CURRENT_NOW` / `CURRENT_AVERAGE` | `Integer.MIN_VALUE` | `UNSUPPORTED_ON_THIS_DEVICE` |
| `CURRENT_NOW` / `CURRENT_AVERAGE` | `0 µA` | `REPORTED_VALUE_IMPLAUSIBLE` |
| any `getIntProperty` | `Integer.MIN_VALUE` | `UNSUPPORTED_ON_THIS_DEVICE` |
| `getThermalHeadroom` | `NaN` | `UNSUPPORTED_ON_THIS_DEVICE` |
| `getThermalHeadroom` | infinite | `REPORTED_VALUE_IMPLAUSIBLE` |
| `getThermalHeadroom` | negative | clamped to `0f` (documented platform behaviour) |
| thermal status | unknown integer | `ThermalStatusBand.UNKNOWN` (never treated as safe) |

---

## 4. Persistence

### 4.1 Room schema (version 1, `exportSchema = false`)

| Table | Key | Purpose | Notes |
|---|---|---|---|
| `thermal_samples` | auto id, index on `timestampMillis` | Raw measurements. | Nullable columns are absences, not zeros. Stores `requestedIntervalMillis` so a later sampling-policy change cannot retroactively relabel old gaps. |
| `thermal_aggregates` | PK (`bucketStartMillis`, `bucketMillis`) | Hourly (3 600 000) and daily (86 400 000) rollups. | Fixed `samplesAtOrAbove40C` column: later edits to the warning threshold must not rewrite what already happened. |
| `thermal_events` | auto id, index on `timestampMillis` | Timeline: monitoring start/stop, alert raised/reminder/cleared, heating, cooling, charging start/stop, gap, compaction. | `level` and `trigger` are stored as strings so the vocabulary survives enum reordering. |
| `charging_sessions` | auto id | One row per charging session; `endMillis == null` means open. | Peak/average temperature and sample count are maintained incrementally. |

There is **no destructive-migration fallback**: a schema change requires a real migration.
Dropping history to make a migration easy would delete the only record the app has.

### 4.2 DataStore (`engine_settings`)

| Key group | Contents | Why persisted |
|---|---|---|
| Monitoring intent | `monitoring_enabled`, last start/stop, stop reason, session count | Makes "resume only what the user asked for" verifiable after process death. |
| Alert rules | thresholds, hysteresis, cooldown, charging rule, status bands | The engine must evaluate the same rules the UI showed. |
| Alert state | level, trigger, since, last notified level/time, evaluations | A reboot or service restart must not be usable to bypass a cooldown. |

Writes go through `editSafely`: an `IOException` logs and keeps the previous value rather
than crashing the monitoring service.

### 4.3 Retention ladder (`RetentionPolicy`)

```
raw samples            7 days      → compacted into hourly buckets
hourly buckets        90 days      → merged into daily buckets
daily buckets        400 days      → deleted
events + closed sessions 400 days  → deleted
```

Compaction and deletion happen only on whole-bucket boundaries. The cost is that raw data can
outlive its retention window by less than one bucket; the benefit is that a bucket can never
be compacted twice from partial data and silently overwrite its own first contribution.
Gaps are never interpolated: an hourly bucket with no samples stores a null average.

---

## 5. Collectors

### 5.1 `BatteryCollector`

* Registers a `RECEIVER_NOT_EXPORTED` receiver for the sticky `ACTION_BATTERY_CHANGED`
  (system broadcast: no export flag is required on API 34+ for a receiver registered only
  for protected system broadcasts; the flag is passed anyway because it is never wrong).
* Caches the latest intent so a sample taken between broadcasts still has the most recent
  platform statement, and the sample carries the *broadcast* observation time separately from
  the sample time, so the UI can say "reading from 3 minutes ago".
* Reads `BatteryManager` properties on demand: `CHARGE_COUNTER`, `CURRENT_NOW`,
  `CURRENT_AVERAGE`, `CAPACITY`, `ENERGY_COUNTER`, `STATUS`.
* Reads `EXTRA_CYCLE_COUNT` (API 34+) and `EXTRA_CAPACITY_LEVEL` (API 36+) and reports
  absence where the platform does not answer.
* Never derives one value from another (no "temperature from voltage", no current from
  charge counter deltas). Derived values are estimates, and estimates do not belong in a
  record that the alert engine acts on.

### 5.2 `ThermalCollector`

* `PowerManager.getCurrentThermalStatus()` (API 29+) plus `addThermalStatusListener`.
* `PowerManager.getThermalHeadroom(forecastSeconds)` (API 30+), polled no faster than ~1 Hz;
  polling faster is documented to *create* `NaN`, and `NaN` means "unsupported", not "cool".
* On API 36+, `addThermalHeadroomListener(Executor, …)` and
  `getThermalHeadroomThresholds()` are available; thresholds may change between calls, so
  they are re-read rather than cached.
* Both signals are independent: a device can report a headroom value while the status
  listener is silent, and the alert engine treats a missing status as "no information"
  rather than as "normal".

---

## 6. Adaptive sampling

`SamplingPolicy.decide(context)` returns both an interval and a human-readable reason.

| Condition (highest wins) | Interval | Reason |
|---|---|---|
| Alert escalated (WARNING/CRITICAL) | 5 s | Fast follow-up while a threshold is breached. |
| Platform status ≥ SEVERE | 5 s | Platform is already throttling. |
| Charging | 15 s | Charging heat is the dominant thermal load. |
| Platform status ≥ MODERATE, or temperature ≥ warning − 2 °C | 10 s | Elevated but not yet alerting. |
| Screen off | 60 s | Nothing is watching; save power. |
| Battery < 15 % and unplugged | 90 s | The last few percent of charge are not spent on monitoring. |
| Otherwise | 30 s | Normal cadence. |

Floor: 5 s (`MIN_INTERVAL_MS`). Each decision carries a reason so Diagnostics can explain
the current cadence instead of showing an unexplained number.

---

## 7. Alert engine

### 7.1 Rule set

| Field | Default | Valid range |
|---|---|---|
| `warningEnabled` | `true` | — |
| `warningThresholdC` | 40 °C | 30…60 |
| `criticalEnabled` | `true` | — |
| `criticalThresholdC` | 48 °C | 30…70, ≥ warning + 1 |
| `hysteresisC` | 1 °C | 0…5, ≤ critical − warning |
| `cooldownMinutes` | 15 | 5…120 |
| `chargingWarningEnabled` | `false` | — |
| `chargingWarningThresholdC` | 42 °C | 30…60 |
| `statusBandTriggers` | SEVERE, CRITICAL | `selectable` bands only |

Invalid combinations can be stored only by an older version; `updateRules` clamps every
write, and the screen renders `RuleViolation` messages rather than accepting an incoherent
configuration silently.

### 7.2 State machine

Levels: `NORMAL → WARNING → CRITICAL`, with de-escalation governed by hysteresis.

* **Escalation is monotonic and immediate.** A single 48.5 °C reading goes straight to
  `CRITICAL`; a cooldown can never swallow an escalation.
* **Hysteresis governs the state.** WARNING clears at `warningThreshold − hysteresis`;
  CRITICAL de-escalates to WARNING at `criticalThreshold − hysteresis`. A cooldown alone is
  *not* hysteresis: without a recovery level, a device parked at 40.1 °C would re-alert every
  cooldown forever.
* **The cooldown governs repeats only.** While the level is unchanged, `Notify` is emitted
  only when `now − lastNotifiedAt ≥ cooldown`.
* **Cleared is always announced**, deliberately outside the cooldown: suppressing "back in
  range" would leave a user staring at a stale warning.
* **Absent data holds the state.** If both temperature and thermal status are unavailable,
  the state is held and nothing is emitted — the engine does not "recover" because a sensor
  stopped reporting.
* Status-band triggers and temperature triggers are evaluated independently and the higher
  level wins; a status of `CRITICAL` or above maps to `CRITICAL`, anything from the selected
  lowest band upward maps to at least `WARNING`.
* Every evaluation returns an `explanation` string, which is what Diagnostics shows.

### 7.3 Notifications

| Channel id | Importance | Use |
|---|---|---|
| `monitoring` | `IMPORTANCE_LOW`, no badge | Ongoing, silent "monitoring active" notification (id 4101). |
| `thermal_alerts` | `IMPORTANCE_HIGH` | Warning/critical alerts (4102) and the "back in range" note (4103). |

`notifyIfPermitted` checks `POST_NOTIFICATIONS` (API 33+), `areNotificationsEnabled()` and
catches `SecurityException`. When notifications are blocked the notification is skipped and
**monitoring continues** — the platform still lists the foreground service, and Diagnostics
says the notification is hidden. The alert text quotes the threshold that actually fired.

---

## 8. Service lifecycle

| Decision | Choice | Reason |
|---|---|---|
| Foreground service type | `specialUse` (+ `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`) | Continuous monitoring fits no other type; `dataSync` is capped at 6 h/24 h on Android 15 and forbids `BOOT_COMPLETED` starts, which would break the product honestly. Play review requires a written justification. |
| Return value | `START_NOT_STICKY` | A sticky restart can be rejected on API 31+ and risks a crash loop; a silent restart would also make the app's own "monitoring stopped" reporting untrue. |
| Where monitoring may start | A visible screen (`MonitoringController.start()`), or `resumeIfRequested()` from a visible screen | Foreground-service starts from the background are rejected on API 31+. `Application.onCreate` deliberately never starts the service. |
| Stop reasons | `USER_REQUEST`, `PLATFORM_STOPPED`, `PERMISSION_REVOKED`, `UNKNOWN` | Written from `onDestroy` on the *application* scope, which outlives the service, and only when the persisted intent is still "on" — so an OEM kill is recorded and shown, never disguised as the user's choice. |
| Notification-suppressed path | Monitoring still runs | The user turning off notifications must not silently disable monitoring. |
| Widget updates | Throttled to 1/min from the sampling loop; one-hour fallback refresh | Sampling can be 5 s; rebuilding a widget that fast would cost more battery than the monitoring. |

Failure states and what the user sees:

| Failure | Detected by | UI |
|---|---|---|
| Notifications blocked | `notificationsVisible()` | Diagnostics WARNING + "Monitoring still runs". |
| Service killed by the platform | persisted intent still true in `onDestroy` | Dashboard: "Monitoring is paused (stopped by Android)" + Resume. |
| Battery broadcast never arriving | `batteryBroadcastCheck` | Diagnostics FAILED with a remedy. |
| Headroom unsupported (`NaN`) | `headroomCheck` | Diagnostics "Cannot be verified" and `—` in the UI, never `0.00`. |
| OEM background restrictions | not detectable | Diagnostics `UNKNOWN` — "cannot be verified" is the honest answer. |
| Database empty | row counts | Diagnostics reports 0 rows rather than a green tick. |

---

## 9. Diagnostics (11 checks)

`notifications`, `monitoring service`, `battery broadcast`, `thermal status`, `headroom`,
`battery properties`, `database`, `data freshness`, `retention`, `background limits`,
`export cache`.

Each `ReadinessCheck` has `state ∈ {OK, WARNING, FAILED, UNKNOWN}`, a `detail` describing what
was actually measured, and an optional `remedy`. `UNKNOWN` exists for questions the app
genuinely cannot answer, and is not a soft failure.

---

## 10. Export

* CSV of the **sample table only**; buckets are averages and are never exported as readings.
* 18 columns (`timestamp_iso`, `timestamp_epoch_millis`, `battery_temperature_c`,
  `android_thermal_status`, `thermal_headroom`, `battery_percent`, `charging_status`,
  `power_source`, `current_now_ua`, `current_average_ua`, `voltage_mv`,
  `charge_counter_uah`, `battery_health`, `cycle_count`, `sample_reason`,
  `requested_interval_ms`, `actual_interval_ms`, `screen_interactive`), RFC 4180 quoting,
  `Locale.ROOT` decimals, ISO-8601 timestamps with offset.
* An empty field means "not measured" — that rule is stated in the share text.
* The window is clamped to raw retention (7 days) and `truncatedToRetention` is reported so
  a 30-day request never silently returns 7 days as if it were complete.
* Written to the app cache and shared through `FileProvider`
  (`${applicationId}.engine.files`). Previous exports are cleared on request.

---

## 11. Widget

`GlanceAppWidget` + `GlanceAppWidgetReceiver` (`APPWIDGET_UPDATE`), `updatePeriodMillis`
set to one hour as a fallback only. Content: battery temperature (or "Temperature
unavailable"), platform thermal status, charge level, headroom, and either the sample clock
or an explicit "Monitoring is off — showing the last stored reading" line. With no data at
all it says "No measurement yet". It never shows a stale number without saying it is stale.

---

## 12. Test matrix

JVM unit tests (run by `./gradlew testDebugUnitTest` in CI):

| Area | Test class | Cases |
|---|---|---|
| Domain availability | `ReadingTest` | `Present`/`Absent` round trip, `valueOrNull`, `absenceReason`, display labels for every reason. |
| Plausibility | `PlatformValuesTest` | `MIN_VALUE` → unsupported; 0 deci-°C → implausible; voltage bounds; `NaN` headroom → unsupported; negative headroom → 0; unknown status level → `UNKNOWN`; deci-°C conversions round-trip. |
| Alert engine | `AlertEngineTest` | escalation NORMAL→WARNING→CRITICAL; jump to CRITICAL; hysteresis blocks early clear; absent-data hold; cooldown suppresses repeats but not escalation; reminder after cooldown; cleared always emitted; status-band mapping; evaluation counter and explanation text. |
| Alert rules | `ThermalAlertRulesTest` | every `RuleViolation`; `clamped()` never leaves an invalid set; charging rule threshold selection and recovery. |
| Sampling policy | `SamplingPolicyTest` | priority order of the eight branches, reason strings, 5 s floor. |
| History maths | `HistoryMathTest` | gaps split segments; `null` points never bridge; min/max/average over present values only; `timeAboveThresholdMillis` counts only fully measured intervals and respects the gap limit; rise-rate needs ≥3 points and ≥3 min; HEATING/COOLING thresholds; `largestGapMillis`. |
| Retention boundaries | `RetentionPolicyTest` | bucket starts are floor-aligned; cut-offs; expected sample counts. |
| CSV | `ThermalCsvTest` | quoting/escaping, absence → empty field, header stability, decimal separator independence. |
| Mappers | `SampleMappersTest` | domain → entity drops only the reason; entity → domain turns null into `NOT_RECORDED`; aggregate average rounding. |
| UI contracts | `EngineContractParsingTest`, `UiPreferencesParsingTest` | storage values round-trip; unknown storage values fall back; widget style set is unique. |
| Navigation | `RouteContractTest` | routes and drawer entries agree. |

Instrumentation tests (`connectedDebugAndroidTest`, not run in CI):

| Area | Test | Purpose |
|---|---|---|
| Navigation smoke | `NavigationSmokeTest` | every destination resolves; the live screens render with an empty database. |
| Service lifecycle | `MonitoringServiceTest` (planned) | start → notification posted → `MonitoringStatus.isRunning`; stop → `USER_REQUEST` recorded. |
| Room | `EngineDatabaseTest` (planned) | insert/query round trip, retention maintenance on a seeded database. |

Not testable on the JVM and therefore deliberately isolated behind collectors: the platform
APIs themselves. The collectors only translate platform values into `Reading`s;
everything that decides anything is pure Kotlin.

---

## 13. Known limits and open items

1. **No boot receiver.** Monitoring resumes when the app is opened, not after a reboot. A
   boot-time start of a `specialUse` foreground service would need its own justification and
   would risk a background-start rejection; the honest alternative is the Dashboard resume
   prompt.
2. **Vendor background restrictions are unverifiable.** Diagnostics reports `UNKNOWN`; the
   app cannot know whether a device's battery manager will kill the service.
3. **`specialUse` needs a Play Store justification** before any release.
4. **Room schema changes need migrations.** There is no destructive fallback by design.
5. **Instrumentation tests for the service and Room are planned, not written.**
6. **`versionName = 0.2.0-engine`; `applicationId` is unchanged** so the installed identity
   does not move. Finalise before the first upload.

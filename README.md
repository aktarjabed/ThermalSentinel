# Thermal Sentinel UI

Jetpack Compose UI foundation for **Thermal Sentinel**, a private, local-first Android device-protection app (thermal, battery, network and security monitoring).

This is a **UI-only build**. Screens, navigation, the theme system and persisted appearance settings are real. The monitoring, VPN, DNS, scanner and alert engines are **not** connected.

| | |
|---|---|
| Package | `com.thermalsentinel.ui` |
| Gradle project name | `ThermalSentinelUi` |
| minSdk / targetSdk / compileSdk | 31 / 36 / 37 |
| AGP / Gradle / Kotlin | 9.4.0 / 9.6.0 / 2.4.10 |
| Compose BOM | 2026.09.00 |
| JDK | 17 |

## What is in this build

- Material 3 with a drawer plus bottom navigation and **20 preview screens** (see [`UI_FEATURE_MATRIX.md`](UI_FEATURE_MATRIX.md)).
- Appearance preferences persisted with DataStore: theme mode, dynamic colour, AMOLED black, accent.
- A splash gate (`androidx.core:core-splashscreen`) that holds the splash until DataStore emits its first value, so a saved Dark/AMOLED theme does not flash light on cold start.
- Frozen contracts for the future engine: the `WidgetStyle` and `ThermalStatusBand` enums, the Alert Rules screen and the Quick Access screen.

### Preview-only state

Alert thresholds and the widget style use `rememberSaveable` only. They survive rotation but are **not** written to DataStore, and nothing schedules a notification. Only the appearance preferences are persisted.

### Preview data

The temperature figures on the Dashboard, Thermal, Battery (temperature row), History and Widgets screens are derived from two synthetic series in `Screens.kt` (`thermalPoints`, `historyPoints`), so the numbers on a given screen agree with each other and with the chart.

Some values are still hard-coded literals and are placeholders, not derived data: the Battery charging-session summary, the thermal-event and charging-marker rows, the risk bars, and "Time above warning".

### Dynamic colour precedence

When **Dynamic system colors** is on (the default), the system wallpaper scheme is used and the accent selection is disabled and not applied. Turn dynamic colour off to use a custom accent. AMOLED black applies on top of either source when the dark theme is active.

## Product position

- No ads, no advertising SDKs, no remote analytics.
- If monetised, a **one-time Pro unlock** only. Play Billing is not implemented here.
- Automatic brightness reduction stays **outside** the monitoring engine. It needs its own explicit `Settings.System.canWrite()` authorisation flow.

## Gradle wrapper

`gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` are intentionally **not** included, because they cannot be shipped as text. Only `gradle/wrapper/gradle-wrapper.properties` (Gradle 9.6.0) is checked in.

Generate the wrapper once, in an **empty folder** (or via Android Studio), so a locally installed older Gradle does not try to apply AGP:

```bash
mkdir /tmp/wrapper-gen && cd /tmp/wrapper-gen
gradle wrapper --gradle-version 9.6.0
```

Copy `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` into the project root, then commit them (the `git add` must come first; `update-index` fails on an untracked file):

```bash
git add gradlew gradlew.bat gradle/wrapper
git update-index --chmod=+x gradlew
git commit -m "Add Gradle wrapper"
```

CI validates the wrapper with `gradle/actions/wrapper-validation` and will fail until the wrapper is committed.

## Build and test

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug
```

v1 is not considered frozen until that command exits 0.

### Tests

**15 JVM unit tests**

| Class | Tests | Covers |
|---|---|---|
| `UiPreferencesParsingTest` | 5 | `ThemeMode` / `AccentPreset` parse fallbacks, round trips, unique storage values |
| `EngineContractParsingTest` | 8 | `WidgetStyle` and `ThermalStatusBand` fallbacks, round trips, out-of-range level → `UNKNOWN`, `selectable` |
| `RouteContractTest` | 2 | `Routes` constants ↔ `allDestinations`, no duplicate routes |

**2 instrumented smoke tests** (`NavigationSmokeTest`, need a device or emulator): the app launches into the Dashboard, and the drawer reaches Alert Rules.

`RouteContractTest` does not see the `when` in `AppNavHost`. A route that is in `Routes` and `allDestinations` but has no screen branch fails on first navigation (`error("No composable screen registered ...")`), not in CI.

## Layout

```
app/src/main/java/com/thermalsentinel/ui/
  MainActivity.kt        splash gate, edge-to-edge, drawer + bottom nav
  components/            shared Compose components
  data/                  DataStore repository, ViewModel, frozen enums
  navigation/            Routes, allDestinations, AppNavHost
  screens/Screens.kt     all 20 screens (deliberately one file for the v1 freeze)
  theme/                 colour seeds, theme, typography, shapes
```

## Known follow-ups

- Split `Screens.kt` per screen (optional, after the v1 freeze).
- Replace the `VERSION_NAME` display with a dedicated `displayVersion` build field if the version name ever needs a different public form.
- Verify the `androidx.core:core-splashscreen` pin (1.0.1) against the latest stable release before release.
- Replace the remaining preview literals when the data layer exists.

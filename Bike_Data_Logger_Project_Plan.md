# Bike Data Logger & Navigator — Project Plan

A repurposed Samsung Galaxy S4 as a bike-mounted telemetry device: navigate, log ride sensor data, and mine it for route comparison, speed-decay modeling, and predictive ETA.

## Document Control

| Field | Value |
|---|---|
| Version | 0.1 (Draft) |
| Owner | Anushil |
| Status | Planning — no code written yet |
| Last updated | 2026-07-09 |

**Change log**

| Date | Version | Change | Author |
|---|---|---|---|
| 2026-07-09 | 0.1 | Initial draft plan created | Claude |

> This document is meant to be edited directly as decisions change. Anything marked **[OPEN]** in Section 13 is an unresolved decision — update the log entry when you resolve it instead of just changing the text elsewhere, so there's a record of *why* it changed.

---

## 1. Project Overview

**Vision:** Turn a spare Galaxy S4 into a dedicated bike computer that (a) provides turn-by-turn navigation, and (b) silently logs GPS + motion + environmental sensor data for every ride, producing a personal dataset you own and can analyze.

**Objectives**

1. Build a custom Android app (the "CSE-flex path") that logs sensor + GPS data during a ride, independent of whatever navigation app is running on screen.
2. Use the same device for turn-by-turn navigation while it logs in the background.
3. Build a repeatable data pipeline that pulls ride data off the phone and into a Python analysis environment.
4. Answer three concrete questions with the collected data:
   - Which of my regular routes is actually best (time / smoothness / effort), not just which one feels best?
   - How does my speed decay over the course of a ride, and what predicts it?
   - Can I predict ride ETA better than a generic maps app, using my own historical pace data?

**Success criteria**

- App reliably logs a full ride (start to finish) without crashing or losing data, across at least 10 real rides.
- At least 3 distinct commute/leisure routes ridden multiple times each, enough for route comparison to be statistically meaningful (not just an n=1 anecdote).
- A working ETA model that can be evaluated against actual ride outcomes (held-out rides it wasn't trained on).

---

## 2. Scope

**In scope**

- Custom Android logging app (Kotlin), GPS + IMU + barometer capture.
- Navigation via an on-device maps/nav app (exact app TBD — see Section 5).
- Local storage on-device (SQLite/Room), manual or WiFi export to a laptop.
- Python-based analysis: route comparison, speed decay, predictive ETA.
- Documentation of the build so it's reproducible and correctable later.

**Out of scope (for now — candidates for future phases)**

- Cloud sync / remote dashboard accessible from anywhere.
- Real-time analytics *during* a ride (this is post-ride analysis only, at least initially).
- Pairing with external sensors (heart rate strap, cadence sensor) — noted as a future extension in Section 9.
- Machine-learning models beyond simple regression/tree-based methods for the first pass.

**Assumptions**

- The S4 (Verizon SCH-I545 variant, pending confirmation) will be used WiFi-only; no active cellular plan assumed. All design choices below default to "must work over WiFi only," so the Verizon SIM-lock question becomes irrelevant to this project.
- You have a personal computer to run the Python analysis side; the phone itself is not doing the data science.
- Riding is outdoors with generally clear sky view (GPS accuracy assumption).

---

## 3. Device Inventory & Hardware Setup

### 3.1 Device identification checklist

Fill this in before doing anything else — it determines several downstream decisions.

| Item | How to find it | Your value |
|---|---|---|
| Exact model number | Settings → About phone → Model number (or under the battery on older units) | _________ (expect `SCH-I545` if Verizon) |
| IMEI | Dial `*#06#` or Settings → About phone → Status | _________ |
| Current Android version | Settings → About phone → Android version | _________ (stock ceiling is Android 5.0.1 for this model) |
| Build number | Settings → About phone → Build number | _________ |
| Battery health / swelling check | Visual inspection — do this before mounting anything on a moving bike | Pass / Fail |
| Carrier lock status | Insert a non-Verizon SIM and see if it registers, or check via IMEI lookup on Verizon's device unlock page | _________ |
| Storage available | Settings → Storage | _________ |

### 3.2 Confirmed hardware specs (SCH-I545)

Sourced from device spec databases, cross-checked ([GSMChoice](https://www.gsmchoice.com/en/catalogue/samsung/galaxys4verizon/), [PhoneDB](https://phonedb.net/index.php?c=samsung_sch-i545_galaxy_s4__samsung_altius&d=detailed_specs&id=4240&m=device)):

| Sensor | Present | Relevant to |
|---|---|---|
| Accelerometer | Yes | Bump/roughness detection, braking events |
| Gyroscope | Yes | Cornering/lean, orientation |
| Magnetometer (compass) | Yes | Heading, cross-check GPS bearing |
| Barometer | Yes | Elevation/altitude, independent of GPS altitude (which is noisy) |
| GPS | Yes | Position, speed, route path |
| Light sensor | Yes | Optional: day/night riding tag |
| Proximity | Yes | Not used for this project |

This is good news — the barometer in particular means elevation data doesn't have to rely solely on GPS altitude, which is typically the least accurate GPS output.

### 3.3 Physical setup

- Bike mount bracket compatible with S4 dimensions (~$8–15) — check reviews for vibration-induced disconnects, cheap mounts are the most common failure point on rough roads.
- Weatherproofing: sealed phone pouch, or a mount with a built-in weatherproof case. The phone will be exposed to vibration, occasional rain, and temperature swings.
- Power: a small USB power bank (5000mAh class is plenty) if rides exceed ~2 hours, since continuous GPS + sensor logging + screen-on navigation will drain the battery faster than normal use.
- Cable routing: short cable from power bank to phone, zip-tied to avoid dangling into spokes/pedals.

### 3.4 Software prep — read this before flashing anything

**Important constraint discovered during research:** Google's own support pages state that after August 2025, the Google Maps app no longer ships new releases for Android 8.1 and below, and devices need Android 9.0+ with a recent app version to keep receiving updates ([Google Maps Help](https://support.google.com/maps/answer/10993350?hl=en)). The S4's stock ceiling is Android 5.0.1 Lollipop, and even a full custom-ROM install (LineageOS 14.1, unofficial builds for this device family) only reaches Android 7.1 — still short of 9.0.

This directly affects the navigation piece of the plan and is addressed as a formal open decision in Section 5. Don't flash a ROM before that decision is resolved, since the choice changes what you need (stock Play Services vs. GApps package vs. no Google services at all).

Recommended prep sequence:

1. Complete the device identification checklist (3.1).
2. Resolve Open Decision #1 and #2 (Section 13) — OS path and navigation app — before touching the bootloader.
3. Back up current state (even a wiped phone — take a note of current firmware version) before any unlock/root/flash step.
4. If rooting: unlock bootloader via the correct method for the exact model (SCH-I545 Verizon models have historically been the hardest S4 variant to unlock/root — verify a working method for your exact build number before starting).

---

## 4. System Architecture

### 4.1 Components

- **Sensor Service** — background service reading accelerometer, gyroscope, magnetometer, barometer at defined sampling rates.
- **Location Service** — GPS position/speed/bearing capture.
- **Ride Session Manager** — state machine controlling start/pause/stop of a logging session, ties sensor + location streams to a single `ride_id`.
- **Local Storage** — Room/SQLite database on-device.
- **Navigation Launcher** — triggers the chosen navigation app via Android Intent, independent of the logger (they run side by side, not integrated).
- **Export Module** — dumps a ride (or all rides) to CSV/SQLite file for transfer to a laptop.
- **Analysis Pipeline** (off-device) — Python scripts/notebooks that ingest exported data and run the three analysis modules.

Data flow: `Sensors + GPS → Ride Session Manager → Room DB → Export (CSV) → WiFi/USB transfer → Python ingestion → Cleaning → Analysis modules → Reports/visualizations`.

### 4.2 Tech stack

| Layer | Choice | Why |
|---|---|---|
| App language | Kotlin | Modern Android standard, less boilerplate than Java, first-class Android Studio support |
| Local DB | Room (over SQLite) | Type-safe queries, easier schema migrations than raw SQLite |
| Location API | `FusedLocationProviderClient` if Google Play Services available, else `LocationManager` as fallback | Fused API is more accurate/battery-efficient but depends on GMS being present |
| Sensors | Android `SensorManager` | Standard API, no extra dependency |
| Export format | CSV (+ optionally raw SQLite file) | Universally readable by pandas, human-inspectable |
| Analysis | Python: pandas, numpy, matplotlib/plotly, folium (maps), scikit-learn | Standard data science stack, matches your coursework |
| Version control | Git repo for both the Android app and analysis scripts | Keeps code and this plan doc's decisions traceable together |

### 4.3 Permissions required

| Permission | Purpose |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS logging |
| `ACCESS_BACKGROUND_LOCATION` | Keep logging while navigation app is in foreground |
| `FOREGROUND_SERVICE` (+ `FOREGROUND_SERVICE_LOCATION` on newer Android) | Keep the logging service alive during a ride |
| `WRITE_EXTERNAL_STORAGE` / scoped storage APIs | Export CSV files |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Optional: pulling weather data by timestamp/location for later analysis |
| `WAKE_LOCK` | Prevent the logging service from being killed when the screen sleeps |

---

## 5. Navigation Integration — Decision Required

This is the piece most affected by the phone's age, so it's broken out on its own.

**The problem:** the newest Google Maps app effectively requires Android 9+. The S4 cannot reach Android 9 through any known ROM path for this hardware. That doesn't mean Google Maps is impossible — older, archived versions of the app (from APK archive sites) may still install and function on Android 5–7, since the app itself doesn't stop working, it just stops receiving *updates*. Map data and traffic info in an old app version will eventually go stale, though, since it depends on backend API compatibility that Google doesn't guarantee to maintain forever for old clients.

**Fallback matrix**

| Option | Works on Android 5–7 | Offline capable | Live traffic | Effort |
|---|---|---|---|---|
| Sideload archived Google Maps APK | Likely, unmaintained | No | Yes (until backend cuts off old clients) | Low |
| OsmAnd | Yes, actively maintained for old Android | Yes (full offline maps) | No | Low |
| Waze | Uncertain — similar version constraints to Maps likely | No | Yes | Unverified |
| Browser-based Google Maps (mobile site) | Yes | No | Yes | Low, but worse UX for turn-by-turn |

**Recommendation (default, open to correction):** Use **OsmAnd** as the primary navigation app. It's built for exactly this situation — old devices, offline-first, no forced version cutoff — and it decouples your navigation reliability from Google's release policy. Your custom logger app doesn't care which navigation app is on screen; it logs sensors/GPS independently either way, so this choice doesn't block anything else in the plan.

If you specifically want the Google Maps UI/routing quality, sideloading an archived APK is the fallback, with the caveat that it may degrade over time without a clear failure point (silent staleness rather than a hard error).

This is logged as **Open Decision #2** in Section 13 — resolve it before Phase 2.

---

## 6. Data Schema (Local Database)

### `rides`

| Field | Type | Notes |
|---|---|---|
| ride_id | INTEGER (PK) | Auto-increment |
| start_timestamp | DATETIME | |
| end_timestamp | DATETIME | |
| start_lat / start_lon | REAL | |
| end_lat / end_lon | REAL | |
| distance_m | REAL | Computed from GPS points at export/analysis time |
| duration_s | INTEGER | |
| avg_speed_kmh | REAL | Computed |
| max_speed_kmh | REAL | Computed |
| elevation_gain_m | REAL | Computed, cross-check GPS vs. barometer |
| route_tag | TEXT | Manually entered label, e.g. "commute-main-st" — critical for route comparison |
| weather_notes | TEXT | Manual entry initially; automate later via API |
| battery_start_pct / battery_end_pct | INTEGER | For battery-budget tracking |
| notes | TEXT | Freeform |

### `gps_points`

| Field | Type | Notes |
|---|---|---|
| point_id | INTEGER (PK) | |
| ride_id | INTEGER (FK → rides) | |
| timestamp | DATETIME | |
| lat / lon | REAL | |
| altitude_gps_m | REAL | Noisy — barometer is the better elevation source |
| speed_mps | REAL | |
| bearing_deg | REAL | |
| accuracy_m | REAL | GPS-reported accuracy estimate, useful for filtering bad points |

### `sensor_readings`

| Field | Type | Notes |
|---|---|---|
| reading_id | INTEGER (PK) | |
| ride_id | INTEGER (FK → rides) | |
| timestamp | DATETIME | |
| sensor_type | TEXT | 'accelerometer', 'gyroscope', 'magnetometer', 'barometer', 'light' |
| x, y, z | REAL, nullable | Null for scalar sensors |
| scalar_value | REAL, nullable | Used for barometer (pressure), light |

### `ride_summary` (derived, computed post-hoc, not written by the app)

| Field | Type | Notes |
|---|---|---|
| ride_id | INTEGER (FK) | |
| roughness_score | REAL | From accelerometer variance |
| num_braking_events | INTEGER | From accel/gyro spike detection |
| elevation_gain_baro_m | REAL | |
| elevation_gain_gps_m | REAL | For comparison/validation |
| avg_temp_c | REAL | If weather API integrated |

> Schema is intentionally normalized and append-only at the raw layer (`gps_points`, `sensor_readings`). All derived/computed fields live in `ride_summary`, generated by the Python pipeline — not the app. This keeps raw data reprocessable if your analysis methods improve later, without needing to re-ride anything.

---

## 7. Data Collection Plan

| Sensor | Sampling rate | Rationale |
|---|---|---|
| Accelerometer | 50 Hz (`SENSOR_DELAY_GAME`) | Fast enough to catch bumps/braking, not so fast it floods storage |
| Gyroscope | 50 Hz | Matched to accelerometer for combined motion analysis |
| Magnetometer | 10 Hz | Heading changes slowly relative to accel/gyro |
| Barometer | 1 Hz | Pressure changes slowly; elevation doesn't need high frequency |
| GPS | 1 Hz (every 1 second) | Standard for cycling speed/route granularity |
| Light | On-change | Cheap, optional context tag |

**Storage budget estimate:** at ~50Hz for two 3-axis sensors plus 1Hz for GPS/barometer, expect roughly 5,000–6,000 rows/minute across tables. A 1-hour ride is on the order of a few MB as CSV — trivial for phone storage, but worth batching writes (not one DB write per sample) to avoid I/O overhead and battery drain.

**Calibration notes:** do a short stationary calibration ride (phone mounted, bike stationary) at the start of the project to record sensor baseline/noise floor — this becomes the reference for what counts as a "real" bump vs. sensor noise later.

---

## 8. Data Pipeline (Phone → Analysis)

1. **Export** — app writes CSV (or exposes the raw SQLite `.db` file) to a designated folder.
2. **Transfer** — WiFi file transfer (e.g., a simple local HTTP server on the phone, or syncing to a laptop-visible folder) to avoid depending on cellular/cloud. USB is the reliable fallback.
3. **Ingest** — Python script loads CSV/SQLite into pandas DataFrames.
4. **Clean** — drop GPS points with poor accuracy (`accuracy_m` above a threshold), interpolate small gaps, flag but don't silently drop sensor dropouts.
5. **Store** — append into a master local dataset (SQLite or Parquet) so every ride accumulates into one queryable history rather than living as scattered CSVs.
6. **Feed** — cleaned data goes into the three analysis modules (Section 9).

---

## 9. Analysis Modules

### 9.1 Route Comparison

- **Objective:** determine which route is actually best across time, smoothness, and elevation effort — not just which feels fastest.
- **Inputs:** `rides` (filtered by `route_tag`), `gps_points`, `ride_summary.roughness_score`.
- **Method:** group rides by `route_tag`, compare distributions (not single runs) of duration, avg speed, roughness score, elevation gain. Plot routes on a map (folium) color-coded by speed or roughness.
- **Output:** a comparison table/report ranking routes on each metric, plus a visual map overlay.
- **Minimum data needed:** at least 3–5 rides per route before drawing conclusions — a single ride per route is an anecdote, not a comparison.

### 9.2 Speed Decay Modeling

- **Objective:** model how speed drops off over the course of a ride and identify what predicts it.
- **Inputs:** `gps_points.speed_mps` over `timestamp`/distance-into-ride, joined with elevation and roughness at each point.
- **Method:** fit speed as a function of distance/time-into-ride, elevation gradient, and roughness; start with simple linear/polynomial regression before anything fancier.
- **Output:** a decay curve per ride and aggregate patterns (e.g., "speed drops fastest in the first 10 minutes" or "elevation matters more than time-in-ride").

### 9.3 Predictive ETA

- **Objective:** predict ride duration for a given route before departure, using your own historical pace rather than a generic estimate.
- **Inputs:** route distance, elevation profile, historical pace on that route/similar routes, optionally weather.
- **Method:** regression model (start with scikit-learn linear regression or a simple decision tree) trained on completed rides.
- **Validation:** split by *ride*, not by individual point, to avoid leakage (points within the same ride are correlated). Hold out entire rides for testing. Report MAE (mean absolute error in minutes).
- **Output:** given a route, produce a predicted ETA with an uncertainty range, testable against your generic maps app's estimate.

**Future extension candidates (not in initial scope, but the schema supports them):**

- Weather correlation (pull historical weather by ride timestamp/location).
- Pothole/road-hazard mapping from roughness spikes, plotted geographically.
- Braking-event / near-miss detection at intersections.
- Pairing external sensors (heart rate, cadence) for effort-normalized analysis.

---

## 10. Roadmap & Milestones

| Phase | Focus | Rough effort | Key deliverable |
|---|---|---|---|
| 0 | Device prep & verification | ~1 week | Completed checklist (3.1), OS/nav decisions resolved |
| 1 | MVP sensor logger app | 2–3 weeks | App that logs a ride to local DB, start/stop UI, CSV export |
| 2 | Navigation integration | ~1 week | Nav app launches and works alongside logger during a real ride |
| 3 | Field data collection | 2–4 weeks, ongoing | 10+ logged rides across 3+ routes |
| 4 | Data pipeline & cleaning | 1–2 weeks | Reproducible ingest/clean script, master dataset |
| 5 | Analysis modules | 3–4 weeks | Route comparison report, speed decay model, ETA model with validation numbers |
| 6 | Presentation layer (optional) | 1–2 weeks | Notebook report or simple Streamlit dashboard |
| 7 | Documentation & retrospective | Ongoing | This doc kept current, lessons-learned notes added |

Phases 3–5 can overlap — you don't need to stop collecting data before starting the pipeline work.

---

## 11. Risks & Mitigations

| Risk | Impact | Likelihood | Mitigation |
|---|---|---|---|
| Google Maps incompatible/degrades on old Android | Navigation feature unreliable | Medium–High | Default to OsmAnd (Section 5); logger is independent of nav app either way |
| Bootloader unlock/root bricks the device | Total device loss | Low–Medium | Full backup first; verify method against exact model/build number before flashing anything |
| GPS drift in urban areas/under tree cover | Noisy location/speed data | Medium | Filter by `accuracy_m`; smooth with a moving average or Kalman filter in the pipeline |
| Battery drain mid-ride | Incomplete ride data | Medium | Power bank; log battery %; tune sampling rates if drain is excessive |
| App crash / data loss mid-ride | Lost ride | Low–Medium | Batch DB writes with periodic flush (not only at ride end); consider crash-safe writes |
| Not enough rides for meaningful ML | Weak/overfit ETA model | Medium | Set a minimum-N threshold before trusting model output; treat early results as exploratory |
| Weatherproofing failure | Water/dust damage to phone | Low | Test the case/pouch before committing to real rides |
| Scope creep (adding features before MVP works) | Delayed working prototype | Medium | Follow the phase order in Section 10; resist adding analysis modules before Phase 1–2 are solid |

---

## 12. Testing & Validation Plan

- **Short test ride before full data collection** — a 5-minute stationary + short loop test to confirm logging starts/stops cleanly and all sensor streams populate.
- **Cross-validation of elevation sources** — compare barometer-derived elevation gain against a known reference (Google Earth elevation profile or a mapping tool) for at least one familiar route, to catch calibration drift early.
- **Data quality checks before analysis** — null checks, timestamp gap detection, outlier filtering (e.g., impossible speed jumps) run automatically before any ride enters the analysis pipeline.
- **Model validation discipline** — always split by ride for train/test, never by individual data point; report error metrics (MAE/RMSE) rather than just eyeballing plots.

---

## 13. Open Decisions Log

Update this table as decisions get made — don't just silently edit earlier sections. This is the "room for future modifications and corrections" the plan needs.

| # | Decision | Status | Options | Current recommendation |
|---|---|---|---|---|
| 1 | OS path: stay stock/rooted vs. custom ROM | **OPEN** | (a) Stay on stock/rooted Android 5.0.1 (b) Flash LineageOS 14.1 (Android 7.1, unofficial build) | (a) — simpler Google Play Services compatibility, avoids extra flashing risk |
| 2 | Navigation app | **OPEN** | Google Maps (sideloaded old APK) / OsmAnd / Waze | OsmAnd — offline-first, actively maintained for old Android (Section 5) |
| 3 | Data transfer method | **OPEN** | USB / local WiFi transfer / cloud sync | WiFi local transfer — no cloud dependency, keeps data private |
| 4 | App language | Resolved | Kotlin vs. Java | Kotlin |
| 5 | ETA modeling approach | **OPEN** (revisit in Phase 5) | scikit-learn regression vs. more complex ML | Start simple (linear/tree regression); escalate only if underfitting |
| 6 | Rooting method for SCH-I545 specifically | **OPEN** | TBD — needs verification against exact build number | Research once Section 3.1 checklist is filled in |

---

## Appendix A: Glossary

- **IMU** — Inertial Measurement Unit; here, the combination of accelerometer + gyroscope + magnetometer.
- **GMS** — Google Mobile Services (Play Store, Play Services) — required for the stock Google Maps app to function normally.
- **Room** — Android's official abstraction layer over SQLite.
- **MAE / RMSE** — Mean Absolute Error / Root Mean Squared Error, standard regression evaluation metrics.
- **Kalman filter** — an algorithm for smoothing noisy sequential sensor data (commonly used for GPS/IMU fusion).
- **APK** — Android application package file; sideloading means installing one manually instead of via the Play Store.
- **Data leakage** — when information from the test set improperly influences training, inflating apparent model performance.

## Appendix B: Sources Referenced

- [Samsung Galaxy S4 Verizon (SCH-I545) specifications — GSMChoice](https://www.gsmchoice.com/en/catalogue/samsung/galaxys4verizon/)
- [Samsung SCH-I545 Galaxy S4 detailed tech specs — PhoneDB](https://phonedb.net/index.php?c=samsung_sch-i545_galaxy_s4__samsung_altius&d=detailed_specs&id=4240&m=device)
- [End of support for outdated Google Maps versions — Google Maps Help](https://support.google.com/maps/answer/10993350?hl=en)
- [Google Maps system & browser requirements — Android — Google Maps Help](https://support.google.com/maps/answer/3096703?hl=en&co=GENIE.Platform%3DAndroid)
- [Google Maps Intents for Android — Google for Developers](https://developers.google.com/maps/documentation/urls/android-intents)

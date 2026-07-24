# Bike Data Logger — Project Plan

A repurposed Samsung Galaxy S4 as a bike-mounted telemetry device: log ride sensor data, show a live ride dashboard, and mine the data for route comparison, speed-decay modeling, and predictive ETA.

## Document Control

| Field | Value |
|---|---|
| Version | 0.6 |
| Owner | Anushil |
| Status | Phase 1 complete (Steps 0-16). Phase 2 navigation dropped 2026-07-23 after real-ride testing; rescoped to a ride dashboard (live speed/odometer/elevation + local street view) — checklist written (`BUILD_CHECKLIST_Phase2.md` Steps 4-10), none started yet. |
| Last updated | 2026-07-24 |

**Change log**

| Date | Version | Change | Author |
|---|---|---|---|
| 2026-07-24 | 0.7 | Documentation coherence pass: removed leftover "navigation" framing from the title/opening line/Objective #2/permissions table (nav was dropped 2026-07-23, this doc's own Section 5 already said so but other sections hadn't caught up); fixed Section 8's transfer-method wording to match Section 13 #3 (USB primary, WiFi fallback — it had drifted to say the reverse); filled in Section 3.1's device-ID blanks with values already settled elsewhere in this doc and `CLAUDE.md`; added the confirmed-but-untracked export `OutOfMemoryError` (found 2026-07-22, ride 39) to Sections 11 and 13; clarified `ACCESS_BACKGROUND_LOCATION`'s stated purpose now that there's no separate nav app to reference. | Claude |
| 2026-07-09 | 0.1 | Initial draft plan created | Claude |
| 2026-07-17 | 0.2 | Section 13 backfilled: decisions #1 (OS path), #2 (nav app), #6 (rooting method) marked Resolved with actual reasoning from Phase 0/1 build notes (see `CLAUDE.md`). Decision #3 (data transfer) left OPEN — USB has been used for every data pull so far, but that reflected dev/debugging convenience, not a considered choice. | Claude |
| 2026-07-17 | 0.3 | Decision #3 (data transfer) resolved to USB — confirmed reliable across Phase 1, WiFi ADB retained as an existing fallback rather than the primary path. | Claude |
| 2026-07-17 | 0.4 | Decision #2 (nav app) reopened from Resolved:OsmAnd to Testing:Google Maps — on-device evidence (Maps already installed, functional, offline maps downloaded) outweighs the earlier theoretical Play-Services-cutoff concern. Gray-dot GPS symptom identified as the same known no-SIM/no-A-GPS limitation from Phase 1, not a Maps-specific issue. | Claude |
| 2026-07-20 | 0.5 | Decision #2 (nav app) re-resolved from Testing:Google Maps to Resolved:Organic Maps — Google Maps directly tested and ruled out (no offline bicycling directions, confirmed via Google's docs and an on-site WiFi-off test); OsmAnd found to no longer support this device's Android 5.x at all (dropped in 2021). Organic Maps confirmed compatible and working (offline cycling route to a real destination, WiFi disconnected). | Claude |
| 2026-07-23 | 0.6 | Decision #2 (nav app) reopened and reversed to **no navigation**, dropping Organic Maps entirely — real Step 3 combined-ride testing (rides 38/39, 2026-07-22) found it exhausts this device's RAM alongside the logger, causing both its own native crashes and silent mid-ride death of `LoggingService` (up to 55% of a ride lost, masked by the Step 10 fallback). Investigated exporting a calculated route from Organic Maps to drive a lighter display instead — confirmed hands-on, no such path exists in the installed build. Phase 2 rescoped from "navigation integration" to "ride dashboard": live speed/odometer/elevation (from data already logged) plus a local street view (~75m radius) from a bundled offline OSM extract, no third-party nav app. | Claude |

> This document is meant to be edited directly as decisions change. Anything marked **[OPEN]** in Section 13 is an unresolved decision — update the log entry when you resolve it instead of just changing the text elsewhere, so there's a record of *why* it changed.

---

## 1. Project Overview

**Vision:** Turn a spare Galaxy S4 into a dedicated bike computer that (a) shows a live ride dashboard — speed, odometer, elevation, and a small local street view of your immediate surroundings — and (b) silently logs GPS + motion + environmental sensor data for every ride, producing a personal dataset you own and can analyze.

**Revised 2026-07-23 — turn-by-turn navigation dropped.** The original vision paired the logger with a full navigation app (Organic Maps). Real Step 3 testing (`BUILD_CHECKLIST_Phase2.md`, rides 38/39 on 2026-07-22) found that running Organic Maps' live map rendering continuously alongside the logger exhausts this device's 2GB RAM: Organic Maps itself native-crashed on both rides, and — more seriously — `LoggingService` silently died mid-ride on both rides too (190s early on ride 38, 947s/55% of ride 39), masked by the Step 10 ride-bookkeeping fallback still producing a plausible-looking `endTime`. No hands-free mitigation survived scrutiny (switching apps mid-ride isn't possible with no free hands on a bike; voice guidance is unreliable in traffic noise). Full forensics in `CLAUDE.md`. Navigation is out of scope going forward — see Section 5.

**Objectives**

1. Build a custom Android app (the "CSE-flex path") that logs sensor + GPS data during a ride, independent of whatever else is on screen.
2. Show a live ride dashboard (speed, odometer, elevation, local street view) on the same device while it logs in the background — no separate navigation app (dropped 2026-07-23, see Section 5).
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
- Live on-device ride dashboard: speed, odometer, elevation (from already-logged GPS/barometer data), and a small local street view (~75m radius) rendered from a bundled offline OSM extract, bounded to the bike's actual operating range (Lansing + East Lansing + Haslett + Okemos, MI — confirmed the bike doesn't leave this region; genuinely new areas outside it are out of scope by design, an iPhone is the accepted fallback there) — see Section 5.
- Local storage on-device (SQLite/Room), manual or WiFi export to a laptop.
- Python-based analysis: route comparison, speed decay, predictive ETA.
- Documentation of the build so it's reproducible and correctable later.

**Out of scope (for now — candidates for future phases)**

- Turn-by-turn navigation — dropped 2026-07-23; see Section 5 for why. Not ruled out forever, just not something this device can do reliably alongside logging.
- Cloud sync / remote dashboard accessible from anywhere.
- Real-time analytics *during* a ride beyond the dashboard itself (deeper analysis is post-ride only).
- Pairing with external sensors (heart rate strap, cadence sensor) — noted as a future extension in Section 9.
- Machine-learning models beyond simple regression/tree-based methods for the first pass.

**Assumptions**

- The S4 (Verizon SCH-I545, confirmed) will be used WiFi-only; no active cellular plan/SIM. All design choices below default to "must work over WiFi only," so the Verizon SIM-lock question is irrelevant to this project.
- You have a personal computer to run the Python analysis side; the phone itself is not doing the data science.
- Riding is outdoors with generally clear sky view (GPS accuracy assumption).

---

## 3. Device Inventory & Hardware Setup

### 3.1 Device identification checklist

Fill this in before doing anything else — it determines several downstream decisions.

| Item | How to find it | Your value |
|---|---|---|
| Exact model number | Settings → About phone → Model number (or under the battery on older units) | `SCH-I545` (Verizon) |
| IMEI | Dial `*#06#` or Settings → About phone → Status | `990003378910794` |
| Current Android version | Settings → About phone → Android version | `5.0.1` (stock ceiling for this model) |
| Build number | Settings → About phone → Build number | `LRX22C.I545VRUGOF1` |
| Battery health / swelling check | Visual inspection — do this before mounting anything on a moving bike | Pass — visually inspected, no swelling |
| Carrier lock status | Insert a non-Verizon SIM and see if it registers, or check via IMEI lookup on Verizon's device unlock page | N/A — WiFi-only design, no SIM used |
| Storage available | Settings → Storage | 16GB — confirmed sufficient |

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
- **Live Dashboard** — speed/odometer/elevation computed from the same GPS+barometer stream already being logged, surfaced to the UI in real time while a ride is active.
- **Local Street Renderer** — draws nearby street geometry (~75m radius) around the current GPS position on a plain Canvas, from a locally-stored OSM extract fetched once over WiFi. Replaces the originally-planned Navigation Launcher/third-party nav app (dropped — see Section 5).
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
| `ACCESS_BACKGROUND_LOCATION` | Keep GPS logging active if the app itself gets backgrounded (e.g. screen locks) during an active ride — not needed when no ride is active |
| `FOREGROUND_SERVICE` (+ `FOREGROUND_SERVICE_LOCATION` on newer Android) | Keep the logging service alive during a ride |
| `WRITE_EXTERNAL_STORAGE` / scoped storage APIs | Export CSV files |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Optional: pulling weather data by timestamp/location for later analysis |
| `WAKE_LOCK` | Prevent the logging service from being killed when the screen sleeps |

---

## 5. Navigation Integration — Dropped 2026-07-23

**This section is now historical.** It documents how a nav-app decision was reached and then reversed — kept in full rather than deleted, per this doc's own rule of recording *why* things changed, not just the current state.

**Why navigation was dropped:** Organic Maps (the app this section resolved to) was integrated per `BUILD_CHECKLIST_Phase2.md` Steps 1-2, then tested on two real combined rides for Step 3 (rides 38/39, 2026-07-22). Both rides showed Organic Maps native-crashing (`std::bad_alloc`/SIGABRT on one, SIGSEGV on the other) and — the more serious finding — `LoggingService` itself silently dying mid-ride under the same memory pressure, well before the rider tapped Stop Ride (190s early on ride 38; 947s/55% of ride 39 lost). The existing Step 10 fallback (`getActiveRide()`) still closed each ride with a plausible `endTime`, so nothing in the app's own UI or a casual DB check flagged the data loss — it only surfaced via cross-referencing last-logged-timestamp against `endTime`. Mitigations considered and ruled out: backgrounding Organic Maps between glances (not viable — no free hands on a bike to switch apps); voice-guided navigation with the screen off (not viable — unreliable in traffic noise, and no verified basis for Organic Maps' voice guidance quality); exporting a calculated route from Organic Maps to drive a lighter custom display instead of its full rendering UI (investigated hands-on on the physical device — no such export path exists in the installed build, despite an unrelated "save planned routes" changelog entry suggesting otherwise). Full forensics (DB pull, dropbox crash logs, `batterystats` history correlation) in `CLAUDE.md`.

**What replaces it:** a live on-device dashboard (speed/odometer/elevation, computed from data already being logged) plus a small local street view (~75m radius) rendered from a bundled offline OSM extract — see the new Component list in Section 4.1 and the rescoped `BUILD_CHECKLIST_Phase2.md`. This deliberately avoids the failure mode above: no third-party native map renderer running continuously, no large-area live rendering, no dependency Organic Maps' own crash risk.

The original decision narrative (kept for context) follows.

**The problem:** the newest Google Maps app effectively requires Android 9+. The S4 cannot reach Android 9 through any known ROM path for this hardware. That doesn't mean Google Maps is impossible — older, archived versions of the app (from APK archive sites) may still install and function on Android 5–7, since the app itself doesn't stop working, it just stops receiving *updates*. Map data and traffic info in an old app version will eventually go stale, though, since it depends on backend API compatibility that Google doesn't guarantee to maintain forever for old clients.

**Fallback matrix**

| Option | Works on Android 5–7 | Offline capable | Live traffic | Effort |
|---|---|---|---|---|
| Sideload archived Google Maps APK | Likely, unmaintained | No | Yes (until backend cuts off old clients) | Low |
| OsmAnd | Yes, actively maintained for old Android | Yes (full offline maps) | No | Low |
| Waze | Uncertain — similar version constraints to Maps likely | No | Yes | Unverified |
| Browser-based Google Maps (mobile site) | Yes | No | Yes | Low, but worse UX for turn-by-turn |

**Resolved (2026-07-17 — see Section 13 #2):** Nav app is **Organic Maps** (`app.organicmaps`). Path to that answer: Google Maps (already installed, v10.63.6) was directly tested and ruled out — confirmed via Google's own documentation and an on-site offline test (WiFi disconnected) that it cannot compute bicycling directions without a live connection, disqualifying given this project's WiFi-only design. OsmAnd, the original default in the fallback matrix above, turned out to no longer support this device's Android version at all — it dropped Android 5.x support in 2021, and current builds require Android 7.0+. Organic Maps was then tried and confirmed compatible (Android 5.0 minimum, matches this device exactly, no legacy-APK workaround needed) and confirmed working: installed via Play Store, offline maps downloaded, and an offline cycling-mode route to a real destination confirmed with WiFi disconnected.

This was logged as **Open Decision #2** in Section 13 — reopened and re-resolved 2026-07-23 to **no navigation** (see the top of this section and Section 13 for the current status).

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
2. **Transfer** — USB (`adb pull`, or `su -c cp` to `/sdcard` + `adb pull` for root-owned files) — confirmed reliable across every Phase 1 data pull (Steps 7-10). WiFi ADB (`adb tcpip`/`adb connect`) is an available fallback if USB ever degrades, per Section 13 #3.
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
| 2 | Ride dashboard (navigation dropped 2026-07-23) | ~1-2 weeks | Live speed/odometer/elevation + local street view (~75m) working alongside logger during a real ride |
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
| Combined nav app + logger exhausts device RAM | Silent mid-ride data loss, third-party app crashes | **Materialized, resolved by dropping nav** | Confirmed on real rides 38/39 (2026-07-22): Organic Maps native-crashed and `LoggingService` silently died mid-ride under combined memory pressure. Navigation dropped entirely 2026-07-23; replaced with a lightweight in-app dashboard + local street view that doesn't run a third-party native map renderer (Section 5) |
| Bootloader unlock/root bricks the device | Total device loss | Low–Medium | Full backup first; verify method against exact model/build number before flashing anything |
| GPS drift in urban areas/under tree cover | Noisy location/speed data | Medium | Filter by `accuracy_m`; smooth with a moving average or Kalman filter in the pipeline |
| Battery drain mid-ride | Incomplete ride data | Medium | Power bank; log battery %; tune sampling rates if drain is excessive |
| App crash / data loss mid-ride | Lost ride | Low–Medium | Batch DB writes with periodic flush (not only at ride end); consider crash-safe writes |
| Not enough rides for meaningful ML | Weak/overfit ETA model | Medium | Set a minimum-N threshold before trusting model output; treat early results as exploratory |
| Weatherproofing failure | Water/dust damage to phone | Low | Test the case/pouch before committing to real rides |
| Scope creep (adding features before MVP works) | Delayed working prototype | Medium | Follow the phase order in Section 10; resist adding analysis modules before Phase 1–2 are solid |
| Export of long rides can OOM-crash the app | Export silently fails / app crashes instead of showing "Export failed" | **Materialized** — confirmed 2026-07-22, ride 39 (~199k sensor rows) | `MainActivity.exportMostRecentRide()` loads a ride's entire `sensor_readings` into memory as one `List` before writing; needs to stream rows to disk instead. Not caught by the existing `catch (Exception)` since `OutOfMemoryError` is an `Error`. Not yet fixed — see Section 13 #7 |

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
| 1 | OS path: stay stock/rooted vs. custom ROM | **RESOLVED** | (a) Stay on stock/rooted Android 5.0.1 (b) Flash LineageOS 14.1 (Android 7.1, unofficial build) | (a), but forced rather than chosen — build `LRX22C.I545VRUGOF1` has a permanently eFuse-locked bootloader, so no custom ROM (LineageOS/TWRP/CWM) is possible on this exact firmware at all. A 2023 community chainload exploit exists as a theoretical advanced option but is out of scope. |
| 2 | Navigation app | **RESOLVED → DROPPED (2026-07-23)** | Google Maps (already installed) / OsmAnd / Organic Maps / Waze / no navigation | **No navigation.** Organic Maps was resolved, integrated (Phase 2 Steps 1-2), and confirmed working offline — but real Step 3 rides (38/39, 2026-07-22) found it exhausts this device's RAM alongside the logger, native-crashing itself and silently killing `LoggingService` mid-ride (up to 55% of a ride lost, masked by the Step 10 fallback). No viable mitigation found (app-switching needs free hands; voice guidance is unreliable in traffic; Organic Maps has no route-export path to drive a lighter custom display — verified hands-on). Replaced with an in-app dashboard + local street view (Section 5). |
| 3 | Data transfer method | **RESOLVED** | USB / local WiFi transfer / cloud sync | USB (`adb pull` / `su -c cp` + `adb pull`) — confirmed reliable across every Phase 1 data pull (Steps 7–10). `adb tcpip`/WiFi ADB already exists as a proven fallback (used to work around a separate USB install-specific flakiness in Step 10), so it's available without new setup if the USB path ever degrades again. |
| 4 | App language | Resolved | Kotlin vs. Java | Kotlin |
| 5 | ETA modeling approach | **OPEN** (revisit in Phase 5) | scikit-learn regression vs. more complex ML | Start simple (linear/tree regression); escalate only if underfitting |
| 6 | Rooting method for SCH-I545 specifically | **RESOLVED** | TBD — needs verification against exact build number | KingRoot 4.5.0 — not the originally-identified KingoRoot, whose one-click flow now fails with a JSON parse error against its live backend (a decade-old app calling a backend whose response format has since changed). KingRoot 4.5.0 is a different company despite the similar name, and is independently documented as working for this exact firmware. Executed during Phase 1 Step 7 — needed sooner than planned, to work around `adb run-as` being blocked (`ro.debuggable=0`) on this stock Samsung build. |
| 7 | Fix export `OutOfMemoryError` on long rides | **OPEN** | (a) Materialize the full ride into a `List` before writing (current, broken) (b) Stream rows to disk as they're read | (b) — root-caused 2026-07-22: `MainActivity.exportMostRecentRide()` loads a ride's entire `sensor_readings` table into memory at once; ride 39's ~199k rows threw a real `OutOfMemoryError` (an `Error`, not caught by the existing `catch (Exception)`). Not yet implemented. |

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

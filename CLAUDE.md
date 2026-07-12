# Bike Data Logger — Project Memory

Android app (Kotlin) turning a wiped Samsung Galaxy S4 (Verizon SCH-I545, build
`LRX22C.I545VRUGOF1`, Android 5.0.1) into a bike-mounted telemetry device:
turn-by-turn navigation + simultaneous sensor/GPS logging, analyzed afterward in
Python/pandas. Built by a data science student (CSE background) using Android
Studio on Windows.

Read this file before making any code suggestions or edits.

## How to work with me

- Concise, direct answers. Minimal unnecessary formatting.
- Following `BUILD_CHECKLIST_Phase1.md` strictly, step by step. Implement only
  what the current step asks for. Stop at its verify condition. Don't add
  robustness/abstraction to placeholder code that's about to be replaced.
- Actively avoiding vibe coding — I want to understand and be able to defend
  every line, not just accept diffs. Push back or ask me to explain if
  something looks like I'm rubber-stamping without understanding it.
- Ask directly when something is a real decision point, don't guess.
- Commit at each verified checkpoint (not mid-step). Commit messages should
  match the actual diff.

### Explain before acting

- Before making any change, explain in 2-4 plain-language sentences what
  you're about to do and why — not just a diff to approve. I should know the
  intent before I see the code.
- This project is also how I'm learning Android/Kotlin. When you introduce a
  concept, API, or annotation I haven't used yet in this repo (Room, a
  coroutine, a lifecycle callback, etc.), briefly explain what it is and why
  it's needed here — not just how to use it. Don't assume I already know it
  just because it's common.
- If something you're about to do is non-obvious or has a simpler
  alternative, say so before doing it, not after.

### Keep it simple — no over-engineering

- Default to the simplest implementation that satisfies the current
  checklist step. Nothing extra.
- Don't add config options, interfaces, helper abstractions, extra error
  handling, or defensive code for cases the current step doesn't ask about —
  even if it seems like "good practice." If you think something will be
  needed later, say so briefly and move on; don't build it now.
- If you're ever choosing between a clever/idiomatic solution and a
  boring/obvious one and both satisfy the step, pick the boring one.

## Where the project stands

**Phase 0 (device prep): complete.**
**Phase 1 (sensor logger app): Steps 1-6 of 10 done and verified. Step 7
(physical S4) next.**

Full plan: `Bike_Data_Logger_Project_Plan.md`. Step-by-step build order:
`BUILD_CHECKLIST_Phase1.md`.

### Phase 0 — resolved decisions

- **OS path: forced to stock/rooted.** Build `LRX22C.I545VRUGOF1` has a
  permanently eFuse-locked bootloader — no LineageOS/TWRP/CWM/any custom ROM
  is possible on this exact firmware. (A 2023 community chainload exploit
  exists as a theoretical advanced option but is out of scope.)
- **Root method: identified**, OF1-specific guide confirmed working. Not yet
  executed — not needed until Phase 1 Step 7 (physical device).
- **Carrier lock: not applicable** — project is WiFi-only by design.
- **Navigation app: OsmAnd** (not yet implemented — that's Phase 2). Chosen
  over Google Maps because Google ended Play Services updates for all of
  Android Lollipop as of July 2024. Mitigation: lean on GMS-independent
  tooling (OsmAnd, F-Droid/Aurora Store).
- 16GB storage confirmed sufficient; battery visually inspected, no swelling.

### Phase 1 — build progress

- **Step 0 (environment):** Android Studio installed, emulator created.
- **Step 1 (Hello World):** Empty Views Activity template, Kotlin, minSdk 21
  (matches the S4's real-world floor). Verified on emulator.
- **Step 2 (accelerometer → Logcat):** `SensorEventListener` for
  `TYPE_ACCELEROMETER`, registered in `onResume`/unregistered in `onPause`
  (so it doesn't drain battery while backgrounded). Logs x/y/z via
  `Log.d("SENSOR", ...)`. Verified via emulator's Extended Controls rotation
  dial.
- **Step 3 (GPS → Logcat):** `FusedLocationProviderClient.getCurrentLocation()`,
  one-shot. Requires both `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`
  — Android 12+/API 31+ rejects a FINE-only permission request, so both are
  in the manifest and the runtime request array. Verified via emulator's
  Extended Controls fake GPS location.
  - Bug caught and fixed: `onRequestPermissionsResult` and `getOneLocation`
    had been accidentally nested inside `onCreate()` instead of declared as
    class members (legal Kotlin syntax, wrong behavior — the functions
    weren't proper Activity lifecycle callbacks). Fixed.
- **Step 4 (Room database) — done:** one entity `GpsPoint(id, timestamp, lat,
  lon)`, `GpsPointDao` (insert/getAll, both `suspend`), `AppDatabase`
  singleton, insert/query test buttons wired via `lifecycleScope.launch`.
  Verified via Database Inspector — row appears in `bike_data.db` /
  `gps_points` table, not just Logcat. Nothing from Step 3's real GPS is
  wired in yet — that's Step 5. Added `androidx.lifecycle:lifecycle-runtime-ktx:2.6.2`
  for `lifecycleScope`; confirmed via manifest merge that it doesn't bump the
  minSdk floor past 21 (same class of check that got Room pinned to 2.7.2).
- **Step 5 (real GPS → DB) — done:** `btnInsert` now does one real
  `getCurrentLocation()` fetch and inserts the result (replacing the
  hardcoded lat/lon). New `btnStartLogging` button repeats that 6 times, 5
  seconds apart (~30s total), via `lifecycleScope.launch { repeat(6) { ...;
  delay(5000) } }`. Removed the Step 3 leftover (auto GPS-fetch-to-Logcat on
  launch) since it was superseded by the real DB insert path — nothing later
  in the checklist depended on it. Verified on emulator: two separate
  "Start logging" runs each produced exactly 6 rows, 5 seconds apart, with
  real fake-GPS coordinates.
- **Step 6 (sensor logging table) — done:** new entity `SensorReading(id,
  timestamp, x, y, z)`, separate table from `GpsPoint`. `AppDatabase` bumped
  to version 2 with `fallbackToDestructiveMigration()` (disposable emulator
  test data, no need for a real `Migration` yet). New `btnStartSensorLogging`
  button: while active, `onSensorChanged` appends readings to an in-memory
  list (can't call suspend Room inserts directly from that callback, and
  250 individual insert coroutines would be wasteful); after a 5-second
  window it does one batch `insertAll`.
  - Bug caught and fixed: `registerListener` had been using
    `SensorManager.SENSOR_DELAY_NORMAL` since Step 2 (a slow, UI-oriented
    hint, ~5-16Hz) instead of `SENSOR_DELAY_GAME` (~50Hz, meant for logging).
    First test run produced only 76 rows in 5s; switching to `SENSOR_DELAY_GAME`
    fixed it to 326 rows in 5s (~65Hz — delay hints are a requested minimum
    interval, not a hard cap, so faster-than-requested is expected, not a bug).
  - Verified on emulator via Database Inspector: `sensor_readings` table
    populated, row count in the right ballpark for 50Hz × 5s.
- **Not yet started:** Step 7 onward (physical device, ride sessions, CSV
  export, first real ride).

### Repo

- GitHub: `github.com/anushiljha/bike-data-logger`, public.
- Default branch is `master` (not `main` — minor naming inconsistency from
  setup, functionally irrelevant).
- `gh` CLI authenticated locally.
- `.gitignore` excludes `build/`, `.gradle/`, `local.properties`, `*.iml`,
  most of `.idea/`.

## Working style notes specific to this repo

- Steps 1-6 of Phase 1 are done entirely on the Windows PC emulator — the
  physical S4 doesn't come into play until Step 7. This deliberately
  separates "is the app logic sound" from "is this an old-phone quirk."
- Don't combine GPS and sensor logic before both are independently verified
  — that's how tutorials/projects end up with unclear bugs.

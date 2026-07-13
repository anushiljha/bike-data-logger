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
**Phase 1 (sensor logger app): Steps 1-8 of 10 done. Step 8's code is built,
smoke-tested indoors on the physical S4, and two real bugs found during that
testing are fixed — but its actual checklist verify condition (a real 5-minute
walk) is still outstanding, on hold while a sprained ankle heals. Step 9
(CSV export) next once that walk is done.**

Full plan: `Bike_Data_Logger_Project_Plan.md`. Step-by-step build order:
`BUILD_CHECKLIST_Phase1.md`.

### Phase 0 — resolved decisions

- **OS path: forced to stock/rooted.** Build `LRX22C.I545VRUGOF1` has a
  permanently eFuse-locked bootloader — no LineageOS/TWRP/CWM/any custom ROM
  is possible on this exact firmware. (A 2023 community chainload exploit
  exists as a theoretical advanced option but is out of scope.)
- **Root method: executed during Phase 1 Step 7** (see below) — turned out to
  be needed sooner than planned, to work around `adb run-as` being blocked on
  this stock Samsung build. Full narrative under Step 7.
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
    First test run produced only 76 rows in 5s. The table isn't cleared
    between runs (only the in-memory buffer is), so the next test showed
    326 total rows — 76 old + 250 new, confirmed via a Database Inspector
    SQL query split on `id`. The 250 new rows is a spot-on match for
    50Hz × 5s once `SENSOR_DELAY_GAME` was in place.
  - Verified on emulator via Database Inspector: `sensor_readings` table
    populated, 250 new rows for the fixed-rate run — matches expected
    50Hz × 5s exactly.
- **Step 7 (move to the real S4) — done:** Developer Options/USB
  debugging already enabled from earlier root prep; `adb devices` shows the
  S4 (`SCH_I545`/`jfltevzw`) as authorized. Screen mirroring isn't available
  on this device (too old), so verification is via `adb`/Logcat/DB queries
  instead of a mirrored screen. Built the Step 6 APK with `gradlew
  assembleDebug`, installed with `adb install -r`, launched with `adb shell
  monkey`. Confirmed: app UI on the physical device matches the emulator
  exactly, and the raw Step 2 accelerometer stream (`Log.d("SENSOR", ...)`)
  is producing real, live values from the actual hardware.
  - **DB verification blocked, then unblocked via root:** Android Studio's
    Database Inspector requires API 26+ and this device is API 21, so it
    can't attach at all — verification has to happen by pulling the raw
    `.db` file instead. `adb shell run-as` then failed with "Package
    'jhaanush.bikedevice' is unknown" even though `pm list packages` showed
    it installed correctly — a known restriction on stock Samsung/Verizon
    firmware (`ro.debuggable=0` on the device build), not an app problem.
    That's what made root necessary now instead of later.
  - **Rooted with KingRoot 4.5.0**, not the KingoRoot chain originally
    identified in Phase 0 — KingoRoot's one-click flow now crashes with a
    JSON parse error (`Expected BEGIN_ARRAY but was BEGIN_OBJECT`), because
    its decade-old app is calling a live backend whose response format has
    moved on. KingRoot 4.5.0 (a different company, easily confused by name)
    is independently documented as the version confirmed for this exact
    firmware, and worked. Confirmed via `adb shell su -c id` → `uid=0`.
    Deliberately skipped the SuperSU swap from the original guide — that
    step only replaces KingRoot's own root manager with a more trusted one,
    which doesn't matter here since the project's only use of root is
    developer-side `adb shell su` access, not anything the app itself
    depends on at runtime.
  - **DB pull method:** copy `.db` and `.db-wal` to `/sdcard` via
    `su -c cp`, then `adb pull` (not `adb shell ... > file` — PowerShell's
    redirect re-encodes stdout as text and corrupts binary SQLite files).
    Room uses WAL journal mode, so the `-wal` file has to be pulled
    alongside the main `.db` or recent rows won't be visible. Query with
    `sqlite3.exe`, which is already bundled in the Android SDK at
    `platform-tools/sqlite3.exe` — no separate install needed.
  - **Sensor logging: confirmed matches emulator exactly.** Pulled data
    showed two distinct test runs (251 rows, then a 212s gap, then 251
    more) — each a clean ~50Hz/5s window, same cumulative-across-runs
    behavior already known from Step 6.
  - **GPS logging: found and fixed a real bug** (not a device quirk).
    `insertOneRealLocation()` used a fire-and-forget
    `.addOnSuccessListener` callback instead of awaiting the result, so
    `repeat(6) { insertOneRealLocation(); delay(5000) }` fired all 6
    location *requests* correctly spaced 5s apart but never waited for a
    result before moving on. The emulator's fake GPS resolves instantly, so
    this was invisible there. On real hardware — no SIM/cellular, so no
    assisted-GPS, and cold-start fixes can take a long time especially
    indoors — all 6 pending requests resolved in a burst once a fix
    finally arrived: pulled data showed 6 rows within 13ms of each other
    instead of 5 seconds apart.
    - Fixed: `insertOneRealLocation` is now `suspend` and calls `.await()`
      (added `kotlinx-coroutines-play-services:1.7.3` — pinned to match
      the `kotlinx-coroutines-core` version already resolved transitively
      via Room/lifecycle, confirmed via manifest merge that minSdk stays
      at 21, same check applied to every other dependency in this
      project), so the loop now genuinely waits for each GPS attempt
      before its next `delay(5000)`.
    - Added `try/catch` around `.await()`, rethrowing `CancellationException`
      first — it's itself an `Exception` subtype, and swallowing it would
      break normal coroutine cancellation — so one failed location request
      logs and moves on instead of crashing the whole 6-attempt run.
    - Rebuilt and reinstalled. Confirmed indoors that Play Services now
      correctly resolves to `null` (logged, not silently hung or bursty)
      when no fix is available — matches `dumpsys location` showing only a
      38-minute-old, 4-satellite fix on hand, no fresher one obtainable
      near a window.
    - **Outdoor test — verified.** Real fix outdoors produced two
      consecutive rows (`42.7611468, -84.4784629`) exactly **5.19 seconds
      apart** — matching the intended ~5s cadence and confirming the fix is
      correct. Several repeat attempts after this all stopped partway
      through (1-3 of 6 iterations) instead of completing all 6. Not a bug
      in the fix — confirmed root cause below.
  - **Step 7 verify condition met**: both `gps_points` (correct timing when
    a fix is available) and `sensor_readings` (exact 50Hz/5s match) now
    behave correctly on physical hardware, consistent with emulator design
    intent.
  - **Confirmed finding for Step 8: this device's low-memory killer reaps
    the app process itself whenever it leaves the foreground**, not just
    pauses/recreates the Activity. Verified directly: the app's PID changed
    between test runs (e.g. `2103` → `10330`) with no user action other
    than backgrounding it, and `adb logcat`'s `ActivityManager` lines
    showed the OS killing several other background apps
    (`Killing ...: bgCount ##41`) at the same moments logging runs cut off.
    Once killed, no further DB writes ever resumed on their own — matches
    a genuinely dead process, not a paused one. **Implication: Step 8 must
    use a real foreground service with a persistent notification** (the
    project plan already lists `FOREGROUND_SERVICE`/`WAKE_LOCK`
    permissions for this) — a plain Activity + `lifecycleScope`, however
    correct its logic is, cannot survive backgrounding on this hardware.
    This isn't optional hardening; it's a hard requirement confirmed by
    testing, not a theoretical concern.
- **Step 8 (ride session wrapper) — code done, indoor smoke-tests passed,
  outdoor walk still pending:**
  - Added `Ride(id, startTime, endTime)` and a `rideId` `@ForeignKey` column
    on both `GpsPoint` and `SensorReading` (DB version 2→3,
    `fallbackToDestructiveMigration()` still fine — same disposable-test-data
    reasoning as Step 6). Sensor and GPS logging moved entirely out of
    `MainActivity` and into a new `LoggingService` (a `LifecycleService`,
    which gives the same `lifecycleScope.launch{}` pattern already used
    elsewhere) — this is the foreground-service fix Step 7 found necessary.
    `MainActivity`'s old fixed-window test buttons (`btnStartLogging`,
    `btnStartSensorLogging`, `btnInsert`) are gone, replaced by
    `btnStartRide`/`btnStopRide`, which just send the service a plain
    `Intent` (a `rideId` extra to start, a `STOP_RIDE` action to stop) — no
    binding needed since it's fire-and-forget commands, not a live
    connection. Added a ride-elapsed-time `TextView`, ticking every second
    off `System.currentTimeMillis() - startTime` in `MainActivity` — pure
    local UI state, no DB/service involvement.
  - **Bug found and fixed: `MainActivity`'s `isRideActive` flag didn't
    survive screen rotation.** Rotating the phone destroys and recreates the
    Activity by default (a basic Android config-change behavior), which
    reset `isRideActive` to `false` in the new instance — even though the
    service itself kept running fine underneath (that's the whole point of
    moving logging into a service). Symptom chain, confirmed via `dumpsys
    activity services` and a DB pull: a reset `isRideActive` let a second
    "Start Ride" tap silently create a second ride on the *same* running
    service instance (services are singletons — this didn't spawn a new
    one), which re-registered the sensor listener a second time and
    orphaned the first ride's GPS/flush coroutines; then the next "Stop
    Ride" tap did nothing because the guard `if (isRideActive)` was false
    again, leaving the service running as an orphaned foreground process
    (confirmed still alive via `dumpsys`, `lastStartId=2`, ~4m40s after it
    should have stopped — had to be stopped manually via
    `adb shell su -c "am startservice ... -a STOP_RIDE"`, since a plain
    shell `am startservice` is rejected by the service's
    `android:exported="false"`, but root bypasses that check).
    **Fixed** by adding `android:screenOrientation="portrait"` to
    `MainActivity` — a deliberate design choice, not just a patch: this is a
    handlebar-mounted device with no real reason to support landscape, so
    removing the rotation trigger removes the whole bug class at its root
    rather than adding recreation-survival plumbing (e.g. re-deriving ride
    state from the DB on every `onCreate`) for a device orientation this app
    will never actually see in use. After the fix, a clean single-tap
    ride (55.7s, then a second at 10.7s) closed correctly: one `rides` row,
    correct `endTime`, no lingering service afterward.
  - **Investigated, not a bug: sensor rate is now ~100Hz instead of the
    ~50Hz confirmed in Steps 6-7.** Measured directly via a temporary
    per-event counter in `onSensorChanged` (bypassing the DB/buffer layer
    entirely) across three separate test runs: consistently ~92-100Hz, e.g.
    1081 events over 10.840s = 99.7Hz. Ruled out the obvious cause (a
    duplicate `registerListener` call, or two app processes both writing to
    the shared db file) — confirmed via `grep` that there's exactly one
    registration call in the whole codebase, and via logcat's
    `ActivityManager` lines that each reinstall cleanly killed the prior
    process first. Tried switching from the named constant
    `SensorManager.SENSOR_DELAY_GAME` to an explicit
    `registerListener(..., 20_000)` (20ms period) on the theory that the
    named constant's mapping might be context-dependent — measured again,
    rate unchanged (still ~99.7Hz), which disproves that theory (`GAME` is
    a fixed AOSP constant that always maps to 20,000µs, so the two calls
    were functionally identical; reverted back to the named constant since
    the literal offered no benefit). The one remaining structural
    difference from Steps 6-7 is registering the listener from a `Service`
    instead of an `Activity` — which Step 8 requires architecturally — so
    this looks like this specific Samsung hardware's sensor HAL delivering
    a different real rate depending on caller context, not an app bug.
    **Decision: accept ~100Hz as the new real baseline rather than keep
    chasing it.** Every row already carries a genuine `timestamp`, so
    downstream pandas analysis keys off that rather than an assumed fixed
    interval — a higher (or slightly variable) rate doesn't break anything,
    it only costs roughly double the rows/storage and marginally more
    battery per ride, both negligible for this device and ride lengths.
  - **Outstanding:** the checklist's actual Step 8 verify condition (a real
    5-minute walk around the block, confirming one plausible-duration
    `rides` row with GPS/sensor rows sharing that `ride_id` under real
    motion) hasn't been done yet — on hold while an ankle sprain heals.
    Indoor testing already confirms the ride-session mechanics (start/stop,
    FK correctness, timer, no lingering service) work correctly; what's left
    is purely the real-world GPS-in-motion validation.
- **Not yet started:** Step 9 onward (CSV export, first real ride).

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

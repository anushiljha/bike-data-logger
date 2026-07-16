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
**Phase 1 (sensor logger app): Steps 1-9 of 10 done and verified. Step 10
(first real bike ride) next.**

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
  - **Outdoor verify — done.** Real 5-minute walk (ankle wasn't fully
    healed but tolerable). Rebuilt/reinstalled the APK first to make sure
    the S4 had the exact Step 8 code, including the portrait-lock fix. Pulled
    `bike_data.db`/`-wal` afterward via the same root `su -c cp` +
    `adb pull` method as Step 7. Result: ride id 6, 305.3s duration
    (~5m5s), 9 `gps_points` and 24,001 `sensor_readings` rows, all correctly
    tagged `rideId=6`. Latitude moved from 42.76113 → 42.76206 → back to
    42.76169 across the fixes — real motion, not a stuck reading. Verify
    condition met.
  - **Investigated: irregular GPS gaps during the walk (93s and 29s gaps
    mid-walk, 75s gap before Stop Ride), not a device fault.** `dumpsys
    location` right after the walk showed the fixes were locked onto only
    **4 satellites** (accuracy 39-74m) — the bare minimum for any 3D fix,
    not a healthy lock. `dumpsys telephony.registry` confirmed (again) no
    SIM/telephony at all, which matters here specifically: no cellular means
    no A-GPS (assisted GPS) — normally the network hands the GPS chip
    satellite ephemeris data so a lost fix re-acquires in a couple seconds;
    without it, this device has to pull that data directly off the
    satellites themselves, which is far slower. `dumpsys wifi` showed the
    phone also dropped off the home network 69s into the walk, so for most
    of it there was no network-assisted location fallback either — GPS
    alone, on a marginal 4-satellite lock, with no fast recovery path.
    **Conclusion: expected behavior given the Phase 0 WiFi-only/no-SIM
    decision, not broken hardware.** `LoggingService.insertOneGpsPoint()`
    (`LoggingService.kt:83-90`) waits for `getCurrentLocation()` to resolve
    before starting its 5s delay, so slow fixes directly reduce point
    count — 9 points instead of the ~61 a perfect 5s cadence would give
    over 5 minutes. **Implication for Step 10:** expect the same patchy GPS
    density on real rides, especially under tree cover or between
    buildings — not something to fix now (would mean accepting a SIM or
    reworking the location request strategy), but relevant context before
    interpreting ride tracks later.
- **Step 9 (CSV export) — done, verified on the physical S4:** Added an
  `Export` button that writes the most-recent ride's `rides`/`gps_points`/
  `sensor_readings` rows to three CSVs (`ride_<id>.csv`, `_gps.csv`,
  `_sensors.csv`) via `getExternalFilesDir(null)` — app-specific external
  storage, chosen over a public Downloads folder specifically to avoid the
  three different storage-permission models spanning minSdk 21 → targetSdk
  35 (install-time grant, runtime `WRITE_EXTERNAL_STORAGE` request, scoped
  storage/MediaStore) — no permission needed at all, still fully pullable
  via `adb pull` same as every other verification so far. Picked "most
  recent ride" over a ride-picker UI as the simplest thing that satisfies
  this step. File writes wrapped in `withContext(Dispatchers.IO)` since
  (unlike Room's `suspend` queries) raw file I/O doesn't move itself off
  the main thread on its own.
  - **Bug found and explained: first export attempt after a fresh app
    launch silently failed.** Logcat showed `Failed to ensure directory:
    /storage/extSdCard/Android/data/jhaanush.bikedevice/files` —
    `getExternalFilesDir(null)` resolved to the S4's removable-SD-card
    volume, which has no card physically inserted, instead of internal
    storage. No crash resulted because `mkdirs()` just returns `false` on
    failure rather than throwing, and `File(null, "exports")` in the
    underlying Java constructor silently falls back to a relative path
    instead of throwing — so the failure was invisible without deliberately
    checking. A second attempt right after (fresh reinstall + relaunch)
    correctly resolved to internal storage
    (`/storage/emulated/0/Android/data/.../files`) and succeeded, so this
    looks like storage-volume enumeration being briefly unsettled
    immediately after a fresh process start, not a permanent fault.
    **Fixed defensively, not speculatively** — added a try/catch around the
    export call that surfaces failures via `Toast` instead of failing
    silently (warranted because this demonstrably happened once, not
    hypothetical), plus two `Log.d` lines showing the resolved path and
    whether the directory actually got created — matches the existing
    `Log.d("SENSOR"...)`/`Log.d("GPS"...)` diagnostic style already used
    elsewhere in the app.
  - **Verified via adb automation** (no screen mirroring on this device,
    same constraint as Step 7): dumped the UI hierarchy with `uiautomator
    dump` to get the Export button's exact screen coordinates, dismissed
    the lockscreen via `input keyevent 82` (`wm dismiss-keyguard` isn't
    available on API 21), then `input tap` to trigger the export. Pulled
    the resulting CSVs for ride 6 (Step 8's outdoor-walk ride) via the
    established `su -c cp` + `adb pull` method — note the real underlying
    path was `/data/media/0/Android/data/.../files/exports/`, not
    `/storage/emulated/0/...` — the latter is a FUSE view
    (`sdcardfs`/`/mnt/shell/emulated`) that wasn't present in the root
    shell's own mount namespace, so `ls` on it from `adb shell su -c`
    reported "No such file or directory" even though the app itself (and
    the real underlying path) had the files. Confirmed the CSVs are
    structurally correct and match the DB exactly: `ride_6.csv` (1 row,
    startTime/endTime matching the DB), `ride_6_gps.csv` (9 rows, lat/lon
    matching the DB), `ride_6_sensors.csv` (24,001 rows matching the DB
    count). Plain comma-separated numeric data, no quoting needed. Did not
    literally open the files in pandas (no working Python install
    available in the environment used for this verification pass — only a
    Microsoft Store stub) but the row-count/value cross-check against the
    known DB truth was accepted as sufficient to call the step verified.
- **Step 10 (first real bike ride) — in progress.**
  - **Small UX tweak made to the Export flow, written but not yet on the
    phone:** `exportMostRecentRide()` in `MainActivity.kt` now shows a short
    `Toast.LENGTH_SHORT` "Ride exported" message (was a `LENGTH_LONG` toast
    with the full file path — that detail moved to a `Log.d("EXPORT", ...)`
    line instead, so it's not lost, just off the visible UI) and resets
    `rideTimerText` to `00:00` after a successful export. Purely cosmetic,
    not required for Step 10's verify condition — done as a "temp measure"
    ahead of a commute ride on 2026-07-15.
  - **Discovered: `adb install` became unreliable mid-session** — repeated
    failures with `adb: error: failed to read copy response` (push reports
    success but the file doesn't actually land — confirmed via
    `adb shell ls` afterward showing it missing), and separately
    `'device <serial> not found' ... shell:pm install-commit ...` plus the
    device intermittently disappearing and reappearing in `adb devices`.
    Tried: killing/restarting the adb server, `pm install` from
    `/data/local/tmp` instead of `adb install` directly, retrying multiple
    times — none fixed it. Points to a physical-layer USB issue (worn
    micro-USB port/cable — heavily used across Steps 7-9's installs/pulls,
    and possibly aggravated by moisture/debris from the Step 8 outdoor
    walk), not an adb/gradle config regression, since nothing in that setup
    changed between sessions. **Unresolved — next session, before
    reinstalling:** try a different cable/USB port, or switch to
    `adb tcpip 5555` / `adb connect` over WiFi to sidestep USB entirely.
  - **Net effect: the 2026-07-15 commute ride(s) are being logged on the
    Step 9 build already on the phone** (CSV export works, just without the
    shortened toast / timer-reset tweak above) — the app itself wasn't
    touched beyond that cosmetic diff, so Step 10's actual verify condition
    (clean export, no crashes, no gaps) is unaffected. The toast/timer diff
    is uncommitted in the working tree pending install + verification.
  - **Confirmed bug, found from real 2026-07-15 ride data: every real ride
    ended up with `endTime = NULL`, immediately followed by a near-instant
    "ghost" ride that got a clean `endTime`.** Pulled `bike_data.db` and
    found 6 real rides (ids 7, 9, 11, 13, 15, 17 — 7.3 to 19.3 minutes each,
    ~92 minutes total), each followed within milliseconds by a 0.6-4.7s
    ghost ride (8, 10, 12, 14, 16, 18). Sensor data for each real ride is
    fully continuous with no internal gaps — the bug is in ride
    bookkeeping, not logging.
    - **Root cause: same class of bug Step 8 diagnosed, different trigger.**
      `MainActivity.isRideActive` (an in-memory `var`) is never
      re-derived after the Activity is recreated. Step 8's fix
      (`screenOrientation="portrait"`) only removed *rotation* as a
      recreation trigger. Confirmed via user report: screen lock/unlock
      alone does *not* kill the timer (Activity survives a normal
      pause/resume), but staying locked for a long stretch does — Android
      reclaiming a long-stopped Activity for memory, which happens on
      *every* real ride since you can't watch the screen while biking.
      Symptom chain matches exactly: timer reset to 0 (fresh Activity,
      `timerJob`/`isRideActive` lost), Stop Ride did nothing (guarded by
      `isRideActive`, silently false), notification only cleared after a
      manual Start-then-Stop (which created the ghost ride and finally sent
      a real stop command).
    - **Fix:** `RideDao.getActiveRide()` — `SELECT * FROM rides WHERE
      endTime IS NULL ORDER BY id DESC LIMIT 1`. `MainActivity.onCreate`
      now queries this and, if found, restores `isRideActive = true` and
      resumes the timer from the ride's real `startTime`, instead of always
      assuming no ride is active. Timer-ticking loop factored into
      `startTimerTicking()`, shared between `startRide()` and this restore
      path. The DB (not in-memory Activity state) is now the source of
      truth for "is a ride running."
    - **Verified two ways:** (1) simulated the exact trigger on-device via
      `settings put global always_finish_activities 1` + home button —
      confirmed the Activity was destroyed (`LoggingService` stayed alive
      underneath), relaunched, timer correctly resumed from real elapsed
      time instead of showing `00:00`, and Stop Ride then genuinely
      stopped the service and wrote a real `endTime` (ride 19, 245s, no
      ghost ride created). (2) confirmed by an actual real ride the same
      day: timer ran continuously and only stopped when Stop Ride was
      pressed, matching intended behavior.
    - **Data recovery:** exported the 6 orphaned real rides' `gps_points`/
      `sensor_readings` to CSV (`exports/ride_{7,9,11,13,15,17}...csv`,
      matching Step 9's format) directly from the pulled `.db` file before
      any fix — this data was never reachable via the app's own Export
      button, since that picks the single most-recent `rides` row, which
      was always one of the 1-5s ghost rides. `exports/` added to
      `.gitignore` (ride data, not code).
    - **Then patched the 6 rows' `endTime` directly in the on-device DB**
      (`UPDATE rides SET endTime = (SELECT MAX(timestamp) FROM
      sensor_readings WHERE rideId = rides.id) WHERE id IN
      (7,9,11,13,15,17)`), since the live device has no `sqlite3`/`busybox`
      to run this in place — had to `am force-stop` the app, pull
      `.db`+`.db-wal`, checkpoint (`PRAGMA wal_checkpoint(TRUNCATE)`) into a
      single self-contained file, edit locally, push back over the
      original (preserving `u0_a209:u0_a209` ownership by overwriting the
      existing inode rather than replacing the file), and delete the
      device's now-stale `-wal`/`-shm` so nothing gets replayed against the
      edited file. Verified ownership/permissions unchanged before/after,
      and that the app reopens the DB without error.
    - **New wrinkle exposed by the cleanup, not yet fixed:** ride 1 (ancient
      Step-8-era test data) was already sitting at `endTime = NULL` before
      any of this, untouched since it's not one of the 6 real rides. After
      cleanup it became the *only* remaining open row, so
      `getActiveRide()` picked it up as "the active ride" on next launch —
      cosmetic only (no live service is attached, so nothing real is at
      risk), but it'll keep reappearing on every launch showing a huge
      bogus elapsed time until ride 1's `endTime` is patched the same way.
      Deferred until the phone's next reachable (it left with the user
      mid-session for real-world testing).
  - **GPS: 4 of the 6 real rides logged zero GPS points at all** (vs. 5 and
    2 points for the other two, over 13-19 min rides) — worse than Step 8's
    walk (9 points/5 min). Beyond the already-known no-SIM/no-A-GPS
    limitation, `LoggingService.insertOneGpsPoint()` calls a fresh one-shot
    `getCurrentLocation()` every 5s rather than a continuous
    `requestLocationUpdates()` subscription — likely forcing the GPS chip
    to cold-start-reacquire every cycle instead of staying locked. **Real
    candidate fix, deliberately deferred** (user's call): switch to
    `requestLocationUpdates()` for the ride's duration.
  - **`adb install` unreliability (open since 2026-07-15) — confirmed same
    symptom, real workaround found.** Push-then-install still fails with
    `failed to read copy response: EOF`, file demonstrably not landing
    on-device, even via `pm install` from `/data/local/tmp`. Revised theory:
    since plain data *pulls* of similar/larger size (21MB+ `.db` file)
    succeed reliably on the same cable, the drop is more likely the S4
    being too CPU/disk-busy running the package manager's install-time
    work (parse/verify/dexopt, on a memory-starved old device) to service
    USB in time, rather than pure physical cable wear — matches the drop
    happening specifically during install and clearing right after.
    **Workaround that works: ADB over WiFi.** `adb tcpip 5555` (over the
    existing connection) then `adb connect <phone-ip>:5555` — installed
    successfully this way when direct-USB install kept failing. Caveat:
    this device is API 21, predating Android's persistent "wireless
    debugging" pairing (11+), so `adb tcpip` doesn't survive a reboot or
    USB disconnect — every session needs one moment of USB (or already-live
    WiFi) connectivity to re-arm it.
  - **Android Studio's own update relocated the JBR, breaking the pinned
    `JAVA_HOME`.** The update installed the new IDE version into a sibling
    folder (`D:\Android\Android Studio\inthisnewone\`) instead of replacing
    the original in place, leaving the old
    `D:\Android\Android Studio\jbr\lib\jvm.cfg` missing/broken. Gradle
    builds now need
    `JAVA_HOME = "D:\Android\Android Studio\inthisnewone\jbr"` until the IDE
    install settles into its normal location.
  - Ride done via the app UI directly (Start Ride before mounting, Stop
    Ride + Export after, no PC needed) — first real confirmation that the
    Step 8 foreground-service logging survives being taken out and used
    away from the PC, not just indoor/short-walk testing.

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

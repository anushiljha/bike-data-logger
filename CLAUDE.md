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
- **Priority order for code decisions (added 2026-07-17): correct/working
  code first, battery efficiency second** — ahead of polish, UI, or new
  features. Added after ride 26 showed the S4's battery crashing hard under
  combined GPS+sensor logging, navigation, and screen-on load (see Phase 1
  Step 10 section, "Post-Step-10 finding").

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
**Phase 1 (sensor logger app): Steps 0-10 complete and verified.** Step 10
(first real bike ride) closed out 2026-07-17 on ride 25's outdoor data,
confirming the ride-bookkeeping and GPS/sensor-contention fixes hold up in
real conditions.
**Revised 2026-07-17: Phase 1 wasn't actually fully scoped.** Only the
accelerometer was ever logged, despite gyroscope/magnetometer/barometer/
light all being in-scope per the main plan (Section 3.2/6/7 — the barometer
specifically was called out as the reliable elevation source). Steps 11-16
were added to `BUILD_CHECKLIST_Phase1.md` (schema migration + one step per
remaining sensor + a combined-verification step) to close this gap before
Phase 3 starts. **Steps 11-16 all done and verified on the physical S4 as
of 2026-07-20 — this gap is now fully closed.** Gyroscope, magnetometer,
and barometer all matched expectations (with the barometer's expected
pressure *range* corrected from sea-level to raw-ambient, see Step 14's
notes). The light sensor (Step 15) is registered correctly per the app's
own code, but this specific hardware cannot actually deliver on-change
behavior for it — it forces a continuous ~5.5Hz stream regardless of
requested rate, confirmed by explicitly testing a 60-second requested
period with no effect. That's a standing, not-yet-addressed battery/storage
cost on every future ride, and light sensor values look like raw
uncalibrated counts (0/1) rather than real lux — worth remembering before
using light data quantitatively in the Section 8-9 analysis pipeline. Step
16's combined run confirmed all five sensors together don't regress
accelerometer/gyroscope rate versus Step 10's known-good baseline (~98-100Hz
held), and also explains Step 13's one-off "gyro logged 2x accelerometer"
reading as a fluke, not a systematic issue. Full details in
`BUILD_CHECKLIST_Phase1.md` Steps 11-16.
**2026-07-20: light sensor removed from `LoggingService` entirely, and a
real CSV export bug fixed.** Given the light sensor's standing cost noted
above (forced ~5.5Hz continuous stream, no way to rate-limit it, and only
raw uncalibrated 0/1 counts rather than real lux), it was pure battery/
storage cost with no analyzable data behind it — cut per the
correctness-then-battery priority, rather than kept "just in case." Sensor
registration, the `onSensorChanged` branch, and the `Sensor` field were all
removed (not just disabled), so the app now logs accelerometer/gyroscope/
magnetometer/barometer only. Separately: `MainActivity.exportMostRecentRide()`
had never been updated after Step 11 added `sensorType`/`scalarValue` to
`SensorReading` — the exported `_sensors.csv` still wrote only the Step
6-era `x,y,z` columns, so every ride exported since Step 12 (2026-07-17
onward) had gyroscope/accelerometer/magnetometer rows indistinguishable
from each other in the CSV, and barometer/light rows exported as
empty columns (their real data lived in `scalarValue`, which wasn't
written at all). Fixed by adding `sensorType,scalarValue` to the CSV
header/rows. The underlying `bike_data.db` was never affected — only past
CSV exports are incomplete; a re-export from the DB would recover the full
data if needed.
**2026-07-21: device debloated — not a checklist step, done under the
battery-efficiency priority.** 228 preloaded Samsung/Verizon/Amazon/Google
packages disabled via `pm disable-user --user 0` (persistent across reboots,
reversible with `pm enable`), covering widgets (weather/clock/story album),
sync/backup services (Samsung Cloud, Verizon VMS, visual voicemail — this
device has no SIM at all), Knox, push services, and unused preloaded apps
(Amazon store/video/music, NFL, Peel remote, IMDb, etc.). Also disabled
`com.kingstudio.purify`, the "cleaner" app bundled with the KingRoot
one-click installer (Step 7) — untrusted third-party software with no
purpose here. Deliberately left alone anything radio/SIM/USB/GPS/IME-adjacent
that this project actually depends on: `com.google.android.gms`/`gsf`
(location), `com.sec.android.inputmethod` (the actual active keyboard —
confirmed via `ps`, not guessed), `com.sec.usbsettings`/
`com.samsung.android.MtpApplication` (USB, since the whole verification
workflow lives on `adb`), anything Ril/modem/phone/telecom/smartcard-named,
and the ANT+ stack (`com.dsi.ant.*` — potential future bike sensor
relevance). Motivation is two-sided: direct battery draw from background
sync/polling, and reduced RAM pressure against the Step 7-confirmed
low-memory-killer that reaps `LoggingService` under memory pressure (same
mechanism implicated in the ride-26 battery crash). `pm disable-user`
required root (`su 0 pm disable-user ...`) — plain `adb shell` alone hit
`SecurityException: Permission Denial` on this Verizon build, same class of
stock-firmware restriction Step 7 hit with `run-as`. Not yet re-verified via
a real ride whether this measurably changes battery drain or LMK behavior —
that's a natural thing to check during Phase 2 Step 3's real combined ride.
**Phase 2 (navigation): underway. Steps 1-2 done and verified on the
physical S4, 2026-07-20/21.** Nav app is **Organic Maps** (`app.organicmaps`), not
OsmAnd as originally planned — OsmAnd no longer supports this device's
Android version at all (dropped Android 5.x support in 2021, current builds
require Android 7.0+). Confirmed working: installed via Play Store, offline
maps downloaded, and an offline cycling-mode route to a real destination
confirmed with WiFi disconnected. `BUILD_CHECKLIST_Phase2.md` (3 steps:
Navigate button, confirm logging survives Organic Maps in the foreground,
real combined ride) is written.
- **Step 1 (Navigate button) — done, verified 2026-07-20.** New
  `btnNavigate` in `activity_main.xml`, wired in `MainActivity.onCreate()`
  to `packageManager.getLaunchIntentForPackage("app.organicmaps")` →
  `startActivity()` (null-checked since the API is nullable-returning, not
  extra defensiveness — no destination passed, no ride/logging state
  touched, matching this step's deliberately narrow scope). Verified via
  `adb`: same no-screen-mirroring `uiautomator dump` + `input tap` method
  used since Steps 7/9, then `dumpsys window windows` confirmed
  `mCurrentFocus`/`mFocusedApp` both switched to
  `app.organicmaps/.DownloadResourcesActivity` after the tap.
- **Step 2 (logging survives Organic Maps foregrounded) — done, verified
  2026-07-21.** First attempt that day was accidentally run on a stale
  install (last updated 13:19, before that morning's CSV-export-fix/
  light-sensor-removal commit and even the Navigate-button commit) —
  caught by cross-checking the pulled CSV's column headers against current
  source, rather than trusting the app's presence on-device alone. Rebuilt
  from `HEAD` (`gradlew assembleDebug` with `JAVA_HOME` pointed at
  `Android Studio\inthisnewone\jbr` — the top-level `jbr` folder's
  `jvm.cfg` is still missing, same finding as Step 10) and reinstalled
  before repeating the test. Ride 37 (indoor, 85.2s): Start Ride → Navigate
  → used Organic Maps → Stop Ride → Export produced a sensor CSV with zero
  gaps over 1 second anywhere in the stream, confirming Step 8's
  foreground-service fix holds with Organic Maps visibly in front. Also
  confirmed via this ride that the rebuild genuinely picked up both other
  same-day fixes: CSV rows carried the new `sensorType`/`scalarValue`
  columns, and logged types were `accelerometer`/`gyroscope`/
  `magnetometer`/`barometer` only — no `light` rows, confirming that
  removal is live on-device, not just in source.
  - **Open item to watch on Step 3, not a blocker:** per-sensor rates were
    lopsided on this ride — accelerometer ~49.7Hz, magnetometer ~46.5Hz,
    gyroscope ~160.6Hz (over 3x accelerometer, more pronounced than Step
    13's ~2x "fluke"), barometer ~5.5Hz. Accelerometer itself ran well
    under the ~98-100Hz indoor baseline from Step 16. Possibly Organic
    Maps' own main-thread load contending for CPU, same class of effect
    Step 10 found with GPS polling — not dug into further since Step 2's
    actual verify condition (no gap) was already met, but worth watching
    during Step 3's real ride.
- **Step 3 (real combined ride) — not started.**

Full plan: `Bike_Data_Logger_Project_Plan.md`. Step-by-step build order:
`BUILD_CHECKLIST_Phase1.md` (Steps 0-16 all complete) and
`BUILD_CHECKLIST_Phase2.md` (Steps 1-2 done, Step 3 next).

### Phase 0 — resolved decisions

- **OS path: forced to stock/rooted.** Build `LRX22C.I545VRUGOF1` has a
  permanently eFuse-locked bootloader — no LineageOS/TWRP/CWM/any custom ROM
  is possible on this exact firmware. (A 2023 community chainload exploit
  exists as a theoretical advanced option but is out of scope.)
- **Root method: executed during Phase 1 Step 7** (see below) — turned out to
  be needed sooner than planned, to work around `adb run-as` being blocked on
  this stock Samsung build. Full narrative under Step 7.
- **Carrier lock: not applicable** — project is WiFi-only by design.
- **Navigation app: Organic Maps** (`app.organicmaps`) — decision resolved
  and verified, not yet implemented (that's Phase 2). Originally planned as
  OsmAnd; revised 2026-07-17 when OsmAnd turned out to no longer support
  this device's Android version at all (dropped Android 5.x in 2021,
  current builds require Android 7.0+ — the same Play-Services-era problem
  that ruled out Google Maps, just further along). Google Maps was also
  directly tested and ruled out: confirmed via Google's own documentation
  and an on-site offline test (WiFi disconnected) that it cannot compute
  bicycling directions without a live connection — disqualifying given this
  project's WiFi-only design. Organic Maps confirmed compatible (Android
  5.0 minimum, matches this device exactly, no legacy-APK workaround
  needed like Google Maps required) and confirmed working: offline
  cycling-mode route to a real destination, GPS fix acquired outdoors in
  10-15s, WiFi off throughout. Full decision history in the main plan's
  Section 13 #2 changelog.
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
- **Step 10 (first real bike ride) — done, verified 2026-07-17.**
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
  - **2026-07-16 session: three real commute/leisure rides recorded** (ids
    20, 21, 22 — 15.2, 6.8, 15.8 min). All three closed with real `endTime`s
    on the pre-fix build — no ghost rides that day, though the underlying
    bug below was still live and just didn't happen to trigger. Manually
    exported all three to `exports/ride_{20,21,22}*.csv` (same method as
    the earlier orphaned-ride recovery — the app's own Export button only
    ever grabs the single most-recent ride, so it can't reach 20/21 once 22
    exists; a ride-picker for Export is a candidate future addition, not
    done now).
  - **Found and fixed: ride 1 (the original Step-8-era orphan, `endTime`
    NULL since before the Step 10 cleanup) was still sitting unpatched** —
    confirmed as the actual cause of a "timer showed 4337 min" report from
    earlier the same day (matches `getActiveRide()` picking up ride 1's
    ancient `startTime` on a cold `MainActivity` launch). Patched the same
    way as the 6 other orphans: `su`-root `cp` to `/sdcard`, `adb pull`,
    `PRAGMA wal_checkpoint(TRUNCATE)`, `UPDATE rides SET endTime = (SELECT
    MAX(timestamp) FROM sensor_readings WHERE rideId=1) WHERE id=1`, push
    back preserving `u0_a209:u0_a209` ownership, delete the device's now-stale
    `-wal`/`-shm`. Verified via `adb logcat` (no crash) and a UI dump that
    the app reopens clean with `Start Ride` enabled again.
  - **Found and fixed: a second, more serious cause of the same "phantom
    active ride" symptom, not just the one stale row.** `LoggingService`
    keeps `rideId` as a plain in-memory field (`= -1` by default), only set
    in `onStartCommand`'s "start" branch. `MainActivity.stopRide()` sends
    `ACTION_STOP` via a blind `startService()` call with no check that the
    *same* service process is still alive. If the process had died mid-ride
    (Step 7's confirmed low-memory-killer behavior) and the Stop tap
    relaunches it fresh, that new instance hits `ACTION_STOP` immediately
    with `rideId` still `-1` — `setEndTime(-1, ...)` matches zero rows, and
    the real ride is orphaned with `endTime = NULL` forever, reproducing
    the exact "huge stale elapsed time" symptom on the next cold launch.
    Step 10's original fix only ever addressed the *Activity*-side half of
    this (state surviving recreation); this service-side gap was still
    live. **Fixed** in `LoggingService.stopRide()`
    (`LoggingService.kt:132-150`): if the in-memory `rideId` is `-1`, fall
    back to `db.rideDao().getActiveRide()?.id` to find the real open ride
    to close — same "DB is the source of truth" principle as the Activity
    fix.
  - **Stress-tested the fix by simulating the actual failure mode** (per
    project decision: mix of a forced test now + passive verification on
    2026-07-17's rides): started a ride, waited for sensor/GPS logging to
    begin, then `su -c kill -9 <pid>` on the app process mid-ride. Logcat
    showed `ActivityManager` treating it as a crashed foreground service
    (`Scheduling restart of crashed service ... in 1000ms`) but the
    scheduled restart never actually fired — `dumpsys activity services`
    confirmed no `LoggingService` running afterward, and no `rideId=-1`
    rows ever appeared, so this device doesn't quietly resurrect a killed
    foreground service and silently mislog under the wrong ride — it just
    stays dead with the notification going stale. Relaunched `MainActivity`
    cold (confirmed via `dumpsys`/UI dump the process had actually changed
    PID): the ride-restore fix correctly resumed the timer at the real
    elapsed time (`05:25`, matching the actual gap), not `00:00` or a bogus
    number. Tapped Stop Ride: the freshly-restarted `LoggingService` hit
    the `rideId=-1` path exactly as predicted, and the new fallback
    correctly found and closed the real ride (`endTime` set, duration
    347.5s matching real elapsed time) — no ghost ride, no orphaned
    `rideId=-1` data. Fix confirmed working against its actual target
    failure, not just "compiles and doesn't crash."
  - **Found and fixed: GPS point sparsity and the Step 8→today sensor-rate
    drop (100Hz → 33-45Hz on 2026-07-16's three real rides) share one root
    cause.** `insertOneGpsPoint()` called `getCurrentLocation()` fresh every
    5 seconds — a one-shot request the GPS chip has to cold-reacquire each
    time (worse with no A-GPS, per Step 8's finding), and the coroutine sat
    blocked on `.await()` until it resolved. Since `lifecycleScope` runs on
    the main dispatcher, and `onSensorChanged` also lands on the main
    thread, a stalled GPS wait was starving sensor event delivery — matches
    today's data showing GPS gap counts and sensor gap counts tracking each
    other closely per ride (12/11, 5/6, 18/15). **Fixed**: replaced the
    5-second polling loop with a continuous `FusedLocationProviderClient
    .requestLocationUpdates()` subscription, started once when the ride
    begins and stopped in `stopRide()` — results arrive via a
    `LocationCallback` (a listener, not something `await()`-ed), so the
    ride's coroutines are never blocked waiting on a fix. Kept the same
    ~5s interval via `LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,
    5000L)`. `play-services-location` was already on 21.3.0, well past
    where `LocationRequest.Builder` was introduced, so no dependency bump
    needed. Verified indoors (no outdoor GPS fix available) that sensor
    rate immediately returned to ~100Hz (99.8Hz measured over the test
    ride) with the polling removed — real outdoor GPS-density validation
    still pending 2026-07-17's rides.
  - **Decision: fix GPS/sensor contention before treating Step 10 as
    closed**, rather than moving on to Phase 2 (navigation) with it as a
    fast-follow — the checklist explicitly gates Phase 2 on Step 10 being
    reliable, and this affects every future ride's data quality, not just
    an edge case.
  - **Decision: keep and commit the AGP/Gradle version bump** (AGP
    8.9.1→8.13.2, Gradle wrapper 8.11.1→8.13) that Android Studio's own
    update had left sitting uncommitted in the working tree, rather than
    reverting to the previously-pinned versions — matches what Studio
    already built successfully with, avoids fighting the IDE's own
    auto-update on every open.
  - **2026-07-17: ride 25 (947.9s, ~15.8 min) confirmed all three pending
    outdoor checks from CSV export.** Analyzed `ride_25.csv` /
    `_gps.csv` / `_sensors.csv` directly (no `bike_data.db` pull needed
    for this pass):
    - **(a) GPS density — real improvement.** 41 points over 915.5s of
      coverage, gaps ranging 5.7s–60.3s (avg ~22.9s against a 5s target
      interval, so still patchy) — but a large jump from 2026-07-16's 4
      of 6 rides logging **zero** GPS points, and the worst single gap
      (60.3s) is well under Step 8's walk (93s). Matches the intended
      effect of the `requestLocationUpdates()` fix; the remaining
      patchiness is the already-understood no-SIM/no-A-GPS ceiling, not a
      new bug.
    - **(b) Sensor rate — improved, not fully back to the indoor
      baseline.** 63,613 rows over 947.4s = **~67Hz**, well above
      2026-07-16's 33-45Hz (old GPS-polling contention) but below the
      ~99.8Hz measured in the indoor-only retest. Read as: indoors GPS was
      failing fast (no real fix, so little callback traffic to contend
      with); outdoors, genuine `LocationCallback` deliveries on the main
      thread still cost something even without blocking `.await()` — the
      fix reduced main-thread contention with sensor delivery, didn't
      eliminate it.
    - **(c) Service-kill/orphan fix — held up naturally.** `endTime`
      (1784303854475) lands 80ms after the last `sensor_readings`
      timestamp (1784303854395), consistent with a clean, intentional Stop
      Ride tap. User confirmed directly (without a db pull) that no ghost
      ride was created this time.
  - **Step 10 status: done.** All three checks the fixes were pending on
    are now confirmed against real outdoor data. Phase 2 (OsmAnd
    navigation) starts next.
  - **Post-Step-10 finding, same day: battery crashes hard under combined
    nav + logging load — now a standing priority.** Three more rides
    recorded after Step 10 closed (ids 26, 27, 28 — 13.1, 18.7, 14.0 min).
    Rides 27 and 28 logged continuously with no gaps. Ride 26 (17:02-17:15)
    has a real ~418s gap: sensor/GPS logging stopped at 17:08:28, and the
    ride's `endTime` wasn't set until 17:15:26 — matches the Step 10
    fallback (`getActiveRide()`) correctly closing out a ride whose logging
    had actually died earlier, not a new bug in that fix.
    - **Root cause, confirmed via `dumpsys batterystats --history`:**
      battery drained normally before the ride (18%→15% over ~23 min), then
      crashed **15%→4% in ~13 minutes** during the ride — roughly 3.7x the
      normal rate — with voltage sagging to 3.3-3.6V, while
      `app.organicmaps` was the foreground app (run manually alongside
      logging, ahead of Phase 2's own integration work). No reboot occurred
      (single battery-history reset for the whole session), so this reads
      as a severe sag/aggressive-power-save reaction under combined
      GPS+sensor+navigation+screen-on load, not the phone fully dying —
      most likely compounding on top of Step 7's already-confirmed
      low-memory-killer behavior once battery hit a critical threshold.
    - **Read as low-battery-driven, not a one-off RAM fluke** — battery
      state getting critical is the primary trigger, with the OS's
      already-known aggressive process-killing (Step 7) as the mechanism
      that actually cuts logging off.
    - **New standing priority: battery efficiency is priority #2, right
      after correct/working code** — ahead of polish, UI, or new features.
      Directly relevant to Phase 2, which pairs this app with Organic Maps
      (a heavy app in its own right) for the first time — see
      `BUILD_CHECKLIST_Phase2.md` Step 3.

- **Step 11 (schema migration for multi-sensor logging) — done, verified on
  the physical S4, 2026-07-17.** `SensorReading` gained `sensorType` (TEXT,
  column `sensor_type`) and `scalarValue` (REAL nullable, column
  `scalar_value`); `x`/`y`/`z` became nullable — the schema Section 6 of the
  main plan always specified, laying groundwork for Steps 12-15's
  gyroscope/magnetometer (vector, `x`/`y`/`z`) and barometer/light (scalar,
  `scalarValue`). `AppDatabase` bumped 3→4.
  - **First real Room `Migration` in this project, not
    `fallbackToDestructiveMigration()`.** Every earlier version bump (Steps
    6, 8) used the destructive fallback since the DB only held disposable
    test data at the time; by Step 11 it held 28 real rides, so that same
    default would have silently wiped them on next launch. Also removed
    `fallbackToDestructiveMigration()` entirely (not just added the
    migration alongside it) — if the migration path had ever failed to
    match for any reason, keeping the fallback would silently wipe real
    data instead of crashing loudly, which is the safer failure mode here.
  - **SQLite can't relax a column's `NOT NULL` via `ALTER TABLE`**, so
    `x`/`y`/`z` becoming nullable required the standard rebuild pattern:
    create `sensor_readings_new` with the target schema, copy all rows
    across (tagging every existing row `sensor_type='accelerometer'` since
    that's the only sensor ever logged so far), drop the old table, rename
    the new one into place, recreate the `rideId` index.
  - **Migration took longer than expected on real hardware — not a bug,
    just this device being slow.** Rebuilding a 742,278-row table is a real
    piece of work on 2013-era eMMC storage. A DB pull taken only ~3 seconds
    after launch caught the migration still mid-transaction (a 131MB
    in-flight WAL, schema still showing the old version) — re-pulling a few
    minutes later showed it complete (WAL fully checkpointed, `user_version`
    at 4). Worth remembering if a "migrating…" indicator is ever needed for
    a future schema change on this hardware.
  - **Verified via DB pull:** all pre-existing data intact — 28 `rides`, 517
    `gps_points`, 742,278 `sensor_readings`, every one correctly backfilled
    `sensor_type='accelerometer'`/`scalar_value=NULL` with `x`/`y`/`z`
    unchanged. No crash on launch.
- **App icon and splash screen added, 2026-07-17 (not a checklist step —
  branding, done alongside Step 11's build/install).** Both use a
  user-provided bike-mounted-phone illustration (`device_bike_icon.png`,
  repo root, untracked — source asset, not code).
  - **Icon:** resized to the 5 standard launcher sizes (48/72/96/144/192px)
    and dropped into `mipmap-{m,h,x,xx,xxx}hdpi/ic_launcher(_round).png`
    (converted from the template's `.webp`). Deliberately left the
    `mipmap-anydpi-v26` adaptive-icon XML/drawable layers untouched — that
    path requires API 26+, and the S4 (API 21) never reads it, so touching
    it would be effort spent on something the one real target device can't
    use. (Only relevant if this is ever run on the emulator or a newer
    phone, where the old default icon would still show.)
  - **Splash screen:** added `androidx.core:core-splashscreen:1.0.1` — the
    official backport of Android 12's SplashScreen API down to API 21
    (matches this project's floor exactly), replacing the old
    themed-background-plus-artificial-delay hack. New `Theme.App.Starting`
    (in both `values/themes.xml` and `values-night/themes.xml`) sets
    `windowSplashScreenBackground` and `windowSplashScreenAnimatedIcon` to
    `@mipmap/ic_launcher` (reused, no new asset), falling back to
    `Theme.BIkeDevice` after. `MainActivity.onCreate()` calls
    `installSplashScreen()` as its first line (required ordering, before
    `super.onCreate()`). Not a fixed-delay screen — shows only until the
    first frame is actually ready. Confirmed minSdk stays at 21 after adding
    the dependency (same check applied to every other dependency in this
    project).
  - **Bug found on-device: initial background color choice made the icon
    invisible.** First attempt sampled `windowSplashScreenBackground`
    (`#D88623`) directly from the icon's own gradient edge, intending a
    cohesive look — but for a two-layer icon-on-background splash, a
    background that closely matches the icon makes the icon blend into the
    field instead of standing out, so the splash just read as a flat
    orange screen. **Fixed:** switched to a deliberately contrasting dark
    navy (`#14213D`) instead of color-matching the icon. Not yet
    re-verified on-device (pending next install).
  - **Icon: user feedback is it's too ugly as-is** (source illustration is
    AI-generated — "whatever Gemini built"). Functionally in place and not
    blocking any checklist work; a redesign/re-crop is deferred to later,
    not fixed now.
- **Step 12 (gyroscope) — code written and building, NOT yet verified,
  2026-07-17.** `LoggingService` now also registers `TYPE_GYROSCOPE` at the
  same `SENSOR_DELAY_GAME` rate as the accelerometer. Since both sensors now
  feed the same `onSensorChanged` callback, it branches on
  `event.sensor.type` to tag each row `sensor_type='accelerometer'` or
  `'gyroscope'` (an unrecognized sensor type is ignored via `else -> return`
  — nothing else is registered yet). **Still needs Step 12's actual verify
  condition:** install on the S4, pull the DB, confirm gyroscope row count
  roughly matches the accelerometer's for the same window, and that values
  move when the device is rotated. Do this before starting Step 13 —
  don't skip the verify step just because the code compiles.
- **Post-Step-10 investigation, 2026-07-17: Organic Maps died mid-ride —
  root-caused to the battery finding above, not a new/separate bug.** See
  "Post-Step-10 finding" above for the full battery-crash writeup; this is
  the same incident, just noting here that it was originally reported as
  "the nav app died mid-ride" before the battery-history investigation
  traced it to the actual cause.

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

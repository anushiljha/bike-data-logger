# Phase 1 Build Checklist — Sensor Logger (Incremental)

Each step below has one job. Don't move to the next step until the current one's "verify" line is actually true on your screen. This is the fastest way to avoid a pile of code that doesn't work and you don't know why.

You can do steps 1-6 entirely on the Windows PC using the Android emulator — the physical S4 doesn't come into play until Step 7. That decouples "learning Android dev" from "dealing with old-phone quirks," which is worth keeping separate.

---

## Step 0 — Environment

- [ ] Install Android Studio (includes JDK + SDK manager).
- [ ] Create one emulator device (Pixel-class, any recent Android version — doesn't need to match the S4 yet).
- **Verify:** Emulator boots to the Android home screen.

## Step 1 — Hello World

- [ ] File → New Project → Empty Views Activity, Kotlin, minSdk 21 (matches the S4's real-world floor).
- [ ] Run it on the emulator, no edits.
- **Verify:** App installs and shows the default "Hello World" screen. This confirms your toolchain works before you've written a single line of real logic.

## Step 2 — One sensor, printed to Logcat

- [ ] Add `ACCESS_FINE_LOCATION` — skip for now, sensors first.
- [ ] In `MainActivity`, register a `SensorEventListener` for `TYPE_ACCELEROMETER` only.
- [ ] On each reading, `Log.d("SENSOR", "x=$x y=$y z=$z")` — don't store anything yet.
- **Verify:** Logcat scrolls with real-looking accelerometer values as you drag the emulator's rotation control (emulator can fake sensor input via the extended controls panel).

## Step 3 — One GPS reading, printed to Logcat

- [ ] Add `ACCESS_FINE_LOCATION` permission (request at runtime).
- [ ] Get a single one-shot location via `FusedLocationProviderClient.getCurrentLocation()`.
- [ ] Log the lat/lon.
- **Verify:** Logcat shows one coordinate pair. Use the emulator's extended controls to set a fake GPS location if it doesn't return real coordinates.

Stop and confirm both Step 2 and Step 3 work independently before combining anything — this is the point where most tutorials merge everything at once and it becomes unclear which part broke.

## Step 4 — One database table, one row

- [ ] Add Room. Define exactly one entity: `GpsPoint(id, timestamp, lat, lon)`.
- [ ] Add a button: "Insert test row" → inserts one hardcoded row.
- [ ] Add a second button: "Query" → logs all rows from the table.
- **Verify:** Open Android Studio's Database Inspector (View → Tool Windows → App Inspection) while the app runs, confirm the row is actually in the DB, not just in Logcat.

## Step 5 — Wire Step 3's real GPS into Step 4's real DB

- [ ] Replace the hardcoded insert with the actual location result from Step 3.
- [ ] Add a "Start logging" button that requests location every 5 seconds for 30 seconds, inserting a real row each time.
- **Verify:** After 30 seconds, Database Inspector shows ~6 rows with real-ish incrementing timestamps.

## Step 6 — Same pattern for sensors

- [ ] Add a `SensorReading` entity (separate table from `GpsPoint`).
- [ ] Repeat Step 5's pattern: "Start logging" writes real accelerometer readings to the DB at ~50Hz for a fixed test window (5 seconds is plenty — don't run this indefinitely yet).
- **Verify:** Row count roughly matches expected (5 sec × 50Hz ≈ 250 rows). If it's way off, you have a threading/rate issue to fix before building anything on top of it.

**This is the point where the emulator has taught you everything it can.** Steps 2-6 working on the emulator means your app logic is sound. What's left is device-specific.

## Step 7 — Move to the real S4

- [ ] Enable Developer Options + USB debugging on the S4 (Settings → About phone → tap Build number 7 times).
- [ ] Connect via USB, run the exact same app from Step 6 on the physical device instead of the emulator.
- **Verify:** Same DB behavior as the emulator. If sensor values look wrong/missing here specifically, that's a device/driver quirk to isolate now — much easier to debug with only 250 rows of test data than mid-ride.

## Step 8 — Ride session wrapper

- [ ] Add the `rides` table and a `ride_id` foreign key to both `GpsPoint` and `SensorReading`.
- [ ] Replace the fixed 5/30-second test windows with a real Start/Stop ride button pair.
- **Verify:** Do one real 5-minute walk around the block with the phone in hand. Confirm one `rides` row exists with a plausible duration, and the GPS/sensor rows all share that `ride_id`.

## Step 9 — CSV export

- [ ] Add an "Export" button that dumps one ride's tables to CSV in a visible folder.
- [ ] Pull the file off via USB or a WiFi transfer method, open it in Excel/pandas.
- **Verify:** The CSV opens cleanly and the numbers match what you saw in the Database Inspector.

## Step 10 — First real bike ride

- [ ] Mount the phone, do one short real ride with logging on (navigation app not required yet — this step is purely about validating the logger survives real vibration/motion/duration).
- **Verify:** Full ride's worth of data exports cleanly, no crashes, no gaps.

---

**Gap found 2026-07-17: Phase 1's own stated scope (Section 1 of the main plan — "logs sensor + GPS data") was never fully built.** Only the accelerometer was ever wired up (Step 6); gyroscope, magnetometer, barometer, and light — all listed as present hardware in Section 3.2, all with defined sampling rates in Section 7 — were not. This matters concretely: the barometer was specifically called out as the *reliable* elevation source (GPS altitude is the noisy one), and Section 12's elevation cross-validation test can't run without it. Steps 11-16 close this gap.

## Step 11 — Extend the sensor schema for multiple types

- [ ] Add `sensor_type` (TEXT) and `scalar_value` (REAL, nullable) columns to `SensorReading`; make `x`/`y`/`z` nullable — this is the schema Section 6 of the main plan always specified, only the accelerometer-only version got built in Step 6.
- **Important, different from every earlier schema bump:** Steps 6 and 8 both used `fallbackToDestructiveMigration()` because the DB only held disposable test data at the time. That's no longer true — the device now holds 25+ real rides. Using `fallbackToDestructiveMigration()` here would silently wipe all of it on next launch. Either write a real Room `Migration`, or explicitly export/back up every existing ride via the Step 9 CSV path first and accept the reset as a deliberate, informed choice — not the default.
- **Verify:** App launches without crashing, and a DB pull afterward shows every pre-existing `rides`/`gps_points`/`sensor_readings` row still intact.

## Step 12 — Gyroscope

- [x] Register `TYPE_GYROSCOPE` alongside the existing accelerometer listener, same rate (`SENSOR_DELAY_GAME`), tagged `sensor_type='gyroscope'`, `x`/`y`/`z` populated, `scalar_value` null.
- **Verify:** DB pull shows a gyroscope row roughly matching the accelerometer row count for the same window (real hardware rate may land near ~100Hz per Step 8's finding, not exactly 50Hz — that's already-understood behavior, not a new bug), and values move when the device is rotated.
- **Verified on the physical S4, 2026-07-20.** Ride 30 (60.2s): 3713 accelerometer rows vs 3720 gyroscope rows — near-identical, as expected from both being registered at the same rate. Rotating the phone by hand during the ride produced gyroscope x/y/z swinging from about -6.7 to +4.9 rad/s, clearly distinct from idle noise (~±0.05 rad/s seen in the rows before/after the rotation window). Ride closed cleanly (`endTime` set, no ghost ride).

## Step 13 — Magnetometer

- [ ] Register `TYPE_MAGNETIC_FIELD` at ~10Hz, tagged `sensor_type='magnetometer'` — deliberately slower than accel/gyro, per Section 7's rationale that heading changes slowly relative to motion.
- **Verify:** DB pull shows roughly 1 magnetometer row per 5 accelerometer rows for the same window, and values shift when the phone's heading changes.

## Step 14 — Barometer

- [ ] Register `TYPE_PRESSURE` at ~1Hz, tagged `sensor_type='barometer'`, logged via `scalar_value` (hPa) — `x`/`y`/`z` null, this is a scalar sensor.
- Note: unlike accelerometer/GPS, the emulator's Extended Controls may not simulate barometer input — this may need direct verification on the S4 rather than the emulator, same pattern as GPS ultimately needed.
- **Verify:** DB pull shows barometer rows around 1/second with plausible sea-level-range pressure values (~1000-1030 hPa).

## Step 15 — Light

- [ ] Register `TYPE_LIGHT`, on-change — no fixed `SENSOR_DELAY_GAME`, since Section 7 specifies this one as event-driven, not polled. Tagged `sensor_type='light'`, logged via `scalar_value`.
- **Verify:** DB pull shows light rows only when the sensor is covered/uncovered during the test window, not a constant stream — confirms it's genuinely on-change.

## Step 16 — All five sensors together

- [ ] Run one real ~5-minute logging session (indoor is fine) with all five sensor types registered simultaneously.
- **Verify:** DB pull shows all five `sensor_type` values present with plausible row counts for each rate, and — the important check — accelerometer/gyroscope rate hasn't dropped from adding three more listeners on the same thread (compare against Step 10's known-good rate; a real drop here would be the same class of main-thread contention Steps 8 and 10 already found with GPS).

---

Only after Step 16 works reliably does it make sense to add navigation integration (Section 5 of the main plan) or start on the analysis pipeline (Section 8-9) — those are separate, independent tracks once the logger itself is solid. Steps 11-16 don't block Phase 2 (navigation doesn't touch sensors) and can happen in either order relative to it — but they should land before Phase 3's real field data collection ramps up, since every ride logged before then is permanently missing gyroscope/magnetometer/barometer/light data.

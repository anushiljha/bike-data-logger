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

Only after Step 10 works reliably does it make sense to add navigation integration (Section 5 of the main plan) or start on the analysis pipeline (Section 8-9) — those are separate, independent tracks once the logger itself is solid.

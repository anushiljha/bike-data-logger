# Phase 2 Build Checklist — Ride Dashboard (Navigation Dropped 2026-07-23)

Same rule as Phase 1: one job per step, don't move on until the verify line is
actually true on your screen.

**Navigation is dropped. Steps 1-3 below are kept as history, not deleted** —
Steps 1-2 were real, verified work; Step 3 is where real-ride testing found
the problem that ended navigation. See the note right before Step 3 for the
full reasoning, and `Bike_Data_Logger_Project_Plan.md` Section 5 / Section 13
#2 for the decision-log version. Everything from **Step 4 onward** is the new
direction: a live ride dashboard (speed/odometer/elevation) plus a small
local street view (~75m radius), no navigation, no third-party map app.

**Not in scope here, deliberately:** anything to do with Phase 1 Steps 11-16
(multi-sensor logging) — that's a fully independent track. UI polish is still
the separate future Phase 8.

---

## Step 1 — Add a "Navigate" button — done, verified on the physical S4, 2026-07-20

- [x] Add a button to `MainActivity` that launches Organic Maps via
  `packageManager.getLaunchIntentForPackage("app.organicmaps")` — an
  explicit launch, not a `geo:`/`ACTION_VIEW` intent, since the nav app
  choice is already made and there's no reason to show a chooser dialog.
- [x] No destination passed yet — the button just opens Organic Maps to
  wherever it last was. Nothing about your own ride/logging state needs to
  change for this step.
- **Verify:** Tapping the button switches from your app to Organic Maps.
  Confirmed via `adb`: after tapping (coordinates found via `uiautomator
  dump`, same no-screen-mirroring method used since Step 7/9),
  `dumpsys window windows` showed `mCurrentFocus` and `mFocusedApp` both
  switch to `app.organicmaps/.DownloadResourcesActivity`.

## Step 2 — Confirm logging survives Organic Maps taking the screen — done, verified on the physical S4, 2026-07-21

- [x] With `Start Ride` active, tap Navigate and use Organic Maps normally
  for a minute or two (indoors/short walk is fine for this step).
- [x] Return to your app, tap `Stop Ride` + `Export`.
- **Verify:** DB pull (or the exported CSV) shows continuous timestamps
  through the period Organic Maps was in the foreground — no gap
  corresponding to that window. This is really just confirming Step 8's
  foreground-service fix still holds now that a second app is the one
  visibly in front, not a new mechanism.
  - Confirmed via ride 37 (indoor test, 85.2s): sensor CSV had zero gaps
    over 1 second anywhere in the stream, straight through the window
    Organic Maps was foregrounded. Ran on a freshly rebuilt install
    (confirmed via the new `sensorType`/`scalarValue` CSV columns and the
    absence of any `light` rows) after discovering the prior same-day test
    attempt had run on a stale APK that predated the CSV-export fix and
    light-sensor removal — see `CLAUDE.md` for that finding.
  - **Open item to watch on Step 3, not a blocker:** per-sensor rates were
    lopsided — accelerometer ~49.7Hz, magnetometer ~46.5Hz, gyroscope
    ~160.6Hz (over 3x accelerometer), barometer ~5.5Hz. Step 13 saw a
    similar but smaller "gyro 2x accelerometer" blip chalked up as a
    one-off fluke; this is more pronounced and accelerometer itself is
    well under the ~98-100Hz indoor baseline. Possibly Organic Maps'
    own main-thread load contending for CPU, same class of effect Step 10
    found with GPS — not investigated further since Step 2's actual verify
    condition (no gap) was already met, but worth watching during Step 3's
    real ride.

## Step 3 — Real combined ride — ABANDONED, navigation dropped 2026-07-23

- [x] Attempted on two real rides, 2026-07-22 (ride 38, 16.2 min; ride 39,
  28.6 min): Start Ride → launch Organic Maps → navigate to an actual
  destination → Stop Ride → Export.
- **What actually happened, found via DB pull + dropbox crash logs +
  `batterystats --history` correlation (not just observation — see
  `CLAUDE.md` for the full forensic writeup):**
  - Organic Maps native-crashed on **both** rides — `std::bad_alloc`
    (SIGABRT, out of native memory) on ride 39, a null-pointer-style SIGSEGV
    on ride 38. Two different crash types inside Organic Maps' own code.
  - Far more serious: **`LoggingService` itself silently died mid-ride on
    both rides**, well before Stop Ride was tapped — 190s early on ride 38,
    **947s (15.8 min, 55% of the ride) early on ride 39**. `batterystats`
    showed the app's own foreground/GPS-active flag drop at the same moment
    logging stopped, not at the real Stop Ride time, and Organic Maps'
    native crash landed 32-54s *after* that — consistent with one shared
    cause (the device running out of RAM under combined GPS+sensor+nav+
    screen-on load), not two unrelated bugs.
  - The existing Step 10 `getActiveRide()` fallback still closed each ride
    with a plausible-looking `endTime`, so **nothing in the app's own UI or
    a casual DB check flagged the data loss** — it only surfaced by
    comparing last-logged-timestamp against `endTime` directly. Worth
    remembering as a gap in that fallback: it correctly prevents an
    orphaned ride, but says nothing about *how much of the ride actually
    got logged*.
  - This is the same battery/memory-pressure class of failure the
    post-Step-10 finding (`CLAUDE.md`, ride 26) already flagged as a risk
    to watch for here — just confirmed now, and worse than expected (data
    loss, not just faster battery drain).
- **Mitigations considered and ruled out** (conversation record, not
  re-litigated in future sessions):
  - Switching between Organic Maps and this app mid-ride to reduce
    Organic Maps' foreground time — not viable, no free hands on a bike.
  - Voice-guided navigation with the screen off/backgrounded — not viable,
    unreliable in real traffic noise, and no verified basis for Organic
    Maps' voice-guidance quality (unlike e.g. Apple Maps).
  - Extracting a calculated route from Organic Maps (GPX/track export) to
    drive a lighter custom display instead of its full rendering UI —
    investigated via web research (Organic Maps added "save planned routes
    as tracks" per its June 2025 changelog) **and verified hands-on on the
    physical device**: calculated a real route, checked every plausible UI
    path (routing panel, gear/settings icon, long-press on the route line,
    global Bookmarks and Tracks screen, main menu) — no such export path
    exists in the installed build (2026.07.15-11-Google). Also checked
    Organic Maps' own `api-android` library: it's an intent-only wrapper
    (show a point, pick a coordinate), no embeddable view, no route data
    access.
- **Decision: drop navigation entirely.** Phase 2 rescoped to a dashboard —
  see Step 4 onward. Logged in `Bike_Data_Logger_Project_Plan.md` Section 5
  and Section 13 #2.

---

## Step 4 — Remove Organic Maps integration — code done 2026-07-26, compile-verified only

- [x] Removed `btnNavigate` from `activity_main.xml` (re-chaining
  `btnStopRide`'s constraint to `btnQuery`, closing the gap left in the
  vertical button chain) and its click listener from
  `MainActivity.onCreate()` (the `getLaunchIntentForPackage(
  "app.organicmaps")` code from Step 1). Full removal, not just hiding the
  button — matches this project's convention of deleting dead code rather
  than commenting it out (see the Step 15 light-sensor removal). Confirmed
  no remaining `btnNavigate`/`app.organicmaps` references anywhere in
  `app/`.
- **Verify:** app builds and runs with no Navigate button; no leftover
  references to `app.organicmaps` anywhere in the app source.
  - **Partial — phone unavailable.** `gradlew assembleDebug` succeeds
    (`BUILD SUCCESSFUL`), so the code compiles and the layout's constraint
    chain is valid, but the "runs with no Navigate button" half of this
    verify condition has **not** been confirmed on the physical S4 — no
    device access this session. Treat this step as code-complete, not
    verified, until it's actually launched on the phone.

## Step 5 — Live speed readout — code done, indoor-verified only, 2026-08-02

- [x] `LoggingService` already gets a `Location` on every GPS update
  (`requestLocationUpdates`, since the Step 10 fix); `Location.getSpeed()`
  is sitting right there unused. Surface it to `MainActivity` live while a
  ride is active and show it as a plain number (mph or km/h, pick one) on
  the ride screen.
  - **New pattern for this project:** so far the service and the Activity
    have only talked one-way (Activity sends start/stop commands to the
    service via `Intent`). Showing a live value means data flowing the
    other way, continuously, while the service runs. Worth stopping to
    explain the mechanism (e.g. a `LiveData`/`StateFlow` the service
    updates and the Activity observes) before wiring it up, not just
    dropping it in — this is exactly the kind of new-concept moment
    `CLAUDE.md` asks to slow down for.
  - **Faster GPS polling while the dashboard is visible, decoupled from
    the logged row rate.** The current 5s location interval (Step 10) was
    tuned for logging, not for a live glanceable view — at riding speed
    that's 22-45m between fixes, which would make both the speed number
    and Step 9's street view jump instead of update smoothly. With Organic
    Maps gone there's no more GPS contention to protect against (that's
    what forced caution before), so there's headroom to request updates
    faster (~1-2Hz) specifically while a ride is active. This doesn't need
    to change how often rows are written to `gps_points` for logging —
    just how often the live UI gets a callback.
  - **Gap handling:** if no location update arrives within roughly 2x the
    active interval, show a stale indicator (e.g. "--" or grey out the
    number) rather than silently freezing on the last real value — matches
    the known patchy-GPS reality (tree cover, no-A-GPS) already documented
    from Steps 8/10.
- **Verify:** during a real or simulated ride, the on-screen speed changes
  as GPS speed changes, updates smoothly (not in 5s jumps) while moving,
  goes stale visibly during a real GPS gap instead of freezing silently,
  and roughly matches a known reference (e.g. walking pace, or
  cross-checked against the phone's other GPS-speed display).
  - **Implementation:** `LoggingService` companion object holds a
    `MutableStateFlow<LiveLocationUpdate?>` (speed + timestamp), written on
    every `LocationCallback` delivery — no service binding needed, any
    component can read `.value`. `MainActivity` reads it once a second off
    the same tick loop that already drives `rideTimerText`, rather than
    introducing a separate `Flow.collect` consumer, to stay close to the
    existing code style. Location request interval dropped from 5s to 1s
    for live-UI purposes, but a `lastGpsRowWriteMs` gate inside the
    callback still only inserts a `gps_points` row every ~5s, so the
    logged row cadence is unchanged. Stale threshold is a flat 2s (2x the
    new 1Hz request interval). Units: mph (unasked-for default, given the
    Michigan/US context — flag if km/h is actually wanted).
  - **Partial — indoor smoke test only, no outdoor/moving verification
    yet.** Installed and ride-tested on the physical S4 (ride 40, 80.1s):
    Start Ride → speed correctly showed `--` throughout (no indoor GPS fix
    ever arrived, consistent with every other indoor test in this
    project) → Stop Ride cleanly reset it, no crash either side.
    Confirmed no regression from the faster location polling: accelerometer
    ran 7979 rows/80.1s ≈ 99.6Hz, matching the established indoor baseline
    exactly; gyroscope/magnetometer/barometer counts all in line with
    prior sessions. The actual "number moves with real speed and roughly
    matches a reference" half of this verify condition needs a real
    outdoor ride and is still open.

## Step 6 — Live odometer

- [ ] Running total distance for the active ride: accumulate haversine
  distance between consecutive GPS points as they arrive, same live-update
  mechanism as Step 5. Store/display in miles or km (match Step 5's unit
  choice).
  - **Discard implausible jumps:** if a gap in updates closes with a fix
    that implies a speed no bike ride could plausibly hit (pick a generous
    ceiling, e.g. 40mph, well above real riding speed but below "GPS
    jitter/multipath teleport"), don't add that segment to the odometer
    total. Otherwise a GPS gap followed by a fix arriving from a slightly
    different position reads as a burst of impossible speed and silently
    inflates the total. Same underlying patchy-GPS reality as Step 5's gap
    handling.
- **Verify:** after a short real walk/ride of known approximate distance,
  the displayed odometer is in the right ballpark; cross-check against the
  ride's exported GPS points post-hoc (sum of consecutive-point distances
  should match what was shown live, give or take the last unflushed point).

## Step 7 — Live elevation (relative)

- [ ] Convert barometer pressure readings to elevation *change since ride
  start* (standard barometric formula on the pressure delta), not an
  absolute altitude — matches Step 14's finding that this sensor's raw
  range isn't calibrated to sea-level, so only relative change is honest.
  Display as "+X ft" / "-X ft" climbed/descended since Start Ride.
- **Verify:** ride somewhere with a known, noticeable elevation change
  (a hill, a parking garage ramp, stairs while carrying the phone) and
  confirm the displayed number moves in the right direction and rough
  magnitude.

## Step 8 — Acquire local street data — done, verified on the physical S4, 2026-08-02

- [x] **Resolved data source: a real OSM road-only extract, bounded to a
  20-mile radius of home (6231 Gossard Ave)** — this is the actual defining
  radius (corrected 2026-07-26 from an earlier "Lansing + East Lansing +
  Haslett + Okemos" description, which was a reasonable-sounding
  approximation but not the real rule; 20mi from home is what actually
  bounds where the bike goes). This supersedes the earlier "just use
  historical `gps_points`" idea, which only would have covered
  previously-ridden streets — doesn't satisfy showing an outline in a place
  ridden for the first time, which is the actual requirement.
  - **All the heavy work happens on the PC, not the S4** — matches this
    project's existing division of labor (the phone logs, the PC
    analyzes). Pull an OSM extract for the bounded region (Overpass API
    query or a clipped regional extract), filter to road ways only (drop
    buildings, POIs, land use, labels — none of that is needed), simplify
    to plain line-segment geometry, write out a lightweight file.
  - **Push the finished file to the phone once, over USB** — same
    `adb push`-style transfer already used throughout this project for
    every DB pull. No OSM parser needed on-device at all.
  - **On-device: a simple Room table of line segments**, no routing graph,
    no tile pyramid, no parsing logic. At ride time it's one cheap
    bounding-box query ("segments within 75m of here") — cost is the same
    whether the table holds a few routes or the whole region, since only a
    tiny local slice is ever queried/drawn at once. This is the piece that
    makes it safe: the crash risk before was continuous complex rendering,
    never "having offline data sitting in storage."
  - No new manifest permission needed on the phone — the extract is
    fetched on the PC (which already has internet), not the device.
  - **Provision for occasional trips outside the default region (e.g. a
    planned Novi or Atlanta trip):** the same PC-side pipeline is reusable
    on demand — re-run the extract for the new area, push an update before
    departing. Import must **append** new segments to the existing table
    rather than replace it, so coverage accumulates across regions
    permanently instead of needing to be rebuilt each time. This only
    covers *planned* trips (advance WiFi+USB access at home beforehand) —
    spontaneous travel outside the default region is still out of scope,
    same iPhone fallback as before.
- **Verify:** the stored data covers the full 20-mile radius (spot-check a
  few streets by shape against a reference like Google Maps, not just
  streets already ridden), and the on-device storage footprint is
  reasonable for a region this size (expect low tens of MB at most for
  road-only geometry — nowhere near Organic Maps' full offline map package
  for the same area).
  - **PC-side extraction — done 2026-07-26/27.** Geocoded the anchor point
    (6231 Gossard Ave → ~42.7624, -84.4780) and the four town names via
    Nominatim, then queried the Overpass API for every
    `primary|secondary|tertiary|unclassified|residential|living_street|
    track|cycleway|*_link` way inside a bounding box big enough to contain
    the full 20-mile circle.
  - **The main public Overpass instance (`overpass-api.de`) was down for
    the entire session** — every request failed, including a trivial
    single-way query, with a backend `Dispatcher_Client::
    request_read_and_idx::timeout` error, confirming it wasn't our query
    at fault. Tried several other public mirrors
    (`overpass.kumi.systems`, `lz4.overpass-api.de`,
    `overpass.private.coffee`, `overpass.nchc.org.tw`) — none responded
    usefully. **`https://maps.mail.ru/osm/tools/overpass/api/interpreter`**
    (Mail.ru's public mirror) worked and is what the extract was actually
    pulled from.
  - **A single request for the whole 20-mile bounding box timed out** even
    on the working mirror — too much for one shot on a shared free server.
    Fixed by splitting the box into a 5x5 grid (25 tiles, ~8x8.7mi each)
    and fetching each tile separately with a 2s pause between requests;
    one tile (over the dense Lansing/East Lansing core) still timed out at
    that size and was split again into 4 smaller sub-tiles, which all
    succeeded.
  - **Merging required de-duplication**: a way that crosses a tile
    boundary comes back in full in every tile that touches it, so the same
    road appeared in more than one tile file. De-duped by OSM way id
    before converting to segments (14,600 unique ways from 15,188 raw
    records across the 28 tile files).
  - **No Python available in this session** (same standing gap noted back
    in Step 9's verification) — used Node.js instead for the merge/
    dedupe/clip/CSV-write step, which is a one-off script, not part of the
    app; doesn't change the project's Python/pandas analysis pipeline.
  - **Circle-clipping:** the tiled fetch returns a square superset of the
    real 20-mile circle (and Overpass doesn't clip a way's geometry to the
    query box — it returns the full way if any point touches the box).
    Fixed by computing haversine distance from home for both endpoints of
    every consecutive-point segment and dropping any segment where neither
    endpoint is within 20mi — this both trims the square down to a circle
    and cuts long roads down to just the portion actually in range,
    without needing real polygon-clipping math.
  - **Result:** `map_data/street_segments.csv` (gitignored — generated
    data, same convention as `/exports/`) — 143,745 segments, 5.76MB,
    covering lat 42.4712–43.0531 / lon -84.8767–-84.0788, matching the
    expected 20-mile extent almost exactly. Well under the "low tens of
    MB" ceiling this step expected.
  - **Coverage spot-check:** confirmed several expected named roads
    present in the raw tile data before tags were dropped (Michigan
    Avenue, Farm Lane, Hagadorn Road, Gossard Avenue itself, Okemos Road,
    Haslett Road). One initial scare — "Grand River Avenue" showed zero
    matches — turned out to be a false alarm: OSM tags it "East/North
    Grand River Avenue", not a bare "Grand River Avenue", and it is
    present (primary/tertiary tagged, 6 tile files). Also rendered the
    full segment set as a simple SVG line plot (red dot at home) and sent
    it for visual comparison against a real map — worth a second look
    before treating this as fully verified.
  - **On-device half — done 2026-08-02.** New `StreetSegment(id, lat1, lon1,
    lat2, lon2)` entity/table, no `rideId` foreign key (static reference
    data, not per-ride) — `AppDatabase` bumped 4→5 via `MIGRATION_4_5`, a
    plain `CREATE TABLE` since this is a brand-new table, not a rebuild of
    an existing one like `MIGRATION_3_4`, so no risk to real ride history.
    New "Import streets" button in `MainActivity` streams the CSV with a
    `BufferedReader` and inserts in 2000-row batches (not one big `List` +
    single insert) — a deliberate choice given the real `OutOfMemoryError`
    a similarly-sized ~199,000-row `List` caused in Step 3's export
    investigation on this same device.
  - **Two real footguns hit getting the file onto the phone, both fixed:**
    (1) `adb push` straight into the app's external files dir
    (`/storage/emulated/0/Android/data/jhaanush.bikedevice/files/...`)
    failed with "Read-only file system" — worked around the same way as
    the Step 10 DB patches, by pushing to plain `/sdcard/` first, then
    `su`-root `cp`-ing it into place. (2) That root `cp` initially left the
    new `map_data/` folder owned `u0_a209:u0_a209`, matching the
    convention documented for the **internal** `bike_data.db` path — but
    external/FUSE storage (`getExternalFilesDir`) turns out to need
    `media_rw:media_rw` instead (confirmed by comparing against the
    existing `exports/` folder's real ownership), and the wrong ownership
    made the entire `files/` directory invisible through the FUSE view,
    not just the new folder — the app's own `File.exists()` check
    (`false`) is what caught it, not a plain `adb shell ls` (which can't
    see another app's `Android/data/<pkg>` folder either way on this
    device, working or not, so it's not a useful diagnostic here). Fixed
    by `chown -R media_rw:media_rw` + matching permission bits on
    `map_data/`. **Worth remembering as a standing distinction on this
    device:** internal app storage (`/data/data/.../databases/`) wants
    `u0_a209:u0_a209`; external/FUSE app storage
    (`Android/data/<pkg>/files/...`) wants `media_rw:media_rw`. Different
    rules, easy to cross the streams.
  - **Duplicate import caught and fixed.** The import button got tapped
    twice in quick succession while waiting on the (genuinely slow, ~6+
    min on this device's eMMC) first run — once via `adb input tap`, once
    physically — so the table ended up with 287,490 rows, exactly 2×
    expected. Confirmed via DB pull it was two clean, uncorrupted,
    back-to-back full passes (ids 1–143745 and 143746–287490 pairwise
    identical, not interleaved/corrupted), so the second half was safely
    dropped (`DELETE FROM street_segments WHERE id > 143745`, then
    `VACUUM`), patched back onto the device the same way Step 10's ride
    bookkeeping fixes were (force-stop app, push corrected `.db` over the
    original preserving `u0_a209:u0_a209` + deleting stale `-wal`/`-shm`).
  - **Final on-device verification:** `street_segments` = 143,745 rows
    (exact match to the PC-side extract), table's own on-disk footprint
    (via `dbstat`) = ~6.1MB — comfortably under the "low tens of MB"
    ceiling this step expected, consistent with the 5.76MB source CSV. All
    39 existing `rides` rows and their sensor/GPS data confirmed untouched
    by the migration. App relaunches cleanly post-patch, no crash.

## Step 9 — Render nearby streets

- [ ] New view (plain `Canvas`, not a map library — no OpenGL, no tile
  rendering, that's the whole point of this design vs. Organic Maps):
  given current GPS position, query stored street segments within ~75m,
  draw them as simple lines centered on your position. Redraw on each
  location update from Step 5's faster-polling stream (not the older 5s
  logging cadence) so the view actually tracks smoothly while moving, per
  what you were hoping to see.
  - **Resolved heading source: GPS course-over-ground while moving, not
    magnetometer.** A magnetometer this close to a metal bike frame/mount
    is a known distortion risk; GPS bearing between consecutive fixes is
    the more reliable source at riding speed, which is when this view
    matters most anyway. Use magnetometer only as a fallback below some
    low-speed threshold (e.g. under ~2mph) where GPS bearing gets noisy
    from position jitter rather than real movement — same already-logged
    magnetometer data, just not the primary source.
  - **Resolved orientation mode: track-up, not north-up.** The arrow
    representing you is drawn fixed, always pointing straight up on
    screen — it never rotates. What rotates is the street geometry: before
    drawing each segment, rotate its coordinates around your current
    position by the negative of current heading, so "forward" always lands
    pointing up. Standard convention for this kind of glanceable display
    (matches Beeline Velo 2, Google Maps/Waze in nav mode) — the
    surroundings turn, you don't.
- **Verify:** walk/ride a known local street and confirm the rendered
  lines match the real street layout around you (turns, intersections in
  the right relative places), that the view visibly tracks while moving
  rather than jumping every few seconds, and that the orientation doesn't
  visibly glitch near the bike frame (spot-check by comparing displayed
  heading against actual direction of travel at riding speed).

## Step 10 — Combined verification

- [ ] Real ride with the full dashboard (speed, odometer, elevation, local
  street view) all running together, logging active throughout.
- **Verify, matching the same discipline Step 3 skipped past too fast:**
  - **No regression:** sensor/GPS rate holds against Phase 1 Step 16's
    baseline (~98-100Hz indoor) — this dashboard should cost far less than
    Organic Maps did, but confirm rather than assume.
  - **No silent data loss:** compare last-logged-timestamp against the
    ride's `endTime` directly (the check that actually caught Step 3's
    problem) — don't rely on the app "looking fine" or the ride closing
    cleanly as proof nothing was lost.
  - **Dashboard sanity:** displayed speed/odometer/elevation during the
    ride roughly match what the exported CSV shows after the fact.
  - **Battery drain rate:** compare against the ride 26 baseline (15%→4%
    in ~13 min under combined GPS+sensor+nav+screen-on load). Dropping
    Organic Maps' rendering should help a lot, but the screen is still on
    the whole ride for the dashboard, which was itself named as a factor
    in that crash — don't assume battery is solved just because the
    memory/crash problem is. Check `dumpsys batterystats --history` same
    as the Step 3 investigation, not just a before/after percentage glance.

---

Once Step 10 is solid, Phase 2 is done. At that point — assuming Phase 1
Steps 11-16 are also closed out — every independent track from the original
plan (Section 10) is unblocked: Phase 3 (real field data collection) can
ramp up in earnest, since the logger now captures its full intended sensor
set and the dashboard runs alongside it without degrading the data.

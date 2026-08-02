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

## Step 4 — Remove Organic Maps integration — done, verified 2026-08-02

- [x] Remove `btnNavigate` from `activity_main.xml` and its click listener
  from `MainActivity.onCreate()` (the `getLaunchIntentForPackage(
  "app.organicmaps")` code from Step 1). Full removal, not just hiding the
  button — matches this project's convention of deleting dead code rather
  than commenting it out (see the Step 15 light-sensor removal).
- **Verify:** app builds and runs with no Navigate button; no leftover
  references to `app.organicmaps` anywhere in the app source.
  - Confirmed: `grep -rn "organicmaps|btnNavigate|Navigate"` across
    `app/src/main` returns no matches. `btnStopRide`'s
    `layout_constraintBottom_toTopOf` re-pointed to `@id/btnQuery` to keep
    the vertical button chain (`tvRideTimer` → `btnStartRide` →
    `btnStopRide` → `btnQuery` → `btnExport`) connected after removing
    `btnNavigate`. `AndroidManifest.xml` needed no change — no `<queries>`
    block ever referenced `app.organicmaps`. `gradlew assembleDebug`
    succeeds (built and confirmed clean before this change too, as a
    baseline). Not yet installed/verified on the physical S4 — this step's
    verify condition is build-level only, no on-device behavior changed.

## Step 5 — Live speed readout

- [ ] `LoggingService` already gets a `Location` on every GPS update
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

## Step 8 — Acquire local street data

- [ ] **Resolved data source: a real OSM road-only extract, bounded to the
  Lansing + East Lansing + Haslett + Okemos, MI area** (the bike's actual
  operating range — confirmed it doesn't leave this region; genuinely new
  areas outside it are out of scope by design, iPhone is the accepted
  fallback there). This supersedes the earlier "just use historical
  `gps_points`" idea, which only would have covered previously-ridden
  streets — doesn't satisfy showing an outline in a place ridden for the
  first time, which is the actual requirement.
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
- **Verify:** the stored data covers the full Lansing/East Lansing/
  Haslett/Okemos region (spot-check a few streets in each town by shape
  against a reference like Google Maps, not just streets already ridden),
  and the on-device storage footprint is reasonable for a region this size
  (expect low tens of MB at most for road-only geometry — nowhere near
  Organic Maps' full offline map package for the same area).

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

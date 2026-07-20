# Phase 2 Build Checklist — Navigation Integration (Incremental)

Same rule as Phase 1: one job per step, don't move on until the verify line is
actually true on your screen.

**Decisions already resolved before writing this (see `Bike_Data_Logger_Project_Plan.md`
Section 13 #2 for the full history):** nav app is **Organic Maps**
(`app.organicmaps`), already installed, offline maps downloaded, offline
cycling-mode routing confirmed working with WiFi off. Nothing in this
checklist re-litigates that — it's just wiring your app to launch it.

**Not in scope here, deliberately:** passing a destination into Organic Maps
from your app, any UI polish (that's the new Phase 8), and anything to do
with Phase 1 Steps 11-16 (multi-sensor logging) — that's a fully independent
track, can happen before, after, or interleaved with this one.

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

## Step 2 — Confirm logging survives Organic Maps taking the screen

- [ ] With `Start Ride` active, tap Navigate and use Organic Maps normally
  for a minute or two (indoors/short walk is fine for this step).
- [ ] Return to your app, tap `Stop Ride` + `Export`.
- **Verify:** DB pull (or the exported CSV) shows continuous timestamps
  through the period Organic Maps was in the foreground — no gap
  corresponding to that window. This is really just confirming Step 8's
  foreground-service fix still holds now that a second app is the one
  visibly in front, not a new mechanism.

## Step 3 — Real combined ride

- [ ] Start Ride → launch Organic Maps → navigate to an actual destination
  → Stop Ride → Export, on a real ride.
- **Verify, two things:**
  - **Data quality:** compare GPS point density and sensor rate against
    ride 25's baseline (41 GPS points / 915s, ~67Hz sensor rate). Nav
    running alongside shouldn't meaningfully degrade this further — if it
    does, that's a new main-thread-contention-style issue worth digging
    into, same class as the GPS/sensor contention Step 10 already found
    and fixed once.
  - **Real-world sanity check:** did Organic Maps actually navigate the
    ride correctly? Also worth just *observing* (not necessarily fixing):
    if you background Organic Maps mid-ride (e.g. switch back to your app
    or the home screen), does it survive on this device's aggressive
    low-memory killer (Step 7's finding), or does it need relaunching?
    That's Organic Maps' own process-survival behavior, not something in
    your codebase to engineer around — just useful to know before relying
    on it mid-ride.
  - **Already confirmed, not just hypothetical: this combination stresses
    the battery hard.** Ride 26 (2026-07-17, run manually alongside Organic
    Maps ahead of this checklist) showed the battery crash 15%→4% in ~13
    minutes under combined GPS+sensor logging, navigation, and screen-on
    load — see `CLAUDE.md`'s Phase 1 Step 10 section, "Post-Step-10
    finding." Battery efficiency is now priority #2 project-wide (right
    after correctness). Worth watching this specifically during Step 3's
    real ride, not just treating it as a one-off.

---

Once Step 3 is solid, Phase 2 is done. At that point — assuming Phase 1
Steps 11-16 are also closed out — every independent track from the original
plan (Section 10) is unblocked: Phase 3 (real field data collection) can
ramp up in earnest, since the logger now captures its full intended sensor
set and nav runs alongside it without degrading the data.

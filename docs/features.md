# Cardea — Feature Inventory

> Interactive table of contents for all app features. Click any section to expand.

---

<details open>
<summary><h2>1. Home Dashboard</h2></summary>

The landing screen after splash — shows fitness status at a glance and routes to key actions.

- **Greeting & Active Session Banner** — tappable to return to an in-progress workout
- **Sensor Status Chip** — paired BLE device name + connection state; tap to re-scan
- **Coaching Insights Card** — AI-generated plain-language takeaways (efficiency trends, resting HR, consistency)
- **Bootcamp Hero Card** *(enrolled users)*
  - Today's session name, duration, zone label
  - Start button + details preview
  - Week number and program progress
- **Bootcamp Progress Ring** *(enrolled users)* — animated circular % of weeks completed
- **Weekly Volume Card** — progress bars for distance, time, and run count vs. weekly targets
- **Stat Chips**
  - Enrolled: goal runs/week, last run distance, no-miss streak
  - Free run: this week's runs, last run distance, last run duration
- **Structured Training CTA** *(non-enrolled)* — encourages bootcamp enrollment
- **Profile Avatar Badge** — navigates to Account screen

**Key files:** `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`, `ui/home/BootcampHeroCard.kt`, `ui/home/HomeSessionStreak.kt`

</details>

---

<details>
<summary><h2>2. Workout Setup</h2></summary>

Configuration interface for building and launching a workout.

<details>
<summary><h3>2a. Workout Modes</h3></summary>

- **Steady State** — fixed target HR (bpm) with configurable buffer zone (± bpm)
- **Distance Profile** — ordered segments, each with a distance milestone and target HR
- **Free Run** — no HR target; monitoring only (optional planned duration for bootcamp timed sessions)

</details>

<details>
<summary><h3>2b. Preset Library</h3></summary>

- Curated presets: Lactate Threshold, Aerobic Tempo, Norwegian 4x4, Strides, Race Sim
- Categories: Base Aerobic, Threshold, Interval, Race Prep
- Max HR input personalizes all preset targets
- Buffer zone and alert timing customization

</details>

<details>
<summary><h3>2c. BLE Device Pairing</h3></summary>

- Scans for compatible BLE HR monitors (targeting Coospo H808S)
- Device list with signal strength
- Saves last-used device for quick reconnect
- Manual re-scan trigger

</details>

<details>
<summary><h3>2d. Bootcamp Integration</h3></summary>

- "Go to Bootcamp" navigation for enrolled users
- "Set Up Bootcamp" CTA for non-enrolled users

</details>

**Key files:** `ui/setup/SetupScreen.kt`, `ui/setup/SetupViewModel.kt`, `domain/model/WorkoutConfig.kt`, `domain/model/WorkoutMode.kt`

</details>

---

<details>
<summary><h2>3. Active Workout</h2></summary>

Real-time coaching interface during a run.

<details>
<summary><h3>3a. Core Metrics</h3></summary>

- **HR Ring** — current bpm with pulse animation, zone color coding, connection indicator
- **Zone Status Header** — "In Zone" / "Below Zone" / "Above Zone" / "No Signal" / "Free Run" with target HR
- **Elapsed Time** — digital timer (h:mm:ss)
- **Distance & Pace Cards** — km with decimals, min/km, zone-aware accent colors
- **Guidance Card** — real-time coaching message with pulsing accent bar

</details>

<details>
<summary><h3>3b. Segment & Progress Tracking</h3></summary>

- Current segment label + countdown (time or distance remaining)
- Next segment preview
- Time-based workouts: elapsed / total with remaining bar
- Distance-based: distance covered / total with remaining bar
- Bootcamp timed sessions: progress bar despite free-run HR

</details>

<details>
<summary><h3>3c. Controls</h3></summary>

- **While Running:** Pause (ghost) + Stop (red outline)
- **While Paused:** Resume (gradient) + Stop (low opacity)
- **Auto-Pause Toggle** — mid-workout on/off; silences alerts at traffic stops
- **Tertiary Stats Row** — average HR + auto-pause indicator

</details>

<details>
<summary><h3>3d. Advanced</h3></summary>

- **Projected HR** — adaptive controller prediction ("Projected X bpm")
- **Zone Ambient Coloring** — background breathes with current zone color
- **Simulation Overlay** *(debug only)* — simulated HR/pace data

</details>

**Key files:** `ui/workout/WorkoutScreen.kt`, `service/WorkoutForegroundService.kt`, `service/WorkoutState.kt`

</details>

---

<details>
<summary><h2>4. Audio Coaching & Alerts</h2></summary>

Real-time verbal and audio guidance during workouts.

<details>
<summary><h3>4a. Voice Coaching (TTS)</h3></summary>

- **Coaching Directives** — "Speed up", "Slow down", "Return to zone", predictive warnings, segment changes
- **Informational Cues** *(configurable)* — halfway reminder, km splits, workout complete, in-zone confirmation
- **Signal Cues** — "Signal lost" / "Signal regained"
- **TTS Briefing** — startup announcement of mode, target HR, estimated duration

</details>

<details>
<summary><h3>4b. Verbosity Levels</h3></summary>

- **Off** — no voice coaching
- **Minimal** — directives only (speed up, slow down, segment changes)
- **Full** — directives + all informational cues

</details>

<details>
<summary><h3>4c. Earcon & Haptic Alerts</h3></summary>

- Alert volume slider (0–100%)
- Escalating earcons (single beep → double beep by urgency)
- Vibration toggle for silent haptic feedback
- Plays over music without stealing audio focus (`STREAM_NOTIFICATION`)

</details>

<details>
<summary><h3>4d. Countdown & Briefing</h3></summary>

- 3-2-1-GO countdown at workout start
- TTS briefing announces workout parameters before running

</details>

**Key files:** `service/AlertManager.kt`, `data/repository/AudioSettingsRepository.kt`

</details>

---

<details>
<summary><h2>5. Bootcamp (Structured Training)</h2></summary>

Adaptive 12-week training program with personalized sessions.

<details>
<summary><h3>5a. Onboarding</h3></summary>

- Goal selection: 5-mile tempo, 10K, Half Marathon, Marathon
- Fitness assessment: estimated CTL, recent volume, fitness signals
- Weekly availability: runs/week (3–6), distance target (km), preferred days
- 12-week fixed structure with adaptive progression

</details>

<details>
<summary><h3>5b. Phase Engine</h3></summary>

- **Phase 1 — Base Building:** high-volume Z2/Z3, confidence building
- **Phase 2 — Threshold Development:** Z4 sessions, lactate threshold work
- **Phase 3 — Peak & Taper:** Z5 intervals, race-specific intensity
- **Phase 4 — Race Prep:** reduced volume, maintained intensity, recovery

</details>

<details>
<summary><h3>5c. Session Types</h3></summary>

- Easy Run, Long Run, Tempo Run, Interval Workout, Strides, Race Simulation
- Discovery Session (assessment), Check-In Session (fitness evaluation)
- Each maps to a preset profile with CTL-scaled intensity targets

</details>

<details>
<summary><h3>5d. Scheduling & Adaptation</h3></summary>

- Weekly session calendar view
- Auto-reschedule missed sessions to next available day
- Gap advisor: warns if rest days too short or volume too high
- Fitness-based scaling: CTL tiers determine session intensity
- Overtraining detection: adjusts volume if signals indicate fatigue

</details>

<details>
<summary><h3>5e. Bootcamp Dashboard</h3></summary>

- Today's session hero card with start button
- Week number + total weeks + progress ring
- Stat chips: goal pace, weekly volume, session streak
- Phase-specific coaching insights

</details>

**Key files:** `ui/home/BootcampHeroCard.kt`, `data/db/BootcampDao.kt`, `data/db/BootcampEnrollmentEntity.kt`, `data/db/BootcampSessionEntity.kt`

</details>

---

<details>
<summary><h2>6. History & Activity Log</h2></summary>

Complete workout history with detailed analytics.

<details>
<summary><h3>6a. Workout List</h3></summary>

- Grouped by week ("This Week", "Mar 9–15", etc.) with run count + total km
- Workout cards: day, distance, duration, avg pace, mode badge, color-coded indicator
- Activity vs. Trends segmented toggle
- Long-press to reveal delete; confirmation dialog

</details>

<details>
<summary><h3>6b. History Detail</h3></summary>

- Date header + full stats (duration, distance, avg pace, avg HR, max HR, elevation)
- **Route Map Replay** *(requires Maps API key)*
  - Interactive map with HR-zone gradient coloring
  - Playback controls (play/pause, speed)
  - Start/finish markers
- **Segment Breakdown** *(distance profile mode)* — target vs. actual HR, time-in-zone %
- **Zone Time Distribution** — pie chart
- **HR Curve** — graph over time with target zone shading
- **Coaching Events Timeline** — alerts triggered during the run

</details>

<details>
<summary><h3>6c. Post-Run Summary</h3></summary>

- Session type + metrics recap
- AI coaching feedback and insights
- Performance comparison vs. recent history
- Next session recommendation
- Navigation to Progress dashboard

</details>

**Key files:** `ui/history/HistoryScreen.kt`, `ui/history/HistoryDetailScreen.kt`, `ui/history/PostRunSummaryScreen.kt`, `data/db/WorkoutDao.kt`

</details>

---

<details>
<summary><h2>7. Progress & Trends Dashboard</h2></summary>

Long-term fitness analytics across three categories.

<details>
<summary><h3>7a. Efficiency Metrics</h3></summary>

- **Heartbeats/km** bar chart — aerobic efficiency (lower = better), weekly delta
- **Pace at Fixed HR** line chart — pace at reference HR over time
- **VO2 Max Estimate** line chart — trend with confidence interval
- **Aerobic Efficiency** line chart — speed-to-HR ratio over time

</details>

<details>
<summary><h3>7b. Load Metrics</h3></summary>

- **Weekly Distance** bar chart — last 12 weeks of volume
- **Training Load** bar chart — TRIMP-like (duration x avg HR / 100)
- **HR Zone Distribution** pie chart — % time in Z1–Z5
- **Speed vs. HR** scatter plot — shows relationship evolution

</details>

<details>
<summary><h3>7c. Health Metrics</h3></summary>

- **Resting HR Proxy** line chart — estimated from warm-up trends
- **HR Recovery** line chart — time to settle back in-zone after drift
- **Running Calendar Heatmap** — 365-day grid, brightness = distance

</details>

<details>
<summary><h3>7d. Coach's Take</h3></summary>

- AI-generated 1–2 sentence insights at top of dashboard
- Actionable recommendations based on trends
- Filter by workout mode: All / Steady State / Distance Profile

</details>

**Key files:** `ui/progress/ProgressScreen.kt`, `ui/charts/BarChart.kt`, `ui/charts/PieChart.kt`, `ui/charts/ScatterPlot.kt`

</details>

---

<details>
<summary><h2>8. Account & Settings</h2></summary>

<details>
<summary><h3>8a. Profile</h3></summary>

- Display name (max 20 chars)
- Avatar symbol picker (10 unicode choices)
- Run count badge

</details>

<details>
<summary><h3>8b. Appearance</h3></summary>

- Theme toggle: System / Light / Dark
- Live theme switching

</details>

<details>
<summary><h3>8c. Configuration</h3></summary>

- Google Maps API key input + save (for route replay)
- Max Heart Rate input (100–220 bpm, personalizes presets)

</details>

<details>
<summary><h3>8d. Audio & Alerts</h3></summary>

- Alert volume slider (0–100%)
- Voice coaching verbosity: Off / Minimal / Full
- Vibration toggle
- Informational cue toggles: halfway, km splits, complete, in-zone confirm

</details>

<details>
<summary><h3>8e. Workout Preferences</h3></summary>

- Auto-pause toggle — silences alerts and pauses timer at stops

</details>

<details>
<summary><h3>8f. Achievements Gallery</h3></summary>

- Grid of earned badges (First Run, 5-Run Streak, 50km Club, etc.)
- Shown conditionally when achievements exist

</details>

**Key files:** `ui/account/AccountScreen.kt`, `ui/account/AccountViewModel.kt`

</details>

---

<details>
<summary><h2>9. BLE Heart Rate Connectivity</h2></summary>

- Automatic discovery of compatible BLE HR monitors
- Device list with signal strength indicators
- Last-used device saved for quick reconnect
- Auto-reconnect on app return
- Live bpm stream (~1 Hz), signal quality indication
- Graceful degradation on signal drop during workout

**Key files:** `service/BleHrManager.kt`

</details>

---

<details>
<summary><h2>10. GPS & Distance Tracking</h2></summary>

- Real-time distance calculation via GPS fixes (Haversine formula)
- Stationary detection — no distance increment when stopped
- Track point recording: lat, lon, timestamp, HR, pace (for route replay)
- Elevation data collection (GPS altitude)
- 5-second save interval for track points

**Key files:** `service/GpsDistanceTracker.kt`, `data/db/TrackPointEntity.kt`

</details>

---

<details>
<summary><h2>11. Adaptive Pace Controller</h2></summary>

Predictive engine that models individual pace-to-HR relationships over time.

- **Pace-HR Calibration** — learns "at X min/km, this user runs at Y bpm"
- **Predictive Guidance** — forecasts HR 20–30s ahead; proactive coaching before zone drift
- **Fitness Adaptation** — CTL/ATL signals adjust the model (better fitness = same pace = lower HR)
- **Cross-Session Learning** — trim offsets and HR slope persist via `AdaptiveProfileRepository`

**Key files:** `domain/engine/AdaptivePaceController.kt`, `data/repository/AdaptiveProfileRepository.kt`

</details>

---

<details>
<summary><h2>12. Coaching Insights Engine</h2></summary>

AI-driven feedback surfaced across multiple screens.

- **Insight Sources:** efficiency trends (HB/km), recovery markers (resting HR), volume trends, zone distribution, consistency
- **Delivery Points:**
  - Home screen coaching card
  - Post-run summary insights
  - Progress dashboard "Coach's Take"
- **Insight Types:**
  - Efficiency: "Aerobic efficiency improving" / "Base needs attention"
  - Recovery: "Resting HR dropping" / "HR recovery slow — prioritize easy runs"
  - Consistency: "Strong week" / "No sessions this week"
  - Volume: "Approaching weekly target" / "Behind on distance goal"

</details>

---

<details>
<summary><h2>13. Achievements & Milestones</h2></summary>

- **Streak:** consecutive days with at least 1 workout
- **Distance Milestones:** 50 km, 100 km, 200 km, 500 km (cumulative)
- **Monthly Consistency:** 3+ runs in a calendar month
- **Badges:** First Run, 5-Run Streak, 50 km Club, 100 km Club, One Month Consistency, Bootcamp Completion, Personal Record, Zone Mastery
- Unlock notifications after workouts
- Gallery display in Account screen

</details>

---

<details>
<summary><h2>14. Notifications</h2></summary>

- **Bootcamp Reminders** — daily notification for scheduled session with tap-to-open
- **Active Workout Notification** — persistent; shows HR, distance, time with pause/resume controls
- **Workout Complete Notification** — summary with tap to post-run screen
- **Critical Alerts** — BLE disconnect, GPS signal loss, low battery

</details>

---

<details>
<summary><h2>15. Design System</h2></summary>

Dark glass-morphic "Cardea" visual language.

- **Cardea Gradient:** `#FF5A5F -> #FF2DA6 -> #5B5BFF -> #00D1FF` at 135deg
- **Background:** `#0B0F17` with radial gradient
- **Glass Surfaces:** semi-transparent cards with 1dp borders, subtle shadows, `GlassCard` composable
- **Zone Colors:** green (in-zone), amber (below), red (above), blue (Z1-Z2)
- **Gradient Nav Icons:** active icons use `CompositingStrategy.Offscreen` + `BlendMode.SrcIn`
- **CardeaLogo:** canvas-drawn heart + ECG + orbital ring (large: splash, small: nav badge)
- **Charts:** all custom Canvas-drawn (no charting library)

**Key files:** `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/components/GlassCard.kt`, `ui/components/CardeaLogo.kt`

</details>

---

<details>
<summary><h2>16. Data Persistence</h2></summary>

- **Room Database** (`hr_coach_db`): workouts, track_points (FK with CASCADE), bootcamp enrollments, bootcamp sessions
- **SharedPreferences:** audio settings, user profile, theme, Maps API key (encrypted), adaptive profile, auto-pause
- **Config as JSON:** `targetConfig` column stores workout zone configuration

**Key files:** `data/db/AppDatabase.kt`, `data/db/WorkoutEntity.kt`, `data/db/TrackPointEntity.kt`, `di/AppModule.kt`

</details>

---

<details>
<summary><h2>17. Debug / Simulation Mode</h2></summary>

Testing framework for validating coaching logic without a real HR monitor.

- HR simulator: ramps HR following pace changes with configurable lag
- Location simulator: traces predefined route at adjustable speed (1x–10x)
- Simulation overlay showing simulated vs. real data
- Activated from Account screen (debug builds only)

</details>

---

<details>
<summary><h2>18. Planned / Future Features</h2></summary>

Features not yet implemented but on the roadmap. Roughly priority-ordered.

<details>
<summary><h3>18a. Onboarding & First-Run Tutorial</h3></summary>

Guided walkthrough for new users covering:
- What HR zones are and why zone-based training matters
- BLE sensor pairing step-by-step
- Workout mode explanations (Steady State vs. Distance Profile vs. Free Run)
- Quick path to first workout (or Bootcamp enrollment)
- Currently only Bootcamp has an onboarding carousel; the core app drops users on Home cold

</details>

<details>
<summary><h3>18b. HR Zone Education</h3></summary>

In-app explanations of what each heart rate zone means and why it matters:
- Zone definitions (Z1–Z5) with physiological descriptions
- "Why am I being told to slow down?" contextual help
- Inline tooltips or a "Learn" section accessible from setup and workout screens
- Connects coaching actions to training adaptations (e.g., "Z2 builds aerobic base because...")

</details>

<details>
<summary><h3>18c. Workout Export & Sharing</h3></summary>

Allow users to export or share workout summaries:
- **Image card** — shareable post-run graphic with key stats (distance, pace, time, route thumbnail)
- **GPX / TCX export** — standard formats for importing into other platforms
- **Strava / Garmin Connect / Google Fit sync** — push completed workouts to ecosystem apps (see 18g)
- Share via system share sheet (WhatsApp, Instagram story, etc.)

</details>

<details>
<summary><h3>18d. Ecosystem Sync (Strava / Google Fit / Apple Health)</h3></summary>

Two-way or push-only integration with fitness platforms:
- **Export to Strava** — auto-upload completed runs (OAuth, activity upload API)
- **Google Fit / Health Connect** — write workout sessions, HR data, distance
- Prevents data lock-in; lets users keep Cardea as the coaching brain while participating in their ecosystem
- Priority: Strava first (largest running community), Health Connect second

</details>

<details>
<summary><h3>18e. Rest Day & Recovery Guidance</h3></summary>

Surface recovery recommendations based on training load:
- "Take a rest day" recommendation when load is elevated
- Recovery status indicator on Home screen (fresh / recovering / fatigued)
- Integrate with resting HR proxy trends from Progress dashboard
- Coaching insights already track load signals — this surfaces them as actionable guidance

</details>

<details>
<summary><h3>18f. Push Notification Reminders</h3></summary>

Proactive nudges to keep users consistent:
- Scheduled run reminders ("Time for your Z2 easy run")
- Bootcamp session reminders (morning-of notification for today's planned session)
- Inactivity nudge (no run in X days)
- Configurable: time of day, which days, or off entirely
- Bootcamp already schedules sessions but doesn't push-notify

</details>

<details>
<summary><h3>18g. Personal Records (PRs)</h3></summary>

Track and celebrate personal bests:
- Fastest 1 km, 5 km, 10 km, half-marathon segments
- Longest run (distance and duration)
- Best aerobic efficiency (lowest HB/km)
- PR detection on post-run summary with celebration UI
- PR history timeline in Progress dashboard
- Feeds into achievements system (already has "Personal Record" badge defined but no PR detection logic)

</details>

<details>
<summary><h3>18h. Structured Interval Timer</h3></summary>

Simple repeating interval mode beyond distance-profile segments:
- "30s hard / 60s easy x 8" style timer with audio countdown
- Configurable work/rest durations and repeat count
- Visual countdown with segment progress
- Works alongside HR zone coaching (zone targets per interval phase)
- Currently distance-profile mode handles segments but lacks a pure time-interval UX

</details>

<details>
<summary><h3>18i. Voice Coaching Improvements</h3></summary>

Enhance existing TTS coaching quality:
- More natural phrasing and varied vocabulary (avoid repetitive "speed up" / "slow down")
- Contextual encouragement ("You're halfway there and looking strong")
- Configurable voice speed / pitch
- Segment-specific coaching cues ("Entering threshold interval — settle into rhythm")
- Explore third-party TTS engines for more natural-sounding speech

</details>

<details>
<summary><h3>18j. Route Planning & Saved Routes</h3></summary>

Pre-run route visualization and reuse:
- Save favourite routes from completed workouts
- View saved routes on map before starting
- Distance-aware route suggestions ("show me a 5K loop")
- Route replay comparison (same route, different days)
- Currently maps show post-run routes only; no pre-run planning

</details>

<details>
<summary><h3>18k. Cloud Backup & Cross-Device Sync</h3></summary>

Protect workout history from device loss:
- Backup workout history + bootcamp progress + adaptive profile to cloud
- Restore on new device / reinstall
- Foundation exists: `userId` (UUID) already generated per device, `ShareableBootcampConfig` data model ready
- No authentication or backend currently planned — evaluate Firebase, Supabase, or encrypted local backup to Google Drive
- Currently explicitly out of scope but important for retention

</details>

<details>
<summary><h3>18l. Accountability Partners</h3></summary>

Lightweight social connection without leaderboards or public sharing:
- Pair with 1–3 accountability partners (via invite code or QR)
- See partner's weekly run count and streak (not pace, distance, or HR — privacy-first)
- Optional gentle nudge: "Your partner ran today — your turn?"
- Not a social feed, not competitive, not public — just mutual encouragement
- Foundation: `ShareableBootcampConfig` and `userId` already exist for buddy features

</details>

</details>

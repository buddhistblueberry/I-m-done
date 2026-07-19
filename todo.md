# Continue Watching + Recommended Section

## 1. Data Layer
- [x] Create `WatchProgress` data model (tmdbId, contentType, positionMs, durationMs, season, episode, title, year, posterPath, backdropPath, timestamp, completed)
- [x] Create `WatchProgressStore` singleton (SharedPreferences + JSON, thread-safe, load/save/upsert/markCompleted/list incomplete)
- [x] Wire WatchProgressStore.init() into MarioCartApplication.onCreate()

## 2. Player Integration
- [x] Modify `PlayerActivity.newIntent` to accept `resumePositionMs` and read it in onCreate → pass to PlayerScreen → ExoPlayerView
- [x] Add periodic progress save in ExoPlayerView (LaunchedEffect polling currentPosition every ~10s)
- [x] On STATE_ENDED, mark title completed in WatchProgressStore
- [x] On PlayerActivity.onDestroy, save final position
- [x] Seek to resumePositionMs on player ready (if > 0)

## 3. Home View Model
- [x] Add `_continueWatching` StateFlow + loadContinueWatching() from WatchProgressStore (incomplete, sorted by timestamp)
- [x] Add `_recommended` StateFlow + loadRecommended() (aggregate genres from watched, discover, exclude watched, StreamAvailabilityChecker filter)

## 4. Home Screen UI
- [x] Add "Continue Watching" row (after hero, before genre bar) with progress overlay cards
- [x] Add "Recommended for You" row (after continue watching)
- [x] Continue-watching card click → launch PlayerActivity with resumePositionMs (via onResume → launchResume in MainActivity)

## 5. Card Component
- [x] ContinueWatchingCard with red progress bar overlay + resume label + play affordance

## 6. Build & Ship
- [x] Compile (BUILD SUCCESSFUL) — compileDebugKotlin + assembleDebug both green
- [x] Fix progressMap key-lookup bug in ContinueWatchingRow (TV keys carry _S_s_E_e suffix)
- [ ] Commit and push to current branch / PR #37

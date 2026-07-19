# Round 2 — TV D-pad focus fixes, search, sliding sidebar, and Load More button

## Completed (uncommitted)
- [x] Fix A: D-pad Up clamps at top of screen (focusGroup on all 7 scrollable screens)
- [x] Fix B: Search Enter/Done dismisses keyboard + moves focus to results
- [x] Fix C: ImeAction.Search instead of Done (magnifying glass on TV)
- [x] Redundancy sweep: removed unused imports (ContentCard, ContentRow, HomeScreen, TvSideNav dims param)
- [x] Load More button: moved from top-of-row to END of row, renamed "Explore All" → "Load More", D-pad focusable
- [x] Fix D: TV sidebar slides away by default, slides in only on Left key press, slides out on nav selection
- [x] Verified build compiles (gradlew compileDebugKotlin — BUILD SUCCESSFUL, 0 errors)

## Remaining
- [ ] Commit + push all Round 2 changes to branch fix/tv-dpad-focus-and-stale-show-data
- [ ] Confirm PR #37 is updated

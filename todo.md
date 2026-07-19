# Round 3 — Comprehensive review + Show More for genre movie lists

## In progress
- [ ] Commit & push all Round 3 changes to `fix/tv-dpad-focus-and-stale-show-data`

## Done
- [x] Fix BrowseScreen "Show More" button for genre movie lists
  - [x] Restructure grid: always show cards when items non-empty; full-screen spinner only when `isLoading && items.isEmpty()`
  - [x] Replace plain TextButton with D-pad-focusable styled "Show More" button (red border, spinner when loading)
  - [x] Compile (BUILD SUCCESSFUL)
- [x] MoviesScreen: add focusGroup() + rememberInitialFocusRequester
- [x] BrowseScreen: replace chunked(3) Row hack with LazyVerticalGrid + GridCells.Fixed(dims.gridColumns)
- [x] ContentCard: add fillMaxWidth param (grid cells fill instead of pin to fixed width)
- [x] SearchScreen: pass fillMaxWidth = true to grid cards
- [x] MainActivity: BackHandler to dismiss side rail + Right-key dismiss on TvSideNav

# Video Playback Fixes & Improvements Summary

## v6 — WebView-Only Playback & Manual Server Selection (latest)

### Goals
- Videos play **only** through the embedded WebView — no native ExoPlayer, no direct stream extraction.
- Server selection is **always manual** — the user picks from a list every time. There is no automatic selection, background probing, or ranking.

### Changes

#### `PlayerActivity.kt` (complete rewrite)
- Removed all calls to the streaming backend API (`ApiClient.streamingBackendApi`).
- Servers are now loaded directly from `assets/servers.json` via `ServerManager.initialize()`.
- On launch the player immediately shows a manual server picker dialog (`AlertDialog`).
- After the user selects a server the embed URL is constructed locally (`StreamingServer.movieUrl` / `tvUrl`) and loaded into the `WebView`.
- `ServerTester` is no longer called from the player.

#### `ServerTester.kt` (stub)
- All probing and ranking logic removed.
- `rankForContent()` is now a no-op stub that returns the input list unchanged.
- Kept as a stub to avoid breaking any existing call-sites.

#### `stream_api_service.py` (backend v6)
- `/api/servers` now returns `{ success, servers, total }` — the shape the Android `StreamingBackendClient` expects. Previously it returned a plain list.
- Added `/api/embed` endpoint: accepts `serverId`, `tmdbId`, `type`, `season`, `episode` and returns `{ success, embedUrl, serverId }`. No extraction or probing — just URL construction.
- Removed all auto-selection and parallel-probing code from the backend.
- Query parameters now use camelCase (`serverId`, `tmdbId`, `type`) matching the Retrofit interface.

### How playback works now
1. User taps a title → `PlayerActivity` launches.
2. `ServerManager.initialize()` loads `assets/servers.json` (33 servers).
3. An `AlertDialog` lists all servers — the user taps one.
4. The embed URL is built locally and loaded into the `WebView`.
5. The `WebView` renders the embed page; the user interacts with the player inside it.
6. If a server doesn't work the user presses Back and picks another.

---

## v5 — Previous (superseded)

- Introduced hybrid extraction + WebView fallback.
- Backend `/api/servers` returned a plain list (did not match Android contract).
- `ServerTester` probed all servers in parallel and auto-ranked them.
- `PlayerActivity` called the backend to fetch servers instead of using the local asset.

---

## Files Modified in v6

```
app/src/main/java/com/mariocart/app/
└── ui/
    └── player/
        └── PlayerActivity.kt  (complete rewrite — WebView-only, manual selection)
└── data/
    └── server/
        └── ServerTester.kt    (all probing removed, stub only)

backend/stream-resolver/
└── stream_api_service.py      (fixed /api/servers shape, added /api/embed)

FIXES_SUMMARY.md               (this file)
```

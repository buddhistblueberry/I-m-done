# I'm Done — Streaming App + Backend

A single repo containing:

| Folder | Purpose |
|--------|---------|
| `app/`        | Android (Kotlin) client. ExoPlayer-only, no WebView for playback. |
| `backend/`    | FastAPI streaming-resolver service. Returns direct `.m3u8 / .mp4 / .mpd / .mkv / .webm` URLs. |
| `gradle*`, `build.gradle.kts`, `settings.gradle.kts` | Android Gradle build files. |
| `servers.json`| Reference list of embed providers (kept for compatibility — the runtime list is in `backend/servers_config.py`). |

---

## Backend (FastAPI)

### Run locally

```bash
cd backend
pip install -r requirements.txt
uvicorn server:app --host 0.0.0.0 --port 8001
```

`.env` (next to `server.py`) needs `MONGO_URL` and `DB_NAME` (defaults work for local Mongo).

### Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET    | `/api/health`          | Health probe |
| GET    | `/api/servers`         | Configured providers |
| GET    | `/api/servers/status`  | Live aliveness probe |
| GET    | `/api/stream`          | Auto-find the best direct stream |
| GET    | `/api/embed`           | (Informational) build embed URL |
| POST   | `/api/captcha/submit`  | Complete extraction with a user-solved `_rcp` token |
| GET    | `/api/captcha/help`    | Human-readable doc for the captcha flow |
| POST   | `/api/report`          | Client reports playback success/failure |
| GET    | `/api/scores`          | Adaptive per-provider score |
| POST   | `/api/cache/clear`     | Drop in-process caches |
| GET    | `/api/history`         | Last 50 resolves (Mongo) |

### Deploy

The backend is self-contained Python. A few one-click options:

* **Render** — New Web Service, Build: `pip install -r requirements.txt`, Start: `uvicorn server:app --host 0.0.0.0 --port $PORT`
* **Railway** — Same start command, Railway provides `$PORT` automatically.
* **Fly.io** — `fly launch` from the `backend/` directory.

After deploy, set `MONGO_URL` + `DB_NAME` to any MongoDB instance you own (MongoDB Atlas free tier works fine).

---

## Android client

Open the repo root in Android Studio. Point the client at your deployed backend by editing **one line** in `app/src/main/java/com/mariocart/app/data/api/ApiClient.kt`:

```kotlin
private const val STREAMING_BACKEND_BASE = "https://YOUR-BACKEND-HOST/"
```

(must end with a trailing slash).

### What changed vs the previous release

* `PlayerActivity` is now **backend-only**. All on-device scraping (`StreamExtractor.extract`, the per-server retry loop, every fallback that touches a remote URL) has been removed from the playback path. ExoPlayer plays exactly what `/api/stream` returns or shows an error.
* The player adds a **season/episode picker** for TV content — tap the `S{n}·E{n}` chip in the top-right corner of the player.
* A new one-shot `CaptchaActivity` (a *tiny* WebView used only for solving Cloudflare Turnstile) captures the `_rcp` token and hands it back to the backend, which finishes extraction and returns a real `.m3u8`. The video itself is still played natively.
* Subtitle tracks returned by the backend are sideloaded automatically.

### Supported stream types

Native ExoPlayer playback for **HLS (`.m3u8`)**, **DASH (`.mpd`)**, **MP4**, **MKV**, and **WebM**. The backend tags each result with the correct `Content-Type` and `streamType`; ExoPlayer picks the right demuxer.

---

## Why a backend at all?

Every modern embed provider (vidsrc.to, vidlink.pro, embed.su, …) now:

1. Refuses to serve videos to Android `WebView` (header fingerprinting + UA detection).
2. Wraps the real `.m3u8` behind several JS hops (`/rcp/<hash>` → `/prorcp/<hash>`) and Cloudflare Turnstile.
3. Constantly rotates endpoints.

The backend deals with all of that server-side, returns a single clean URL the phone can play immediately, and only asks the user to solve a CAPTCHA on the rare occasions it can't be avoided.

---

## Roadmap

* Per-provider error analytics surfaced in `/api/scores`
* Subtitle language selection in the player UI
* Optional CDN-style edge caching of `/api/stream`
* Auto-fail-over to next episode when current episode is unavailable

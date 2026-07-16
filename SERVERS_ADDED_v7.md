# Servers added from filmcave.ru watch page (v7)

**Date:** 2026-07-16
**Files changed:** `working-servers.json`, `app/src/main/assets/servers.json`
**Server count:** 27 → 32 (+5 new embed servers, verified)
**Version:** 6 → 7

---

## Where these came from

filmcave.ru's watch page (`/movie/watch/{tmdb}` and `/tv/watch/{tmdb}`) renders a
"Server:" picker with eight embed providers. Three of them
(AutoEmbed, VidFast, Videasy) were already in the list from the earlier
fmcave.lovable.app pass. **Five were new** and are added here.

The server array was extracted directly from filmcave.ru's app bundle
(`https://filmcave.ru/assets/index-C-Us2C2a.js`) after passing its Cloudflare
challenge in a real browser session:

```js
// movies
{ id:"vidrock",  name:"VidRock",  url:`https://vidrock.ru/movie/${id}` }
{ id:"vidstorm", name:"VidStorm", url:`https://vidstorm.ru/movie/${id}` }
{ id:"vidzee",   name:"VidZee",   url:`https://player.vidzee.wtf/embed/movie/${id}` }
{ id:"vidnest",  name:"VidNest",  url:`https://vidnest.fun/movie/${id}` }
{ id:"vidlink",  name:"VidLink",  url:`https://vidlink.pro/movie/${id}` }   // already in list
{ id:"vidfast",  name:"VidFast",  url:`https://vidfast.pro/movie/${id}` }   // already in list
{ id:"videasy",  name:"Videasy",  url:`https://player.videasy.net/movie/${id}` } // already in list
{ id:"vidsrc",   name:"VidSrc",   url:`https://vidsrcme.ru/embed/movie/${id}` }

// tv (all follow the same {season}/{episode} suffix, VidZee/VidSrc use /embed/tv/)
```

## New servers added (all verified HTTP 200)

Verification: `curl -L` against the movie URL for **The Green Mile (tmdb 497)**
and the TV URL for **Rick and Morty (tmdb 60625, s1e1)**. All returned HTTP 200.

| id          | name       | baseUrl                     | movie template                  | tv template                             | tier | reliability | cloudflare |
|-------------|------------|-----------------------------|---------------------------------|-----------------------------------------|------|-------------|------------|
| `vidrock`   | VidRock    | https://vidrock.ru          | `{base}/movie/{id}`             | `{base}/tv/{id}/{season}/{episode}`     | 1    | 88          | true       |
| `vidstorm`  | VidStorm   | https://vidstorm.ru         | `{base}/movie/{id}`             | `{base}/tv/{id}/{season}/{episode}`     | 1    | 90          | true       |
| `vidzee`    | VidZee     | https://player.vidzee.wtf   | `{base}/embed/movie/{id}`       | `{base}/embed/tv/{id}/{season}/{episode}`| 1   | 86          | true       |
| `vidnest`   | VidNest    | https://vidnest.fun         | `{base}/movie/{id}`             | `{base}/tv/{id}/{season}/{episode}`     | 1    | 85          | true       |
| `vidsrc_ru` | VidSrc.ru  | https://vidsrcme.ru         | `{base}/embed/movie/{id}`       | `{base}/embed/tv/{id}/{season}/{episode}`| 1   | 84          | true       |

All five sit behind Cloudflare (`Server: cloudflare` header), so they are marked
`cloudflare: true` — the app plays these via its existing WebView/Cloudflare
path, the same way it handles `vidsrc_cc`, `vidsrc_to`, etc.

---

## The "cloud / server list" button inside the players (element sources)

Each embedded VidStorm/AutoEmbed/VidRock player has a **Server List** button
(the icon the user referred to as the "cloud button") that opens a picker of
named sources that are **dynamic per title**. These are NOT static embed
providers — they are direct stream URLs returned by the VidStorm API:

```
GET https://vidstorm.ru/api/{movie|tv}/{encryptedId}
```

The API returns a JSON object keyed by **element names**. The full set of
source names is:

> **Lithium, Hydrogen, Boron, Helium, Beryllium, Carbon, Nitrogen, Oxygen,
> Fluorine, Neon**

(Notably **Neon** is one of the names the user spotted in the picker.)

`VidStormExtractor.kt` already reverse-engineered and handles this API:
- AES-256-CBC encrypts the TMDB id with key `x7k9mPqT2rWvY8zA5bC3nF6hJ2lK4mN9`.
- It walks **all** live sources, sorts English-first, and **verifies** each
  candidate URL with a 2-byte ranged GET before returning it — so playback is
  fast and never hands ExoPlayer a dead URL.
- The result is named `VidStorm·{element}` (e.g. `VidStorm·Boron`).

### Verified LIVE sources (direct API calls, 2026-07-16)

| Title                          | tmdb           | LIVE sources          |
|--------------------------------|----------------|-----------------------|
| The Green Mile                 | movie 497      | Boron                 |
| Interstellar                   | movie 157336   | Boron                 |
| Rick and Morty s1e1            | tv 60625       | Hydrogen              |
| The Odyssey                    | movie 1368337  | Hydrogen, Boron       |
| Inception                      | movie 27205    | Boron                 |

So the "hidden servers in the video player" the user saw on The Green Mile and
Interstellar are the **VidStorm element sources** (Boron / Hydrogen / Neon / …)
and are **already resolved directly** by `VidStormExtractor` — no extra config
needed. Adding the five new embed providers above gives the app WebView
fallbacks for the same titles when the direct API path is unavailable.

---

## How installs pick this up

`RemoteServerListFetcher` fetches
`https://raw.githubusercontent.com/ashtonhardy555-stack/I-m-done/main/working-servers.json`
on launch (24h cache + bundled-asset fallback). **Pushing this to `main` updates
every existing install on its next launch — no APK update required.** New
installs get the same list via the updated bundled asset
`app/src/main/assets/servers.json`.

The `version` field bumped `6 → 7` and the `updated` date is `2026-07-16`.

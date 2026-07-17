# Video Extraction Reference — How to Properly Extract Streams

This document compiles the **correct, current extraction patterns** for every
streaming server used by the MarioCart app, drawn from multiple open-source
reference implementations found on GitHub. It explains, per server, the exact
HTTP pipeline, the decoding/decryption steps, and where the app's current
extractors match or diverge from the proven reference code.

The reference source files are saved alongside this document in
`docs/extraction-references/`.

---

## TL;DR — what the references taught us

1. **`vidsrc.net` (VidSrcNetExtractor) — our new extractor needs a DECRYPTOR.**
   The simple `file: '...'` regex used by the older `lestresolver` Go package
   no longer works because **cloudnestra now obfuscates the final URL**
   (confirmed by webstreamr issue #490: "They changed something and are most
   likely obscuring the final URL"). The current, working extractor
   (`cool-dev-guy/vidsrc.ts`) resolves the final HLS URL through a **named
   decryption function** that is selected dynamically from the page's JS.
   There are **12 named decoders** and the page picks one by name at runtime.
   Our `VidSrcNetExtractor` must port these decoders to match.

2. **`vidsrc.net` uses TMDB ids, not IMDb ids** — `?tmdb={id}`. The lestresolver
   `?imdb=` approach is the older/alternate form. Both work, but TMDB avoids
   the extra round-trip. (Our extractor currently does TMDB→IMDb then `?imdb=` —
   this works but is an unnecessary extra hop.)

3. **`vidsrc.me` (VidSrcExtractor) — the app's approach is correct but
   missing the `decode_src` hex-XOR + redirect step.** The reference
   (`Ciarands/vidsrc-me-resolver`, `habitual69/VidSrc-Streamer`) shows the
   canonical flow: embed page → `div.server[data-hash]` list →
   `rcp.vidsrc.me/rcp/{hash}` → `div#hidden[data-h]` + `body[data-i]` →
   **hex-decode + XOR with seed (the imdb id)** → follow 302 redirect →
   then either `vidsrc.stream` (PRO, base64 HLS) or `multiembed.mov`
   (hunter-unpack). The app's VidSrcExtractor uses a `cloudorchestranova`
   RCP/PRORCP pipeline which is the *newer* vidsrc.me backend variant — both
   are valid; the references document the older `rcp.vidsrc.me` path.

4. **`vidsrc.to` (Vidplay/VidStream) — uses a decryption helper service.**
   The `crxssed7/vidsrc-extractor` shows: embed page → `data-id` →
   `/ajax/embed/episode/{id}/sources` (get Vidplay id) →
   `/ajax/embed/source/{vidplayId}` (get encrypted URL) → **decrypt via
   `9anime.eltik.net/fmovies-decrypt`** → parse `vidstream.pro` futoken →
   get raw URL → final `file:"..."` HLS. The decryption relies on an
   external helper API (`9anime.eltik.net`), which is a dependency to be
   aware of.

5. **SuperEmbed / multiembed.mov — uses the `hunter` packer decoder.**
   The obfuscated JS is a `eval(function(h,u,n,t,e,r){...}(...))` packer
   that must be unpacked with the `hunter()` algorithm (base conversion +
   char-code shift) before `file:"..."` appears.

6. **VidSrc PRO (vidsrc.stream) — base64 URL-safe HLS with a junk suffix.**
   The `file:"..."` value is base64url with a `/@#@/...==` junk suffix that
   must be stripped before decoding. A `pass_path` URL must be hit once
   (referer-gated) to "unlock" playback.

---

## Server 1: vidsrc.net (cloudnestra) — the critical one

**Reference:** `cool-dev-guy/vidsrc.ts` (108 stars, actively maintained)
→ files: `src/vidsrc.ts`, `src/helpers/decoder.ts`

### The correct pipeline (TMDB-based)

```
1. GET https://vidsrc.net/embed/{movie|tv}?tmdb={id}[&season={s}&episode={e}]
   → parse the <iframe src="..."> to discover BASEDOM (the RCP origin,
     e.g. https://whisperingauroras.com — it rotates).
   → parse ".serversList .server" elements → list of {name, data-hash}.

2. For each server: GET {BASEDOM}/rcp/{dataHash}
   → parse `src: '...'` (regex /src:\s*'([^']*)'/) → a path like /prorcp/{hash2}

3. GET {BASEDOM}/prorcp/{hash2}
   → the HTML references a <script src="/{x}.js?_=..."> tag.
   → fetch that JS file.
   → in the JS, find:  /{}\}window\[([^\"]+)\(\"([^\"]+)\"\)/
     group 1 = the decoder FUNCTION NAME (e.g. "Iry9MQXnLs")
     group 2 = the KEY/seed string
   → use decoder name to pick the matching function from the 12 decoders.
   → call decoder(key) → returns an HTML element id.
   → find <div id="{thatId}"> in the prorcp HTML → its .text() is ENCRYPTED DATA
   → call decoder(encryptedData, key) again → returns the final HLS URL.
```

### The 12 decoders (MUST be ported to Kotlin)

Each is a distinct obfuscation scheme. The page's JS names which one to use.
The decoders are in `docs/extraction-references/decoder.ts`. Summary of each:

| Function name | Algorithm |
|---|---|
| `LXVUMCoAHJ` | reverse → base64url → charCode-3 |
| `GuxKGDsA2T` | reverse → base64url → charCode-7 |
| `laM1dAi3vO` | reverse → base64url → charCode-5 |
| `nZlUnj2VSo` | substitution cipher (3-shift alphabet map) |
| `Iry9MQXnLs` | hex→char → XOR with key `"pWB9V)[*4I\`nJpp?ozyB~dbr9yt!_n4u"` → charCode-3 → base64 |
| `IGLImMhWrI` | reverse → ROT13 → reverse → base64 |
| `GTAxQyTyBx` | reverse → take every 2nd char → base64 |
| `C66jPHx8qu` | reverse → hex→char → XOR with key `"X9a(O;FMV2-7VO5x;Ao:dN1NoFs?j,"` |
| `MyL1IRSfHe` | reverse → charCode-1 → hex→char |
| `detdj7JHiK` | slice(10,-16) → base64 → XOR with key `"3SAY~#%Y(V%>5d/Yg\"$G[Lh1rK4a;7ok"` repeated |
| `SqmOaLsKHv7vWtli` | creates a Blob URL (browser-only; skip) |
| `bMGyx71TzQLfdonN` | 3-char chunk reverse (used to resolve the blob key name) |

### Why our current VidSrcNetExtractor will likely fail

Our extractor (created from the `lestresolver` Go package) uses:
```
GET vidsrc.net/embed → iframe src → RPC page → prorcp → file: '{hls_url}'
```
This is the **old** flow. The `file:` value is now **encrypted** — a bare
regex match returns an obfuscated blob, not a playable `.m3u8`. The webstreamr
maintainers confirmed this breakage in issue #490. **To make VidSrcNet
actually work, we must port the 12 decoders and the dynamic-name selection.**

### Action item
Rewrite `VidSrcNetExtractor.kt` to:
- Use `?tmdb={id}` (drop the TMDB→IMDb hop).
- Parse the iframe to discover BASEDOM (it rotates; do not hardcode cloudnestra).
- Parse `.serversList .server[data-hash]`.
- Fetch `{BASEDOM}/rcp/{hash}` → `src:'...'` → `{BASEDOM}/prorcp/{hash2}`.
- Fetch the page's `.js` file, regex the decoder name + key.
- Port all 12 decoders to Kotlin; dispatch by name.
- Double-decrypt (key→elementId, then elementText→HLS URL).

---

## Server 2: vidsrc.me (rcp.vidsrc.me backend) — the app's VidSrcExtractor

**References:** `Ciarands/vidsrc-me-resolver` (`vidsrc.py`, `vidsrcpro.py`,
`superembed.py`, `utils.py`), `habitual69/VidSrc-Streamer`

### Canonical flow (works on vidsrc.me / .in / .pm / .xyz / .net)

```
1. GET https://vidsrc.me/embed/{movie|tv}?{provider}={id}[&season=&episode=]
   provider = "imdb" if id starts with "tt" else "tmdb"
   → parse <div class="server" data-hash="...">  →  {name: hash} map

2. GET https://rcp.vidsrc.me/rcp/{hash}   (Referer: the embed url)
   → parse <div id="hidden" data-h="...">  (encoded hex)
   → parse <body data-i="...">  (the seed = imdb id "ttXXXXXXX")

3. decode_src(encoded, seed):
     bytes = hex_decode(encoded)
     for i: decoded[i] = bytes[i] XOR seed[i % len(seed)]
   → a URL like //vidsrc.stream/... or //multiembed.mov/...

4. GET that URL (allow_redirects=False, Referer: rcp url)
   → 302 → Location header
     - if "vidsrc.stream" in location → VidSrc PRO (see below)
     - if "multiembed.mov" in location → SuperEmbed (see below)
```

### VidSrc PRO (vidsrc.stream) decoder
```
GET the location URL (Referer: rcp url)
→ regex  file:"([^"]*)"   →  encoded_hls_url
→ strip junk:  re.sub(r"/@#@/[^=/]+==", "", encoded[2:])   (recursive)
→ base64url-decode  →  the real HLS .m3u8 URL
→ also hit  var pass_path = "(.*?)";   once (Referer: rcp url) to unlock
```

### SuperEmbed (multiembed.mov) decoder — the `hunter` packer
```
GET the multiembed URL (Referer: rcp url)
→ regex  eval(function(h,u,n,t,e,r){...}\((.*?)\)\)   →  the 6 packer args
→ parse args: [string, int, string, int, int, int]
→ hunter(h, u, n, t, e, r):  base-conversion unpacker (see utils.py)
→ from unpacked JS:  regex  file:"([^"]*)"   →  HLS URL(s)
→ also subtitle:"..."  →  [lang]url subtitle map
```

### The `hunter` unpacker (port if we add SuperEmbed)
```
hunter_def(d, e, f):  converts d from base-e to base-f using charset
  "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/"
hunter(h,u,n,t,e,r):
  walk h char by char, split on n[e], replace each n[j] with str(j),
  hunter_def each chunk with (e,10) - t  →  chr()  →  build output string
```

### App status
The app's `VidSrcExtractor.kt` uses the **newer** `cloudorchestranova.com`
RCP/PRORCP + `generate.php` token pipeline, which is a different (also valid)
vidsrc.me backend. It has a `bestUnverified` fallback. This appears to be
working for movies and TV in build 4. The reference above documents the
*classic* `rcp.vidsrc.me` path — useful as an alternate fallback if the
cloudorchestranova pipeline breaks.

---

## Server 3: vidsrc.to (Vidplay / VidStream) — not yet in the app

**Reference:** `crxssed7/vidsrc-extractor` (`vidsrc.py`)

### Flow (uses an external decryption helper)
```
1. GET https://vidsrc.to/embed/movie/{imdbOrTmdbId}
   → regex  data-id="([^"]*)"   →  data_id

2. GET https://vidsrc.to/ajax/embed/episode/{data_id}/sources
   → regex  "id":"([^"]*)".*"Vidplay   →  vidplay_id

3. GET https://vidsrc.to/ajax/embed/source/{vidplay_id}
   → regex  "url":"([^"]*)"   →  encrypted_provider_url

4. GET https://9anime.eltik.net/fmovies-decrypt?query={enc}&apikey=jerry
   → regex  "url":"([^"]*)"   →  provider_embed  (a vidstream.pro/e/{id}?{params})

5. parse provider_embed:  /e/([^?]*)(\?.*)   →  provider_query, params

6. GET https://vidstream.pro/futoken   →  futoken (a JS string)

7. POST https://9anime.eltik.net/rawvizcloud?query={q}&apikey=jerry
       body: query={provider_query}&futoken={futoken}
   → regex  "rawURL":"([^"]*)"   →  raw_url

8. GET {raw_url}{params}  (Referer: provider_embed)
   → unescape \/ → /
   → regex  "file":"([^"]*)"   →  the final HLS .m3u8 URL
```

**Note:** depends on `9anime.eltik.net` for decryption. If that helper is
down, this extractor fails. The app does NOT currently use vidsrc.to — it
could be added as another coverage source, but with the helper dependency.

---

## Server 4: SuperEmbed (multiembed.mov) — via vidsrc.me

Covered above under vidsrc.me. The `hunter` unpacker is the key algorithm.
Not currently a standalone extractor in the app (it's reached via the embed
WebView fallback). Could be ported to a pure-HTTP extractor using the
reference code.

---

## Server 5: VidStorm (vidstorm.ru) — the app's primary movie extractor

No public reference extractor was found for vidstorm.ru (it appears to be a
private/FilmCave backend). The app's `VidStormExtractor` uses an AES-256-CBC
encrypted API call to `vidstorm.ru/api/{movie|tv}/{encryptedId}` with a
hardcoded key. This is the build-4 fast path (movies in ~0.5s). The known
issue is that TV returned dead 403 hellstorm.lol mp4s — now fixed by
`verifyUrl()` rejecting 403/401/405. No reference to improve upon; the app's
implementation is the source of truth here.

---

## Server 6: VidLink (vidlink.pro) — app's VidLinkExtractor

Uses `enc-dec.app/api/enc-vidlink` to encrypt the TMDB id, then
`vidlink.pro/api/b/{movie|tv}/{encodedId}[/{s}/{e}]?multiLang=0` → JSON with
quality→url map. Pure HTTP, ~2 round-trips. No public reference found; the
app's implementation is the source of truth.

---

## Server 7: VixSrc (vixsrc.to) — app's VixSrcExtractor

3-step: `vixsrc.to/api/{movie|tv}/{tmdbId}[/{s}/{e}]` → embed HTML
(token/expires/url) → build `{playlist}?token={token}&expires={expires}&h=1`.
Pure HTTP. No public reference found; app implementation is source of truth.

---

## Server 8: NoTorrent (Stremio addon) — app's NoTorrentExtractor

`addon-osvh.onrender.com/stream/{movie|series}/{imdbId}[:{s}:{e}].json`.
Standard Stremio addon. The reference pattern (TMDB→IMDb via `external_ids`)
is correct and reused.

---

## Server 9: 2Embed (2embed.to) — app's TwoEmbedExtractor

**Reference:** the `parnexcodes/2embed-api` port mentioned in the app's
extractor header. Flow: embed page → find Vidcloud `data-id` → resolve
rabbitstream URL via ajax play endpoint → HLS sources. Does NOT implement
the Google reCAPTCHA token flow (falls through if challenged).

---

## Summary: what needs fixing for maximum coverage

| Server | Status | Action |
|---|---|---|
| VidStorm | ✅ working (movies fast) | keep; TV dead-source fix already in |
| VidSrc.me | ✅ working (movies+TV) | keep; cloudorchestranova pipeline OK |
| **VidSrc.net** | ⚠️ **new extractor will FAIL** | **port the 12 decoders + dynamic selection** |
| VidLink | ✅ working | keep |
| VixSrc | ✅ working | keep |
| NoTorrent | ✅ working | keep |
| Videasy | ✅ working | keep |
| LordFlix | ✅ working | keep |
| DahmerMovies | ✅ working | keep |
| TwoEmbed | ✅ working (no reCAPTCHA) | keep |
| vidsrc.to | ❌ not in app | could add (needs 9anime.eltik.net helper) |
| SuperEmbed | ⚠️ WebView-only | could port `hunter` unpacker to pure HTTP |

**The single most important fix:** `VidSrcNetExtractor` must be rewritten
with the decoder port from `vidsrc.ts`, otherwise it returns obfuscated blobs
instead of playable HLS URLs.

---

## Reference files (in `docs/extraction-references/`)

- `vidsrc.ts` — cool-dev-guy/vidsrc.ts main module (TMDB, .me/.net/.xyz)
- `decoder.ts` — the 12 named decryption functions (PORT THESE)
- `vidsrc_main.py` — Ciarands/vidsrc-me-resolver orchestrator
- `vidsrcpro.py` — VidSrc PRO base64 HLS decoder
- `superembed.py` — SuperEmbed hunter-unpack decoder
- `vidsrc_utils.py` — decode_src (hex XOR), hunter, base64url helpers
- `habitual69_extractor.py` — habitual69/VidSrc-Streamer (IMDb-based, full)
- `crxssed7_vidsrc.py` — crxssed7/vidsrc-extractor (vidsrc.to, Vidplay)
- `vidsrcscraper_server.js` — DivineChile/vidsrc-scraper (Playwright, all domains)

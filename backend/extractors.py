"""
Provider-specific stream extractors.

Each extractor returns either:
    {"url": "https://...m3u8", "type": "hls"|"mp4", "headers": {...}}
or None.

The flow models exactly how the official web embed loads its video:
  1. Hit the embed page with a desktop browser UA + matching Referer.
  2. Walk every iframe (cloudnestra/rcp -> cloudnestra/prorcp -> ...).
  3. Unpack any p,a,c,k,e,d JavaScript.
  4. Scan for direct .m3u8 / .mp4, filtering out ad URLs.
"""

from __future__ import annotations

import asyncio
import logging
import re
from typing import Optional
from urllib.parse import urljoin, urlparse, parse_qs

import httpx

from ad_filter import is_ad_url
from unpacker import unpack_all

logger = logging.getLogger(__name__)


class CaptchaRequired(Exception):
    """Raised when a provider gates the stream behind a CAPTCHA challenge
    that the end-user must solve. `challenge` carries the metadata the
    Android client needs to render the challenge UI."""

    def __init__(self, challenge: dict):
        self.challenge = challenge
        super().__init__(f"captcha required: {challenge.get('type')}")

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)

_M3U8_RE = re.compile(r"""(https?://[^\s'"<>()\\]+?\.m3u8[^\s'"<>()\\]*)""", re.IGNORECASE)
_MP4_RE = re.compile(r"""(https?://[^\s'"<>()\\]+?\.mp4[^\s'"<>()\\]*)""", re.IGNORECASE)
_MPD_RE = re.compile(r"""(https?://[^\s'"<>()\\]+?\.mpd[^\s'"<>()\\]*)""", re.IGNORECASE)
_MKV_RE = re.compile(r"""(https?://[^\s'"<>()\\]+?\.mkv[^\s'"<>()\\]*)""", re.IGNORECASE)
_WEBM_RE = re.compile(r"""(https?://[^\s'"<>()\\]+?\.webm[^\s'"<>()\\]*)""", re.IGNORECASE)
_FILE_RE = re.compile(r"""['"](?:file|src|source|stream|url)['"]?\s*[:=]\s*['"]([^'"]+\.(?:m3u8|mp4|mpd|mkv|webm)[^'"]*)['"]""", re.IGNORECASE)
_HLS_KEY_RE = re.compile(r"""(['"])(hls\d?|sources)\1\s*[:=]\s*\[?\s*\{[^}]*?['"](?:file|src|url)['"]?\s*:\s*['"]([^'"]+)['"]""", re.IGNORECASE)
_SUB_RE = re.compile(r"""['"](?:track|subtitle|caption)s?['"]?\s*[:=]\s*\[(.+?)\]""", re.IGNORECASE | re.DOTALL)
_SUB_ITEM_RE = re.compile(r"""['"](?:file|src|url)['"]?\s*[:=]\s*['"]([^'"]+\.(?:vtt|srt|ass)[^'"]*)['"][^}]*?(?:['"](?:label|lang|language|name)['"]?\s*[:=]\s*['"]([^'"]+)['"])?""", re.IGNORECASE)
_IFRAME_RE = re.compile(r"""<iframe[^>]+src=['"]([^'"]+)['"]""", re.IGNORECASE)
_PRORCP_RE = re.compile(r"""['"]/prorcp/([^'"]+)['"]""")
_DATA_HASH_RE = re.compile(r"""data-hash\s*=\s*['"]([^'"]+)['"]""", re.IGNORECASE)
_TURNSTILE_RE = re.compile(
    r"""class\s*=\s*['"][^'"]*\bcf-turnstile\b[^'"]*['"][^>]*data-sitekey\s*=\s*['"]([^'"]+)['"]""",
    re.IGNORECASE,
)
_TURNSTILE_RE_ALT = re.compile(
    r"""data-sitekey\s*=\s*['"]([^'"]+)['"][^>]*class\s*=\s*['"][^'"]*\bcf-turnstile\b""",
    re.IGNORECASE,
)


def detect_captcha(html: str, page_url: str) -> Optional[dict]:
    """Detect a Cloudflare Turnstile (or hCaptcha) challenge in the page HTML.

    Returns a dict describing the challenge or None.
    """
    m = _TURNSTILE_RE.search(html) or _TURNSTILE_RE_ALT.search(html)
    if m:
        return {
            "type": "cloudflare_turnstile",
            "siteKey": m.group(1),
            "challengeUrl": page_url,
            # Where the page will POST the solved token (relative path on same origin)
            "verifyPath": "/rcp_verify",
            # Token will be appended as ?_rcp=... when the page reloads
            "tokenParam": "_rcp",
        }
    return None


def _headers(referer: str) -> dict:
    parsed = urlparse(referer)
    origin = f"{parsed.scheme}://{parsed.netloc}"
    return {
        "User-Agent": UA,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9",
        "Referer": referer,
        "Origin": origin,
        "Sec-Fetch-Dest": "iframe",
        "Sec-Fetch-Mode": "navigate",
        "Sec-Fetch-Site": "cross-site",
    }


def _scan_for_stream(text: str) -> Optional[str]:
    """Run every regex over (text + unpacked variants) and return first hit."""
    expanded = unpack_all(text)
    for rx in (_FILE_RE, _HLS_KEY_RE, _M3U8_RE, _MP4_RE, _MPD_RE, _MKV_RE, _WEBM_RE):
        for m in rx.finditer(expanded):
            url = m.group(m.lastindex).replace("\\/", "/").strip()
            if not url.startswith("http"):
                continue
            if is_ad_url(url):
                continue
            low = url.lower()
            if not any(ext in low for ext in (".m3u8", ".mp4", ".mpd", ".mkv", ".webm")):
                continue
            return url
    return None


def _scan_for_subtitles(text: str) -> list[dict]:
    """Extract subtitle entries (.vtt / .srt / .ass) from JS player config."""
    out: list[dict] = []
    expanded = unpack_all(text)
    seen: set[str] = set()
    for block in _SUB_RE.finditer(expanded):
        for it in _SUB_ITEM_RE.finditer(block.group(1)):
            url = it.group(1).replace("\\/", "/").strip()
            if not url.startswith("http") or url in seen or is_ad_url(url):
                continue
            seen.add(url)
            out.append({"url": url, "label": it.group(2) or "Unknown", "language": (it.group(2) or "und").lower()[:5]})
        if len(out) >= 20:
            break
    return out


# ---------------------------------------------------------------------------
# Stream validation - HEAD-check the URL to confirm it's actually playable
# ---------------------------------------------------------------------------
_VIDEO_MIME_HINTS = (
    "mpegurl", "x-mpegurl", "vnd.apple", "octet-stream",
    "video/", "application/dash", "application/x-mpegurl",
    "application/vnd.apple.mpegurl", "application/octet-stream",
)


async def validate_stream(client: httpx.AsyncClient, payload: dict) -> bool:
    """HEAD/GET the stream URL with provider headers; reject ads, 404s and HTML pages."""
    url = payload["url"]
    headers = payload.get("headers", {})
    if is_ad_url(url):
        return False
    # Try HEAD first
    try:
        r = await client.head(url, headers=headers, timeout=8.0, follow_redirects=True)
        status = r.status_code
        ct = (r.headers.get("content-type") or "").lower()
        if status < 400:
            if any(h in ct for h in _VIDEO_MIME_HINTS) and "html" not in ct:
                return True
            if not ct:  # some CDNs return no Content-Type on HEAD; verify with GET
                pass
            elif "html" in ct or "text/" in ct and "plain" not in ct:
                # text/plain is allowed (some HLS playlists)
                return False
        elif status == 405:  # HEAD not allowed, fall through to GET
            pass
        else:
            return False
    except Exception:
        pass

    # Fallback: range GET (first 1 KB) to check content
    try:
        h = {**headers, "Range": "bytes=0-1023"}
        r = await client.get(url, headers=h, timeout=8.0, follow_redirects=True)
        if r.status_code >= 400:
            return False
        ct = (r.headers.get("content-type") or "").lower()
        if "html" in ct:
            return False
        # HLS playlists start with #EXTM3U
        body = r.content[:64]
        if body.startswith(b"#EXTM3U"):
            return True
        # DASH manifests start with <?xml or <MPD
        if body.lstrip().startswith(b"<?xml") or b"<MPD" in body:
            return True
        if any(h in ct for h in _VIDEO_MIME_HINTS):
            return True
        # No content type but binary blob with reasonable size
        if not ct and len(body) > 32:
            return True
    except Exception:
        return False
    return False


def _mime_for(url: str) -> str:
    low = url.lower()
    if ".m3u8" in low:
        return "application/x-mpegurl"
    if ".mpd" in low:
        return "application/dash+xml"
    if ".webm" in low:
        return "video/webm"
    if ".mkv" in low:
        return "video/x-matroska"
    return "video/mp4"


def _stream_payload(url: str, referer: str, subtitles: Optional[list[dict]] = None) -> dict:
    parsed = urlparse(referer)
    origin = f"{parsed.scheme}://{parsed.netloc}"
    low = url.lower()
    if ".m3u8" in low:
        stype = "hls"
    elif ".mpd" in low:
        stype = "dash"
    elif ".webm" in low:
        stype = "webm"
    elif ".mkv" in low:
        stype = "mkv"
    else:
        stype = "mp4"
    return {
        "url": url,
        "type": stype,
        "mime_type": _mime_for(url),
        "headers": {
            "Referer": referer,
            "Origin": origin,
            "User-Agent": UA,
        },
        "subtitles": subtitles or [],
    }


async def _get(client: httpx.AsyncClient, url: str, referer: str, timeout: float = 12.0) -> Optional[httpx.Response]:
    try:
        r = await client.get(url, headers=_headers(referer), timeout=timeout, follow_redirects=True)
        if 200 <= r.status_code < 400:
            return r
        logger.debug("GET %s -> %s", url, r.status_code)
    except Exception as e:
        logger.debug("GET %s failed: %s", url, e)
    return None


async def _walk_iframes(
    client: httpx.AsyncClient,
    url: str,
    referer: str,
    depth: int = 0,
    max_depth: int = 3,
) -> Optional[dict]:
    """Recursively load `url`, scan for streams, then descend into iframes."""
    if depth > max_depth:
        return None
    r = await _get(client, url, referer)
    if not r:
        return None
    html = r.text
    final_url = str(r.url)

    found = _scan_for_stream(html)
    if found:
        subs = _scan_for_subtitles(html)
        return _stream_payload(found, final_url, subs)

    # follow iframes
    seen = set()
    for m in _IFRAME_RE.finditer(html):
        src = m.group(1).strip().replace("&amp;", "&")
        if not src or src.startswith("javascript:") or src.startswith("about:"):
            continue
        if is_ad_url(src):
            continue
        absolute = urljoin(final_url, src)
        if absolute in seen:
            continue
        seen.add(absolute)
        nested = await _walk_iframes(client, absolute, final_url, depth + 1, max_depth)
        if nested:
            return nested
    return None


# ---------------------------------------------------------------------------
# Provider: VidSrc family (vidsrc.xyz, vidsrc.net, vidsrc.to, vidsrc.pro, vidsrc.cc)
# Flow: embed -> cloudnestra/rcp -> cloudnestra/prorcp -> m3u8
# ---------------------------------------------------------------------------
async def extract_vidsrc(
    client: httpx.AsyncClient, embed_url: str, referer: str
) -> Optional[dict]:
    r = await _get(client, embed_url, referer)
    if not r:
        return None
    html, final = r.text, str(r.url)

    # 1. Look for the rcp iframe (cloudnestra)
    iframe_match = _IFRAME_RE.search(html)
    rcp_url: Optional[str] = None
    if iframe_match:
        rcp_url = urljoin(final, iframe_match.group(1).strip())

    if not rcp_url:
        # vidsrc.cc / vidsrc.pro sometimes expose a data-hash attribute that
        # has to be POSTed to /ajax/embed/source
        hash_match = _DATA_HASH_RE.search(html)
        if hash_match:
            data_hash = hash_match.group(1)
            api_url = f"{urlparse(final).scheme}://{urlparse(final).netloc}/ajax/embed/source/{data_hash}"
            ar = await _get(client, api_url, final)
            if ar:
                m = _M3U8_RE.search(ar.text)
                if m and not is_ad_url(m.group(1)):
                    return _stream_payload(m.group(1), final)
        return await _walk_iframes(client, embed_url, referer)

    rcp = await _get(client, rcp_url, final)
    if not rcp:
        return None

    # 2. /prorcp/<token> link inside rcp page
    prorcp_match = _PRORCP_RE.search(rcp.text)
    if prorcp_match:
        token = prorcp_match.group(1)
        parsed = urlparse(str(rcp.url))
        prorcp_url = f"{parsed.scheme}://{parsed.netloc}/prorcp/{token}"
        pro = await _get(client, prorcp_url, str(rcp.url))
        if pro:
            found = _scan_for_stream(pro.text)
            if found:
                subs = _scan_for_subtitles(pro.text)
                return _stream_payload(found, str(pro.url), subs)
            # No stream found - check if a CAPTCHA is gating the page.
            challenge = detect_captcha(pro.text, str(pro.url))
            if challenge:
                raise CaptchaRequired(challenge)

    # Fallback: scan rcp itself (or detect captcha on it)
    found = _scan_for_stream(rcp.text)
    if found:
        subs = _scan_for_subtitles(rcp.text)
        return _stream_payload(found, str(rcp.url), subs)
    challenge = detect_captcha(rcp.text, str(rcp.url))
    if challenge:
        raise CaptchaRequired(challenge)
    return None


# ---------------------------------------------------------------------------
# Provider: VidLink.pro
# Has /api/b/movie/{id} returning JSON with sources
# ---------------------------------------------------------------------------
async def extract_vidlink(
    client: httpx.AsyncClient,
    embed_url: str,
    referer: str,
    tmdb_id: int,
    content_type: str,
    season: int,
    episode: int,
) -> Optional[dict]:
    # Hit known JSON endpoints first
    json_candidates = [
        f"https://vidlink.pro/api/b/movie/{tmdb_id}" if content_type == "movie"
        else f"https://vidlink.pro/api/b/tv/{tmdb_id}/{season}/{episode}",
        f"https://vidlink.pro/api/source/movie/{tmdb_id}" if content_type == "movie"
        else f"https://vidlink.pro/api/source/tv/{tmdb_id}/{season}/{episode}",
    ]
    for api in json_candidates:
        r = await _get(client, api, "https://vidlink.pro/")
        if not r:
            continue
        try:
            data = r.json()
        except Exception:
            data = None
        if isinstance(data, dict):
            for key in ("stream", "url", "file", "source"):
                v = data.get(key)
                if isinstance(v, str) and ("m3u8" in v or "mp4" in v) and not is_ad_url(v):
                    return _stream_payload(v, "https://vidlink.pro/")
            srcs = data.get("sources") or data.get("streams")
            if isinstance(srcs, list):
                for s in srcs:
                    if not isinstance(s, dict):
                        continue
                    v = s.get("file") or s.get("url") or s.get("src")
                    if isinstance(v, str) and ("m3u8" in v or "mp4" in v) and not is_ad_url(v):
                        return _stream_payload(v, "https://vidlink.pro/")
    # Fallback to iframe walking
    return await _walk_iframes(client, embed_url, referer)


# ---------------------------------------------------------------------------
# Provider: Embed.su
# Flow: /embed/movie/{id} -> getUrl()/getSources() inside the JS -> JSON
# ---------------------------------------------------------------------------
_EMBEDSU_K_RE = re.compile(r"""['"]k['"]\s*:\s*['"]([^'"]+)['"]""")
_EMBEDSU_HASH_RE = re.compile(r"""(?:atob\(['"]([^'"]+)['"]\)|hash['"]?\s*[:=]\s*['"]([^'"]+)['"])""")


async def extract_embedsu(
    client: httpx.AsyncClient,
    embed_url: str,
    referer: str,
    tmdb_id: int,
    content_type: str,
    season: int,
    episode: int,
) -> Optional[dict]:
    r = await _get(client, embed_url, referer)
    if not r:
        return None
    text = r.text
    # Try direct scan first
    found = _scan_for_stream(text)
    if found:
        return _stream_payload(found, str(r.url))

    # Try the JSON `/api/e/` endpoint embed.su uses
    k_match = _EMBEDSU_K_RE.search(text)
    if k_match:
        api = f"https://embed.su/api/e/{k_match.group(1)}"
        ar = await _get(client, api, str(r.url))
        if ar:
            try:
                data = ar.json()
            except Exception:
                data = None
            if isinstance(data, dict):
                src = data.get("source") or data.get("url") or data.get("file")
                if isinstance(src, str) and ("m3u8" in src or "mp4" in src):
                    return _stream_payload(src, str(r.url))
                streams = data.get("streams") or []
                if isinstance(streams, list):
                    for s in streams:
                        v = (s or {}).get("url") or (s or {}).get("file")
                        if v and ("m3u8" in v or "mp4" in v):
                            return _stream_payload(v, str(r.url))
    # Last resort: iframe walking
    return await _walk_iframes(client, embed_url, referer)


# ---------------------------------------------------------------------------
# Provider: AutoEmbed.cc
# Flow: /embed/movie/{id} -> iframe to player -> packed JS -> m3u8
# ---------------------------------------------------------------------------
async def extract_autoembed(
    client: httpx.AsyncClient,
    embed_url: str,
    referer: str,
    tmdb_id: int,
    content_type: str,
    season: int,
    episode: int,
) -> Optional[dict]:
    # AutoEmbed exposes a v2 JSON endpoint
    api = (
        f"https://autoembed.cc/api/v2/movie/{tmdb_id}"
        if content_type == "movie"
        else f"https://autoembed.cc/api/v2/tv/{tmdb_id}/{season}/{episode}"
    )
    r = await _get(client, api, "https://autoembed.cc/")
    if r:
        try:
            data = r.json()
        except Exception:
            data = None
        if isinstance(data, dict):
            srcs = data.get("sources") or data.get("streams") or []
            if isinstance(srcs, list):
                for s in srcs:
                    if not isinstance(s, dict):
                        continue
                    v = s.get("file") or s.get("url")
                    if v and ("m3u8" in v or "mp4" in v) and not is_ad_url(v):
                        return _stream_payload(v, "https://autoembed.cc/")
    return await _walk_iframes(client, embed_url, referer)


# ---------------------------------------------------------------------------
# Provider: 2Embed.cc
# Flow: /embed/<id> -> iframe to streamcdn / swish.to -> packed JS -> m3u8
# ---------------------------------------------------------------------------
async def extract_2embed(
    client: httpx.AsyncClient, embed_url: str, referer: str
) -> Optional[dict]:
    return await _walk_iframes(client, embed_url, referer, max_depth=4)


# ---------------------------------------------------------------------------
# Provider: Videasy
# Flow: /movie/{id} -> player loads /api/source -> JSON with sources[]
# ---------------------------------------------------------------------------
async def extract_videasy(
    client: httpx.AsyncClient,
    embed_url: str,
    referer: str,
    tmdb_id: int,
    content_type: str,
    season: int,
    episode: int,
) -> Optional[dict]:
    api = (
        f"https://player.videasy.net/api/source/movie/{tmdb_id}"
        if content_type == "movie"
        else f"https://player.videasy.net/api/source/tv/{tmdb_id}/{season}/{episode}"
    )
    r = await _get(client, api, "https://videasy.net/")
    if r:
        try:
            data = r.json()
        except Exception:
            data = None
        if isinstance(data, dict):
            srcs = data.get("sources") or []
            if isinstance(srcs, list):
                for s in srcs:
                    if not isinstance(s, dict):
                        continue
                    v = s.get("file") or s.get("url")
                    if v and ("m3u8" in v or "mp4" in v) and not is_ad_url(v):
                        return _stream_payload(v, "https://videasy.net/")
    return await _walk_iframes(client, embed_url, referer)


# ---------------------------------------------------------------------------
# Provider: MultiEmbed.mov
# Flow: /directstream.php?video_id=... returns JSON
# ---------------------------------------------------------------------------
async def extract_multiembed(
    client: httpx.AsyncClient,
    embed_url: str,
    referer: str,
    tmdb_id: int,
    content_type: str,
    season: int,
    episode: int,
) -> Optional[dict]:
    base = "https://multiembed.mov/directstream.php"
    if content_type == "movie":
        api = f"{base}?video_id={tmdb_id}&tmdb=1"
    else:
        api = f"{base}?video_id={tmdb_id}&tmdb=1&s={season}&e={episode}"
    r = await _get(client, api, "https://multiembed.mov/")
    if r:
        found = _scan_for_stream(r.text)
        if found:
            return _stream_payload(found, "https://multiembed.mov/")
    return await _walk_iframes(client, embed_url, referer)


# ---------------------------------------------------------------------------
# Provider: SmashyStream
# ---------------------------------------------------------------------------
async def extract_smashy(
    client: httpx.AsyncClient,
    embed_url: str,
    referer: str,
    tmdb_id: int,
    content_type: str,
    season: int,
    episode: int,
) -> Optional[dict]:
    api_candidates = [
        f"https://embed.smashystream.com/playere.php?tmdb={tmdb_id}"
        if content_type == "movie"
        else f"https://embed.smashystream.com/playere.php?tmdb={tmdb_id}&season={season}&episode={episode}",
    ]
    for api in api_candidates:
        r = await _get(client, api, "https://smashystream.com/")
        if not r:
            continue
        found = _scan_for_stream(r.text)
        if found:
            return _stream_payload(found, "https://smashystream.com/")
    return await _walk_iframes(client, embed_url, referer)


# ---------------------------------------------------------------------------
# Provider: RiveStream
# ---------------------------------------------------------------------------
async def extract_rivestream(
    client: httpx.AsyncClient,
    embed_url: str,
    referer: str,
    tmdb_id: int,
    content_type: str,
    season: int,
    episode: int,
) -> Optional[dict]:
    api = (
        f"https://rivestream.live/api/backendfetch?requestID=movieVideoProvider&id={tmdb_id}"
        if content_type == "movie"
        else f"https://rivestream.live/api/backendfetch?requestID=tvVideoProvider&id={tmdb_id}&season={season}&episode={episode}"
    )
    r = await _get(client, api, "https://rivestream.live/")
    if r:
        try:
            data = r.json()
        except Exception:
            data = None
        if isinstance(data, dict):
            sources = data.get("data", {}).get("sources") if isinstance(data.get("data"), dict) else data.get("sources")
            if isinstance(sources, list):
                for s in sources:
                    if not isinstance(s, dict):
                        continue
                    v = s.get("url") or s.get("file") or s.get("source")
                    if v and ("m3u8" in v or "mp4" in v) and not is_ad_url(v):
                        return _stream_payload(v, "https://rivestream.live/")
    return await _walk_iframes(client, embed_url, referer)


# ---------------------------------------------------------------------------
# Dispatch table
# ---------------------------------------------------------------------------
GENERIC_PROVIDERS = {"primewire", "showbox", "filmku", "nontongo", "vidplay", "warezcdn", "moviesapi"}


async def extract_for_server(
    client: httpx.AsyncClient,
    server: dict,
    embed_url: str,
    tmdb_id: int,
    content_type: str,
    season: int,
    episode: int,
) -> Optional[dict]:
    referer = server.get("referer") or server["base"] + "/"
    sid = server["id"]
    primary: Optional[dict] = None

    try:
        if sid in {"vidsrc_to", "vidsrc_xyz", "vidsrc_net", "vidsrc_pro", "vidsrc_cc"}:
            primary = await extract_vidsrc(client, embed_url, referer)
        elif sid == "vidlink":
            primary = await extract_vidlink(client, embed_url, referer, tmdb_id, content_type, season, episode)
        elif sid == "embed_su":
            primary = await extract_embedsu(client, embed_url, referer, tmdb_id, content_type, season, episode)
        elif sid == "autoembed":
            primary = await extract_autoembed(client, embed_url, referer, tmdb_id, content_type, season, episode)
        elif sid == "twoembed":
            primary = await extract_2embed(client, embed_url, referer)
        elif sid == "videasy":
            primary = await extract_videasy(client, embed_url, referer, tmdb_id, content_type, season, episode)
        elif sid == "multiembed":
            primary = await extract_multiembed(client, embed_url, referer, tmdb_id, content_type, season, episode)
        elif sid == "smashy":
            primary = await extract_smashy(client, embed_url, referer, tmdb_id, content_type, season, episode)
        elif sid == "rivestream":
            primary = await extract_rivestream(client, embed_url, referer, tmdb_id, content_type, season, episode)
        elif sid in GENERIC_PROVIDERS:
            primary = await _walk_iframes(client, embed_url, referer, max_depth=3)
    except CaptchaRequired:
        raise  # propagate so the resolver can hand it to the app
    except asyncio.TimeoutError:
        primary = None
    except Exception as e:
        logger.debug("extractor %s failed: %s", sid, e)
        primary = None

    if primary:
        return primary

    # Fallback: iframe walking (catches providers we don't have a dedicated extractor for)
    fallback = await _walk_iframes(client, embed_url, referer)
    if fallback:
        return fallback

    # NOTE: a previous version of this code attempted a headless-browser
    # fallback (Playwright + Chromium). In practice every major embed
    # provider in 2026 either (a) detects headless mode and refuses to
    # serve video, or (b) gates the stream behind a Cloudflare Turnstile
    # CAPTCHA. Pursuing that path requires a paid CAPTCHA solver and
    # rotating residential proxies, which is out of scope here.
    return None


# ---------------------------------------------------------------------------
# CAPTCHA-completion helper
# ---------------------------------------------------------------------------
async def complete_with_captcha_token(
    client: httpx.AsyncClient,
    challenge_url: str,
    rcp_token: str,
    referer: str,
) -> Optional[dict]:
    """Given the challenge URL (the /prorcp page) and a user-solved `_rcp`
    token, re-fetch the page and extract the actual stream.

    Returns a stream-payload dict or None on failure.
    """
    sep = "&" if "?" in challenge_url else "?"
    unlocked_url = f"{challenge_url}{sep}_rcp={rcp_token}"
    r = await _get(client, unlocked_url, referer or challenge_url, timeout=15.0)
    if not r:
        return None
    text = r.text
    found = _scan_for_stream(text)
    if found:
        subs = _scan_for_subtitles(text)
        return _stream_payload(found, str(r.url), subs)

    # The unlocked page often contains an iframe that holds the real player
    return await _walk_iframes(client, unlocked_url, referer or challenge_url)


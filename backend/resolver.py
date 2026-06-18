"""
Stream resolver.

STRICT direct-stream extraction. Never returns embed URLs, never falls back
to WebView, never returns a URL that hasn't been HEAD-checked for a real
video MIME type. If no provider yields a playable .m3u8/.mp4/.mpd/.mkv/.webm,
the response is `success: false` and the Android app shows an error.
"""

from __future__ import annotations

import asyncio
import logging
import time
from typing import Optional

import httpx
from cachetools import TTLCache

from extractors import UA, CaptchaRequired, complete_with_captcha_token, extract_for_server, validate_stream
from servers_config import SERVERS, get_server

logger = logging.getLogger(__name__)

# Stream cache (30 min) + server-health cache (5 min) + captcha-session cache (10 min)
_resolve_cache: TTLCache = TTLCache(maxsize=2048, ttl=60 * 30)
_health_cache: TTLCache = TTLCache(maxsize=256, ttl=60 * 5)
_captcha_sessions: TTLCache = TTLCache(maxsize=1024, ttl=60 * 10)

# Adaptive per-server score (success +1, failure -1; clamped to [-50, +50])
_score: dict[str, int] = {s["id"]: 0 for s in SERVERS}

# Per-server extraction budget
PER_SERVER_TIMEOUT = 14.0
# Whole-wave timeout
WAVE_TIMEOUT = 22.0


# ---------------------------------------------------------------------------
# Health probing
# ---------------------------------------------------------------------------
async def probe_server(client: httpx.AsyncClient, server: dict) -> dict:
    cached = _health_cache.get(server["id"])
    if cached is not None:
        return cached

    started = time.perf_counter()
    alive = False
    status = 0
    try:
        r = await client.get(
            server["base"],
            headers={"User-Agent": UA, "Referer": server.get("referer", server["base"])},
            timeout=6.0,
            follow_redirects=True,
        )
        status = r.status_code
        alive = r.status_code < 500
    except Exception:
        pass

    result = {
        "id": server["id"],
        "name": server["name"],
        "base": server["base"],
        "priority": server["priority"],
        "alive": alive,
        "status_code": status,
        "latency_ms": int((time.perf_counter() - started) * 1000),
    }
    _health_cache[server["id"]] = result
    return result


async def probe_all() -> list[dict]:
    async with httpx.AsyncClient() as client:
        return await asyncio.gather(*[probe_server(client, s) for s in SERVERS])


# ---------------------------------------------------------------------------
# Per-server direct extraction (validated)
# ---------------------------------------------------------------------------
async def _resolve_one(
    client: httpx.AsyncClient,
    server: dict,
    tmdb_id: int,
    content_type: str,
    season: int,
    episode: int,
) -> Optional[dict]:
    try:
        stream = await asyncio.wait_for(
            extract_for_server(client, server, _embed(server, tmdb_id, content_type, season, episode),
                               tmdb_id, content_type, season, episode),
            timeout=PER_SERVER_TIMEOUT,
        )
    except CaptchaRequired as cr:
        # Bubble the challenge up unchanged - find_best_stream decides what to do
        return {
            "captcha_required": True,
            "server_id": server["id"],
            "server_name": server["name"],
            "challenge": cr.challenge,
        }
    except asyncio.TimeoutError:
        return None
    except Exception as e:
        logger.debug("extract %s failed: %s", server["id"], e)
        return None

    if not stream:
        return None

    # Strict validation: HEAD/GET the URL and verify a real video mime type.
    try:
        ok = await asyncio.wait_for(validate_stream(client, stream), timeout=8.0)
    except asyncio.TimeoutError:
        ok = False
    if not ok:
        logger.info("rejected non-playable stream from %s: %s", server["id"], stream.get("url"))
        _score[server["id"]] = max(-50, _score[server["id"]] - 1)
        return None

    _score[server["id"]] = min(50, _score[server["id"]] + 1)
    return {
        "success": True,
        "server_id": server["id"],
        "server_name": server["name"],
        "stream": stream,
        "is_direct": True,
    }


def _embed(server: dict, tmdb_id: int, content_type: str, season: int, episode: int) -> str:
    """Build embed URL (used internally by extractors, never returned to the client)."""
    from servers_config import build_embed_url
    return build_embed_url(server, tmdb_id, content_type, season, episode)


def _ranked_servers() -> list[dict]:
    """Sort by priority (lower first), then by adaptive score (higher first)."""
    return sorted(SERVERS, key=lambda s: (s["priority"], -_score.get(s["id"], 0)))


# ---------------------------------------------------------------------------
# CAPTCHA session storage + completion
# ---------------------------------------------------------------------------
def _store_captcha_session(
    server_id: str,
    challenge: dict,
    tmdb_id: int,
    content_type: str,
    season: int,
    episode: int,
) -> str:
    import uuid as _uuid

    session_id = _uuid.uuid4().hex
    _captcha_sessions[session_id] = {
        "server_id": server_id,
        "challenge_url": challenge["challengeUrl"],
        "site_key": challenge["siteKey"],
        "tmdb_id": tmdb_id,
        "content_type": content_type,
        "season": season,
        "episode": episode,
    }
    return session_id


async def complete_captcha(session_id: str, rcp_token: str) -> dict:
    """Called by /api/captcha/submit. Re-fetches the provider page with the
    user-supplied token and returns the final direct stream."""
    sess = _captcha_sessions.get(session_id)
    if not sess:
        return {"success": False, "error": "Captcha session expired. Please try again."}
    if not rcp_token or len(rcp_token) < 8:
        return {"success": False, "error": "Invalid captcha token."}

    server = get_server(sess["server_id"])
    referer = server.get("referer", server["base"] + "/") if server else sess["challenge_url"]

    timeout = httpx.Timeout(15.0, connect=8.0)
    async with httpx.AsyncClient(timeout=timeout, follow_redirects=True) as client:
        try:
            stream = await asyncio.wait_for(
                complete_with_captcha_token(client, sess["challenge_url"], rcp_token, referer),
                timeout=PER_SERVER_TIMEOUT,
            )
        except asyncio.TimeoutError:
            return {"success": False, "error": "Provider timed out after solving CAPTCHA."}
        except Exception as e:
            logger.warning("captcha completion failed: %s", e)
            return {"success": False, "error": "Failed to complete extraction after CAPTCHA."}

        if not stream:
            return {"success": False, "error": "No stream URL found on the unlocked page."}

        try:
            ok = await asyncio.wait_for(validate_stream(client, stream), timeout=8.0)
        except asyncio.TimeoutError:
            ok = False
        if not ok:
            return {"success": False, "error": "Stream URL returned by provider is not playable."}

    # Success - cache so we don't ask again for the same title
    result = {
        "success": True,
        "server_id": sess["server_id"],
        "server_name": (server["name"] if server else sess["server_id"]),
        "stream": stream,
        "is_direct": True,
    }
    cache_key = (sess["tmdb_id"], sess["content_type"], sess["season"], sess["episode"])
    _resolve_cache[cache_key] = result
    # Single-use session
    _captcha_sessions.pop(session_id, None)
    return result


# ---------------------------------------------------------------------------
# Top-level resolution
# ---------------------------------------------------------------------------
async def find_best_stream(
    tmdb_id: int,
    content_type: str = "movie",
    season: int = 1,
    episode: int = 1,
) -> dict:
    cache_key = (tmdb_id, content_type, season, episode)
    cached = _resolve_cache.get(cache_key)
    if cached:
        return {**cached, "cached": True}

    ordered = _ranked_servers()
    tried: list[str] = []
    captcha_challenges: list[dict] = []

    timeout = httpx.Timeout(15.0, connect=8.0)
    async with httpx.AsyncClient(timeout=timeout, follow_redirects=True) as client:
        # First pass: probe alive-ness (5-min cached)
        statuses = await asyncio.gather(*[probe_server(client, s) for s in ordered])
        alive_ids = {s["id"] for s in statuses if s["alive"]}

        # Second pass: extract in priority waves
        for priority in sorted({s["priority"] for s in ordered}):
            wave = [s for s in ordered if s["priority"] == priority and s["id"] in alive_ids]
            if not wave:
                continue
            tried.extend(s["id"] for s in wave)

            tasks = [
                asyncio.create_task(_resolve_one(client, s, tmdb_id, content_type, season, episode))
                for s in wave
            ]
            try:
                for fut in asyncio.as_completed(tasks, timeout=WAVE_TIMEOUT):
                    res = await fut
                    if not res:
                        continue
                    if res.get("is_direct"):
                        for t in tasks:
                            if not t.done():
                                t.cancel()
                        _resolve_cache[cache_key] = res
                        return {**res, "cached": False, "tried": tried}
                    if res.get("captcha_required"):
                        captcha_challenges.append(res)
            except asyncio.TimeoutError:
                pass
            finally:
                for t in tasks:
                    if not t.done():
                        t.cancel()

    # No direct stream worked. If we hit any CAPTCHA gate, hand the FIRST
    # one back to the app so the user can solve it.
    if captcha_challenges:
        challenge = captcha_challenges[0]
        session_id = _store_captcha_session(
            challenge["server_id"],
            challenge["challenge"],
            tmdb_id,
            content_type,
            season,
            episode,
        )
        return {
            "success": False,
            "needs_captcha": True,
            "captcha": {
                **challenge["challenge"],
                "sessionId": session_id,
                "serverId": challenge["server_id"],
                "serverName": challenge["server_name"],
                "submitUrl": "/api/captcha/submit",
            },
            "tried": tried,
        }

    return {
        "success": False,
        "error": (
            "No provider returned a playable direct stream. "
            "Most modern embed sites now sit behind Cloudflare Turnstile or "
            "detect headless extraction; this title may be temporarily unavailable."
        ),
        "tried": tried,
    }


async def resolve_specific(
    server_id: str,
    tmdb_id: int,
    content_type: str = "movie",
    season: int = 1,
    episode: int = 1,
) -> dict:
    server = get_server(server_id)
    if not server:
        return {"success": False, "error": f"Unknown server '{server_id}'"}
    timeout = httpx.Timeout(15.0, connect=8.0)
    async with httpx.AsyncClient(timeout=timeout, follow_redirects=True) as client:
        direct = await _resolve_one(client, server, tmdb_id, content_type, season, episode)
    if direct:
        return direct
    return {
        "success": False,
        "error": f"Could not extract a direct stream from '{server_id}'.",
    }


# ---------------------------------------------------------------------------
# Adaptive ranking helpers (optional - the app can post playback results)
# ---------------------------------------------------------------------------
def report_result(server_id: str, success: bool) -> dict:
    if server_id not in _score:
        return {"ok": False, "error": "unknown server"}
    delta = 1 if success else -1
    _score[server_id] = max(-50, min(50, _score[server_id] + delta))
    return {"ok": True, "server_id": server_id, "new_score": _score[server_id]}


def get_scores() -> dict[str, int]:
    return dict(_score)


def clear_caches() -> dict:
    n1, n2 = len(_resolve_cache), len(_health_cache)
    _resolve_cache.clear()
    _health_cache.clear()
    return {"cleared": True, "resolve_cache_dropped": n1, "health_cache_dropped": n2}

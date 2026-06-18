"""
I'm Done - Streaming Backend API (v2)
=====================================

Direct-stream resolver for the Android client
(`ashtonhardy555-stack/I-m-done`).

Response shape matches the existing Retrofit interface in
`StreamingBackendClient.kt` so the only Android-side change required is the
backend base URL in `ApiClient.kt`.
"""

from __future__ import annotations

import logging
import os
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, List, Optional

from dotenv import load_dotenv
from fastapi import APIRouter, FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from motor.motor_asyncio import AsyncIOMotorClient
from pydantic import BaseModel, Field

ROOT_DIR = Path(__file__).parent
sys.path.insert(0, str(ROOT_DIR))
load_dotenv(ROOT_DIR / ".env")

from resolver import (  # noqa: E402
    clear_caches,
    complete_captcha,
    find_best_stream,
    get_scores,
    probe_all,
    report_result,
    resolve_specific,
)
from servers_config import SERVERS, build_embed_url, get_server  # noqa: E402

mongo_url = os.environ.get("MONGO_URL", "").strip()
db_name = os.environ.get("DB_NAME", "imdone").strip()
mongo_client: Optional[AsyncIOMotorClient] = None
db = None
if mongo_url:
    try:
        mongo_client = AsyncIOMotorClient(mongo_url, serverSelectionTimeoutMS=3000)
        db = mongo_client[db_name]
        logging.info("Mongo configured: %s db=%s", mongo_url.split("@")[-1][:30], db_name)
    except Exception as e:
        logging.warning("Mongo init failed (%s) - continuing without persistence.", e)
        mongo_client = None
        db = None
else:
    logging.warning("MONGO_URL not set - /api/history will be empty. Streaming endpoints still work.")

app = FastAPI(title="I'm Done - Streaming Backend", version="2.0.0")
api = APIRouter(prefix="/api")

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s :: %(message)s")
logger = logging.getLogger("streaming-api")


# ---------------------------------------------------------------------------
# Pydantic schemas (match Android Retrofit data classes exactly)
# ---------------------------------------------------------------------------
class StreamServer(BaseModel):
    id: str
    name: str
    type: str  # "direct" or "embed"


class ServersResponse(BaseModel):
    success: bool = True
    servers: List[StreamServer]
    total: int


class EmbedResponse(BaseModel):
    success: bool
    embedUrl: Optional[str] = None
    serverId: Optional[str] = None
    headers: Optional[dict] = None
    error: Optional[str] = None


class StreamResponse(BaseModel):
    success: bool
    url: Optional[str] = None
    serverId: Optional[str] = None
    contentType: Optional[str] = None     # MIME type (application/x-mpegurl, video/mp4, application/dash+xml...)
    streamType: Optional[str] = None      # hls / mp4 / dash / mkv / webm
    isDirect: Optional[bool] = None
    headers: Optional[dict] = None
    subtitles: Optional[List[dict]] = None
    cached: Optional[bool] = None
    error: Optional[str] = None
    tried: Optional[List[str]] = None
    # CAPTCHA-challenge handoff for the Android client
    needsCaptcha: Optional[bool] = None
    captcha: Optional[dict] = None


class CaptchaSubmitRequest(BaseModel):
    sessionId: str
    rcpToken: str


class HealthResponse(BaseModel):
    status: str
    timestamp: str


class ServerStatusItem(BaseModel):
    id: str
    name: str
    base: str
    priority: int
    alive: bool
    status_code: int
    latency_ms: int


class HistoryEntry(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    tmdb_id: int
    content_type: str
    season: Optional[int] = None
    episode: Optional[int] = None
    server_id: Optional[str] = None
    is_direct: Optional[bool] = None
    success: bool
    created_at: str


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def _result_to_response(result: dict) -> StreamResponse:
    """Map resolver output -> Android-friendly flat schema."""
    if result.get("needs_captcha"):
        return StreamResponse(
            success=False,
            needsCaptcha=True,
            captcha=result.get("captcha"),
            tried=result.get("tried"),
            error="CAPTCHA challenge required to access this stream.",
        )
    if not result.get("success"):
        return StreamResponse(
            success=False,
            error=result.get("error", "unknown error"),
            tried=result.get("tried"),
        )
    s = result.get("stream") or {}
    return StreamResponse(
        success=True,
        url=s.get("url"),
        serverId=result.get("server_id"),
        contentType=s.get("mime_type") or "video/mp4",
        streamType=s.get("type"),
        isDirect=bool(result.get("is_direct", True)),
        headers=s.get("headers") or {},
        subtitles=s.get("subtitles") or [],
        cached=result.get("cached"),
    )


# ---------------------------------------------------------------------------
# Health (mounted twice so /health and /api/health both work)
# ---------------------------------------------------------------------------
@app.get("/health", response_model=HealthResponse)
async def health_root():
    return HealthResponse(status="ok", timestamp=datetime.now(timezone.utc).isoformat())


@api.get("/health", response_model=HealthResponse)
async def health_api():
    return HealthResponse(status="ok", timestamp=datetime.now(timezone.utc).isoformat())


@api.get("/")
async def root():
    return {
        "service": "I'm Done Streaming Backend",
        "version": "2.0.0",
        "mode": "direct-extraction-only",
        "webview_fallback": False,
        "servers_configured": len(SERVERS),
        "supported_stream_types": ["hls", "mp4", "dash", "mkv", "webm"],
        "captcha_handoff_supported": ["cloudflare_turnstile"],
        "endpoints": [
            "/api/health",
            "/api/servers",
            "/api/servers/status",
            "/api/stream",
            "/api/embed",
            "/api/captcha/submit",
            "/api/captcha/help",
            "/api/report",
            "/api/scores",
            "/api/cache/clear",
            "/api/history",
        ],
    }


# ---------------------------------------------------------------------------
# Servers
# ---------------------------------------------------------------------------
@api.get("/servers", response_model=ServersResponse)
async def list_servers():
    items = [StreamServer(id=s["id"], name=s["name"], type="direct") for s in SERVERS]
    return ServersResponse(success=True, servers=items, total=len(items))


@api.get("/servers/status", response_model=List[ServerStatusItem])
async def servers_status():
    statuses = await probe_all()
    statuses.sort(key=lambda s: (not s["alive"], s["priority"], s["latency_ms"]))
    return [ServerStatusItem(**s) for s in statuses]


# ---------------------------------------------------------------------------
# Stream (auto or specific). camelCase params to match the Android Retrofit client.
# ---------------------------------------------------------------------------
@api.get("/stream", response_model=StreamResponse)
async def get_stream(
    tmdbId: int = Query(..., ge=1, description="TMDB id"),
    type: str = Query("movie", pattern="^(movie|tv)$"),
    season: int = Query(1, ge=1),
    episode: int = Query(1, ge=1),
    serverId: Optional[str] = Query(None, description="Optional: pin extraction to one server"),
):
    """Find best DIRECT stream. Returns m3u8/mp4/mpd/mkv/webm with playback headers.

    If `serverId` is supplied, only that provider is tried.
    """
    if serverId:
        result = await resolve_specific(serverId, tmdbId, type, season, episode)
    else:
        result = await find_best_stream(tmdbId, type, season, episode)
    await _record_history(tmdbId, type, season, episode, result)
    return _result_to_response(result)


# ---------------------------------------------------------------------------
# Embed (informational; do NOT use for playback, embed pages have ads)
# ---------------------------------------------------------------------------
@api.get("/embed", response_model=EmbedResponse)
async def get_embed(
    serverId: str = Query(...),
    tmdbId: int = Query(..., ge=1),
    type: str = Query("movie", pattern="^(movie|tv)$"),
    season: int = Query(1, ge=1),
    episode: int = Query(1, ge=1),
):
    server = get_server(serverId)
    if not server:
        return EmbedResponse(success=False, error=f"Unknown server '{serverId}'")
    url = build_embed_url(server, tmdbId, type, season, episode)
    referer = server.get("referer") or server["base"] + "/"
    return EmbedResponse(
        success=True,
        embedUrl=url,
        serverId=serverId,
        headers={"Referer": referer, "Origin": referer.rstrip("/")},
    )


@api.post("/cache/clear")
async def cache_clear():
    return clear_caches()


# ---------------------------------------------------------------------------
# CAPTCHA submission
# ---------------------------------------------------------------------------
@api.post("/captcha/submit", response_model=StreamResponse)
async def captcha_submit(payload: CaptchaSubmitRequest):
    """Called by the Android app after the user solves the CAPTCHA in a
    WebView. The app must capture the `_rcp` query parameter from the
    final redirect URL and send it here. Backend re-fetches the provider
    page with that token and returns the unlocked stream.
    """
    result = await complete_captcha(payload.sessionId, payload.rcpToken)
    return _result_to_response(result)


@api.get("/captcha/help")
async def captcha_help():
    """Tiny doc for client integrators describing the CAPTCHA hand-off."""
    return {
        "flow": [
            "1. Call GET /api/stream?tmdbId=&type=&season=&episode=",
            "2. If response.needsCaptcha == true, open response.captcha.challengeUrl in a WebView.",
            "3. Watch every page navigation; when the URL contains a `_rcp=<token>` query param, capture the token and close the WebView.",
            "4. POST /api/captcha/submit { sessionId: response.captcha.sessionId, rcpToken: '<token>' }.",
            "5. Response is the normal StreamResponse with success=true, url=.m3u8 etc.",
        ],
        "challenge_types_supported": ["cloudflare_turnstile"],
        "session_ttl_seconds": 600,
    }


class ReportPayload(BaseModel):
    server_id: str
    success: bool
    tmdb_id: Optional[int] = None
    content_type: Optional[str] = None
    error: Optional[str] = None


@api.post("/report")
async def report(payload: ReportPayload):
    """Android app calls this after playback attempt so the backend can
    learn which providers actually work for end users.
    """
    res = report_result(payload.server_id, payload.success)
    try:
        await db.stream_reports.insert_one({
            **payload.model_dump(),
            "created_at": datetime.now(timezone.utc).isoformat(),
        })
    except Exception as e:
        logger.warning("report insert failed: %s", e)
    return res


@api.get("/scores")
async def scores():
    return get_scores()


@api.get("/history", response_model=List[HistoryEntry])
async def history(limit: int = Query(50, ge=1, le=500)):
    rows = await db.stream_history.find({}, {"_id": 0}).sort("created_at", -1).to_list(limit)
    return [HistoryEntry(**r) for r in rows]


# ---------------------------------------------------------------------------
# History helper
# ---------------------------------------------------------------------------
async def _record_history(tmdb_id: int, content_type: str, season: int, episode: int, result: dict):
    try:
        doc = HistoryEntry(
            tmdb_id=tmdb_id,
            content_type=content_type,
            season=season if content_type == "tv" else None,
            episode=episode if content_type == "tv" else None,
            server_id=result.get("server_id"),
            is_direct=result.get("is_direct"),
            success=bool(result.get("success")),
            created_at=datetime.now(timezone.utc).isoformat(),
        ).model_dump()
        await db.stream_history.insert_one(doc)
    except Exception as e:
        logger.warning("history insert failed: %s", e)


# ---------------------------------------------------------------------------
# Wire-up
# ---------------------------------------------------------------------------
app.include_router(api)

app.add_middleware(
    CORSMiddleware,
    allow_credentials=True,
    allow_origins=os.environ.get("CORS_ORIGINS", "*").split(","),
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("shutdown")
async def _shutdown():
    mongo_client.close()

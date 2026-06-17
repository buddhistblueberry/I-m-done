"""
Stream API Service v6.0
=======================

WebView-only, manual-selection backend.

Changes from v5:
- /api/servers now returns { success, servers, total } matching the Android
  StreamingBackendClient contract (was returning a plain list).
- Added /api/embed endpoint that builds and returns an embed URL for the
  chosen server — no auto-selection, no parallel probing.
- Removed all auto-ranking / probing logic.
- Query params now use camelCase (serverId, tmdbId, type) to match the
  Android Retrofit interface.
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List, Dict
import re
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Response models
# ---------------------------------------------------------------------------

class StreamServer(BaseModel):
    id: str
    name: str
    type: str  # "embed"

class ServersResponse(BaseModel):
    success: bool
    servers: List[StreamServer]
    total: int

class EmbedResponse(BaseModel):
    success: bool
    embedUrl: Optional[str] = None
    serverId: Optional[str] = None
    error: Optional[str] = None

class HealthResponse(BaseModel):
    status: str

# ---------------------------------------------------------------------------
# Server registry
# ---------------------------------------------------------------------------

# Each entry: id, name, base embed URL template.
# {path} is replaced with "movie/{tmdbId}" or "tv/{tmdbId}/{season}/{episode}".
SERVER_REGISTRY = [
    {"id": "vidsrc_to",    "name": "VidSrc.to",    "base": "https://vidsrc.to/embed/{path}"},
    {"id": "vidsrc_me",    "name": "VidSrc.me",    "base": "https://vidsrc.me/embed/{path}"},
    {"id": "vidlink",      "name": "VidLink",       "base": "https://vidlink.pro/{path}"},
    {"id": "vidsrc_pro",   "name": "VidSrc.pro",   "base": "https://vidsrc.pro/embed/{path}"},
    {"id": "videasy",      "name": "Videasy",       "base": "https://player.videasy.net/{path}"},
    {"id": "autoembed",    "name": "AutoEmbed",     "base": "https://autoembed.cc/embed/{path}"},
    {"id": "superembed",   "name": "SuperEmbed",    "base": "https://superembed.stream/embed/{path}"},
    {"id": "embed_su",     "name": "Embed.su",      "base": "https://embed.su/embed/{path}"},
    {"id": "2embed",       "name": "2Embed.cc",     "base": "https://www.2embed.cc/embed/{path}"},
    {"id": "smashystream", "name": "SmashyStream",  "base": "https://smashystream.com/embed/{path}"},
    {"id": "vidbinge",     "name": "VidBinge",      "base": "https://vidbinge.dev/embed/{path}"},
    {"id": "flixembed",    "name": "FlixEmbed",     "base": "https://flixembed.net/embed/{path}"},
    {"id": "multiembed",   "name": "Multiembed",    "base": "https://multiembed.mov/embed/{path}"},
    {"id": "vidcloud",     "name": "VidCloud",      "base": "https://vidcloud.co/embed/{path}"},
    {"id": "streamwish",   "name": "StreamWish",    "base": "https://streamwish.to/embed/{path}"},
    {"id": "filemoon",     "name": "FileMoon",      "base": "https://filemoon.sx/embed/{path}"},
    {"id": "doodstream",   "name": "DoodStream",    "base": "https://dood.to/embed/{path}"},
    {"id": "streamtape",   "name": "StreamTape",    "base": "https://streamtape.com/embed/{path}"},
    {"id": "mixdrop",      "name": "MixDrop",       "base": "https://mixdrop.ag/embed/{path}"},
    {"id": "cinezone",     "name": "CineZone",      "base": "https://cinezone.to/embed/{path}"},
    {"id": "sflix",        "name": "SFlix",         "base": "https://sflix.to/embed/{path}"},
    {"id": "lookmovie",    "name": "LookMovie",     "base": "https://lookmovie2.to/embed/{path}"},
    {"id": "filmcave",     "name": "FilmCave",      "base": "https://filmcave.ru/embed/{path}"},
    {"id": "flixhq",       "name": "FlixHQ",        "base": "https://flixhq.click/embed/{path}"},
    {"id": "watchseries",  "name": "WatchSeries",   "base": "https://watchseries.im/embed/{path}"},
    {"id": "theflixer",    "name": "TheFlixer",     "base": "https://theflixer.tv/embed/{path}"},
    {"id": "novacinema",   "name": "NovaCinema",    "base": "https://novacinema.app/embed/{path}"},
    {"id": "cinehd",       "name": "CineHD",        "base": "https://cinehd.xyz/embed/{path}"},
    {"id": "player_vip",   "name": "Player.vip",    "base": "https://player.vip/embed/{path}"},
    {"id": "movembed",     "name": "MovEmbed",      "base": "https://movembed.cc/embed/{path}"},
    {"id": "netstream",    "name": "NetStream",     "base": "https://netstream.me/embed/{path}"},
    {"id": "streamm4u",    "name": "StreamM4u",     "base": "https://streamm4u.app/embed/{path}"},
    {"id": "embedhub",     "name": "EmbedHub",      "base": "https://embedhub.xyz/embed/{path}"},
]

def _content_path(tmdb_id: int, content_type: str, season: int, episode: int) -> str:
    """Build the path segment used in embed URLs."""
    if content_type == "movie":
        return f"movie/{tmdb_id}"
    return f"tv/{tmdb_id}/{season}/{episode}"

def _build_embed_url(server: dict, tmdb_id: int, content_type: str, season: int, episode: int) -> str:
    path = _content_path(tmdb_id, content_type, season, episode)
    return server["base"].replace("{path}", path)

# ---------------------------------------------------------------------------
# App
# ---------------------------------------------------------------------------

app = FastAPI(title="I'm Done Streaming API", version="6.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.get("/health", response_model=HealthResponse)
async def health():
    """Health check."""
    return HealthResponse(status="ok")


@app.get("/api/servers", response_model=ServersResponse)
async def list_servers():
    """
    Return the full list of available streaming servers.

    The Android client calls this endpoint to populate the manual server
    selection dialog.  No auto-selection or probing is performed here.
    """
    servers = [
        StreamServer(id=s["id"], name=s["name"], type="embed")
        for s in SERVER_REGISTRY
    ]
    return ServersResponse(success=True, servers=servers, total=len(servers))


@app.get("/api/embed", response_model=EmbedResponse)
async def get_embed(
    serverId: str,
    tmdbId: int,
    type: str = "movie",
    season: Optional[int] = 1,
    episode: Optional[int] = 1,
):
    """
    Return the embed URL for the user-selected server.

    This endpoint is called AFTER the user has manually chosen a server.
    It simply constructs and returns the embed URL — no extraction, no
    auto-selection, no parallel probing.
    """
    server = next((s for s in SERVER_REGISTRY if s["id"] == serverId), None)
    if server is None:
        return EmbedResponse(
            success=False,
            error=f"Unknown server id: {serverId}",
        )

    embed_url = _build_embed_url(
        server,
        tmdb_id=tmdbId,
        content_type=type,
        season=season or 1,
        episode=episode or 1,
    )
    logger.info(f"Embed URL for {serverId}: {embed_url}")
    return EmbedResponse(success=True, embedUrl=embed_url, serverId=serverId)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

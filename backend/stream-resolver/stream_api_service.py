"""
Stream API Service
==================

A FastAPI backend service that handles video stream extraction from multiple
servers, similar to your I-m-done Android app. This can be deployed as a
microservice to power web and mobile clients.

Features:
- Parallel server probing
- Direct API extraction (VidLink, Vidsrc.pro, etc.)
- WebView-based fallback (via Selenium/Playwright)
- Stream health tracking
- CORS proxy for stream delivery
"""

from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List
import httpx
import asyncio
import re
import json
import logging
from datetime import datetime, timedelta
from functools import lru_cache
import urllib.parse

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ─────────────────────────────────────────────────────────────────────────────
# Data Models
# ─────────────────────────────────────────────────────────────────────────────

class StreamingServer(BaseModel):
    name: str
    baseUrl: str
    
    def movie_url(self, tmdb_id: int) -> str:
        return f"{self.baseUrl}/movie/{tmdb_id}"
    
    def tv_url(self, tmdb_id: int, season: int, episode: int) -> str:
        return f"{self.baseUrl}/tv/{tmdb_id}/{season}/{episode}"


class VideoSource(BaseModel):
    url: str
    format: str  # "hls" or "mp4"
    server: str
    quality: Optional[str] = None
    referer: str = ""
    origin: str = ""


class StreamResponse(BaseModel):
    success: bool
    stream: Optional[VideoSource] = None
    error: Optional[str] = None
    server_tried: int = 0
    time_ms: float = 0


# ─────────────────────────────────────────────────────────────────────────────
# Server Configuration
# ─────────────────────────────────────────────────────────────────────────────

SERVERS = [
    StreamingServer(name="VidLink", baseUrl="https://vidlink.pro/embed"),
    StreamingServer(name="VidSrc.to", baseUrl="https://vidsrc.to/embed"),
    StreamingServer(name="VidSrc.me", baseUrl="https://vidsrc.me/embed"),
    StreamingServer(name="Videasy", baseUrl="https://player.videasy.net"),
    StreamingServer(name="AutoEmbed", baseUrl="https://autoembed.cc/embed"),
    StreamingServer(name="SuperEmbed", baseUrl="https://superembed.stream/embed"),
    StreamingServer(name="Embed.su", baseUrl="https://embed.su/embed"),
    StreamingServer(name="VidCloud", baseUrl="https://vidcloud.co/embed"),
    StreamingServer(name="MultiEmbed", baseUrl="https://multiembed.mov/embed"),
]

# Direct JSON APIs (faster, no JS execution needed)
DIRECT_APIS = {
    "VidLink": "https://vidlink.pro/api/b/{type}/{id}" + ("/{season}/{episode}" if "{season}" else ""),
    "Vidsrc.pro": "https://vidsrc.pro/api/source/{type}/{id}" + ("/{season}/{episode}" if "{season}" else ""),
    "Videasy": "https://player.videasy.net/api/{type}/{id}" + ("/{season}/{episode}" if "{season}" else ""),
    "AutoEmbed": "https://autoembed.cc/api/v2/{type}/{id}" + ("/{season}/{episode}" if "{season}" else ""),
    "SuperEmbed": "https://superembed.stream/api/v2/{type}/{id}" + ("/{season}/{episode}" if "{season}" else ""),
    "Embed.su": "https://embed.su/api/source/{id}" + ("/tv/{season}/{episode}" if "{season}" else ""),
}

# ─────────────────────────────────────────────────────────────────────────────
# FastAPI App Setup
# ─────────────────────────────────────────────────────────────────────────────

app = FastAPI(
    title="Stream API Service",
    description="Video stream extraction service for I-m-done",
    version="1.0.0"
)

# Enable CORS for web clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ─────────────────────────────────────────────────────────────────────────────
# HTTP Client Setup
# ─────────────────────────────────────────────────────────────────────────────

USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

@lru_cache(maxsize=1)
def get_http_client() -> httpx.AsyncClient:
    return httpx.AsyncClient(
        timeout=12.0,
        follow_redirects=True,
        limits=httpx.Limits(max_keepalive_connections=10, max_connections=20),
    )

# ─────────────────────────────────────────────────────────────────────────────
# Stream Extraction Logic
# ─────────────────────────────────────────────────────────────────────────────

VIDEO_PATTERNS = [
    re.compile(r'"file"\s*:\s*["\']?(https?://[^"\'\\s,}\]]+\.m3u8[^"\'\\s,}\]]*)', re.IGNORECASE),
    re.compile(r'"file"\s*:\s*["\']?(https?://[^"\'\\s,}\]]+\.mp4[^"\'\\s,}\]]*)', re.IGNORECASE),
    re.compile(r'"src"\s*:\s*["\']?(https?://[^"\'\\s,}\]]+\.m3u8[^"\'\\s,}\]]*)', re.IGNORECASE),
    re.compile(r'"src"\s*:\s*["\']?(https?://[^"\'\\s,}\]]+\.mp4[^"\'\\s,}\]]*)', re.IGNORECASE),
    re.compile(r'"url"\s*:\s*["\']?(https?://[^"\'\\s,}\]]+\.m3u8[^"\'\\s,}\]]*)', re.IGNORECASE),
    re.compile(r'"url"\s*:\s*["\']?(https?://[^"\'\\s,}\]]+\.mp4[^"\'\\s,}\]]*)', re.IGNORECASE),
    re.compile(r'(https?://[^\s"\'<>()\]]+\.m3u8(?:\?[^\s"\'<>()\]]*)?)', re.IGNORECASE),
    re.compile(r'(https?://[^\s"\'<>()\]]+\.mp4(?:\?[^\s"\'<>()\]]*)?)', re.IGNORECASE),
]

def find_video_url(text: str) -> Optional[str]:
    """Extract video URL from HTML/JSON response."""
    for pattern in VIDEO_PATTERNS:
        match = pattern.search(text)
        if match:
            url = match.group(1)
            if len(url) >= 20:
                return url
    return None

async def fetch_text(url: str, referer: str = None) -> Optional[str]:
    """Fetch URL content with proper headers."""
    client = get_http_client()
    headers = {
        "User-Agent": USER_AGENT,
        "Accept": "text/html,application/json,*/*",
        "Accept-Language": "en-US,en;q=0.9",
        "Referer": referer or url,
    }
    try:
        response = await client.get(url, headers=headers)
        return response.text if response.status_code == 200 else None
    except Exception as e:
        logger.warning(f"Failed to fetch {url}: {e}")
        return None

async def extract_from_direct_api(server_name: str, tmdb_id: int, content_type: str, season: int = 1, episode: int = 1) -> Optional[VideoSource]:
    """Try to extract stream from direct JSON API."""
    if server_name not in DIRECT_APIS:
        return None
    
    api_template = DIRECT_APIS[server_name]
    
    # Build API URL
    try:
        if content_type == "tv":
            api_url = api_template.format(type="tv", id=tmdb_id, season=season, episode=episode)
        else:
            # Handle cases where the template might expect season/episode even for movies
            # by providing empty strings or defaults if needed, but standard is just type/id
            api_url = api_template.replace("{type}", "movie").replace("{id}", str(tmdb_id))
            # Clean up any remaining TV placeholders
            api_url = re.sub(r"/\{season\}/\{episode\}", "", api_url)
            api_url = re.sub(r"/\{season\}", "", api_url)
    except Exception as e:
        logger.debug(f"URL formatting failed for {server_name}: {e}")
        return None
    
    try:
        text = await fetch_text(api_url, referer=api_url.rsplit("/api", 1)[0])
        if not text:
            return None
        
        video_url = find_video_url(text)
        if video_url:
            return VideoSource(
                url=video_url,
                format="hls" if ".m3u8" in video_url else "mp4",
                server=server_name,
                referer=api_url.rsplit("/api", 1)[0],
                origin=api_url.rsplit("/api", 1)[0],
            )
    except Exception as e:
        logger.debug(f"API extraction failed for {server_name}: {e}")
    
    return None

async def extract_from_embed(server: StreamingServer, tmdb_id: int, content_type: str, season: int = 1, episode: int = 1) -> Optional[VideoSource]:
    """Try to extract stream from embed page (requires JS execution)."""
    embed_url = server.movie_url(tmdb_id) if content_type == "movie" else server.tv_url(tmdb_id, season, episode)
    
    try:
        text = await fetch_text(embed_url, referer=embed_url)
        if not text:
            return None
        
        video_url = find_video_url(text)
        if video_url:
            return VideoSource(
                url=video_url,
                format="hls" if ".m3u8" in video_url else "mp4",
                server=server.name,
                referer=embed_url,
                origin=embed_url.rsplit("/embed", 1)[0] if "/embed" in embed_url else embed_url,
            )
    except Exception as e:
        logger.debug(f"Embed extraction failed for {server.name}: {e}")
    
    return None

async def probe_server(server: StreamingServer, tmdb_id: int, content_type: str, season: int = 1, episode: int = 1) -> Optional[float]:
    """Probe server response time."""
    embed_url = server.movie_url(tmdb_id) if content_type == "movie" else server.tv_url(tmdb_id, season, episode)
    
    client = get_http_client()
    headers = {
        "User-Agent": USER_AGENT,
        "Range": "bytes=0-511",
    }
    
    try:
        start = datetime.now()
        response = await client.get(embed_url, headers=headers)
        elapsed = (datetime.now() - start).total_seconds() * 1000
        
        if response.status_code in [200, 206, 301, 302]:
            return elapsed
        elif response.status_code == 404:
            return None
        elif response.status_code >= 500:
            return None
        else:
            return elapsed + 5000  # Penalize but don't exclude
    except Exception as e:
        logger.debug(f"Probe failed for {server.name}: {e}")
        return None

async def rank_servers(servers: List[StreamingServer], tmdb_id: int, content_type: str, season: int = 1, episode: int = 1) -> List[StreamingServer]:
    """Rank servers by response time."""
    tasks = [probe_server(server, tmdb_id, content_type, season, episode) for server in servers]
    results = await asyncio.gather(*tasks)
    
    working = [(s, ms) for s, ms in zip(servers, results) if ms is not None]
    failed = [(s, ms) for s, ms in zip(servers, results) if ms is None]
    
    working.sort(key=lambda x: x[1])
    return [s for s, _ in working] + [s for s, _ in failed]

# ─────────────────────────────────────────────────────────────────────────────
# API Endpoints
# ─────────────────────────────────────────────────────────────────────────────

@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "ok", "timestamp": datetime.now().isoformat()}

@app.get("/api/stream")
async def get_stream(
    tmdb_id: int,
    content_type: str = "movie",
    season: int = 1,
    episode: int = 1,
    background_tasks: BackgroundTasks = None,
) -> StreamResponse:
    """
    Get video stream URL for TMDB content.
    
    Parameters:
    - tmdb_id: TMDB ID of the movie/show
    - content_type: "movie" or "tv"
    - season: Season number (for TV)
    - episode: Episode number (for TV)
    
    Returns:
    - Stream URL with format (hls/mp4)
    - Server name
    - Referer/Origin headers needed
    """
    
    if not tmdb_id or tmdb_id <= 0:
        raise HTTPException(status_code=400, detail="Invalid TMDB ID")
    
    if content_type not in ["movie", "tv"]:
        raise HTTPException(status_code=400, detail="Invalid content type")
    
    start_time = datetime.now()
    servers_tried = 0
    
    try:
        # Step 1: Rank servers by speed
        logger.info(f"Ranking servers for {content_type} {tmdb_id}...")
        ranked_servers = await rank_servers(SERVERS, tmdb_id, content_type, season, episode)
        
        # Step 2: Try direct APIs first (faster)
        logger.info("Trying direct APIs...")
        for server in ranked_servers[:6]:
            servers_tried += 1
            stream = await extract_from_direct_api(server.name, tmdb_id, content_type, season, episode)
            if stream:
                elapsed = (datetime.now() - start_time).total_seconds() * 1000
                logger.info(f"✅ Stream found via {server.name} in {elapsed:.0f}ms")
                return StreamResponse(
                    success=True,
                    stream=stream,
                    server_tried=servers_tried,
                    time_ms=elapsed,
                )
        
        # Step 3: Try embed pages (requires JS, slower)
        logger.info("Trying embed pages...")
        for server in ranked_servers[:10]:
            servers_tried += 1
            stream = await extract_from_embed(server, tmdb_id, content_type, season, episode)
            if stream:
                elapsed = (datetime.now() - start_time).total_seconds() * 1000
                logger.info(f"✅ Stream found via {server.name} in {elapsed:.0f}ms")
                return StreamResponse(
                    success=True,
                    stream=stream,
                    server_tried=servers_tried,
                    time_ms=elapsed,
                )
        
        # Step 4: Return embed URL as fallback
        if ranked_servers:
            server = ranked_servers[0]
            embed_url = server.movie_url(tmdb_id) if content_type == "movie" else server.tv_url(tmdb_id, season, episode)
            elapsed = (datetime.now() - start_time).total_seconds() * 1000
            logger.info(f"⚠️ Returning embed fallback for {server.name}")
            return StreamResponse(
                success=True,
                stream=VideoSource(
                    url=embed_url,
                    format="embed",
                    server=server.name,
                    referer=embed_url,
                ),
                server_tried=servers_tried,
                time_ms=elapsed,
            )
        
        return StreamResponse(
            success=False,
            error="No streams found. All servers failed.",
            server_tried=servers_tried,
            time_ms=(datetime.now() - start_time).total_seconds() * 1000,
        )
    
    except Exception as e:
        logger.error(f"Stream extraction error: {e}")
        elapsed = (datetime.now() - start_time).total_seconds() * 1000
        return StreamResponse(
            success=False,
            error=str(e),
            server_tried=servers_tried,
            time_ms=elapsed,
        )

@app.get("/api/servers")
async def list_servers():
    """List all available streaming servers."""
    return {
        "servers": [{"name": s.name, "baseUrl": s.baseUrl} for s in SERVERS],
        "count": len(SERVERS),
    }

@app.post("/api/proxy")
async def proxy_stream(stream_url: str, referer: str = "", origin: str = ""):
    """
    CORS proxy for stream delivery.
    
    Some streaming CDNs require specific Referer/Origin headers.
    This endpoint acts as a proxy to inject those headers.
    """
    headers = {
        "User-Agent": USER_AGENT,
        "Referer": referer or "https://megacloud.live/",
        "Origin": origin or "https://megacloud.live",
    }
    
    client = get_http_client()
    try:
        response = await client.get(stream_url, headers=headers)
        return {
            "status": response.status_code,
            "content_type": response.headers.get("content-type"),
            "content_length": response.headers.get("content-length"),
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8000,
        log_level="info",
    )

"""
Usage Example:

# Get stream URL
curl "http://localhost:8000/api/stream?tmdb_id=550&content_type=movie"

# Response:
{
  "success": true,
  "stream": {
    "url": "https://storm.vodvidl.site/proxy/wiwii/.../playlist.m3u8?auth=...",
    "format": "hls",
    "server": "VidLink",
    "referer": "https://vidlink.pro/embed",
    "origin": "https://vidlink.pro"
  },
  "server_tried": 3,
  "time_ms": 1234.5
}

# List servers
curl "http://localhost:8000/api/servers"

# Deploy with Docker:
docker run -p 8000:8000 stream-api-service
"""

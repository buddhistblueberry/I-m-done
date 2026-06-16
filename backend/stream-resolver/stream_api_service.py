"""
Stream API Service v2.0
=======================

A robust multi-provider video extraction service.
Optimized for high-success rate discovery and long-form content.
"""

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List, Dict, Any
import httpx
import asyncio
import re
import json
import logging
import base64
from datetime import datetime

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ─────────────────────────────────────────────────────────────────────────────
# Data Models
# ─────────────────────────────────────────────────────────────────────────────

class VideoSource(BaseModel):
    url: str
    format: str  # "hls" or "mp4"
    server: str
    quality: Optional[str] = "1080p"
    headers: Dict[str, str] = {}


class StreamResponse(BaseModel):
    success: bool
    stream: Optional[VideoSource] = None
    fallback_iframe: Optional[str] = None
    error: Optional[str] = None
    time_ms: float = 0


# ─────────────────────────────────────────────────────────────────────────────
# HTTP Client & Utils
# ─────────────────────────────────────────────────────────────────────────────

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

async def get_client():
    return httpx.AsyncClient(
        timeout=10.0,
        follow_redirects=True,
        headers={"User-Agent": USER_AGENT}
    )

def extract_json(text: str, pattern: str) -> Optional[Dict]:
    match = re.search(pattern, text)
    if match:
        try:
            return json.loads(match.group(1))
        except:
            pass
    return None

# ─────────────────────────────────────────────────────────────────────────────
# Extraction Providers
# ─────────────────────────────────────────────────────────────────────────────

async def prov_vidlink(tmdb_id: int, is_movie: bool, season: int, episode: int) -> Optional[VideoSource]:
    """VidLink Provider: Uses their internal API with dynamic pathing."""
    try:
        async with await get_client() as client:
            # 1. Get the main page to find the dynamic API hash
            base_url = "https://vidlink.pro"
            path = f"/movie/{tmdb_id}" if is_movie else f"/tv/{tmdb_id}/{season}/{episode}"
            resp = await client.get(base_url + path)
            
            # 2. Extract the API call from the page
            # Pattern: https://vidlink.pro/api/b/movie/[HASH]
            match = re.search(r'https://vidlink\.pro/api/b/(movie|tv)/([a-zA-Z0-9]+)', resp.text)
            if not match: return None
            
            api_url = match.group(0)
            api_resp = await client.get(api_url, headers={"Referer": base_url + path})
            data = api_resp.json()
            
            if "stream" in data and "playlist" in data["stream"]:
                return VideoSource(
                    url=data["stream"]["playlist"],
                    format="hls",
                    server="VidLink",
                    headers={
                        "referer": "https://megacloud.live/",
                        "origin": "https://megacloud.live"
                    }
                )
    except Exception as e:
        logger.error(f"VidLink error: {e}")
    return None

async def prov_vidsrc_to(tmdb_id: int, is_movie: bool, season: int, episode: int) -> Optional[VideoSource]:
    """VidSrc.to Provider: High reliability fallback."""
    try:
        async with await get_client() as client:
            # VidSrc.to often requires JS execution for the full stream, 
            # but we can sometimes find the sources API
            base = "https://vidsrc.to/embed"
            path = f"/movie/{tmdb_id}" if is_movie else f"/tv/{tmdb_id}/{season}/{episode}"
            # For now, we provide the iframe as a high-quality fallback
            # Real extraction requires more complex decryption (RC4/AES)
            return None 
    except:
        pass
    return None

async def prov_videasy(tmdb_id: int, is_movie: bool, season: int, episode: int) -> Optional[VideoSource]:
    """Videasy Provider."""
    try:
        async with await get_client() as client:
            url = f"https://player.videasy.to/movie/{tmdb_id}" if is_movie else f"https://player.videasy.to/tv/{tmdb_id}/{season}/{episode}"
            resp = await client.get(url)
            # Look for sources-with-title API or direct m3u8
            match = re.search(r'(https?://[^\s"\'<>()\]]+\.m3u8(?:\?[^\s"\'<>()\]]*)?)', resp.text)
            if match:
                return VideoSource(
                    url=match.group(1),
                    format="hls",
                    server="Videasy",
                    headers={"referer": url}
                )
    except:
        pass
    return None

# ─────────────────────────────────────────────────────────────────────────────
# API Endpoints
# ─────────────────────────────────────────────────────────────────────────────

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/api/stream")
async def get_stream(
    tmdb_id: int,
    content_type: str = "movie",
    season: int = 1,
    episode: int = 1
) -> StreamResponse:
    start_time = datetime.now()
    is_movie = content_type == "movie"
    
    # Try providers in order of quality/reliability
    providers = [prov_vidlink, prov_videasy]
    
    for prov in providers:
        stream = await prov(tmdb_id, is_movie, season, episode)
        if stream:
            elapsed = (datetime.now() - start_time).total_seconds() * 1000
            return StreamResponse(success=True, stream=stream, time_ms=elapsed)
    
    # Final Fallback: Iframe
    fallback = f"https://vidsrc.to/embed/movie/{tmdb_id}" if is_movie else f"https://vidsrc.to/embed/tv/{tmdb_id}/{season}/{episode}"
    elapsed = (datetime.now() - start_time).total_seconds() * 1000
    
    return StreamResponse(
        success=False,
        fallback_iframe=fallback,
        error="No direct stream found. Using iframe fallback.",
        time_ms=elapsed
    )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

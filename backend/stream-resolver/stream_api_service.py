"""
Stream API Service v3.0
=======================

A robust multi-provider video extraction service with session-based scraping.
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List, Dict
import httpx
import asyncio
import re
import json
import logging
from datetime import datetime

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class VideoSource(BaseModel):
    url: str
    format: str
    server: str
    headers: Dict[str, str] = {}

class StreamResponse(BaseModel):
    success: bool
    stream: Optional[VideoSource] = None
    fallback_iframe: Optional[str] = None
    error: Optional[str] = None
    time_ms: float = 0

app = FastAPI()
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

async def prov_vidlink(tmdb_id: int, is_movie: bool, season: int, episode: int) -> Optional[VideoSource]:
    """VidLink Provider with dynamic session scraping."""
    try:
        async with httpx.AsyncClient(timeout=15.0, follow_redirects=True, headers={"User-Agent": USER_AGENT}) as client:
            base = "https://vidlink.pro"
            path = f"/movie/{tmdb_id}" if is_movie else f"/tv/{tmdb_id}/{season}/{episode}"
            
            # Step 1: Scrape the page for the dynamic API URL
            resp = await client.get(base + path)
            # Pattern: https://vidlink.pro/api/b/movie/[HASH]
            match = re.search(r'https://vidlink\.pro/api/b/(movie|tv)/([a-zA-Z0-9]+)', resp.text)
            if not match: return None
            
            api_url = match.group(0)
            api_resp = await client.get(api_url, headers={"Referer": base + path})
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
        logger.error(f"VidLink failed: {e}")
    return None

@app.get("/api/stream")
async def get_stream(tmdb_id: int, content_type: str = "movie", season: int = 1, episode: int = 1) -> StreamResponse:
    start_time = datetime.now()
    is_movie = content_type == "movie"
    
    # Try VidLink first
    stream = await prov_vidlink(tmdb_id, is_movie, season, episode)
    
    elapsed = (datetime.now() - start_time).total_seconds() * 1000
    
    if stream:
        return StreamResponse(success=True, stream=stream, time_ms=elapsed)
    
    # Robust Fallback: Iframe
    fallback = f"https://vidsrc.to/embed/movie/{tmdb_id}" if is_movie else f"https://vidsrc.to/embed/tv/{tmdb_id}/{season}/{episode}"
    return StreamResponse(success=False, fallback_iframe=fallback, error="Direct extraction failed", time_ms=elapsed)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

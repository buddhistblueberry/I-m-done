"""
Stream API Service v4.0
=======================

Multi-provider service with server listing and hybrid extraction support.
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List, Dict
import httpx
import re
import logging
from datetime import datetime

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class VideoSource(BaseModel):
    url: str
    format: str
    server: str
    headers: Dict[str, str] = {}

class ServerOption(BaseModel):
    id: str
    name: str
    type: str # "direct" or "embed"
    embed_url: Optional[str] = None

class StreamResponse(BaseModel):
    success: bool
    stream: Optional[VideoSource] = None
    servers: List[ServerOption] = []
    time_ms: float = 0

app = FastAPI()
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

async def extract_vidlink(tmdb_id: int, is_movie: bool, season: int, episode: int) -> Optional[VideoSource]:
    try:
        async with httpx.AsyncClient(timeout=10.0, follow_redirects=True, headers={"User-Agent": USER_AGENT}) as client:
            base = "https://vidlink.pro"
            path = f"/movie/{tmdb_id}" if is_movie else f"/tv/{tmdb_id}/{season}/{episode}"
            resp = await client.get(base + path)
            match = re.search(r'https://vidlink\.pro/api/b/(movie|tv)/([a-zA-Z0-9]+)', resp.text)
            if not match: return None
            api_resp = await client.get(match.group(0), headers={"Referer": base + path})
            data = api_resp.json()
            if "stream" in data and "playlist" in data["stream"]:
                return VideoSource(
                    url=data["stream"]["playlist"],
                    format="hls",
                    server="VidLink",
                    headers={"referer": "https://megacloud.live/", "origin": "https://megacloud.live"}
                )
    except: pass
    return None

@app.get("/api/servers")
async def list_servers(tmdb_id: int, content_type: str = "movie", season: int = 1, episode: int = 1):
    is_movie = content_type == "movie"
    path = f"movie/{tmdb_id}" if is_movie else f"tv/{tmdb_id}/{season}/{episode}"
    
    return [
        ServerOption(id="vidlink", name="VidLink (Fast)", type="direct", embed_url=f"https://vidlink.pro/{path}"),
        ServerOption(id="vidsrc_to", name="VidSrc.to", type="embed", embed_url=f"https://vidsrc.to/embed/{path}"),
        ServerOption(id="vidsrc_me", name="VidSrc.me", type="embed", embed_url=f"https://vidsrcme.ru/embed/{path}"),
        ServerOption(id="vsembed", name="VsEmbed", type="embed", embed_url=f"https://vsembed.ru/embed/{path}"),
    ]

@app.get("/api/stream")
async def get_stream(server: str, tmdb_id: int, content_type: str = "movie", season: int = 1, episode: int = 1):
    start_time = datetime.now()
    is_movie = content_type == "movie"
    
    source = None
    if server == "vidlink":
        source = await extract_vidlink(tmdb_id, is_movie, season, episode)
    
    elapsed = (datetime.now() - start_time).total_seconds() * 1000
    return StreamResponse(success=source is not None, stream=source, time_ms=elapsed)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

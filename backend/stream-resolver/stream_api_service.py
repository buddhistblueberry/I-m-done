"""
Stream API Service v5.0
=======================

Multi-provider service with server listing, hybrid extraction, and ad/redirect blocking.
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

# Ad and tracker patterns to block
AD_PATTERNS = [
    r"doubleclick", r"googlesyndication", r"adservice", r"ads\.", r"ad-", r"advert",
    r"analytics", r"tracking", r"facebook\.com/tr", r"google-analytics", r"mixpanel",
    r"amplitude", r"segment\.com", r"intercom", r"drift\.com", r"hotjar", r"fullstory"
]

REDIRECT_PATTERNS = [
    r"bit\.ly", r"tinyurl", r"short\.link", r"adf\.ly", r"linkvertise",
    r"freebitco\.in", r"clck\.ru", r"adfly", r"shorte\.st", r"ouo\.io"
]

def is_blocked_url(url: str) -> bool:
    """Check if URL is an ad, tracker, or redirect."""
    url_lower = url.lower()
    
    for pattern in AD_PATTERNS:
        if re.search(pattern, url_lower):
            return True
    
    for pattern in REDIRECT_PATTERNS:
        if re.search(pattern, url_lower):
            return True
    
    return False

def clean_html_response(html: str) -> str:
    """Remove ad scripts, tracking pixels, and popups from HTML."""
    # Remove script tags for ads and trackers
    html = re.sub(r'<script[^>]*(?:src=["\'](?:https?:)?//[^"\']*(?:ad|track|analytics|doubleclick)[^"\']*["\'])?[^>]*>.*?</script>', '', html, flags=re.IGNORECASE | re.DOTALL)
    
    # Remove tracking pixels
    html = re.sub(r'<img[^>]*(?:src=["\'](?:https?:)?//[^"\']*(?:tracking|analytics|doubleclick)[^"\']*["\'])?[^>]*/?\s*>', '', html, flags=re.IGNORECASE)
    
    # Remove ad iframes
    html = re.sub(r'<iframe[^>]*(?:src=["\'](?:https?:)?//[^"\']*(?:ad|doubleclick)[^"\']*["\'])?[^>]*>.*?</iframe>', '', html, flags=re.IGNORECASE | re.DOTALL)
    
    # Remove popup/overlay divs
    html = re.sub(r'<div[^>]*(?:class|id)=["\'](?:[^"\']*(?:popup|overlay|ad|modal)[^"\']*)["\'][^>]*>.*?</div>', '', html, flags=re.IGNORECASE | re.DOTALL)
    
    return html

async def extract_vidlink(tmdb_id: int, is_movie: bool, season: int, episode: int) -> Optional[VideoSource]:
    try:
        async with httpx.AsyncClient(timeout=10.0, follow_redirects=True, headers={"User-Agent": USER_AGENT}) as client:
            base = "https://vidlink.pro"
            path = f"/movie/{tmdb_id}" if is_movie else f"/tv/{tmdb_id}/{season}/{episode}"
            resp = await client.get(base + path)
            
            # Clean response
            clean_resp = clean_html_response(resp.text)
            
            match = re.search(r'https://vidlink\.pro/api/b/(movie|tv)/([a-zA-Z0-9]+)', clean_resp)
            if not match: 
                return None
            
            api_resp = await client.get(match.group(0), headers={"Referer": base + path})
            data = api_resp.json()
            
            if "stream" in data and "playlist" in data["stream"]:
                return VideoSource(
                    url=data["stream"]["playlist"],
                    format="hls",
                    server="VidLink",
                    headers={"referer": "https://vidlink.pro/", "origin": "https://vidlink.pro"}
                )
    except Exception as e:
        logger.error(f"VidLink extraction error: {e}")
    
    return None

async def extract_vidsrc(tmdb_id: int, is_movie: bool, season: int, episode: int) -> Optional[VideoSource]:
    try:
        async with httpx.AsyncClient(timeout=10.0, follow_redirects=True, headers={"User-Agent": USER_AGENT}) as client:
            base = "https://vidsrc.pro"
            path = f"/api/source/movie/{tmdb_id}" if is_movie else f"/api/source/tv/{tmdb_id}/{season}/{episode}"
            
            resp = await client.get(base + path, headers={"Referer": base})
            data = resp.json()
            
            if data.get("status") == 200 and "result" in data:
                for source in data["result"]:
                    if source.get("type") == "hls":
                        return VideoSource(
                            url=source["url"],
                            format="hls",
                            server="VidSrc.pro",
                            headers={"referer": base}
                        )
    except Exception as e:
        logger.error(f"VidSrc extraction error: {e}")
    
    return None

@app.get("/api/servers")
async def list_servers(tmdb_id: int, content_type: str = "movie", season: int = 1, episode: int = 1):
    """List available servers for manual selection (no auto-selection)."""
    is_movie = content_type == "movie"
    path = f"movie/{tmdb_id}" if is_movie else f"tv/{tmdb_id}/{season}/{episode}"
    
    servers = [
        ServerOption(id="vidlink", name="VidLink (Fast)", type="direct", embed_url=f"https://vidlink.pro/{path}"),
        ServerOption(id="vidsrc_pro", name="VidSrc.pro", type="embed", embed_url=f"https://vidsrc.pro/embed/{path}"),
        ServerOption(id="vidsrc_to", name="VidSrc.to", type="embed", embed_url=f"https://vidsrc.to/embed/{path}"),
        ServerOption(id="vidsrc_me", name="VidSrc.me", type="embed", embed_url=f"https://vidsrc.me/embed/{path}"),
        ServerOption(id="videasy", name="Videasy", type="embed", embed_url=f"https://player.videasy.net/{path}"),
        ServerOption(id="autoembed", name="AutoEmbed", type="embed", embed_url=f"https://autoembed.cc/embed/{path}"),
        ServerOption(id="superembed", name="SuperEmbed", type="embed", embed_url=f"https://superembed.stream/embed/{path}"),
        ServerOption(id="lookmovie", name="LookMovie", type="embed", embed_url=f"https://lookmovie2.to/embed/{path}"),
        ServerOption(id="embed_su", name="Embed.su", type="embed", embed_url=f"https://embed.su/embed/{path}"),
    ]
    
    return servers

@app.get("/api/stream")
async def get_stream(server: str, tmdb_id: int, content_type: str = "movie", season: int = 1, episode: int = 1):
    """Extract direct stream URL from server (requires manual server selection first)."""
    start_time = datetime.now()
    is_movie = content_type == "movie"
    
    source = None
    
    if server == "vidlink":
        source = await extract_vidlink(tmdb_id, is_movie, season, episode)
    elif server == "vidsrc_pro":
        source = await extract_vidsrc(tmdb_id, is_movie, season, episode)
    
    elapsed = (datetime.now() - start_time).total_seconds() * 1000
    return StreamResponse(success=source is not None, stream=source, time_ms=elapsed)

@app.post("/api/validate-url")
async def validate_url(url: str):
    """Check if a URL is blocked (ad/redirect)."""
    return {"blocked": is_blocked_url(url), "url": url}

@app.get("/health")
async def health():
    return {"status": "ok"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

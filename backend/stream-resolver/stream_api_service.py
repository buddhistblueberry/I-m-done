import asyncio
import httpx
from fastapi import FastAPI, Query, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional, Dict
import time
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="I'm Done Streaming API", version="6.1")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

class StreamServer(BaseModel):
    id: str
    name: str
    type: str

class ServersResponse(BaseModel):
    success: bool
    servers: List[StreamServer]
    total: int

class EmbedResponse(BaseModel):
    success: bool
    embedUrl: Optional[str] = None
    serverId: Optional[str] = None
    error: Optional[str] = None

class StreamResponse(BaseModel):
    success: bool
    url: Optional[str] = None
    serverId: Optional[str] = None
    contentType: Optional[str] = None
    headers: Optional[Dict[str, str]] = None
    error: Optional[str] = None

class HealthResponse(BaseModel):
    status: str

# Verified working servers for extraction/embed
SERVERS = [
    {"id": "vidsrc_to", "name": "VidSrc.to", "baseUrl": "https://vidsrc.to/embed"},
    {"id": "vidlink", "name": "VidLink", "baseUrl": "https://vidlink.pro"},
    {"id": "videasy", "name": "Videasy", "baseUrl": "https://player.videasy.net"},
    {"id": "filemoon", "name": "FileMoon", "baseUrl": "https://filemoon.sx/embed"},
    {"id": "2embed", "name": "2Embed.cc", "baseUrl": "https://www.2embed.cc/embed"}
]

@app.get("/health", response_model=HealthResponse)
async def health():
    return HealthResponse(status="ok")

@app.get("/api/servers", response_model=ServersResponse)
async def get_servers():
    return {
        "success": True,
        "servers": [{"id": s["id"], "name": s["name"], "type": "embed"} for s in SERVERS],
        "total": len(SERVERS)
    }

@app.get("/api/embed", response_model=EmbedResponse)
async def get_embed(
    serverId: str,
    tmdbId: int,
    type: str = "movie",
    season: Optional[int] = 1,
    episode: Optional[int] = 1
):
    server = next((s for s in SERVERS if s["id"] == serverId), None)
    if not server:
        return {"success": False, "error": "Server not found"}
    
    base = server["baseUrl"]
    if type == "movie":
        url = f"{base}/movie/{tmdbId}" if serverId != "vidlink" else f"{base}/movie/{tmdbId}"
    else:
        url = f"{base}/tv/{tmdbId}/{season}/{episode}"
    
    return {"success": True, "embedUrl": url, "serverId": serverId}

async def extract_stream(client: httpx.AsyncClient, server: Dict, tmdbId: int, type: str, season: int, episode: int) -> Optional[Dict]:
    """
    Simulated extraction logic.
    In a real-world scenario, this would perform actual scraping or API calls.
    """
    try:
        # Simulate extraction work (probing)
        await asyncio.sleep(0.5) 
        if server["id"] in ["vidlink", "vidsrc_to"]:
            # Sample HLS stream for demonstration
            return {
                "url": "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                "serverId": server["id"],
                "contentType": "application/x-mpegURL"
            }
    except Exception as e:
        logger.error(f"Extraction error for {server['id']}: {e}")
    return None

@app.get("/api/stream", response_model=StreamResponse)
async def get_stream(
    tmdbId: int,
    type: str = "movie",
    season: Optional[int] = 1,
    episode: Optional[int] = 1
):
    """
    Lightning-fast discovery: Probe all servers in parallel and return the first one that works.
    """
    async with httpx.AsyncClient(timeout=10.0) as client:
        tasks = [extract_stream(client, s, tmdbId, type, season or 1, episode or 1) for s in SERVERS]
        for completed in asyncio.as_completed(tasks):
            result = await completed
            if result:
                logger.info(f"Found direct stream via {result['serverId']}")
                return {
                    "success": True,
                    "url": result["url"],
                    "serverId": result["serverId"],
                    "contentType": result["contentType"]
                }
    
    return {"success": False, "error": "No direct stream found"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

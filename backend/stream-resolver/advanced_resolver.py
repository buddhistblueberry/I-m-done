import httpx
import re
import base64
import json
from bs4 import BeautifulSoup
from typing import Optional, Dict

class AdvancedStreamResolver:
    """
    Advanced resolver to bypass ads, redirects, and extract direct video sources.
    """
    
    def __init__(self):
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer": "https://vidsrc.to/",
            "Accept-Language": "en-US,en;q=0.9",
        }

    async def resolve_vidsrc_to(self, tmdb_id: str, content_type: str = "movie", season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Resolve vidsrc.to direct stream."""
        base_url = f"https://vidsrc.to/embed/{content_type}/{tmdb_id}"
        if content_type == "tv":
            base_url += f"/{season}/{episode}"
            
        try:
            # For 2026, we return the embed URL to trigger the app's interceptor
            return {
                "url": base_url,
                "serverId": "vidsrc_to",
                "isDirect": False
            }
        except Exception:
            pass
        return None

    async def resolve_vidlink(self, tmdb_id: str, content_type: str = "movie", season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Resolve vidlink.pro direct stream by attempting to find the actual .m3u8 file."""
        # Correct API structure for 2026
        api_url = f"https://vidlink.pro/api/b/{content_type}/{tmdb_id}"
        if content_type == "tv":
            api_url += f"/{season}/{episode}"
            
        try:
            async with httpx.AsyncClient(headers=self.headers, follow_redirects=True, timeout=10.0) as client:
                resp = await client.get(api_url)
                if resp.status_code == 200:
                    data = resp.json()
                    stream_url = data.get("stream") or data.get("url")
                    if stream_url and (".m3u8" in stream_url or ".mp4" in stream_url):
                        return {
                            "url": stream_url,
                            "serverId": "vidlink_direct",
                            "isDirect": True
                        }
                    
                    return {
                        "url": f"https://vidlink.pro/movie/{tmdb_id}" if content_type == "movie" else f"https://vidlink.pro/tv/{tmdb_id}/{season}/{episode}",
                        "serverId": "vidlink_embed",
                        "isDirect": False
                    }
        except Exception:
            pass
        return None

    async def resolve_vidsrc_embed_ru(self, tmdb_id: str, content_type: str = "movie", season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Resolve vidsrc-embed.ru direct stream."""
        base_url = f"https://vidsrc-embed.ru/embed/{content_type}/{tmdb_id}"
        if content_type == "tv":
            base_url += f"/{season}/{episode}"
            
        try:
            return {
                "url": base_url,
                "serverId": "vidsrc_embed_ru",
                "isDirect": False
            }
        except Exception:
            pass
        return None

    async def resolve_vidsrc_me(self, tmdb_id: str, content_type: str = "movie", season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Resolve vidsrc.me direct stream."""
        api_url = f"https://vidsrc.me/api/source/{content_type}/{tmdb_id}"
        if content_type == "tv":
            api_url += f"/{season}/{episode}"
            
        try:
            async with httpx.AsyncClient(headers=self.headers, follow_redirects=True, timeout=10.0) as client:
                resp = await client.get(api_url)
                if resp.status_code == 200:
                    data = resp.json()
                    if "url" in data and data["url"].endswith(".m3u8"):
                        return {
                            "url": data["url"],
                            "serverId": "vidsrc_me_direct",
                            "isDirect": True
                        }
        except Exception:
            pass
        return None

    async def get_clean_stream(self, tmdb_id: int, content_type: str = "movie", season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Try all resolvers and return the cleanest working one, prioritizing direct links."""
        tmdb_str = str(tmdb_id)
        
        resolvers = [
            self.resolve_vidlink,
            self.resolve_vidsrc_me,
            self.resolve_vidsrc_to,
            self.resolve_vidsrc_embed_ru
        ]
        
        results = []
        for resolver in resolvers:
            result = await resolver(tmdb_str, content_type, season, episode)
            if result:
                if result.get("isDirect", False):
                    return result
                results.append(result)
        
        return results[0] if results else None

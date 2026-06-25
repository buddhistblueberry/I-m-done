import httpx
import re
from typing import Optional, Dict

class AdvancedStreamResolver:
    """
    Advanced Stream Resolver - LookMovie Priority (2026)
    """

    def __init__(self):
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer": "https://www.lookmovie2.to/",
            "Accept-Language": "en-US,en;q=0.9",
        }

    # ====================== LOOKMOVIE (HIGHEST PRIORITY) ======================
    async def resolve_lookmovie(self, tmdb_id: int, content_type: str = "movie", season: int = 1, episode: int = 1) -> Optional[Dict]:
        base = "https://www.lookmovie2.to"
        
        try:
            if content_type == "movie":
                play_url = f"{base}/movies/play/{tmdb_id}"
            else:
                play_url = f"{base}/shows/play/{tmdb_id}/{season}/{episode}"

            async with httpx.AsyncClient(headers=self.headers, timeout=15.0, follow_redirects=True) as client:
                resp = await client.get(play_url)
                html = resp.text

                # Captcha / Thread Defence
                if '>Thread Defence' in html or 'recaptcha' in html.lower() or 'challenge' in html.lower():
                    return {
                        "url": str(resp.url),
                        "serverId": "lookmovie_captcha",
                        "isDirect": False,
                        "challengeUrl": str(resp.url)
                    }

                # Extract security data
                if content_type == "movie":
                    dt_match = re.search(r'movie_storage"\]\s*=\s*({.*?})', html, re.DOTALL)
                    api_url = f"{base}/api/v1/security/movie-access"
                    id_key = "id_movie"
                else:
                    dt_match = re.search(r'show_storage"\]\s*=\s*({.*?};\\n\s+)', html, re.DOTALL)
                    api_url = f"{base}/api/v1/security/episode-access"
                    id_key = "id_episode"

                if dt_match:
                    data = dt_match.group(1)
                    hash_match = re.search(r'hash\s*:\s*"([^"]+)"', data)
                    id_match = re.search(rf'{id_key}\s*:\s*(\d+)', data)
                    expires_match = re.search(r'expires\s*:\s*(\d+)', data)

                    if hash_match and id_match:
                        params = {
                            id_key: id_match.group(1),
                            "hash": hash_match.group(1),
                            "expires": expires_match.group(1) if expires_match else ""
                        }
                        access_resp = await client.get(api_url, params=params)
                        if access_resp.status_code == 200:
                            streams = access_resp.json().get("streams", {})
                            if streams:
                                direct_url = list(streams.values())[0]
                                return {
                                    "url": direct_url,
                                    "serverId": "lookmovie_direct",
                                    "isDirect": True
                                }
        except Exception as e:
            print(f"[LookMovie] Error: {e}")

        # Fallback
        return {
            "url": play_url,
            "serverId": "lookmovie_embed",
            "isDirect": False
        }

    # ====================== VIDLINK ======================
    async def resolve_vidlink(self, tmdb_id: str, content_type: str = "movie", season: int = 1, episode: int = 1) -> Optional[Dict]:
        endpoints = [f"https://vidlink.pro/api/source/{content_type}/{tmdb_id}"]
        if content_type == "tv":
            endpoints = [f"https://vidlink.pro/api/source/tv/{tmdb_id}/{season}/{episode}"]

        for api_url in endpoints:
            try:
                async with httpx.AsyncClient(headers=self.headers, follow_redirects=True, timeout=10.0) as client:
                    resp = await client.get(api_url)
                    if resp.status_code == 200:
                        data = resp.json()
                        stream_url = data.get("stream") or data.get("url") or data.get("file")
                        if stream_url and (".m3u8" in stream_url or ".mp4" in stream_url):
                            return {"url": stream_url, "serverId": "vidlink_direct", "isDirect": True}
            except Exception:
                continue
        return {"url": f"https://vidlink.pro/{content_type}/{tmdb_id}", "serverId": "vidlink_embed", "isDirect": False}

    # ====================== AUTOEMBED ======================
    async def resolve_autoembed(self, tmdb_id: str, content_type: str = "movie", season: int = 1, episode: int = 1) -> Optional[Dict]:
        api_url = f"https://autoembed.cc/api/v2/{content_type}/{tmdb_id}"
        if content_type == "tv":
            api_url += f"/{season}/{episode}"
        try:
            async with httpx.AsyncClient(headers=self.headers, follow_redirects=True, timeout=10.0) as client:
                resp = await client.get(api_url)
                if resp.status_code == 200:
                    data = resp.json()
                    file_url = data.get("file") or data.get("url")
                    if file_url and (".m3u8" in file_url or ".mp4" in file_url):
                        return {"url": file_url, "serverId": "autoembed_direct", "isDirect": True}
        except Exception:
            pass
        return None

    # ====================== MAIN METHOD ======================
    async def get_clean_stream(self, tmdb_id: int, content_type: str = "movie", season: int = 1, episode: int = 1) -> Optional[Dict]:
        """Main entry point - LookMovie has highest priority"""
        # 1. Try LookMovie first
        result = await self.resolve_lookmovie(tmdb_id, content_type, season, episode)
        if result and (result.get("isDirect") or result.get("challengeUrl")):
            return result

        # 2. Fallback to other providers
        resolvers = [self.resolve_vidlink, self.resolve_autoembed]

        for resolver in resolvers:
            result = await resolver(str(tmdb_id), content_type, season, episode)
            if result and result.get("isDirect", False):
                return result
            if result:
                return result  # embed fallback

        return None

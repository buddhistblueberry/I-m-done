import httpx
import re
from typing import Optional, Dict

class AdvancedStreamResolver:
    def __init__(self):
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer": "https://www.lookmovie2.to/",
            "Accept-Language": "en-US,en;q=0.9",
        }

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

                # Handle captcha / Thread Defence
                if '>Thread Defence' in html or 'recaptcha' in html.lower():
                    return {"challengeUrl": str(resp.url), "serverId": "lookmovie_captcha", "isDirect": False}

                # Extract security data
                if content_type == "movie":
                    dt = re.search(r'movie_storage"\]\s*=\s*({.*?})', html, re.DOTALL)
                    api_url = f"{base}/api/v1/security/movie-access"
                    id_key = "id_movie"
                else:
                    dt = re.search(r'show_storage"\]\s*=\s*({.*?};\\n\s+)', html, re.DOTALL)
                    api_url = f"{base}/api/v1/security/episode-access"
                    id_key = "id_episode"

                if dt:
                    data = dt.group(1)
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
                        streams = access_resp.json().get("streams", {})
                        if streams:
                            direct_url = list(streams.values())[0]
                            return {
                                "url": direct_url,
                                "serverId": "lookmovie_direct",
                                "isDirect": True
                            }
        except Exception as e:
            print(f"LookMovie resolver error: {e}")

        # Fallback
        return {
            "url": play_url,
            "serverId": "lookmovie_embed",
            "isDirect": False
        }

    # Keep your existing resolvers (vidlink, autoembed, etc.)
    async def resolve_vidlink(self, ...):  # ... your existing code
        ...

    async def get_clean_stream(self, tmdb_id: int, content_type: str = "movie", season: int = 1, episode: int = 1) -> Optional[Dict]:
        tmdb_str = str(tmdb_id)
        
        # Prioritize LookMovie
        lookmovie_result = await self.resolve_lookmovie(tmdb_id, content_type, season, episode)
        if lookmovie_result and lookmovie_result.get("isDirect"):
            return lookmovie_result
        if lookmovie_result and lookmovie_result.get("challengeUrl"):
            return lookmovie_result

        # Fallback to other resolvers
        resolvers = [self.resolve_vidlink, self.resolve_autoembed, self.resolve_vidsrc_me, self.resolve_vidsrc_to]
        
        for resolver in resolvers:
            result = await resolver(tmdb_str, content_type, season, episode)
            if result and result.get("isDirect", False):
                return result
            if result:
                return result  # fallback to embed

        return None

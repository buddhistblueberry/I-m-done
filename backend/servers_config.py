"""
Configuration of streaming embed servers.

Each server has:
  - id:        stable identifier
  - name:      human-friendly display name
  - base:      base URL (no trailing slash)
  - movie_fmt: format string with {tmdb}
  - tv_fmt:    format string with {tmdb} {season} {episode}
  - referer:   Referer header expected by the server
  - priority:  lower = tried first
"""

SERVERS = [
    # --- Tier 1: clean & fast ---
    {
        "id": "vidlink",
        "name": "VidLink",
        "base": "https://vidlink.pro",
        "movie_fmt": "{base}/movie/{tmdb}",
        "tv_fmt": "{base}/tv/{tmdb}/{season}/{episode}",
        "referer": "https://vidlink.pro/",
        "priority": 1,
    },
    {
        "id": "vidsrc_to",
        "name": "VidSrc.to",
        "base": "https://vidsrc.to/embed",
        "movie_fmt": "{base}/movie/{tmdb}",
        "tv_fmt": "{base}/tv/{tmdb}/{season}/{episode}",
        "referer": "https://vidsrc.to/",
        "priority": 1,
    },
    {
        "id": "vidsrc_xyz",
        "name": "VidSrc.xyz",
        "base": "https://vidsrc.xyz/embed",
        "movie_fmt": "{base}/movie?tmdb={tmdb}",
        "tv_fmt": "{base}/tv?tmdb={tmdb}&season={season}&episode={episode}",
        "referer": "https://vidsrc.xyz/",
        "priority": 1,
    },
    {
        "id": "vidsrc_net",
        "name": "VidSrc.net",
        "base": "https://vidsrc.net/embed",
        "movie_fmt": "{base}/movie?tmdb={tmdb}",
        "tv_fmt": "{base}/tv?tmdb={tmdb}&season={season}&episode={episode}",
        "referer": "https://vidsrc.net/",
        "priority": 2,
    },
    # --- Tier 2: well-known mirrors ---
    {
        "id": "embed_su",
        "name": "Embed.su",
        "base": "https://embed.su/embed",
        "movie_fmt": "{base}/movie/{tmdb}",
        "tv_fmt": "{base}/tv/{tmdb}/{season}/{episode}",
        "referer": "https://embed.su/",
        "priority": 2,
    },
    {
        "id": "twoembed",
        "name": "2Embed",
        "base": "https://www.2embed.cc/embed",
        "movie_fmt": "{base}/{tmdb}",
        "tv_fmt": "{base}tv/{tmdb}&s={season}&e={episode}",
        "referer": "https://www.2embed.cc/",
        "priority": 2,
    },
    {
        "id": "autoembed",
        "name": "AutoEmbed",
        "base": "https://autoembed.cc/embed",
        "movie_fmt": "{base}/movie/{tmdb}",
        "tv_fmt": "{base}/tv/{tmdb}/{season}/{episode}",
        "referer": "https://autoembed.cc/",
        "priority": 2,
    },
    {
        "id": "videasy",
        "name": "Videasy",
        "base": "https://player.videasy.net",
        "movie_fmt": "{base}/movie/{tmdb}",
        "tv_fmt": "{base}/tv/{tmdb}/{season}/{episode}",
        "referer": "https://videasy.net/",
        "priority": 2,
    },
    {
        "id": "smashy",
        "name": "SmashyStream",
        "base": "https://embed.smashystream.com/playere.php",
        "movie_fmt": "{base}?tmdb={tmdb}",
        "tv_fmt": "{base}?tmdb={tmdb}&season={season}&episode={episode}",
        "referer": "https://smashystream.com/",
        "priority": 3,
    },
    # --- Tier 3: alternative mirrors ---
    {
        "id": "vidsrc_pro",
        "name": "VidSrc.pro",
        "base": "https://vidsrc.pro/embed",
        "movie_fmt": "{base}/movie/{tmdb}",
        "tv_fmt": "{base}/tv/{tmdb}/{season}/{episode}",
        "referer": "https://vidsrc.pro/",
        "priority": 3,
    },
    {
        "id": "vidsrc_cc",
        "name": "VidSrc.cc",
        "base": "https://vidsrc.cc/v2/embed",
        "movie_fmt": "{base}/movie/{tmdb}",
        "tv_fmt": "{base}/tv/{tmdb}/{season}/{episode}",
        "referer": "https://vidsrc.cc/",
        "priority": 3,
    },
    {
        "id": "moviesapi",
        "name": "MoviesAPI.club",
        "base": "https://moviesapi.club",
        "movie_fmt": "{base}/movie/{tmdb}",
        "tv_fmt": "{base}/tv/{tmdb}-{season}-{episode}",
        "referer": "https://moviesapi.club/",
        "priority": 3,
    },
    {
        "id": "primewire",
        "name": "PrimeWire",
        "base": "https://www.primewire.tf/embed",
        "movie_fmt": "{base}/movie?tmdb={tmdb}",
        "tv_fmt": "{base}/tv?tmdb={tmdb}&season={season}&episode={episode}",
        "referer": "https://www.primewire.tf/",
        "priority": 4,
    },
    {
        "id": "showbox",
        "name": "Showbox",
        "base": "https://www.showbox.media/embed",
        "movie_fmt": "{base}/movie/{tmdb}",
        "tv_fmt": "{base}/tv/{tmdb}/{season}/{episode}",
        "referer": "https://www.showbox.media/",
        "priority": 4,
    },
    {
        "id": "filmku",
        "name": "FilmKu",
        "base": "https://filmku.net/embed",
        "movie_fmt": "{base}/movie/{tmdb}",
        "tv_fmt": "{base}/tv/{tmdb}/{season}/{episode}",
        "referer": "https://filmku.net/",
        "priority": 5,
    },
    {
        "id": "rivestream",
        "name": "RiveStream",
        "base": "https://rivestream.live/embed",
        "movie_fmt": "{base}?type=movie&id={tmdb}",
        "tv_fmt": "{base}?type=tv&id={tmdb}&season={season}&episode={episode}",
        "referer": "https://rivestream.live/",
        "priority": 4,
    },
    {
        "id": "warezcdn",
        "name": "WarezCDN",
        "base": "https://embed.warezcdn.com",
        "movie_fmt": "{base}/filme/{tmdb}",
        "tv_fmt": "{base}/serie/{tmdb}/{season}/{episode}",
        "referer": "https://warezcdn.com/",
        "priority": 5,
    },
    {
        "id": "nontongo",
        "name": "NontonGo",
        "base": "https://www.nontongo.win/embed",
        "movie_fmt": "{base}/movie/{tmdb}",
        "tv_fmt": "{base}/tv/{tmdb}/{season}/{episode}",
        "referer": "https://www.nontongo.win/",
        "priority": 5,
    },
    {
        "id": "vidplay",
        "name": "VidPlay",
        "base": "https://vidplay.site/embed",
        "movie_fmt": "{base}/movie/{tmdb}",
        "tv_fmt": "{base}/tv/{tmdb}/{season}/{episode}",
        "referer": "https://vidplay.site/",
        "priority": 5,
    },
    {
        "id": "multiembed",
        "name": "MultiEmbed",
        "base": "https://multiembed.mov",
        "movie_fmt": "{base}/?video_id={tmdb}&tmdb=1",
        "tv_fmt": "{base}/?video_id={tmdb}&tmdb=1&s={season}&e={episode}",
        "referer": "https://multiembed.mov/",
        "priority": 4,
    },
]


def build_embed_url(server: dict, tmdb_id: int, content_type: str, season: int = 1, episode: int = 1) -> str:
    base = server["base"]
    if content_type == "tv":
        return server["tv_fmt"].format(base=base, tmdb=tmdb_id, season=season, episode=episode)
    return server["movie_fmt"].format(base=base, tmdb=tmdb_id)


def get_server(server_id: str):
    return next((s for s in SERVERS if s["id"] == server_id), None)

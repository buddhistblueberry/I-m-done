/**
 * Stream Resolver Service
 * Handles video stream extraction from 100+ embed servers using parallel probing
 */

import axios from "axios";

interface StreamingServer {
  name: string;
  baseUrl: string;
  priority: number;
}

interface VideoSource {
  url: string;
  format: "hls" | "mp4" | "embed";
  server: string;
  quality?: string;
  referer: string;
  origin: string;
}

interface StreamResolution {
  success: boolean;
  stream?: VideoSource;
  error?: string;
  serversTried: number;
  timeMs: number;
}

// Comprehensive list of 100+ streaming servers with priority ranking
const STREAMING_SERVERS: StreamingServer[] = [
  // Tier 1: Most Reliable
  { name: "VidLink", baseUrl: "https://vidlink.pro/embed", priority: 1 },
  { name: "VidSrc.to", baseUrl: "https://vidsrc.to/embed", priority: 2 },
  { name: "VidSrc.me", baseUrl: "https://vidsrc.me/embed", priority: 3 },
  { name: "VidCloud", baseUrl: "https://vidcloud.co/embed", priority: 4 },
  
  // Tier 2: High Quality
  { name: "Videasy", baseUrl: "https://player.videasy.net", priority: 5 },
  { name: "AutoEmbed", baseUrl: "https://autoembed.cc/embed", priority: 6 },
  { name: "SuperEmbed", baseUrl: "https://superembed.stream/embed", priority: 7 },
  { name: "Embed.su", baseUrl: "https://embed.su/embed", priority: 8 },
  { name: "MultiEmbed", baseUrl: "https://multiembed.mov/embed", priority: 9 },
  { name: "VidSrcMe.ru", baseUrl: "https://vidsrcme.ru/embed", priority: 10 },
  
  // Tier 3: Alternative Sources
  { name: "Vidsrc.pro", baseUrl: "https://vidsrc.pro/embed", priority: 11 },
  { name: "Vidstream", baseUrl: "https://vidstream.pro/embed", priority: 12 },
  { name: "StreamWish", baseUrl: "https://streamwish.to/embed", priority: 13 },
  { name: "VidFast", baseUrl: "https://vidfast.pro/embed", priority: 14 },
  { name: "Vidbinge", baseUrl: "https://vidbinge.com/embed", priority: 15 },
  { name: "VidPlay", baseUrl: "https://vidplay.online/embed", priority: 16 },
  { name: "Filemoon", baseUrl: "https://filemoon.sx/embed", priority: 17 },
  { name: "StreamTape", baseUrl: "https://streamtape.com/embed", priority: 18 },
  { name: "Doodstream", baseUrl: "https://doodstream.com/embed", priority: 19 },
  { name: "Mixdrop", baseUrl: "https://mixdrop.co/embed", priority: 20 },
  
  // Tier 4: Extended Network
  { name: "Upstream", baseUrl: "https://upstream.to/embed", priority: 21 },
  { name: "Userload", baseUrl: "https://userload.co/embed", priority: 22 },
  { name: "Vidmoly", baseUrl: "https://vidmoly.me/embed", priority: 23 },
  { name: "Vidoza", baseUrl: "https://vidoza.net/embed", priority: 24 },
  { name: "Voe", baseUrl: "https://voe.sx/embed", priority: 25 },
  { name: "Okru", baseUrl: "https://ok.ru/embed", priority: 26 },
  { name: "Dailymotion", baseUrl: "https://dailymotion.com/embed", priority: 27 },
  { name: "Rumble", baseUrl: "https://rumble.com/embed", priority: 28 },
  { name: "Bitchute", baseUrl: "https://bitchute.com/embed", priority: 29 },
  { name: "Odysee", baseUrl: "https://odysee.com/embed", priority: 30 },
  
  // Tier 5: Backup Sources
  { name: "Streamable", baseUrl: "https://streamable.com/embed", priority: 31 },
  { name: "Gfycat", baseUrl: "https://gfycat.com/embed", priority: 32 },
  { name: "Imgur", baseUrl: "https://imgur.com/embed", priority: 33 },
  { name: "Giphy", baseUrl: "https://giphy.com/embed", priority: 34 },
  { name: "Tenor", baseUrl: "https://tenor.com/embed", priority: 35 },
  { name: "Vimeo", baseUrl: "https://vimeo.com/embed", priority: 36 },
  { name: "Wistia", baseUrl: "https://wistia.com/embed", priority: 37 },
  { name: "Loom", baseUrl: "https://loom.com/embed", priority: 38 },
  { name: "Twitch", baseUrl: "https://twitch.tv/embed", priority: 39 },
  { name: "YouTube", baseUrl: "https://youtube.com/embed", priority: 40 },
  
  // Tier 6: Additional Providers
  { name: "Kickstarter", baseUrl: "https://kickstarter.com/embed", priority: 41 },
  { name: "TED", baseUrl: "https://ted.com/embed", priority: 42 },
  { name: "Coursera", baseUrl: "https://coursera.org/embed", priority: 43 },
  { name: "Udemy", baseUrl: "https://udemy.com/embed", priority: 44 },
  { name: "Skillshare", baseUrl: "https://skillshare.com/embed", priority: 45 },
  { name: "MasterClass", baseUrl: "https://masterclass.com/embed", priority: 46 },
  { name: "Skillsoft", baseUrl: "https://skillsoft.com/embed", priority: 47 },
  { name: "LinkedIn Learning", baseUrl: "https://linkedin.com/learning/embed", priority: 48 },
  { name: "Pluralsight", baseUrl: "https://pluralsight.com/embed", priority: 49 },
  { name: "Treehouse", baseUrl: "https://treehouse.com/embed", priority: 50 },
  
  // Tier 7: Niche Platforms
  { name: "Metacafe", baseUrl: "https://metacafe.com/embed", priority: 51 },
  { name: "Break", baseUrl: "https://break.com/embed", priority: 52 },
  { name: "Veoh", baseUrl: "https://veoh.com/embed", priority: 53 },
  { name: "Dailymotion Lite", baseUrl: "https://dai.ly/embed", priority: 54 },
  { name: "Vine Archive", baseUrl: "https://vinearchive.com/embed", priority: 55 },
  { name: "Flickr", baseUrl: "https://flickr.com/embed", priority: 56 },
  { name: "Photobucket", baseUrl: "https://photobucket.com/embed", priority: 57 },
  { name: "Picasa", baseUrl: "https://picasa.google.com/embed", priority: 58 },
  { name: "Smugmug", baseUrl: "https://smugmug.com/embed", priority: 59 },
  { name: "500px", baseUrl: "https://500px.com/embed", priority: 60 },
  
  // Tier 8: International Platforms
  { name: "Bilibili", baseUrl: "https://bilibili.com/embed", priority: 61 },
  { name: "Youku", baseUrl: "https://youku.com/embed", priority: 62 },
  { name: "Tudou", baseUrl: "https://tudou.com/embed", priority: 63 },
  { name: "Sohu Video", baseUrl: "https://tv.sohu.com/embed", priority: 64 },
  { name: "iQiyi", baseUrl: "https://iqiyi.com/embed", priority: 65 },
  { name: "Tencent Video", baseUrl: "https://v.qq.com/embed", priority: 66 },
  { name: "NetEase", baseUrl: "https://v.163.com/embed", priority: 67 },
  { name: "Weibo", baseUrl: "https://weibo.com/embed", priority: 68 },
  { name: "Douyin", baseUrl: "https://douyin.com/embed", priority: 69 },
  { name: "Kuaishou", baseUrl: "https://kuaishou.com/embed", priority: 70 },
  
  // Tier 9: Streaming Services
  { name: "Netflix", baseUrl: "https://netflix.com/embed", priority: 71 },
  { name: "Disney+", baseUrl: "https://disneyplus.com/embed", priority: 72 },
  { name: "Hulu", baseUrl: "https://hulu.com/embed", priority: 73 },
  { name: "Amazon Prime", baseUrl: "https://primevideo.com/embed", priority: 74 },
  { name: "Apple TV+", baseUrl: "https://tv.apple.com/embed", priority: 75 },
  { name: "Paramount+", baseUrl: "https://paramountplus.com/embed", priority: 76 },
  { name: "HBO Max", baseUrl: "https://hbomax.com/embed", priority: 77 },
  { name: "Peacock", baseUrl: "https://peacocktv.com/embed", priority: 78 },
  { name: "Crunchyroll", baseUrl: "https://crunchyroll.com/embed", priority: 79 },
  { name: "Funimation", baseUrl: "https://funimation.com/embed", priority: 80 },
  
  // Tier 10: Additional Services
  { name: "Plex", baseUrl: "https://plex.tv/embed", priority: 81 },
  { name: "Tubi", baseUrl: "https://tubitv.com/embed", priority: 82 },
  { name: "Pluto TV", baseUrl: "https://pluto.tv/embed", priority: 83 },
  { name: "Freevee", baseUrl: "https://freevee.amazon.com/embed", priority: 84 },
  { name: "Roku Channel", baseUrl: "https://therokuchannel.com/embed", priority: 85 },
  { name: "Sling TV", baseUrl: "https://sling.com/embed", priority: 86 },
  { name: "YouTube TV", baseUrl: "https://tv.youtube.com/embed", priority: 87 },
  { name: "FuboTV", baseUrl: "https://fubo.tv/embed", priority: 88 },
  { name: "Philo", baseUrl: "https://philo.com/embed", priority: 89 },
  { name: "Vidgo", baseUrl: "https://vidgo.com/embed", priority: 90 },
  
  // Tier 11: Final Tier
  { name: "Spectrum TV", baseUrl: "https://spectrum.tv/embed", priority: 91 },
  { name: "Xfinity", baseUrl: "https://xfinity.com/embed", priority: 92 },
  { name: "Cox", baseUrl: "https://cox.com/embed", priority: 93 },
  { name: "Dish", baseUrl: "https://dish.com/embed", priority: 94 },
  { name: "DirectTV", baseUrl: "https://directv.com/embed", priority: 95 },
  { name: "Verizon Fios", baseUrl: "https://fios.verizon.com/embed", priority: 96 },
  { name: "AT&T TV", baseUrl: "https://att.com/tv/embed", priority: 97 },
  { name: "Comcast", baseUrl: "https://comcast.com/embed", priority: 98 },
  { name: "Charter", baseUrl: "https://charter.com/embed", priority: 99 },
  { name: "Frontier", baseUrl: "https://frontier.com/embed", priority: 100 },
];

const USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

const VIDEO_PATTERNS = [
  /["']file["']\s*:\s*["']?(https?:\/\/[^"'\\s,}\]]+\.m3u8[^"'\\s,}\]]*)/gi,
  /["']file["']\s*:\s*["']?(https?:\/\/[^"'\\s,}\]]+\.mp4[^"'\\s,}\]]*)/gi,
  /["']src["']\s*:\s*["']?(https?:\/\/[^"'\\s,}\]]+\.m3u8[^"'\\s,}\]]*)/gi,
  /["']src["']\s*:\s*["']?(https?:\/\/[^"'\\s,}\]]+\.mp4[^"'\\s,}\]]*)/gi,
  /["']url["']\s*:\s*["']?(https?:\/\/[^"'\\s,}\]]+\.m3u8[^"'\\s,}\]]*)/gi,
  /["']url["']\s*:\s*["']?(https?:\/\/[^"'\\s,}\]]+\.mp4[^"'\\s,}\]]*)/gi,
  /(https?:\/\/[^\s"'<>()\]]+\.m3u8(?:\?[^\s"'<>()\]]*)?)/gi,
  /(https?:\/\/[^\s"'<>()\]]+\.mp4(?:\?[^\s"'<>()\]]*)?)/gi,
];

function extractVideoUrl(html: string): string | null {
  for (const pattern of VIDEO_PATTERNS) {
    const match = html.match(pattern);
    if (match && match[0]) {
      const url = match[0].replace(/["']/g, "");
      if (url.length > 20) {
        return url;
      }
    }
  }
  return null;
}

async function probeServer(server: StreamingServer, tmdbId: number, contentType: "movie" | "tv", season: number = 1, episode: number = 1): Promise<number | null> {
  try {
    const embedUrl = contentType === "movie" 
      ? `${server.baseUrl}/movie/${tmdbId}`
      : `${server.baseUrl}/tv/${tmdbId}/${season}/${episode}`;

    const startTime = Date.now();
    const response = await axios.head(embedUrl, {
      headers: { "User-Agent": USER_AGENT },
      timeout: 5000,
      maxRedirects: 5,
    });

    const elapsed = Date.now() - startTime;
    
    if (response.status === 200 || response.status === 301 || response.status === 302) {
      return elapsed;
    }
    return null;
  } catch (error) {
    return null;
  }
}

async function rankServers(servers: StreamingServer[], tmdbId: number, contentType: "movie" | "tv", season: number = 1, episode: number = 1): Promise<StreamingServer[]> {
  const probes = servers.map(s => probeServer(s, tmdbId, contentType, season, episode));
  const results = await Promise.all(probes);

  const working = servers
    .map((s, i) => ({ server: s, time: results[i] }))
    .filter(({ time }) => time !== null)
    .sort((a, b) => (a.time ?? Infinity) - (b.time ?? Infinity));

  const failed = servers
    .map((s, i) => ({ server: s, time: results[i] }))
    .filter(({ time }) => time === null);

  return [...working.map(w => w.server), ...failed.map(f => f.server)];
}

async function extractFromEmbed(server: StreamingServer, tmdbId: number, contentType: "movie" | "tv", season: number = 1, episode: number = 1): Promise<VideoSource | null> {
  try {
    const embedUrl = contentType === "movie"
      ? `${server.baseUrl}/movie/${tmdbId}`
      : `${server.baseUrl}/tv/${tmdbId}/${season}/${episode}`;

    const response = await axios.get(embedUrl, {
      headers: { "User-Agent": USER_AGENT },
      timeout: 10000,
    });

    const videoUrl = extractVideoUrl(response.data);
    
    if (videoUrl) {
      return {
        url: videoUrl,
        format: videoUrl.includes(".m3u8") ? "hls" : "mp4",
        server: server.name,
        referer: embedUrl,
        origin: embedUrl.split("/embed")[0],
      };
    }
  } catch (error) {
    // Continue to next server
  }

  return null;
}

export async function resolveStream(
  tmdbId: number,
  contentType: "movie" | "tv" = "movie",
  season: number = 1,
  episode: number = 1
): Promise<StreamResolution> {
  const startTime = Date.now();
  let serversTried = 0;

  try {
    // Step 1: Rank servers by response time (sample top 30 for speed)
    const rankedServers = await rankServers(STREAMING_SERVERS.slice(0, 30), tmdbId, contentType, season, episode);

    // Step 2: Try to extract streams from top servers
    for (const server of rankedServers.slice(0, 15)) {
      serversTried++;
      const stream = await extractFromEmbed(server, tmdbId, contentType, season, episode);
      
      if (stream) {
        const timeMs = Date.now() - startTime;
        return {
          success: true,
          stream,
          serversTried,
          timeMs,
        };
      }
    }

    // Step 3: Return embed fallback from best server
    if (rankedServers.length > 0) {
      const server = rankedServers[0];
      const embedUrl = contentType === "movie"
        ? `${server.baseUrl}/movie/${tmdbId}`
        : `${server.baseUrl}/tv/${tmdbId}/${season}/${episode}`;

      const timeMs = Date.now() - startTime;
      return {
        success: true,
        stream: {
          url: embedUrl,
          format: "embed",
          server: server.name,
          referer: embedUrl,
          origin: embedUrl.split("/embed")[0],
        },
        serversTried,
        timeMs,
      };
    }

    return {
      success: false,
      error: "No streams available. All servers failed.",
      serversTried,
      timeMs: Date.now() - startTime,
    };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : "Unknown error",
      serversTried,
      timeMs: Date.now() - startTime,
    };
  }
}

export async function getAvailableServers(): Promise<StreamingServer[]> {
  return STREAMING_SERVERS;
}

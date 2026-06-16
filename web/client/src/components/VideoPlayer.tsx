import { useEffect, useRef, useState } from "react";
import HLS from "hls.js";
import { trpc } from "@/lib/trpc";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Loader2, X, AlertCircle, Server, RotateCcw } from "lucide-react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { toast } from "sonner";

interface VideoPlayerProps {
  tmdbId: number;
  contentType: "movie" | "tv";
  title: string;
  season?: number;
  episode?: number;
  onClose?: () => void;
}

export function VideoPlayer({ tmdbId, contentType, title, season = 1, episode = 1, onClose }: VideoPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<HLS | null>(null);
  const [selectedServer, setSelectedServer] = useState<string>("");
  const [isRetrying, setIsRetrying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);

  // Fetch stream
  const streamQuery = trpc.streams.resolveStream.useQuery(
    {
      tmdbId,
      contentType,
      season,
      episode,
    },
    {
      enabled: !!tmdbId,
      retry: 1,
    }
  );

  // Fetch available servers
  const serversQuery = trpc.streams.getAvailableServers.useQuery();

  // Handle stream resolution and playback
  useEffect(() => {
    if (!streamQuery.data?.success || !streamQuery.data.stream) return;

    const stream = streamQuery.data.stream;
    setSelectedServer(stream.server);
    setError(null);

    if (!videoRef.current) return;

    // Clean up existing HLS instance
    if (hlsRef.current) {
      hlsRef.current.destroy();
      hlsRef.current = null;
    }

    try {
      if (stream.format === "embed") {
        // Handle embed fallback
        setError(`Using embed player from ${stream.server}`);
      } else if (stream.format === "hls") {
        // HLS stream with hls.js
        if (HLS.isSupported()) {
          const hls = new HLS({
            debug: false,
            enableWorker: true,
          });

          hlsRef.current = hls;

          // Set CORS headers
          hls.on(HLS.Events.MANIFEST_PARSED, () => {
            if (videoRef.current) {
              videoRef.current.play().catch(() => {
                // Autoplay might be blocked
              });
            }
          });

          hls.on(HLS.Events.ERROR, (event: any, data: any) => {
            if (data.fatal) {
              switch (data.type) {
                case HLS.ErrorTypes.NETWORK_ERROR:
                  setError("Network error. Trying fallback...");
                  break;
                case HLS.ErrorTypes.MEDIA_ERROR:
                  setError("Media error. Trying fallback...");
                  hls.recoverMediaError();
                  break;
                default:
                  setError(`HLS Error: ${data.reason || 'Unknown'}`);
                  break;
              }
            }
          });

          hls.loadSource(stream.url);
          hls.attachMedia(videoRef.current);
        } else if (videoRef.current.canPlayType("application/vnd.apple.mpegurl")) {
          // Native HLS support (Safari)
          videoRef.current.src = stream.url;
        } else {
          setError("HLS not supported in this browser");
        }
      } else if (stream.format === "mp4") {
        // MP4 stream
        const source = document.createElement("source");
        source.src = stream.url;
        source.type = "video/mp4";
        videoRef.current.appendChild(source);
        videoRef.current.load();
      }

      // Set referer for CORS
      if (stream.referer && videoRef.current) {
        videoRef.current.setAttribute("data-referer", stream.referer);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load stream");
    }
  }, [streamQuery.data]);

  const handleRetry = async () => {
    setIsRetrying(true);
    await streamQuery.refetch();
    setIsRetrying(false);
    toast.success("Retrying stream resolution...");
  };

  const stream = streamQuery.data?.stream;
  const isLoading = streamQuery.isLoading;
  const serversTried = streamQuery.data?.serversTried || 0;
  const timeMs = streamQuery.data?.timeMs || 0;

  return (
    <div className="relative w-full bg-black rounded-sm border-2 border-cyan-400 overflow-hidden">
      {/* Close Button */}
      {onClose && (
        <button
          onClick={onClose}
          className="absolute top-2 right-2 z-50 p-2 bg-black/80 hover:bg-pink-500 rounded-sm transition-colors"
        >
          <X className="w-5 h-5 text-white" />
        </button>
      )}

      {/* Video Container */}
      <div className="relative bg-black aspect-video">
        {isLoading ? (
          <div className="absolute inset-0 flex items-center justify-center bg-black/80">
            <div className="text-center">
              <Loader2 className="w-12 h-12 animate-spin text-cyan-400 mx-auto mb-4" />
              <p className="text-cyan-400 font-cyberpunk">Resolving stream...</p>
              <p className="text-gray-400 text-sm mt-2">Probing {serversTried} servers</p>
            </div>
          </div>
        ) : error && !stream ? (
          <div className="absolute inset-0 flex items-center justify-center bg-black/80">
            <div className="text-center max-w-md">
              <AlertCircle className="w-12 h-12 text-red-500 mx-auto mb-4" />
              <p className="text-red-400 font-cyberpunk mb-2">Stream Error</p>
              <p className="text-gray-400 text-sm">{error}</p>
            </div>
          </div>
        ) : stream && stream.format !== "embed" ? (
          <video
            ref={videoRef}
            controls
            autoPlay
            className="w-full h-full"
            crossOrigin="anonymous"
            onPlay={() => setIsPlaying(true)}
            onPause={() => setIsPlaying(false)}
          />
        ) : stream && stream.format === "embed" ? (
          <iframe
            src={stream.url}
            className="w-full h-full border-0"
            allowFullScreen
            allow="autoplay"
          />
        ) : null}
      </div>

      {/* Controls */}
      <div className="bg-gray-900 border-t-2 border-cyan-400 p-4 space-y-3">
        {/* Title and Info */}
        <div>
          <h3 className="text-cyberpunk text-lg mb-1">{title}</h3>
          {contentType === "tv" && (
            <p className="text-sm text-gray-400">
              Season {season} • Episode {episode}
            </p>
          )}
          <p className="text-xs text-gray-500 mt-1">
            Resolved in {timeMs}ms from {serversTried} server{serversTried !== 1 ? "s" : ""}
          </p>
        </div>

        {/* Server Selector */}
        {serversQuery.data && (
          <div className="flex items-center gap-2">
            <Server className="w-4 h-4 text-cyan-400" />
            <Select value={selectedServer} onValueChange={setSelectedServer}>
              <SelectTrigger className="w-full bg-gray-800 border-cyan-400 text-white">
                <SelectValue placeholder="Select server" />
              </SelectTrigger>
              <SelectContent className="bg-gray-800 border-cyan-400 max-h-60">
                {serversQuery.data.slice(0, 50).map((server) => (
                  <SelectItem key={server.name} value={server.name} className="text-white hover:bg-pink-500">
                    {server.name} (P:{server.priority})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        {/* Error Alert */}
        {error && stream && (
          <Alert className="bg-yellow-900/20 border-yellow-600 text-yellow-400">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {/* Action Buttons */}
        <div className="flex gap-2">
          <Button
            className="btn-neon-cyan flex-1"
            onClick={() => {
              if (videoRef.current) {
                videoRef.current.play();
              }
            }}
            disabled={isLoading}
          >
            Play
          </Button>
          <Button
            variant="outline"
            className="btn-neon flex-1"
            onClick={() => {
              if (videoRef.current) {
                videoRef.current.pause();
              }
            }}
            disabled={isLoading}
          >
            Pause
          </Button>
          <Button
            size="sm"
            variant="outline"
            className="btn-neon"
            onClick={handleRetry}
            disabled={isRetrying || isLoading}
          >
            <RotateCcw className="w-4 h-4" />
          </Button>
        </div>
      </div>
    </div>
  );
}

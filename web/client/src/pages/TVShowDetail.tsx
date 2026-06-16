import { useState } from "react";
import { useParams, useLocation } from "wouter";
import { trpc } from "@/lib/trpc";
import { VideoPlayer } from "@/components/VideoPlayer";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Heart, Plus, Play } from "lucide-react";

export default function TVShowDetail() {
  const params = useParams();
  const showId = parseInt(params.id || "0");
  const [selectedSeason, setSelectedSeason] = useState(1);
  const [selectedEpisode, setSelectedEpisode] = useState(1);
  const [showPlayer, setShowPlayer] = useState(false);
  const [, navigate] = useLocation();

  // Fetch show details
  const showQuery = trpc.content.getTVShowDetails.useQuery({ showId });
  const seasonQuery = trpc.content.getTVSeasonDetails.useQuery(
    { showId, seasonNumber: selectedSeason },
    { enabled: !!showId }
  );

  const show = showQuery.data;
  const season = seasonQuery.data;
  const episodes = season?.episodes || [];

  if (showQuery.isLoading) {
    return (
      <div className="min-h-screen bg-background p-4">
        <Skeleton className="h-96 w-full mb-4" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }

  if (!show) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <p className="text-red-400 font-cyberpunk">Show not found</p>
      </div>
    );
  }

  const posterUrl = show.poster_path
    ? `https://image.tmdb.org/t/p/w500${show.poster_path}`
    : "/placeholder.jpg";

  const backdropUrl = show.backdrop_path
    ? `https://image.tmdb.org/t/p/w1280${show.backdrop_path}`
    : "";

  return (
    <div className="min-h-screen bg-background">
      {/* Hero Section */}
      {backdropUrl && (
        <div
          className="relative h-96 bg-cover bg-center"
          style={{ backgroundImage: `url(${backdropUrl})` }}
        >
          <div className="absolute inset-0 bg-gradient-to-b from-transparent to-background" />
        </div>
      )}

      {/* Content */}
      <div className="container mx-auto px-4 -mt-32 relative z-10 mb-12">
        <div className="flex flex-col md:flex-row gap-6">
          {/* Poster */}
          <div className="flex-shrink-0">
            <img
              src={posterUrl}
              alt={show.name}
              className="w-48 h-72 object-cover rounded-sm border-2 border-cyan-400 shadow-lg shadow-cyan-400/50"
            />
          </div>

          {/* Info */}
          <div className="flex-1">
            <h1 className="text-4xl md:text-5xl font-cyberpunk text-pink-500 mb-2">{show.name}</h1>

            {/* Meta */}
            <div className="flex flex-wrap gap-4 mb-4 text-sm">
              {show.first_air_date && (
                <span className="text-cyan-400">{new Date(show.first_air_date).getFullYear()}</span>
              )}
              {show.number_of_seasons && (
                <span className="text-cyan-400">{show.number_of_seasons} Seasons</span>
              )}
              {show.vote_average && (
                <span className="text-pink-500">★ {show.vote_average.toFixed(1)}</span>
              )}
            </div>

            {/* Genres */}
            {show.genres && show.genres.length > 0 && (
              <div className="flex flex-wrap gap-2 mb-4">
                {show.genres.map((genre) => (
                  <span
                    key={genre.id}
                    className="px-3 py-1 text-xs border-2 border-cyan-400 text-cyan-400 rounded-sm"
                  >
                    {genre.name}
                  </span>
                ))}
              </div>
            )}

            {/* Overview */}
            <p className="text-gray-300 mb-6 leading-relaxed">{show.overview}</p>

            {/* Action Buttons */}
            <div className="flex gap-3 mb-6">
              <Button className="btn-neon-cyan px-6 py-2" onClick={() => setShowPlayer(true)}>
                <Play className="w-4 h-4 mr-2" />
                Play
              </Button>
              <Button variant="outline" className="btn-neon px-6 py-2">
                <Heart className="w-4 h-4 mr-2" />
                Favorite
              </Button>
              <Button variant="outline" className="btn-neon px-6 py-2">
                <Plus className="w-4 h-4 mr-2" />
                Watchlist
              </Button>
            </div>
          </div>
        </div>
      </div>

      {/* Video Player */}
      {showPlayer && (
        <div className="container mx-auto px-4 mb-12">
          <VideoPlayer
            tmdbId={showId}
            contentType="tv"
            title={`${show.name} - S${selectedSeason}E${selectedEpisode}`}
            season={selectedSeason}
            episode={selectedEpisode}
            onClose={() => setShowPlayer(false)}
          />
        </div>
      )}

      {/* Season & Episode Selector */}
      <div className="container mx-auto px-4 mb-12">
        <div className="card-neon p-6">
          <h2 className="text-cyberpunk text-2xl mb-6">Episodes</h2>

          {/* Season Selector */}
          <div className="mb-6">
            <label className="block text-sm font-cyberpunk text-cyan-400 mb-2">Season</label>
            <Select value={selectedSeason.toString()} onValueChange={(v) => setSelectedSeason(parseInt(v))}>
              <SelectTrigger className="bg-gray-800 border-cyan-400 text-white">
                <SelectValue />
              </SelectTrigger>
              <SelectContent className="bg-gray-800 border-cyan-400">
                {show.seasons?.map((s) => (
                  <SelectItem key={s.season_number} value={s.season_number.toString()} className="text-white">
                    Season {s.season_number}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Episodes Grid */}
          {seasonQuery.isLoading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-48 bg-gray-700" />
              ))}
            </div>
          ) : episodes.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {episodes.map((ep) => (
                <div
                  key={ep.episode_number}
                  onClick={() => {
                    setSelectedEpisode(ep.episode_number);
                    setShowPlayer(true);
                  }}
                  className="group cursor-pointer border-2 border-cyan-400 rounded-sm overflow-hidden hover:border-pink-500 transition-all"
                >
                  {ep.still_path && (
                    <img
                      src={`https://image.tmdb.org/t/p/w300${ep.still_path}`}
                      alt={ep.name}
                      className="w-full h-32 object-cover group-hover:scale-110 transition-transform"
                    />
                  )}
                  <div className="p-3 bg-gray-800">
                    <h3 className="font-cyberpunk text-cyan-400 text-sm mb-1">
                      Ep {ep.episode_number}: {ep.name}
                    </h3>
                    <p className="text-xs text-gray-400 line-clamp-2">{ep.overview}</p>
                    {ep.vote_average && (
                      <p className="text-xs text-pink-500 mt-2">★ {ep.vote_average.toFixed(1)}</p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-gray-400">No episodes available</p>
          )}
        </div>
      </div>
    </div>
  );
}

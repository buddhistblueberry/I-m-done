import { useState } from "react";
import { useParams } from "wouter";
import { trpc } from "@/lib/trpc";
import { VideoPlayer } from "@/components/VideoPlayer";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Heart, Plus, Play } from "lucide-react";

export default function MovieDetail() {
  const params = useParams();
  const movieId = parseInt(params.id || "0");
  const [showPlayer, setShowPlayer] = useState(false);

  // Fetch movie details
  const movieQuery = trpc.content.getMovieDetails.useQuery({ movieId });
  const recommendationsQuery = trpc.content.getMovieRecommendations.useQuery({ movieId });

  const movie = movieQuery.data;
  const recommendations = recommendationsQuery.data;

  if (movieQuery.isLoading) {
    return (
      <div className="min-h-screen bg-background p-4">
        <Skeleton className="h-96 w-full mb-4" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }

  if (!movie) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <p className="text-red-400 font-cyberpunk">Movie not found</p>
      </div>
    );
  }

  const posterUrl = movie.poster_path
    ? `https://image.tmdb.org/t/p/w500${movie.poster_path}`
    : "/placeholder.jpg";

  const backdropUrl = movie.backdrop_path
    ? `https://image.tmdb.org/t/p/w1280${movie.backdrop_path}`
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
              alt={movie.title}
              className="w-48 h-72 object-cover rounded-sm border-2 border-cyan-400 shadow-lg shadow-cyan-400/50"
            />
          </div>

          {/* Info */}
          <div className="flex-1">
            <h1 className="text-4xl md:text-5xl font-cyberpunk text-pink-500 mb-2">{movie.title}</h1>

            {/* Meta */}
            <div className="flex flex-wrap gap-4 mb-4 text-sm">
              {movie.release_date && (
                <span className="text-cyan-400">{new Date(movie.release_date).getFullYear()}</span>
              )}
              {movie.runtime && (
                <span className="text-cyan-400">{movie.runtime} min</span>
              )}
              {movie.vote_average && (
                <span className="text-pink-500">★ {movie.vote_average.toFixed(1)}</span>
              )}
            </div>

            {/* Genres */}
            {movie.genres && movie.genres.length > 0 && (
              <div className="flex flex-wrap gap-2 mb-4">
                {movie.genres.map((genre) => (
                  <span
                    key={genre.id}
                    className="px-3 py-1 text-xs border-2 border-cyan-400 text-cyan-400 rounded-sm"
                  >
                    {genre.name}
                  </span>
                ))}
              </div>
            )}

            {/* Tagline */}
            {movie.tagline && (
              <p className="text-lg text-pink-400 italic mb-4">"{movie.tagline}"</p>
            )}

            {/* Overview */}
            <p className="text-gray-300 mb-6 leading-relaxed">{movie.overview}</p>

            {/* Stats */}
            <div className="grid grid-cols-3 gap-4 mb-6 p-4 bg-gray-800 rounded-sm border-2 border-cyan-400">
              {movie.budget > 0 && (
                <div>
                  <p className="text-xs text-gray-400">Budget</p>
                  <p className="font-cyberpunk text-cyan-400">${(movie.budget / 1000000).toFixed(1)}M</p>
                </div>
              )}
              {movie.revenue > 0 && (
                <div>
                  <p className="text-xs text-gray-400">Revenue</p>
                  <p className="font-cyberpunk text-cyan-400">${(movie.revenue / 1000000).toFixed(1)}M</p>
                </div>
              )}
              {movie.popularity && (
                <div>
                  <p className="text-xs text-gray-400">Popularity</p>
                  <p className="font-cyberpunk text-pink-500">{movie.popularity.toFixed(0)}</p>
                </div>
              )}
            </div>

            {/* Action Buttons */}
            <div className="flex gap-3">
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
            tmdbId={movieId}
            contentType="movie"
            title={movie.title}
            onClose={() => setShowPlayer(false)}
          />
        </div>
      )}

      {/* Recommendations */}
      {recommendations && recommendations.length > 0 && (
        <div className="container mx-auto px-4 mb-12">
          <h2 className="text-cyberpunk text-3xl mb-6">Similar Movies</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-5 gap-4">
            {recommendations.slice(0, 10).map((rec) => (
              <a
                key={rec.id}
                href={`/movie/${rec.id}`}
                className="group relative h-64 overflow-hidden rounded-sm border-2 border-cyan-400 hover:border-pink-500 transition-all"
              >
                {rec.poster_path && (
                  <img
                    src={`https://image.tmdb.org/t/p/w300${rec.poster_path}`}
                    alt={rec.title}
                    className="h-full w-full object-cover group-hover:scale-110 transition-transform"
                  />
                )}
                <div className="absolute inset-0 bg-gradient-to-t from-black to-transparent opacity-0 group-hover:opacity-100 transition-opacity">
                  <div className="absolute bottom-0 left-0 right-0 p-2">
                    <p className="text-xs font-cyberpunk text-cyan-400 line-clamp-2">{rec.title}</p>
                  </div>
                </div>
              </a>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

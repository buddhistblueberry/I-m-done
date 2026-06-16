import { useState, useCallback, useEffect } from "react";
import { trpc } from "@/lib/trpc";
import { Link } from "wouter";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Play, Search as SearchIcon } from "lucide-react";

interface SearchResult {
  id: number;
  title?: string;
  name?: string;
  poster_path?: string | null;
  overview?: string;
  vote_average?: number;
  media_type?: string;
}

export default function Search() {
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");

  // Debounce search query
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(query);
    }, 300);
    return () => clearTimeout(timer);
  }, [query]);

  // Fetch search results
  const searchQuery = trpc.content.searchMulti.useQuery(
    { query: debouncedQuery, page: 1 },
    { enabled: debouncedQuery.length > 0 }
  );

  const results = searchQuery.data || [];
  const isLoading = searchQuery.isLoading;

  const getMediaType = (item: SearchResult): "movie" | "tv" => {
    if (item.media_type === "tv") return "tv";
    if (item.title) return "movie";
    return "tv";
  };

  const getTitle = (item: SearchResult) => item.title || item.name || "Unknown";

  return (
    <div className="min-h-screen bg-background">
      {/* Search Header */}
      <div className="bg-gradient-to-b from-gray-900 to-background py-12 border-b-2 border-cyan-400">
        <div className="container mx-auto px-4">
          <h1 className="text-cyberpunk text-4xl mb-6">Search</h1>

          {/* Search Input */}
          <div className="relative max-w-2xl">
            <SearchIcon className="absolute left-4 top-3 w-5 h-5 text-cyan-400" />
            <Input
              type="text"
              placeholder="Search movies and TV shows..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="pl-12 py-3 bg-gray-800 border-2 border-cyan-400 text-white placeholder-gray-500 rounded-sm focus:border-pink-500 transition-colors"
            />
          </div>

          {/* Search Info */}
          {debouncedQuery && (
            <p className="text-sm text-gray-400 mt-3">
              {isLoading ? "Searching..." : `Found ${results.length} result${results.length !== 1 ? "s" : ""}`}
            </p>
          )}
        </div>
      </div>

      {/* Results */}
      <div className="container mx-auto px-4 py-12">
        {!debouncedQuery ? (
          <div className="text-center py-12">
            <SearchIcon className="w-16 h-16 text-gray-600 mx-auto mb-4" />
            <p className="text-gray-400 text-lg">Start typing to search for movies and TV shows</p>
          </div>
        ) : isLoading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-5 gap-4">
            {Array.from({ length: 10 }).map((_, i) => (
              <Skeleton key={i} className="h-80 rounded-sm bg-gray-800" />
            ))}
          </div>
        ) : results.length > 0 ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-5 gap-4">
            {results.map((item) => {
              const mediaType = getMediaType(item);
              const title = getTitle(item);
              const posterUrl = item.poster_path
                ? `https://image.tmdb.org/t/p/w500${item.poster_path}`
                : "/placeholder.jpg";

              return (
                <Link key={`${mediaType}-${item.id}`} href={`/${mediaType}/${item.id}`}>
                  <div className="group relative h-80 overflow-hidden rounded-sm border-2 border-cyan-400 cursor-pointer transition-all duration-300 hover:border-pink-500 hover:shadow-lg hover:shadow-pink-500/50">
                    {/* Poster Image */}
                    <img
                      src={posterUrl}
                      alt={title}
                      className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-110"
                    />

                    {/* Overlay */}
                    <div className="absolute inset-0 bg-gradient-to-t from-black via-transparent to-transparent opacity-0 transition-opacity duration-300 group-hover:opacity-100">
                      {/* Content */}
                      <div className="absolute bottom-0 left-0 right-0 p-4">
                        <h3 className="font-cyberpunk text-lg text-cyan-400 line-clamp-2 mb-2">{title}</h3>
                        <p className="text-sm text-gray-300 line-clamp-2 mb-3">{item.overview}</p>

                        {/* Rating */}
                        {item.vote_average && (
                          <div className="flex items-center gap-2 mb-3">
                            <span className="text-xs font-bold text-pink-500">★ {item.vote_average.toFixed(1)}</span>
                          </div>
                        )}

                        {/* Media Type Badge */}
                        <div className="flex items-center gap-2 mb-3">
                          <span className="px-2 py-1 text-xs border-2 border-cyan-400 text-cyan-400 rounded-sm">
                            {mediaType === "movie" ? "MOVIE" : "TV SHOW"}
                          </span>
                        </div>

                        {/* Actions */}
                        <Button size="sm" className="btn-neon-cyan w-full h-8 text-xs">
                          <Play className="w-3 h-3 mr-1" />
                          Play
                        </Button>
                      </div>
                    </div>

                    {/* HUD Corner */}
                    <div className="absolute top-2 right-2 w-4 h-4 border-t-2 border-r-2 border-pink-500 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </div>
                </Link>
              );
            })}
          </div>
        ) : (
          <div className="text-center py-12">
            <p className="text-gray-400 text-lg">No results found for "{debouncedQuery}"</p>
            <p className="text-gray-500 text-sm mt-2">Try searching with different keywords</p>
          </div>
        )}
      </div>
    </div>
  );
}

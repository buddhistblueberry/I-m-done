import { useEffect, useState } from "react";
import { trpc } from "@/lib/trpc";
import { Link } from "wouter";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { Play, Heart } from "lucide-react";

interface ContentItem {
  id: number;
  title?: string;
  name?: string;
  poster_path?: string | null;
  backdrop_path?: string | null;
  overview?: string;
  vote_average?: number;
  release_date?: string;
  first_air_date?: string;
  media_type?: string;
}

function ContentCard({ item, type }: { item: ContentItem; type: "movie" | "tv" }) {
  const title = item.title || item.name || "Unknown";
  const posterUrl = item.poster_path
    ? `https://image.tmdb.org/t/p/w500${item.poster_path}`
    : "/placeholder.jpg";

  return (
    <Link href={`/${type}/${item.id}`}>
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

            {/* Actions */}
            <div className="flex gap-2">
              <Button size="sm" className="btn-neon-cyan flex-1 h-8 text-xs">
                <Play className="w-3 h-3 mr-1" />
                Play
              </Button>
              <Button size="sm" variant="outline" className="h-8 px-2">
                <Heart className="w-3 h-3" />
              </Button>
            </div>
          </div>
        </div>

        {/* HUD Corner */}
        <div className="absolute top-2 right-2 w-4 h-4 border-t-2 border-r-2 border-pink-500 opacity-0 group-hover:opacity-100 transition-opacity" />
      </div>
    </Link>
  );
}

function ContentSection({ title, data, isLoading, type }: { title: string; data: ContentItem[] | null; isLoading: boolean; type: "movie" | "tv" }) {
  return (
    <section className="mb-12">
      <h2 className="text-cyberpunk text-3xl mb-6 font-bold">{title}</h2>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-5 gap-4">
        {isLoading ? (
          Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-80 rounded-sm bg-gray-800" />
          ))
        ) : data && data.length > 0 ? (
          data.slice(0, 10).map((item) => (
            <ContentCard key={item.id} item={item} type={type} />
          ))
        ) : (
          <p className="text-gray-400">No content available</p>
        )}
      </div>
    </section>
  );
}

export default function Home() {
  const [scrollY, setScrollY] = useState(0);

  // Fetch content
  const trendingMovies = trpc.content.getTrendingMovies.useQuery({ page: 1 });
  const nowPlayingMovies = trpc.content.getNowPlayingMovies.useQuery({ page: 1 });
  const popularTVShows = trpc.content.getPopularTVShows.useQuery({ page: 1 });

  // Parallax effect
  useEffect(() => {
    const handleScroll = () => setScrollY(window.scrollY);
    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  // Hero backdrop
  const heroBackdrop = nowPlayingMovies.data?.[0]?.backdrop_path
    ? `https://image.tmdb.org/t/p/w1280${nowPlayingMovies.data[0].backdrop_path}`
    : "linear-gradient(135deg, #0a0e27 0%, #1a1f3a 100%)";

  return (
    <div className="min-h-screen bg-background">
      {/* Hero Section */}
      <section
        className="relative h-screen flex items-center justify-center overflow-hidden"
        style={{
          backgroundImage: `url(${heroBackdrop})`,
          backgroundSize: "cover",
          backgroundPosition: "center",
          backgroundAttachment: "fixed",
          transform: `translateY(${scrollY * 0.5}px)`,
        }}
      >
        {/* Overlay */}
        <div className="absolute inset-0 bg-gradient-to-r from-black via-black/50 to-transparent" />
        <div className="absolute inset-0 scan-lines" />

        {/* Content */}
        <div className="relative z-10 container mx-auto px-4 text-left">
          <div className="max-w-2xl">
            <h1 className="text-6xl md:text-7xl font-bold mb-4 text-cyberpunk">
              I-m-done
            </h1>
            <p className="text-2xl md:text-3xl text-cyan-400 mb-6 font-cyberpunk">
              STREAMING PLATFORM
            </p>
            <p className="text-lg text-gray-300 mb-8 max-w-xl">
              Experience movies and TV shows from 100+ streaming servers. Powered by advanced stream resolution and a futuristic interface.
            </p>

            <div className="flex gap-4">
              <Button className="btn-neon-cyan px-8 py-3 text-lg">
                <Play className="w-5 h-5 mr-2" />
                Start Watching
              </Button>
              <Button variant="outline" className="btn-neon px-8 py-3 text-lg">
                Explore
              </Button>
            </div>
          </div>
        </div>

        {/* Animated Border */}
        <div className="absolute bottom-0 left-0 right-0 h-1 bg-gradient-to-r from-pink-500 via-cyan-400 to-pink-500 animate-pulse" />
      </section>

      {/* Content Sections */}
      <div className="container mx-auto px-4 py-16">
        <ContentSection
          title="🔥 Trending Now"
          data={trendingMovies.data || null}
          isLoading={trendingMovies.isLoading}
          type="movie"
        />

        <ContentSection
          title="🎬 Now Playing"
          data={nowPlayingMovies.data || null}
          isLoading={nowPlayingMovies.isLoading}
          type="movie"
        />

        <ContentSection
          title="📺 Popular TV Shows"
          data={popularTVShows.data || null}
          isLoading={popularTVShows.isLoading}
          type="tv"
        />
      </div>
    </div>
  );
}

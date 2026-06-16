/**
 * TMDB API Integration Service
 * Handles all interactions with The Movie Database API
 */

import { ENV } from './_core/env';

const TMDB_BASE_URL = "https://api.themoviedb.org/3";
const TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p";

interface TMDBResponse<T> {
  results?: T[];
  data?: T;
  total_pages?: number;
  total_results?: number;
  page?: number;
  [key: string]: any;
}

interface Movie {
  id: number;
  title: string;
  overview: string;
  poster_path: string | null;
  backdrop_path: string | null;
  release_date: string;
  vote_average: number;
  vote_count: number;
  popularity: number;
  genre_ids: number[];
  adult: boolean;
  original_language: string;
}

interface TVShow {
  id: number;
  name: string;
  overview: string;
  poster_path: string | null;
  backdrop_path: string | null;
  first_air_date: string;
  vote_average: number;
  vote_count: number;
  popularity: number;
  genre_ids: number[];
  original_language: string;
  number_of_seasons: number;
  number_of_episodes: number;
}

interface TVSeason {
  id: number;
  season_number: number;
  name: string;
  overview: string;
  poster_path: string | null;
  episode_count: number;
  air_date: string;
}

interface TVEpisode {
  id: number;
  episode_number: number;
  season_number: number;
  name: string;
  overview: string;
  still_path: string | null;
  air_date: string;
  vote_average: number;
  runtime: number;
}

interface MovieDetails extends Movie {
  runtime: number;
  genres: Array<{ id: number; name: string }>;
  production_companies: Array<{ id: number; name: string; logo_path: string | null }>;
  production_countries: Array<{ iso_3166_1: string; name: string }>;
  revenue: number;
  budget: number;
  status: string;
  tagline: string;
}

interface TVShowDetails extends TVShow {
  genres: Array<{ id: number; name: string }>;
  production_companies: Array<{ id: number; name: string; logo_path: string | null }>;
  production_countries: Array<{ iso_3166_1: string; name: string }>;
  seasons: TVSeason[];
  status: string;
  tagline: string;
  type: string;
}

interface TVSeasonDetails {
  id: number;
  season_number: number;
  name: string;
  overview: string;
  poster_path: string | null;
  air_date: string;
  episodes: TVEpisode[];
}

async function fetchFromTMDB<T>(endpoint: string, params: Record<string, any> = {}): Promise<T | null> {
  try {
    const url = new URL(`${TMDB_BASE_URL}${endpoint}`);
    url.searchParams.append("api_key", ENV.tmdbApiKey || "");
    
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        url.searchParams.append(key, String(value));
      }
    });

    const response = await fetch(url.toString(), {
      headers: {
        "Accept": "application/json",
      },
    });

    if (!response.ok) {
      console.error(`[TMDB] API error: ${response.status} ${response.statusText}`);
      return null;
    }

    return await response.json() as T;
  } catch (error) {
    console.error("[TMDB] Fetch error:", error);
    return null;
  }
}

export function getImageUrl(path: string | null, size: "w92" | "w154" | "w185" | "w342" | "w500" | "w780" | "original" = "w500"): string | null {
  if (!path) return null;
  return `${TMDB_IMAGE_BASE}/${size}${path}`;
}

// ─────────────────────────────────────────────────────────────────────────────
// Movies
// ─────────────────────────────────────────────────────────────────────────────

export async function getTrendingMovies(page: number = 1): Promise<Movie[] | null> {
  const data = await fetchFromTMDB<TMDBResponse<Movie>>("/trending/movie/day", { page });
  return data?.results || null;
}

export async function getNowPlayingMovies(page: number = 1): Promise<Movie[] | null> {
  const data = await fetchFromTMDB<TMDBResponse<Movie>>("/movie/now_playing", { page });
  return data?.results || null;
}

export async function getPopularMovies(page: number = 1): Promise<Movie[] | null> {
  const data = await fetchFromTMDB<TMDBResponse<Movie>>("/movie/popular", { page });
  return data?.results || null;
}

export async function getTopRatedMovies(page: number = 1): Promise<Movie[] | null> {
  const data = await fetchFromTMDB<TMDBResponse<Movie>>("/movie/top_rated", { page });
  return data?.results || null;
}

export async function getMovieDetails(movieId: number): Promise<MovieDetails | null> {
  return fetchFromTMDB<MovieDetails>(`/movie/${movieId}`);
}

export async function searchMovies(query: string, page: number = 1): Promise<Movie[] | null> {
  const data = await fetchFromTMDB<TMDBResponse<Movie>>("/search/movie", { query, page });
  return data?.results || null;
}

export async function getMovieRecommendations(movieId: number, page: number = 1): Promise<Movie[] | null> {
  const data = await fetchFromTMDB<TMDBResponse<Movie>>(`/movie/${movieId}/recommendations`, { page });
  return data?.results || null;
}

// ─────────────────────────────────────────────────────────────────────────────
// TV Shows
// ─────────────────────────────────────────────────────────────────────────────

export async function getTrendingTVShows(page: number = 1): Promise<TVShow[] | null> {
  const data = await fetchFromTMDB<TMDBResponse<TVShow>>("/trending/tv/day", { page });
  return data?.results || null;
}

export async function getPopularTVShows(page: number = 1): Promise<TVShow[] | null> {
  const data = await fetchFromTMDB<TMDBResponse<TVShow>>("/tv/popular", { page });
  return data?.results || null;
}

export async function getTopRatedTVShows(page: number = 1): Promise<TVShow[] | null> {
  const data = await fetchFromTMDB<TMDBResponse<TVShow>>("/tv/top_rated", { page });
  return data?.results || null;
}

export async function getTVShowDetails(showId: number): Promise<TVShowDetails | null> {
  return fetchFromTMDB<TVShowDetails>(`/tv/${showId}`);
}

export async function searchTVShows(query: string, page: number = 1): Promise<TVShow[] | null> {
  const data = await fetchFromTMDB<TMDBResponse<TVShow>>("/search/tv", { query, page });
  return data?.results || null;
}

export async function getTVSeasonDetails(showId: number, seasonNumber: number): Promise<TVSeasonDetails | null> {
  return fetchFromTMDB<TVSeasonDetails>(`/tv/${showId}/season/${seasonNumber}`);
}

export async function getTVEpisodeDetails(showId: number, seasonNumber: number, episodeNumber: number): Promise<TVEpisode | null> {
  return fetchFromTMDB<TVEpisode>(`/tv/${showId}/season/${seasonNumber}/episode/${episodeNumber}`);
}

export async function getTVShowRecommendations(showId: number, page: number = 1): Promise<TVShow[] | null> {
  const data = await fetchFromTMDB<TMDBResponse<TVShow>>(`/tv/${showId}/recommendations`, { page });
  return data?.results || null;
}

// ─────────────────────────────────────────────────────────────────────────────
// Search
// ─────────────────────────────────────────────────────────────────────────────

export async function searchMulti(query: string, page: number = 1): Promise<(Movie | TVShow)[] | null> {
  const data = await fetchFromTMDB<TMDBResponse<Movie | TVShow>>("/search/multi", { query, page });
  return data?.results || null;
}

// ─────────────────────────────────────────────────────────────────────────────
// Genres
// ─────────────────────────────────────────────────────────────────────────────

export async function getMovieGenres(): Promise<Array<{ id: number; name: string }> | null> {
  const data = await fetchFromTMDB<{ genres: Array<{ id: number; name: string }> }>("/genre/movie/list");
  return data?.genres || null;
}

export async function getTVGenres(): Promise<Array<{ id: number; name: string }> | null> {
  const data = await fetchFromTMDB<{ genres: Array<{ id: number; name: string }> }>("/genre/tv/list");
  return data?.genres || null;
}

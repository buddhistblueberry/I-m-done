import { describe, it, expect } from "vitest";
import { getTrendingMovies, getPopularTVShows } from "./tmdb";

describe("TMDB API Integration", () => {
  it("should fetch trending movies successfully", async () => {
    const movies = await getTrendingMovies(1);
    
    expect(movies).not.toBeNull();
    expect(Array.isArray(movies)).toBe(true);
    
    if (movies && movies.length > 0) {
      const movie = movies[0];
      expect(movie).toHaveProperty("id");
      expect(movie).toHaveProperty("title");
      expect(movie).toHaveProperty("overview");
      expect(movie).toHaveProperty("poster_path");
      expect(movie).toHaveProperty("vote_average");
    }
  });

  it("should fetch popular TV shows successfully", async () => {
    const shows = await getPopularTVShows(1);
    
    expect(shows).not.toBeNull();
    expect(Array.isArray(shows)).toBe(true);
    
    if (shows && shows.length > 0) {
      const show = shows[0];
      expect(show).toHaveProperty("id");
      expect(show).toHaveProperty("name");
      expect(show).toHaveProperty("overview");
      expect(show).toHaveProperty("poster_path");
      expect(show).toHaveProperty("vote_average");
    }
  });
});

import { COOKIE_NAME } from "@shared/const";
import { getSessionCookieOptions } from "./_core/cookies";
import { systemRouter } from "./_core/systemRouter";
import { publicProcedure, router, protectedProcedure } from "./_core/trpc";
import { z } from "zod";
import * as tmdb from "./tmdb";
import { resolveStream, getAvailableServers } from "./stream-resolver";
import * as db from "./db";

export const appRouter = router({
  system: systemRouter,
  
  auth: router({
    me: publicProcedure.query(opts => opts.ctx.user),
    logout: publicProcedure.mutation(({ ctx }) => {
      const cookieOptions = getSessionCookieOptions(ctx.req);
      ctx.res.clearCookie(COOKIE_NAME, { ...cookieOptions, maxAge: -1 });
      return { success: true } as const;
    }),
  }),

  content: router({
    // Movies
    getTrendingMovies: publicProcedure
      .input(z.object({ page: z.number().int().min(1).default(1) }))
      .query(async ({ input }) => {
        return await tmdb.getTrendingMovies(input.page);
      }),

    getNowPlayingMovies: publicProcedure
      .input(z.object({ page: z.number().int().min(1).default(1) }))
      .query(async ({ input }) => {
        return await tmdb.getNowPlayingMovies(input.page);
      }),

    getPopularMovies: publicProcedure
      .input(z.object({ page: z.number().int().min(1).default(1) }))
      .query(async ({ input }) => {
        return await tmdb.getPopularMovies(input.page);
      }),

    getMovieDetails: publicProcedure
      .input(z.object({ movieId: z.number().int() }))
      .query(async ({ input }) => {
        return await tmdb.getMovieDetails(input.movieId);
      }),

    searchMovies: publicProcedure
      .input(z.object({ query: z.string().min(1), page: z.number().int().min(1).default(1) }))
      .query(async ({ input }) => {
        return await tmdb.searchMovies(input.query, input.page);
      }),

    getMovieRecommendations: publicProcedure
      .input(z.object({ movieId: z.number().int(), page: z.number().int().min(1).default(1) }))
      .query(async ({ input }) => {
        return await tmdb.getMovieRecommendations(input.movieId, input.page);
      }),

    // TV Shows
    getTrendingTVShows: publicProcedure
      .input(z.object({ page: z.number().int().min(1).default(1) }))
      .query(async ({ input }) => {
        return await tmdb.getTrendingTVShows(input.page);
      }),

    getPopularTVShows: publicProcedure
      .input(z.object({ page: z.number().int().min(1).default(1) }))
      .query(async ({ input }) => {
        return await tmdb.getPopularTVShows(input.page);
      }),

    getTVShowDetails: publicProcedure
      .input(z.object({ showId: z.number().int() }))
      .query(async ({ input }) => {
        return await tmdb.getTVShowDetails(input.showId);
      }),

    getTVSeasonDetails: publicProcedure
      .input(z.object({ showId: z.number().int(), seasonNumber: z.number().int() }))
      .query(async ({ input }) => {
        return await tmdb.getTVSeasonDetails(input.showId, input.seasonNumber);
      }),

    getTVEpisodeDetails: publicProcedure
      .input(z.object({ showId: z.number().int(), seasonNumber: z.number().int(), episodeNumber: z.number().int() }))
      .query(async ({ input }) => {
        return await tmdb.getTVEpisodeDetails(input.showId, input.seasonNumber, input.episodeNumber);
      }),

    searchTVShows: publicProcedure
      .input(z.object({ query: z.string().min(1), page: z.number().int().min(1).default(1) }))
      .query(async ({ input }) => {
        return await tmdb.searchTVShows(input.query, input.page);
      }),

    getTVShowRecommendations: publicProcedure
      .input(z.object({ showId: z.number().int(), page: z.number().int().min(1).default(1) }))
      .query(async ({ input }) => {
        return await tmdb.getTVShowRecommendations(input.showId, input.page);
      }),

    // Search
    searchMulti: publicProcedure
      .input(z.object({ query: z.string().min(1), page: z.number().int().min(1).default(1) }))
      .query(async ({ input }) => {
        return await tmdb.searchMulti(input.query, input.page);
      }),
  }),

  streams: router({
    resolveStream: publicProcedure
      .input(z.object({
        tmdbId: z.number().int(),
        contentType: z.enum(["movie", "tv"]).default("movie"),
        season: z.number().int().default(1),
        episode: z.number().int().default(1),
      }))
      .query(async ({ input }) => {
        return await resolveStream(input.tmdbId, input.contentType, input.season, input.episode);
      }),

    getAvailableServers: publicProcedure
      .query(async () => {
        return await getAvailableServers();
      }),
  }),

  favorites: router({
    add: protectedProcedure
      .input(z.object({
        tmdbId: z.number().int(),
        mediaType: z.enum(["movie", "tv"]),
        title: z.string().optional(),
        posterPath: z.string().optional(),
      }))
      .mutation(async ({ input, ctx }) => {
        return await db.addFavorite(ctx.user.id, input.tmdbId, input.mediaType, input.title, input.posterPath);
      }),

    remove: protectedProcedure
      .input(z.object({ tmdbId: z.number().int() }))
      .mutation(async ({ input, ctx }) => {
        return await db.removeFavorite(ctx.user.id, input.tmdbId);
      }),

    list: protectedProcedure
      .query(async ({ ctx }) => {
        return await db.getUserFavorites(ctx.user.id);
      }),

    isFavorite: protectedProcedure
      .input(z.object({ tmdbId: z.number().int() }))
      .query(async ({ input, ctx }) => {
        return await db.isFavorite(ctx.user.id, input.tmdbId);
      }),
  }),

  watchlist: router({
    add: protectedProcedure
      .input(z.object({
        tmdbId: z.number().int(),
        mediaType: z.enum(["movie", "tv"]),
        title: z.string().optional(),
        posterPath: z.string().optional(),
      }))
      .mutation(async ({ input, ctx }) => {
        return await db.addToWatchlist(ctx.user.id, input.tmdbId, input.mediaType, input.title, input.posterPath);
      }),

    remove: protectedProcedure
      .input(z.object({ tmdbId: z.number().int() }))
      .mutation(async ({ input, ctx }) => {
        return await db.removeFromWatchlist(ctx.user.id, input.tmdbId);
      }),

    list: protectedProcedure
      .query(async ({ ctx }) => {
        return await db.getUserWatchlist(ctx.user.id);
      }),

    isInWatchlist: protectedProcedure
      .input(z.object({ tmdbId: z.number().int() }))
      .query(async ({ input, ctx }) => {
        return await db.isInWatchlist(ctx.user.id, input.tmdbId);
      }),
  }),
});

export type AppRouter = typeof appRouter;

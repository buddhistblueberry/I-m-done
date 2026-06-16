import { int, mysqlEnum, mysqlTable, text, timestamp, varchar } from "drizzle-orm/mysql-core";

/**
 * Core user table backing auth flow.
 * Extend this file with additional tables as your product grows.
 * Columns use camelCase to match both database fields and generated types.
 */
export const users = mysqlTable("users", {
  /**
   * Surrogate primary key. Auto-incremented numeric value managed by the database.
   * Use this for relations between tables.
   */
  id: int("id").autoincrement().primaryKey(),
  /** Manus OAuth identifier (openId) returned from the OAuth callback. Unique per user. */
  openId: varchar("openId", { length: 64 }).notNull().unique(),
  name: text("name"),
  email: varchar("email", { length: 320 }),
  loginMethod: varchar("loginMethod", { length: 64 }),
  role: mysqlEnum("role", ["user", "admin"]).default("user").notNull(),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
  lastSignedIn: timestamp("lastSignedIn").defaultNow().notNull(),
});

export type User = typeof users.$inferSelect;
export type InsertUser = typeof users.$inferInsert;

/**
 * Favorites table for storing user's favorite movies and TV shows.
 */
export const favorites = mysqlTable("favorites", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull(),
  tmdbId: int("tmdbId").notNull(),
  mediaType: mysqlEnum("mediaType", ["movie", "tv"]).notNull(),
  title: text("title"),
  posterPath: text("posterPath"),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
});

export type Favorite = typeof favorites.$inferSelect;
export type InsertFavorite = typeof favorites.$inferInsert;

/**
 * Watchlist table for storing movies/shows users want to watch.
 */
export const watchlist = mysqlTable("watchlist", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull(),
  tmdbId: int("tmdbId").notNull(),
  mediaType: mysqlEnum("mediaType", ["movie", "tv"]).notNull(),
  title: text("title"),
  posterPath: text("posterPath"),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
});

export type WatchlistItem = typeof watchlist.$inferSelect;
export type InsertWatchlistItem = typeof watchlist.$inferInsert;

/**
 * Watch history table for tracking what users have watched and their progress.
 */
export const watchHistory = mysqlTable("watchHistory", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull(),
  tmdbId: int("tmdbId").notNull(),
  mediaType: mysqlEnum("mediaType", ["movie", "tv"]).notNull(),
  title: text("title"),
  posterPath: text("posterPath"),
  season: int("season"), // For TV shows
  episode: int("episode"), // For TV shows
  watchedAt: timestamp("watchedAt").defaultNow().notNull(),
  progressSeconds: int("progressSeconds").default(0), // For resume functionality
  totalSeconds: int("totalSeconds"), // Total duration
});

export type WatchHistoryItem = typeof watchHistory.$inferSelect;
export type InsertWatchHistoryItem = typeof watchHistory.$inferInsert;
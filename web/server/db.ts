import { eq, and } from "drizzle-orm";
import { drizzle } from "drizzle-orm/mysql2";
import { InsertUser, users, favorites, watchlist, watchHistory, Favorite, WatchlistItem, WatchHistoryItem } from "../drizzle/schema";
import { ENV } from './_core/env';

let _db: ReturnType<typeof drizzle> | null = null;

// Lazily create the drizzle instance so local tooling can run without a DB.
export async function getDb() {
  if (!_db && process.env.DATABASE_URL) {
    try {
      _db = drizzle(process.env.DATABASE_URL);
    } catch (error) {
      console.warn("[Database] Failed to connect:", error);
      _db = null;
    }
  }
  return _db;
}

export async function upsertUser(user: InsertUser): Promise<void> {
  if (!user.openId) {
    throw new Error("User openId is required for upsert");
  }

  const db = await getDb();
  if (!db) {
    console.warn("[Database] Cannot upsert user: database not available");
    return;
  }

  try {
    const values: InsertUser = {
      openId: user.openId,
    };
    const updateSet: Record<string, unknown> = {};

    const textFields = ["name", "email", "loginMethod"] as const;
    type TextField = (typeof textFields)[number];

    const assignNullable = (field: TextField) => {
      const value = user[field];
      if (value === undefined) return;
      const normalized = value ?? null;
      values[field] = normalized;
      updateSet[field] = normalized;
    };

    textFields.forEach(assignNullable);

    if (user.lastSignedIn !== undefined) {
      values.lastSignedIn = user.lastSignedIn;
      updateSet.lastSignedIn = user.lastSignedIn;
    }
    if (user.role !== undefined) {
      values.role = user.role;
      updateSet.role = user.role;
    } else if (user.openId === ENV.ownerOpenId) {
      values.role = 'admin';
      updateSet.role = 'admin';
    }

    if (!values.lastSignedIn) {
      values.lastSignedIn = new Date();
    }

    if (Object.keys(updateSet).length === 0) {
      updateSet.lastSignedIn = new Date();
    }

    await db.insert(users).values(values).onDuplicateKeyUpdate({
      set: updateSet,
    });
  } catch (error) {
    console.error("[Database] Failed to upsert user:", error);
    throw error;
  }
}

export async function getUserByOpenId(openId: string) {
  const db = await getDb();
  if (!db) {
    console.warn("[Database] Cannot get user: database not available");
    return undefined;
  }

  const result = await db.select().from(users).where(eq(users.openId, openId)).limit(1);

  return result.length > 0 ? result[0] : undefined;
}

// ─────────────────────────────────────────────────────────────────────────────
// Favorites Management
// ─────────────────────────────────────────────────────────────────────────────

export async function addFavorite(userId: number, tmdbId: number, mediaType: "movie" | "tv", title?: string, posterPath?: string): Promise<Favorite | null> {
  const db = await getDb();
  if (!db) return null;

  try {
    const result = await db.insert(favorites).values({
      userId,
      tmdbId,
      mediaType,
      title,
      posterPath,
    });
    return result[0] as any;
  } catch (error) {
    console.error("[Database] Failed to add favorite:", error);
    return null;
  }
}

export async function removeFavorite(userId: number, tmdbId: number): Promise<boolean> {
  const db = await getDb();
  if (!db) return false;

  try {
    await db.delete(favorites).where(
      and(eq(favorites.userId, userId), eq(favorites.tmdbId, tmdbId))
    );
    return true;
  } catch (error) {
    console.error("[Database] Failed to remove favorite:", error);
    return false;
  }
}

export async function getUserFavorites(userId: number): Promise<Favorite[]> {
  const db = await getDb();
  if (!db) return [];

  try {
    return await db.select().from(favorites).where(eq(favorites.userId, userId));
  } catch (error) {
    console.error("[Database] Failed to get favorites:", error);
    return [];
  }
}

export async function isFavorite(userId: number, tmdbId: number): Promise<boolean> {
  const db = await getDb();
  if (!db) return false;

  try {
    const result = await db
      .select()
      .from(favorites)
      .where(and(eq(favorites.userId, userId), eq(favorites.tmdbId, tmdbId)))
      .limit(1);
    return result.length > 0;
  } catch (error) {
    console.error("[Database] Failed to check favorite:", error);
    return false;
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Watchlist Management
// ─────────────────────────────────────────────────────────────────────────────

export async function addToWatchlist(userId: number, tmdbId: number, mediaType: "movie" | "tv", title?: string, posterPath?: string): Promise<WatchlistItem | null> {
  const db = await getDb();
  if (!db) return null;

  try {
    const result = await db.insert(watchlist).values({
      userId,
      tmdbId,
      mediaType,
      title,
      posterPath,
    });
    return result[0] as any;
  } catch (error) {
    console.error("[Database] Failed to add to watchlist:", error);
    return null;
  }
}

export async function removeFromWatchlist(userId: number, tmdbId: number): Promise<boolean> {
  const db = await getDb();
  if (!db) return false;

  try {
    await db.delete(watchlist).where(
      and(eq(watchlist.userId, userId), eq(watchlist.tmdbId, tmdbId))
    );
    return true;
  } catch (error) {
    console.error("[Database] Failed to remove from watchlist:", error);
    return false;
  }
}

export async function getUserWatchlist(userId: number): Promise<WatchlistItem[]> {
  const db = await getDb();
  if (!db) return [];

  try {
    return await db.select().from(watchlist).where(eq(watchlist.userId, userId));
  } catch (error) {
    console.error("[Database] Failed to get watchlist:", error);
    return [];
  }
}

export async function isInWatchlist(userId: number, tmdbId: number): Promise<boolean> {
  const db = await getDb();
  if (!db) return false;

  try {
    const result = await db
      .select()
      .from(watchlist)
      .where(and(eq(watchlist.userId, userId), eq(watchlist.tmdbId, tmdbId)))
      .limit(1);
    return result.length > 0;
  } catch (error) {
    console.error("[Database] Failed to check watchlist:", error);
    return false;
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Watch History Management
// ─────────────────────────────────────────────────────────────────────────────

export async function addToWatchHistory(
  userId: number,
  tmdbId: number,
  mediaType: "movie" | "tv",
  title?: string,
  posterPath?: string,
  season?: number,
  episode?: number,
  progressSeconds?: number,
  totalSeconds?: number
): Promise<WatchHistoryItem | null> {
  const db = await getDb();
  if (!db) return null;

  try {
    const result = await db.insert(watchHistory).values({
      userId,
      tmdbId,
      mediaType,
      title,
      posterPath,
      season,
      episode,
      progressSeconds,
      totalSeconds,
    });
    return result[0] as any;
  } catch (error) {
    console.error("[Database] Failed to add to watch history:", error);
    return null;
  }
}

export async function getUserWatchHistory(userId: number, limit: number = 20): Promise<WatchHistoryItem[]> {
  const db = await getDb();
  if (!db) return [];

  try {
    return await db
      .select()
      .from(watchHistory)
      .where(eq(watchHistory.userId, userId))
      .orderBy((t) => t.watchedAt)
      .limit(limit);
  } catch (error) {
    console.error("[Database] Failed to get watch history:", error);
    return [];
  }
}

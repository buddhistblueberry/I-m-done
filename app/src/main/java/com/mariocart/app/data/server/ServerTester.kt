package com.mariocart.app.data.server

import com.mariocart.app.data.model.StreamingServer

/**
 * ServerTester — disabled.
 *
 * Auto-probing and auto-ranking have been removed.  The user always selects
 * a server manually from the full list; no background probing is performed.
 *
 * This object is kept as a stub so that any existing call-sites compile
 * without modification.  [rankForContent] simply returns the input list
 * unchanged, preserving the original order from [ServerManager].
 */
object ServerTester {

    /**
     * Returns [servers] unchanged.
     *
     * Previously this method probed each server in parallel and sorted the
     * results by response time.  That behaviour has been removed: server
     * selection is now always manual, so no ranking is needed or desired.
     */
    suspend fun rankForContent(
        servers: List<StreamingServer>,
        tmdbId: Int,
        type: String,
        season: Int = 1,
        episode: Int = 1
    ): List<StreamingServer> = servers
}

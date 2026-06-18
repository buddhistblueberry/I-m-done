package com.mariocart.app.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// ---------------------------------------------------------------------------
// DTOs — must match /app/backend exactly.
// ---------------------------------------------------------------------------

data class StreamServer(
    val id: String,
    val name: String,
    val type: String,                  // always "direct" in the new backend
)

data class ServersResponse(
    val success: Boolean,
    val servers: List<StreamServer>,
    val total: Int,
)

data class EmbedResponse(
    val success: Boolean,
    val embedUrl: String? = null,
    val serverId: String? = null,
    val headers: Map<String, String>? = null,
    val error: String? = null,
)

data class CaptchaChallenge(
    val type: String,                  // "cloudflare_turnstile"
    val siteKey: String,
    val challengeUrl: String,          // open this in a WebView
    val verifyPath: String? = null,    // informational
    val tokenParam: String = "_rcp",   // query param to look for in redirected URL
    val sessionId: String,
    val serverId: String? = null,
    val serverName: String? = null,
    val submitUrl: String? = null,
)

data class Subtitle(
    val url: String,
    val label: String? = null,
    val language: String? = null,
)

data class StreamResponse(
    val success: Boolean,
    val url: String? = null,
    val serverId: String? = null,
    val contentType: String? = null,   // MIME type for ExoPlayer (application/x-mpegurl, video/mp4, ...)
    val streamType: String? = null,    // hls / mp4 / dash / mkv / webm
    val isDirect: Boolean? = null,
    val headers: Map<String, String>? = null,
    val subtitles: List<Subtitle>? = null,
    val cached: Boolean? = null,
    val error: String? = null,
    val tried: List<String>? = null,
    val needsCaptcha: Boolean? = null,
    val captcha: CaptchaChallenge? = null,
)

data class HealthResponse(
    val status: String,
    val timestamp: String,
)

data class CaptchaSubmitRequest(
    val sessionId: String,
    val rcpToken: String,
)

data class ReportPayload(
    val server_id: String,
    val success: Boolean,
    val tmdb_id: Int? = null,
    val content_type: String? = null,
    val error: String? = null,
)

// ---------------------------------------------------------------------------
// API surface
// ---------------------------------------------------------------------------
interface StreamingBackendApi {

    @GET("api/health")
    suspend fun getHealth(): HealthResponse

    @GET("api/servers")
    suspend fun getServers(): ServersResponse

    @GET("api/stream")
    suspend fun getStream(
        @Query("tmdbId") tmdbId: Int,
        @Query("type") type: String,
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
        @Query("serverId") serverId: String? = null,
    ): StreamResponse

    @GET("api/embed")
    suspend fun getEmbed(
        @Query("serverId") serverId: String,
        @Query("tmdbId") tmdbId: Int,
        @Query("type") type: String,
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): EmbedResponse

    @POST("api/captcha/submit")
    suspend fun submitCaptcha(@Body body: CaptchaSubmitRequest): StreamResponse

    @POST("api/report")
    suspend fun report(@Body body: ReportPayload): Map<String, Any>
}

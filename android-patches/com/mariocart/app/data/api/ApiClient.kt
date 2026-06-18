package com.mariocart.app.data.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TMDB_BASE = "https://api.themoviedb.org/3/"

    // POINT THIS AT YOUR DEPLOYED BACKEND (Render / Railway / Fly / etc.).
    // It MUST end with a trailing slash for Retrofit.
    private const val STREAMING_BACKEND_BASE = "https://YOUR-BACKEND-HOST/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)        // backend extraction can take 15-25s
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val tmdbApi: TmdbApi by lazy {
        Retrofit.Builder()
            .baseUrl(TMDB_BASE)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbApi::class.java)
    }

    val streamingBackendApi: StreamingBackendApi by lazy {
        Retrofit.Builder()
            .baseUrl(STREAMING_BACKEND_BASE)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StreamingBackendApi::class.java)
    }
}

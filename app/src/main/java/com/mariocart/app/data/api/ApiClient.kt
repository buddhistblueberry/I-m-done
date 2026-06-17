package com.mariocart.app.data.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TMDB_BASE = "https://api.themoviedb.org/3/"
    private const val STREAMING_BACKEND_BASE = "https://3000-i59hdrx6en2f31d2ghor8-c8b9202c.us1.manus.computer/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
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

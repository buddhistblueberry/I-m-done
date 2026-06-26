package com.mariocart.app.data.server

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object StreamExtractor {

    private var initialized = false

    private fun initPython(context: Context) {
        if (!initialized) {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            initialized = true
        }
    }

    suspend fun extract(
        context: Context,
        tmdbId: Int,
        contentType: String = "movie",
        season: Int = 1,
        episode: Int = 1
    ): String? = withContext(Dispatchers.IO) {
        initPython(context)

        try {
            val py = Python.getInstance()
            val module = py.getModule("lookmovie.scraper")

            val resultJson = module.callAttr(
                "get_stream", tmdbId, contentType, season, episode
            ).toString()

            val json = JSONObject(resultJson)
            if (json.has("url")) {
                val url = json.getString("url")
                Log.i("StreamExtractor", "✅ LookMovie stream found: $url")
                return@withContext url
            } else if (json.has("error")) {
                Log.e("StreamExtractor", "Error: ${json.getString("error")}")
            }
        } catch (e: Exception) {
            Log.e("StreamExtractor", "Python scraper error", e)
        }

        // Fallback to your old method if needed
        return@withContext null
    }
}

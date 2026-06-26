    LaunchedEffect(tmdbId, contentType, season, episode, retryCount) {
        isLoading = true
        error = null

        try {
            Log.d("Player", "🔍 Starting extraction for TMDB $tmdbId")
            val url = StreamExtractor.extract(tmdbId, contentType, season, episode)
            if (!url.isNullOrBlank() && (url.contains(".m3u8") || url.contains(".mp4"))) {
                streamUrl = url
                Log.i("Player", "✅ Direct playable URL: $url")
            } else {
                throw Exception("No direct video URL (embed/redirect only)")
            }
        } catch (e: Exception) {
            Log.e("Player", "💥 Extraction failed", e)
            if (retryCount < maxRetries) {
                retryCount++
                delay(2000) // longer delay for redirects
            } else {
                error = "Failed to load stream.\n\nTry another title or deploy backend."
            }
        } finally {
            isLoading = false
        }
    }

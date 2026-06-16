package com.mariocart.app.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TMDB_ID = "tmdb_id"
        private const val EXTRA_TYPE    = "type"
        private const val EXTRA_TITLE   = "title"
        private const val EXTRA_SEASON  = "season"
        private const val EXTRA_EPISODE = "episode"
        private const val BACKEND_URL   = "https://your-backend-url.com" // Update this

        fun newIntent(context: Context, tmdbId: Int, type: String, title: String, season: Int = 1, episode: Int = 1): Intent = 
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_TMDB_ID, tmdbId)
                putExtra(EXTRA_TYPE, type)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SEASON, season)
                putExtra(EXTRA_EPISODE, episode)
            }
    }

    private var tmdbId = 0
    private var contentType = "movie"
    private var season = 1
    private var episode = 1
    private var videoTitle = ""
    
    private lateinit var webView: WebView
    private lateinit var loadingOverlay: FrameLayout
    private val httpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        tmdbId = intent.getIntExtra(EXTRA_TMDB_ID, 0)
        contentType = intent.getStringExtra(EXTRA_TYPE) ?: "movie"
        videoTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        season = intent.getIntExtra(EXTRA_SEASON, 1)
        episode = intent.getIntExtra(EXTRA_EPISODE, 1)

        setupLayout()
        fetchServers()
    }

    private fun setupLayout() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            visibility = View.GONE
            setupCleanWebView(this)
        }

        loadingOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(TextView(context).apply {
                text = "Fetching Servers..."
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
        }

        root.addView(webView)
        root.addView(loadingOverlay)
        setContentView(root)
    }

    private fun fetchServers() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = "${BACKEND_URL}/api/servers?tmdb_id=$tmdbId&content_type=$contentType&season=$season&episode=$episode"
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: "[]"
                val servers = JSONArray(body)
                
                withContext(Dispatchers.Main) {
                    showServerSelection(servers)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Fallback to default if backend fails
                    switchToWebView()
                }
            }
        }
    }

    private fun showServerSelection(servers: JSONArray) {
        val serverNames = mutableListOf<String>()
        for (i in 0 until servers.length()) {
            serverNames.add(servers.getJSONObject(i).getString("name"))
        }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select Server")
            .setCancelable(false)
            .setItems(serverNames.toTypedArray()) { _, which ->
                val selectedServer = servers.getJSONObject(which)
                handleServerSelection(selectedServer)
            }
            .show()
    }

    private fun handleServerSelection(server: JSONObject) {
        val serverId = server.getString("id")
        val embedUrl = server.getString("embed_url")
        
        loadingOverlay.visibility = View.VISIBLE
        
        // If it's a direct server, try extraction first
        if (server.getString("type") == "direct") {
            tryExtraction(serverId, embedUrl)
        } else {
            switchToWebView(embedUrl)
        }
    }

    private fun tryExtraction(serverId: String, fallbackUrl: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = "${BACKEND_URL}/api/stream?server=$serverId&tmdb_id=$tmdbId&content_type=$contentType&season=$season&episode=$episode"
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                val json = JSONObject(response.body?.string() ?: "{}")
                
                withContext(Dispatchers.Main) {
                    if (json.optBoolean("success", false)) {
                        // In a real app, you'd use ExoPlayer here. 
                        // But per your request for WebView, we'll still use WebView but with the direct URL if possible, 
                        // or just stick to the embed for better compatibility.
                        switchToWebView(fallbackUrl)
                    } else {
                        switchToWebView(fallbackUrl)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    switchToWebView(fallbackUrl)
                }
            }
        }
    }

    private fun switchToWebView(url: String? = null) {
        val embedUrl = url ?: if (contentType == "movie") {
            "https://vidsrc.to/embed/movie/$tmdbId"
        } else {
            "https://vidsrc.to/embed/tv/$tmdbId/$season/$episode"
        }
        
        loadingOverlay.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.loadUrl(embedUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupCleanWebView(web: WebView) {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val host = request?.url?.host ?: ""
                val allowedDomains = listOf("vidsrc.to", "vidsrc.me", "vidlink.pro", "vsembed.ru", "megacloud.live", "vizcloud.co", "2embed")
                return !allowedDomains.any { host.contains(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectCleanupScript(view)
            }
        }
    }

    private fun injectCleanupScript(view: WebView?) {
        val script = """
            (function() {
                const clean = () => {
                    document.querySelectorAll('.ad-overlay, .popup-container, #popunder, div[class*="overlay"], div[class*="popup"], iframe[src*="ads"], a[href*="click"], .fixed-bottom, .top-ad').forEach(el => el.remove());
                    const playBtn = document.querySelector('#play-button, .play-button, div[aria-label="Play"], #pl_but, .vjs-big-play-button, .play-btn');
                    if (playBtn && playBtn.offsetParent !== null) {
                        playBtn.click();
                        playBtn.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, view: window}));
                    }
                };
                clean();
                setInterval(clean, 1000);
                window.open = () => null;
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

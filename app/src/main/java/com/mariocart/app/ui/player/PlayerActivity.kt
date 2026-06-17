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
import com.mariocart.app.data.model.StreamingServer
import com.mariocart.app.data.server.ServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TMDB_ID = "tmdb_id"
        private const val EXTRA_TYPE    = "type"
        private const val EXTRA_TITLE   = "title"
        private const val EXTRA_SEASON  = "season"
        private const val EXTRA_EPISODE = "episode"

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
        showServerSelection()
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
                text = "Select a server to play"
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
        }

        root.addView(webView)
        root.addView(loadingOverlay)
        setContentView(root)
    }

    private fun showServerSelection() {
        val servers = ServerManager.getOrderedServers()
        
        if (servers.isEmpty()) {
            showError("No streaming servers available")
            return
        }

        val serverNames = servers.map { it.name }.toTypedArray()
        
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select Server")
            .setCancelable(false)
            .setItems(serverNames) { _, which ->
                val selectedServer = servers[which]
                playOnServer(selectedServer)
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }

    private fun playOnServer(server: StreamingServer) {
        val embedUrl = if (contentType == "movie") {
            "${server.baseUrl}/movie/$tmdbId"
        } else {
            "${server.baseUrl}/tv/$tmdbId/$season/$episode"
        }
        
        loadingOverlay.visibility = View.VISIBLE
        switchToWebView(embedUrl)
    }

    private fun switchToWebView(url: String) {
        loadingOverlay.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.loadUrl(url)
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
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                
                // Block ads and tracking
                if (isAdOrTracker(url)) {
                    return WebResourceResponse("text/plain", "utf-8", null)
                }
                
                // Block redirects to external sites
                if (isBlockedRedirect(url)) {
                    return WebResourceResponse("text/plain", "utf-8", null)
                }
                
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val host = request.url?.host ?: ""
                
                // Allow only known streaming domains
                val allowedDomains = listOf(
                    "vidsrc.to", "vidsrc.me", "vidsrc.pro", "vidsrc.dev", "vidsrc.in", "vidsrc.pm", 
                    "vidsrc.xyz", "vidsrc.cc", "vidsrc2.to",
                    "vidlink.pro", "videasy.net", "autoembed.cc", "autoembed.co",
                    "superembed.stream", "embed.su", "2embed.cc", "2embed.skin",
                    "lookmovie2.to", "filmcave.ru",
                    "smashystream.com", "rivestream.live", "vidbinge.dev", "flixembed.net",
                    "embedme.top", "multiembed.mov", "nontongo.win", "frembed.live",
                    "warezcdn.net", "vidcloud.co", "streamwish.to", "filemoon.sx",
                    "dood.to", "streamtape.com", "mixdrop.ag", "cinezone.to", "sflix.to",
                    "bflixz.to", "flix2day.to", "123moviesfree.net", "fmovies.ps",
                    "yesmovies.mn", "solarmovie.pe", "primewire.tf", "flixhq.click",
                    "watchseries.im", "theflixer.tv", "novacinema.app", "cinehd.xyz",
                    "phisher.app", "player.vip", "movembed.cc", "embedrapo.com",
                    "netstream.me", "streamm4u.app", "watchmoviesfree.ac", "embedhub.xyz",
                    "filmvf.to"
                )
                
                val isAllowed = allowedDomains.any { host.contains(it) }
                return !isAllowed
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectCleanupScript(view)
            }
        }
    }

    private fun isAdOrTracker(url: String): Boolean {
        val adPatterns = listOf(
            "doubleclick", "googlesyndication", "adservice", "ads.", "ad-", "advert",
            "analytics", "tracking", "facebook.com/tr", "google-analytics", "mixpanel",
            "amplitude", "segment.com", "intercom", "drift.com", "hotjar", "fullstory"
        )
        return adPatterns.any { url.contains(it, ignoreCase = true) }
    }

    private fun isBlockedRedirect(url: String): Boolean {
        val blockedPatterns = listOf(
            "bit.ly", "tinyurl", "short.link", "adf.ly", "linkvertise",
            "freebitco.in", "clck.ru", "adfly", "shorte.st", "ouo.io"
        )
        return blockedPatterns.any { url.contains(it, ignoreCase = true) }
    }

    private fun injectCleanupScript(view: WebView?) {
        val script = """
            (function() {
                const clean = () => {
                    // Remove ad overlays and popups
                    document.querySelectorAll('.ad-overlay, .popup-container, #popunder, div[class*="overlay"], div[class*="popup"], iframe[src*="ads"], a[href*="click"], .fixed-bottom, .top-ad, .bottom-ad, .side-ad').forEach(el => {
                        try { el.remove(); } catch(e) {}
                    });
                    
                    // Remove tracking pixels
                    document.querySelectorAll('img[src*="tracking"], img[src*="analytics"], img[src*="doubleclick"]').forEach(el => {
                        try { el.remove(); } catch(e) {}
                    });
                    
                    // Auto-click play button if visible
                    const playBtn = document.querySelector('#play-button, .play-button, div[aria-label="Play"], #pl_but, .vjs-big-play-button, .play-btn, [data-testid="play"], .jw-icon-playback');
                    if (playBtn && playBtn.offsetParent !== null) {
                        try {
                            playBtn.click();
                            playBtn.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, view: window}));
                        } catch(e) {}
                    }
                };
                
                clean();
                setInterval(clean, 1500);
                window.open = () => null;
                window.alert = () => null;
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

package com.mariocart.app.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mariocart.app.data.model.StreamingServer
import com.mariocart.app.data.server.ServerManager

/**
 * PlayerActivity — WebView-only video player.
 *
 * Servers are loaded from the local assets/servers.json file via [ServerManager].
 * The user MUST manually select a server from the list before playback begins.
 * There is NO automatic server selection, probing, or ranking.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TMDB_ID = "tmdb_id"
        private const val EXTRA_TYPE    = "type"
        private const val EXTRA_TITLE   = "title"
        private const val EXTRA_SEASON  = "season"
        private const val EXTRA_EPISODE = "episode"

        fun newIntent(
            context: Context,
            tmdbId: Int,
            type: String,
            title: String,
            season: Int = 1,
            episode: Int = 1
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_TMDB_ID, tmdbId)
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_SEASON, season)
            putExtra(EXTRA_EPISODE, episode)
        }
    }

    private var tmdbId      = 0
    private var contentType = "movie"
    private var season      = 1
    private var episode     = 1
    private var videoTitle  = ""

    private lateinit var webView:       WebView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var loadingText:   TextView

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        tmdbId      = intent.getIntExtra(EXTRA_TMDB_ID, 0)
        contentType = intent.getStringExtra(EXTRA_TYPE) ?: "movie"
        videoTitle  = intent.getStringExtra(EXTRA_TITLE) ?: ""
        season      = intent.getIntExtra(EXTRA_SEASON, 1)
        episode     = intent.getIntExtra(EXTRA_EPISODE, 1)

        setupLayout()

        // Load servers from local asset and immediately show the manual picker.
        // ServerManager.initialize() is a no-op if already called from MainActivity.
        ServerManager.initialize(this)
        ServerManager.resetHealth()
        showServerSelection()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // UI setup
    // -------------------------------------------------------------------------

    private fun setupLayout() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            setupWebView(this)
        }

        loadingText = TextView(this).apply {
            text = "Select a server to begin playback"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 18f
        }

        loadingOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(loadingText)
        }

        root.addView(webView)
        root.addView(loadingOverlay)
        setContentView(root)
    }

    // -------------------------------------------------------------------------
    // Server selection — always manual, never automatic
    // -------------------------------------------------------------------------

    private fun showServerSelection() {
        val servers = ServerManager.getOrderedServers()

        if (servers.isEmpty()) {
            showError("No streaming servers available.\nPlease check assets/servers.json.")
            return
        }

        val serverNames = servers.map { it.name }.toTypedArray()

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select Server")
            .setCancelable(false)
            .setItems(serverNames) { _, which ->
                loadServerInWebView(servers[which])
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }

    // -------------------------------------------------------------------------
    // WebView playback
    // -------------------------------------------------------------------------

    private fun loadServerInWebView(server: StreamingServer) {
        val embedUrl = if (contentType == "movie") {
            server.movieUrl(tmdbId)
        } else {
            server.tvUrl(tmdbId, season, episode)
        }

        loadingOverlay.visibility = View.VISIBLE
        loadingText.setTextColor(Color.WHITE)
        loadingText.text = "Loading ${server.name}…"

        webView.visibility = View.VISIBLE
        webView.loadUrl(embedUrl)

        // Hide the loading overlay once the page starts rendering.
        // onPageFinished fires when the DOM is ready; the video player
        // inside the embed page handles its own buffering from there.
        webView.webViewClient = buildWebViewClient(embedUrl)
    }

    private fun buildWebViewClient(originUrl: String) = object : WebViewClient() {

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            loadingOverlay.visibility = View.GONE
            injectCleanupScript(view)
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
            val url = request?.url?.toString() ?: return null
            return if (isAdOrTracker(url) || isBlockedRedirect(url)) {
                WebResourceResponse("text/plain", "utf-8", null)
            } else {
                null
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            val host = request?.url?.host ?: return false
            // Keep navigation inside known streaming/embed domains only.
            return !isAllowedDomain(host)
        }
    }

    // -------------------------------------------------------------------------
    // WebView configuration
    // -------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(web: WebView) {
        web.settings.apply {
            javaScriptEnabled               = true
            domStorageEnabled               = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
    }

    private fun injectCleanupScript(view: WebView?) {
        val script = """
            (function() {
                const clean = () => {
                    document.querySelectorAll(
                        '.ad-overlay, .popup-container, #popunder, ' +
                        'div[class*="overlay"], div[class*="popup"], ' +
                        'iframe[src*="ads"], a[href*="click"], ' +
                        '.fixed-bottom, .top-ad, .bottom-ad, .side-ad'
                    ).forEach(el => { try { el.remove(); } catch(e) {} });

                    document.querySelectorAll(
                        'img[src*="tracking"], img[src*="analytics"], img[src*="doubleclick"]'
                    ).forEach(el => { try { el.remove(); } catch(e) {} });

                    const playBtn = document.querySelector(
                        '#play-button, .play-button, div[aria-label="Play"], ' +
                        '#pl_but, .vjs-big-play-button, .play-btn, ' +
                        '[data-testid="play"], .jw-icon-playback'
                    );
                    if (playBtn && playBtn.offsetParent !== null) {
                        try {
                            playBtn.click();
                            playBtn.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true, view:window}));
                        } catch(e) {}
                    }
                };

                clean();
                setInterval(clean, 1500);
                window.open  = () => null;
                window.alert = () => null;
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    // -------------------------------------------------------------------------
    // URL filtering helpers
    // -------------------------------------------------------------------------

    private fun isAdOrTracker(url: String): Boolean {
        val patterns = listOf(
            "doubleclick", "googlesyndication", "adservice", "ads.", "ad-", "advert",
            "analytics", "tracking", "facebook.com/tr", "google-analytics", "mixpanel",
            "amplitude", "segment.com", "intercom", "drift.com", "hotjar", "fullstory"
        )
        return patterns.any { url.contains(it, ignoreCase = true) }
    }

    private fun isBlockedRedirect(url: String): Boolean {
        val patterns = listOf(
            "bit.ly", "tinyurl", "short.link", "adf.ly", "linkvertise",
            "freebitco.in", "clck.ru", "adfly", "shorte.st", "ouo.io"
        )
        return patterns.any { url.contains(it, ignoreCase = true) }
    }

    private fun isAllowedDomain(host: String): Boolean {
        val allowed = listOf(
            "vidsrc.to", "vidsrc.me", "vidsrc.pro", "vidsrc.dev", "vidsrc.in",
            "vidsrc.pm", "vidsrc.xyz", "vidsrc.cc", "vidsrc2.to",
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
        return allowed.any { host.contains(it) }
    }

    // -------------------------------------------------------------------------
    // Error display
    // -------------------------------------------------------------------------

    private fun showError(message: String) {
        loadingOverlay.visibility = View.VISIBLE
        loadingText.setTextColor(Color.RED)
        loadingText.text = message

        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> finish() }
            .show()
    }
}

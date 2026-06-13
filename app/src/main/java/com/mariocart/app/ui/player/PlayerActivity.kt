package com.mariocart.app.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mariocart.app.data.model.StreamingServer
import com.mariocart.app.data.repository.ContentRepository

/**
 * Fullscreen in-app video player activity.
 *
 * Uses a sandboxed, ad-blocked internal renderer for the embed servers —
 * nothing ever opens in an external browser. Includes the same popup-killer,
 * ad-blocker, redirect-blocker, and click-hijack-guard from the HTML version.
 * Server auto-fallback tries the next source if loading fails within 15 s.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TMDB_ID = "tmdb_id"
        private const val EXTRA_TYPE = "type"          // "movie" or "tv"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SEASON = "season"
        private const val EXTRA_EPISODE = "episode"
        private const val FALLBACK_TIMEOUT_MS = 15_000L

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

    private val repo = ContentRepository()
    private val servers: List<StreamingServer> get() = repo.streamingServers
    private var currentServerIndex = 0
    private var tmdbId = 0
    private var contentType = "movie"
    private var season = 1
    private var episode = 1

    private lateinit var playerView: WebView
    private lateinit var statusText: TextView
    private lateinit var titleText: TextView
    private lateinit var serverSpinner: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var fullscreenContainer: FrameLayout

    private val handler = Handler(Looper.getMainLooper())
    private var fallbackRunnable: Runnable? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // --- Ad / redirect blocking ---
    private val adDomains = listOf(
        "doubleclick", "googlesyndication", "adservice", "adnxs", "outbrain",
        "taboola", "revcontent", "mgid", "propellerads", "popcash", "popads",
        "trafficjunky", "exoclick", "juicyads", "adsterra", "hilltopads",
        "eroadvertising", "traffichunt", "clickadu", "richpush", "pushground",
        "mondiad", "bidvertiser", "advertserve", "yieldmo", "undertone",
        "adblade", "media.net", "zedo", "valueclick", "tradedoubler",
        "popunder", "onclickads", "redirect", "betterads", "ad-maven",
        "admaven", "adcash", "adfly", "shorte.st", "linkvertise"
    )

    private fun isAdUrl(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: return false
            adDomains.any { host.contains(it) }
        } catch (_: Exception) { false }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        tmdbId = intent.getIntExtra(EXTRA_TMDB_ID, 0)
        contentType = intent.getStringExtra(EXTRA_TYPE) ?: "movie"
        season = intent.getIntExtra(EXTRA_SEASON, 1)
        episode = intent.getIntExtra(EXTRA_EPISODE, 1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        // ---- Build layout programmatically ----
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#141414"))
            setPadding(24, 12, 24, 12)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        titleText = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(titleText)

        serverSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val serverNames = servers.map { it.name }.toTypedArray()
        serverSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, serverNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        serverSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos != currentServerIndex) loadServer(pos)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        topBar.addView(serverSpinner)

        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            setPadding(16, 8, 8, 8)
            setOnClickListener { finish() }
        }
        topBar.addView(closeBtn)

        root.addView(topBar)

        // Status bar
        statusText = TextView(this).apply {
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 12f
            setPadding(24, 6, 24, 6)
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            visibility = View.GONE
        }
        root.addView(statusText)

        // Progress bar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6)
        }
        root.addView(progressBar)

        // Fullscreen container (for fullscreen video)
        fullscreenContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(fullscreenContainer)

        // WebView for the embed player
        playerView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = false
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                cacheMode = WebSettings.LOAD_DEFAULT
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    // Block ad URLs and external navigation
                    if (isAdUrl(url)) return true
                    // Stay in-app: load inside the WebView
                    return false
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    if (isAdUrl(url)) {
                        return WebResourceResponse("text/plain", "UTF-8", null)
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar.visibility = View.VISIBLE
                    startFallbackTimer()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = View.GONE
                    cancelFallbackTimer()
                    setStatus("")
                    // Inject ad-blocking CSS/JS into the loaded page
                    injectAdBlocker(view)
                }

                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        setStatus("\u26A0\uFE0F Server error, trying next...")
                        tryNextServer()
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                // Support fullscreen video playback
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    customView = view
                    customViewCallback = callback
                    fullscreenContainer.addView(view)
                    fullscreenContainer.visibility = View.VISIBLE
                    playerView.visibility = View.GONE
                }

                override fun onHideCustomView() {
                    fullscreenContainer.removeAllViews()
                    fullscreenContainer.visibility = View.GONE
                    playerView.visibility = View.VISIBLE
                    customViewCallback?.onCustomViewHidden()
                    customView = null
                    customViewCallback = null
                }

                // Block popups
                override fun onCreateWindow(
                    view: WebView?, isDialog: Boolean,
                    isUserGesture: Boolean, resultMsg: android.os.Message?
                ): Boolean = false
            }
        }
        root.addView(playerView)

        setContentView(root)

        // Start loading the first server
        loadServer(0)
    }

    private fun loadServer(index: Int) {
        if (index >= servers.size) {
            setStatus("\u274C All servers tried. Select one manually.")
            return
        }
        currentServerIndex = index
        serverSpinner.setSelection(index, false)
        val server = servers[index]
        setStatus("\u23F3 Loading ${server.name}...")

        val url = if (contentType == "movie") {
            server.movieUrl(tmdbId)
        } else {
            server.tvUrl(tmdbId, season, episode)
        }
        playerView.loadUrl(url)
    }

    private fun tryNextServer() {
        val next = currentServerIndex + 1
        if (next < servers.size) {
            loadServer(next)
        } else {
            setStatus("\u274C All servers tried. Select one manually.")
        }
    }

    private fun startFallbackTimer() {
        cancelFallbackTimer()
        fallbackRunnable = Runnable {
            setStatus("\u26A0\uFE0F Switching to next server...")
            tryNextServer()
        }
        handler.postDelayed(fallbackRunnable!!, FALLBACK_TIMEOUT_MS)
    }

    private fun cancelFallbackTimer() {
        fallbackRunnable?.let { handler.removeCallbacks(it) }
        fallbackRunnable = null
    }

    private fun setStatus(msg: String) {
        handler.post {
            if (msg.isBlank()) {
                statusText.visibility = View.GONE
            } else {
                statusText.text = msg
                statusText.visibility = View.VISIBLE
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun injectAdBlocker(view: WebView?) {
        view?.evaluateJavascript("""
            (function() {
                // Remove common ad elements
                var selectors = [
                    'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]',
                    'iframe[src*="adservice"]', 'div[id*="ad-"]', 'div[class*="ad-"]',
                    'div[id*="ads"]', 'div[class*="ads"]', '.ad-container',
                    '.ad-overlay', '.popup-overlay', '[class*="popup"]',
                    '[id*="overlay"]', '.modal-backdrop'
                ];
                selectors.forEach(function(s) {
                    document.querySelectorAll(s).forEach(function(el) { el.remove(); });
                });

                // Block window.open
                window.open = function() { return null; };

                // Remove invisible overlays
                var all = document.querySelectorAll('*');
                for (var i = 0; i < all.length; i++) {
                    var el = all[i];
                    var st = window.getComputedStyle(el);
                    if ((st.position === 'fixed' || st.position === 'absolute') &&
                        parseInt(st.zIndex) > 9000 &&
                        parseFloat(st.opacity) < 0.15) {
                        el.remove();
                    }
                }
            })();
        """.trimIndent(), null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle TV remote back button
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                playerView.webChromeClient?.onHideCustomView()
                return true
            }
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        playerView.onPause()
    }

    override fun onResume() {
        super.onResume()
        playerView.onResume()
    }

    override fun onDestroy() {
        cancelFallbackTimer()
        playerView.destroy()
        super.onDestroy()
    }
}

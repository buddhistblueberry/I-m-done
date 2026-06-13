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
import com.mariocart.app.data.server.ServerManager

class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TMDB_ID = "tmdb_id"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SEASON = "season"
        private const val EXTRA_EPISODE = "episode"
        private const val FALLBACK_TIMEOUT_MS = 12_000L

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

    private var servers: List<StreamingServer> = emptyList()
    private var currentServerIndex = 0
    private var tmdbId = 0
    private var contentType = "movie"
    private var season = 1
    private var episode = 1
    private var currentEmbedUrl = ""
    private var redirectCount = 0
    private var pageLoadFailed = false

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

    // Expanded ad/redirect domain blocklist
    private val adDomains = listOf(
        "doubleclick", "googlesyndication", "adservice", "adnxs", "outbrain",
        "taboola", "revcontent", "mgid", "propellerads", "popcash", "popads",
        "trafficjunky", "exoclick", "juicyads", "adsterra", "hilltopads",
        "eroadvertising", "traffichunt", "clickadu", "richpush", "pushground",
        "mondiad", "bidvertiser", "advertserve", "yieldmo", "undertone",
        "adblade", "media.net", "zedo", "valueclick", "tradedoubler",
        "popunder", "onclickads", "redirect", "betterads", "ad-maven",
        "admaven", "adcash", "adfly", "shorte.st", "linkvertise",
        "ouo.io", "ouo.press", "bc.vc", "adf.ly", "bit.ly/ad",
        "linkbucks", "adfoc.us", "coinurl", "url.cn", "goo.gl/ad",
        "clk.sh", "shrink.pe", "earnow", "adlinkfly", "linkshrink",
        "grabify", "iplogger", "blasze", "ps.ht", "2no.co",
        "pornhub", "xvideos", "xhamster", "redtube", "youporn",
        "chaturbate", "livejasmin", "cam4", "myfreecams", "bongacams",
        "1xbet", "betway", "bet365", "stake.com", "roobet",
        "track.", "tracker.", "tracking.", "click.", "clicks.",
        "serve.", "servedby.", "delivery.", "pagead", "ads.",
        "banner.", "popup.", "pop.", "redirect.", "redir."
    )

    // Redirect domain patterns — if main frame navigates here, reload embed
    private val redirectDomains = listOf(
        "bit.ly", "tinyurl", "t.co", "goo.gl", "ow.ly",
        "is.gd", "buff.ly", "adf.ly", "ouo.io", "bc.vc",
        "shrink", "short", "redir", "linkvertise", "link.to"
    )

    private fun isAdUrl(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: return false
            adDomains.any { host.contains(it) }
        } catch (_: Exception) { false }
    }

    private fun isRedirectUrl(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: return false
            redirectDomains.any { host.contains(it) }
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

        // Get health-checked servers from ServerManager
        servers = ServerManager.getOrderedServers()

        // ---- Build layout ----
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
        setupServerSpinner()
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

        // Fullscreen container
        fullscreenContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(fullscreenContainer)

        // WebView
        playerView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false  // AUTO-PLAY
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
                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()

                    // Block ad URLs
                    if (isAdUrl(url)) return true

                    // Detect redirect — reload the embed instead of following
                    if (isRedirectUrl(url)) {
                        redirectCount++
                        if (redirectCount > 3) {
                            setStatus("Too many redirects, trying next server...")
                            tryNextServer()
                        } else {
                            setStatus("Redirect blocked, reloading...")
                            handler.postDelayed({ reloadCurrentServer() }, 500)
                        }
                        return true
                    }

                    // If the main frame navigates away from the embed domain, reload
                    if (request.isForMainFrame && currentEmbedUrl.isNotEmpty()) {
                        val embedHost = Uri.parse(currentEmbedUrl).host?.lowercase() ?: ""
                        val newHost = Uri.parse(url).host?.lowercase() ?: ""
                        if (embedHost.isNotEmpty() && newHost.isNotEmpty() && newHost != embedHost) {
                            redirectCount++
                            if (redirectCount > 3) {
                                setStatus("Server keeps redirecting, trying next...")
                                tryNextServer()
                            } else {
                                setStatus("Redirect caught, reloading player...")
                                handler.postDelayed({ reloadCurrentServer() }, 500)
                            }
                            return true
                        }
                    }

                    return false
                }

                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    if (isAdUrl(url)) {
                        return WebResourceResponse("text/plain", "UTF-8", null)
                    }
                    // Block known ad resource types
                    val path = request.url.path?.lowercase() ?: ""
                    if (path.endsWith(".gif") && url.contains("ad")) {
                        return WebResourceResponse("text/plain", "UTF-8", null)
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar.visibility = View.VISIBLE
                    pageLoadFailed = false
                    startFallbackTimer()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = View.GONE
                    cancelFallbackTimer()
                    if (!pageLoadFailed) {
                        setStatus("")
                        redirectCount = 0
                    }
                    injectAdBlocker(view)
                    injectAutoPlay(view)
                }

                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        pageLoadFailed = true
                        setStatus("\u26A0\uFE0F Server error, trying next...")
                        ServerManager.markServerDead(
                            servers.getOrNull(currentServerIndex)?.name ?: ""
                        )
                        tryNextServer()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?, request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    if (request?.isForMainFrame == true) {
                        val code = errorResponse?.statusCode ?: 0
                        if (code >= 500) {
                            pageLoadFailed = true
                            setStatus("\u26A0\uFE0F Server returned $code, trying next...")
                            ServerManager.markServerDead(
                                servers.getOrNull(currentServerIndex)?.name ?: ""
                            )
                            tryNextServer()
                        }
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
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

                override fun onCreateWindow(
                    view: WebView?, isDialog: Boolean,
                    isUserGesture: Boolean, resultMsg: android.os.Message?
                ): Boolean = false
            }
        }
        root.addView(playerView)

        setContentView(root)

        // Auto-play: load first server immediately
        loadServer(0)
    }

    private fun setupServerSpinner() {
        val serverNames = servers.map { it.name }.toTypedArray()
        serverSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, serverNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        serverSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, v: View?, pos: Int, id: Long
            ) {
                if (pos != currentServerIndex) loadServer(pos)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadServer(index: Int) {
        if (index >= servers.size) {
            setStatus("\u274C All servers tried. Select one manually.")
            return
        }
        currentServerIndex = index
        redirectCount = 0
        serverSpinner.setSelection(index, false)
        val server = servers[index]
        setStatus("\u23F3 Loading ${server.name}...")

        currentEmbedUrl = if (contentType == "movie") {
            server.movieUrl(tmdbId)
        } else {
            server.tvUrl(tmdbId, season, episode)
        }
        playerView.loadUrl(currentEmbedUrl)
    }

    private fun reloadCurrentServer() {
        if (currentEmbedUrl.isNotEmpty()) {
            setStatus("\u23F3 Reloading ${servers.getOrNull(currentServerIndex)?.name ?: ""}...")
            playerView.loadUrl(currentEmbedUrl)
        }
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
            setStatus("\u26A0\uFE0F Timeout, switching server...")
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
                // Remove ad elements
                var selectors = [
                    'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]',
                    'iframe[src*="adservice"]', 'div[id*="ad-"]', 'div[class*="ad-"]',
                    'div[id*="ads"]', 'div[class*="ads"]', '.ad-container',
                    '.ad-overlay', '.popup-overlay', '[class*="popup"]',
                    '[id*="overlay"]', '.modal-backdrop', '[class*="banner"]',
                    '[id*="banner"]', '[class*="sticky"]', '[class*="float"]',
                    '.overlay', '#overlay', '.modal', '.popup',
                    'div[style*="z-index: 9999"]', 'div[style*="z-index:9999"]',
                    'div[style*="z-index: 99999"]', 'div[style*="z-index:99999"]',
                    'div[style*="position: fixed"][style*="z-index"]',
                    'a[target="_blank"]', '[onclick*="window.open"]'
                ];
                selectors.forEach(function(s) {
                    try {
                        document.querySelectorAll(s).forEach(function(el) {
                            if (!el.querySelector('video') && !el.querySelector('iframe[src*="embed"]')) {
                                el.remove();
                            }
                        });
                    } catch(e) {}
                });

                // Block window.open permanently
                window.open = function() { return null; };
                
                // Block alert/confirm/prompt
                window.alert = function() {};
                window.confirm = function() { return false; };
                window.prompt = function() { return null; };

                // Remove invisible high-z-index overlays
                var all = document.querySelectorAll('*');
                for (var i = 0; i < all.length; i++) {
                    try {
                        var el = all[i];
                        var st = window.getComputedStyle(el);
                        var z = parseInt(st.zIndex) || 0;
                        if ((st.position === 'fixed' || st.position === 'absolute') && z > 9000) {
                            if (parseFloat(st.opacity) < 0.15 || 
                                el.offsetWidth > window.innerWidth * 0.8 && el.offsetHeight > window.innerHeight * 0.8) {
                                if (!el.querySelector('video')) {
                                    el.remove();
                                }
                            }
                        }
                    } catch(e) {}
                }

                // Disable onclick handlers on body that open popups
                document.body.onclick = null;
                document.body.onmousedown = null;
                document.body.onmouseup = null;

                // Block navigation away via beforeunload
                window.onbeforeunload = null;

                // Mutation observer to auto-remove future ad injections
                if (!window._adObserver) {
                    window._adObserver = new MutationObserver(function(mutations) {
                        mutations.forEach(function(m) {
                            m.addedNodes.forEach(function(node) {
                                if (node.nodeType === 1) {
                                    try {
                                        var st = window.getComputedStyle(node);
                                        var z = parseInt(st.zIndex) || 0;
                                        if ((st.position === 'fixed' || st.position === 'absolute') && z > 9000) {
                                            if (!node.querySelector('video') && !node.querySelector('iframe[src*="embed"]')) {
                                                node.remove();
                                            }
                                        }
                                    } catch(e) {}
                                }
                            });
                        });
                    });
                    window._adObserver.observe(document.body, {childList: true, subtree: true});
                }
            })();
        """.trimIndent(), null)
    }

    private fun injectAutoPlay(view: WebView?) {
        view?.evaluateJavascript("""
            (function() {
                // Auto-click play buttons
                var playBtns = document.querySelectorAll(
                    '[class*="play"], [id*="play"], button[aria-label*="play"], ' +
                    '.vjs-big-play-button, .jw-icon-display, [class*="Play"], ' +
                    'svg[data-icon="play"], [class*="start"], [class*="btn-play"]'
                );
                if (playBtns.length > 0) {
                    playBtns[0].click();
                }
                
                // Auto-play any video elements
                var videos = document.querySelectorAll('video');
                videos.forEach(function(v) {
                    v.muted = false;
                    v.play().catch(function() {
                        v.muted = true;
                        v.play().catch(function(){});
                    });
                });
            })();
        """.trimIndent(), null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
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

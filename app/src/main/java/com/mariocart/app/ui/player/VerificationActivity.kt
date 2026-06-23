package com.mariocart.app.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager as WebCookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpCookie
import java.net.URI

class VerificationActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val VERIFICATION_TIMEOUT_MS = 120000L // 2 minutes

        fun newIntent(context: Context, url: String): Intent {
            return Intent(context, VerificationActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
            }
        }
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var timeoutHandler: Handler? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = android.widget.FrameLayout(this)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = View.GONE
                    
                    // Sync cookies from WebView to our app's CookieManager
                    url?.let { currentUrl ->
                        val webCookieManager = WebCookieManager.getInstance()
                        val cookiesString = webCookieManager.getCookie(currentUrl)
                        if (cookiesString != null) {
                            val javaCookieManager = com.mariocart.app.data.server.StreamExtractor.getCookieManager()
                            try {
                                val uri = URI.create(currentUrl)
                                cookiesString.split(";").forEach {
                                    try {
                                        val cookieParts = it.split("=").map { it.trim() }
                                        if (cookieParts.size >= 2) {
                                            val cookie = HttpCookie(cookieParts[0], cookieParts[1])
                                            cookie.domain = uri.host
                                            cookie.path = "/"
                                            javaCookieManager.cookieStore.add(uri, cookie)
                                        }
                                    } catch (e: Exception) {}
                                }
                            } catch (e: Exception) {}
                        }
                    }

                    // Check if the user has returned to a "normal" URL or if the challenge is gone
                    if (url != null && !isChallengeUrl(url) && !isClickbaitPage(url)) {
                        setResult(RESULT_OK)
                        finish()
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false // Let WebView handle redirects
                }
            }
        }
        
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }

        root.addView(webView)
        root.addView(progressBar, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        
        setContentView(root)

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }
        
        setupVerificationTimeout()
        webView.loadUrl(url)
    }

    private fun setupVerificationTimeout() {
        timeoutHandler = Handler(Looper.getMainLooper())
        timeoutHandler?.postDelayed({
            if (!isFinishing) {
                setResult(RESULT_CANCELED)
                finish()
            }
        }, VERIFICATION_TIMEOUT_MS)
    }

    private fun isChallengeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("verify") || lower.contains("captcha") || 
               lower.contains("checkpoint") || lower.contains("challenge") ||
               lower.contains("vidsrc.to") || lower.contains("vidsrc.me")
    }

    private fun isClickbaitPage(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("click") || lower.contains("ads") || 
               lower.contains("pop") || lower.contains("redirect") ||
               lower.contains("bet") || lower.contains("game") ||
               lower.contains("casino") || lower.contains("porn") ||
               lower.contains("dating") || lower.contains("survey") ||
               lower.contains("earn") || lower.contains("gift") ||
               lower.contains("congratulations") || lower.contains("winner")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            setResult(RESULT_CANCELED)
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timeoutHandler?.removeCallbacksAndMessages(null)
    }
}

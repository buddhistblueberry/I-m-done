package com.mariocart.app.ui.captcha

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tiny one-purpose WebView that loads a CAPTCHA challenge page (e.g. the
 * Cloudflare Turnstile flow on `cloudorchestranova.com/prorcp/<hash>`)
 * and finishes itself as soon as the page redirects to a URL containing
 * the `tokenParam` query parameter (default `_rcp`).
 *
 * Result is delivered via setResult(RESULT_OK, intent.putExtra(EXTRA_TOKEN, "...")).
 *
 * NOTE: this WebView is ONLY used for solving the CAPTCHA. Video playback
 * is still 100% native ExoPlayer.
 */
class CaptchaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHALLENGE_URL = "challenge_url"
        const val EXTRA_TOKEN_PARAM = "token_param"
        const val EXTRA_TOKEN = "captcha_token"

        fun newIntent(ctx: Context, challengeUrl: String, tokenParam: String = "_rcp"): Intent =
            Intent(ctx, CaptchaActivity::class.java).apply {
                putExtra(EXTRA_CHALLENGE_URL, challengeUrl)
                putExtra(EXTRA_TOKEN_PARAM, tokenParam)
            }
    }

    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private var tokenParam: String = "_rcp"
    private var finished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val challengeUrl = intent.getStringExtra(EXTRA_CHALLENGE_URL)
        tokenParam = intent.getStringExtra(EXTRA_TOKEN_PARAM) ?: "_rcp"

        if (challengeUrl.isNullOrBlank()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        status = TextView(this).apply {
            text = "Please verify you are human to start playback"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 24)
        }
        progress = ProgressBar(this).apply { isIndeterminate = true }
        val progressWrap = FrameLayout(this).apply {
            addView(
                progress,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = Gravity.CENTER },
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, 16) }
        }

        web = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                userAgentString =
                    "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return checkUrl(request.url.toString())
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    url?.let { checkUrl(it) }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    this@CaptchaActivity.progress.visibility = View.GONE
                    url?.let { checkUrl(it) }
                }
            }
        }

        root.addView(status)
        root.addView(progressWrap)
        root.addView(web)
        setContentView(root)

        web.loadUrl(challengeUrl)
    }

    /** Returns true if we should consume the URL (i.e. we've extracted the token). */
    private fun checkUrl(url: String): Boolean {
        if (finished) return true
        val token = Uri.parse(url).getQueryParameter(tokenParam)
        if (!token.isNullOrBlank()) {
            finished = true
            val data = Intent().apply { putExtra(EXTRA_TOKEN, token) }
            setResult(Activity.RESULT_OK, data)
            finish()
            return true
        }
        return false
    }

    override fun onDestroy() {
        try {
            web.stopLoading()
            web.destroy()
        } catch (_: Exception) { }
        super.onDestroy()
    }
}

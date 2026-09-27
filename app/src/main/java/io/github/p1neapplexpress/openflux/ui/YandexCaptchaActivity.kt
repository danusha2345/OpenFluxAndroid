package io.github.p1neapplexpress.openflux.ui

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.p1neapplexpress.openflux.R
import io.github.p1neapplexpress.openflux.service.YandexCaptchaFiles

/** Browser and cookie handoff for a challenge encountered on this Android device. */
class YandexCaptchaActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val visitedURLs = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val challenge = YandexCaptchaFiles.pendingURL(this)
        if (challenge == null) {
            Toast.makeText(this, R.string.captcha_invalid, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(R.layout.activity_yandex_captcha)
        visitedURLs.add(challenge)
        webView = findViewById(R.id.captchaWebView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setSupportMultipleWindows(false)
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        manager.setAcceptThirdPartyCookies(webView, true)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !YandexCaptchaFiles.isYandexURL(request.url.toString())

            override fun onPageFinished(view: WebView, url: String) {
                if (YandexCaptchaFiles.isYandexURL(url)) visitedURLs.add(url)
            }
        }
        webView.loadUrl(challenge)
        findViewById<Button>(R.id.captchaSave).setOnClickListener {
            val currentURL = webView.url.orEmpty()
            if (!YandexCaptchaFiles.isYandexURL(currentURL) ||
                YandexCaptchaFiles.isAllowedChallenge(currentURL)) {
                Toast.makeText(this, R.string.captcha_not_complete, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val saved = runCatching {
                YandexCaptchaFiles.saveFromWebView(this, manager, visitedURLs)
            }.getOrDefault(false)
            if (saved) {
                Toast.makeText(this, R.string.captcha_saved, Toast.LENGTH_LONG).show()
                finish()
            } else {
                Toast.makeText(this, R.string.captcha_no_cookies, Toast.LENGTH_LONG).show()
            }
        }
        findViewById<Button>(R.id.captchaClose).setOnClickListener { finish() }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}

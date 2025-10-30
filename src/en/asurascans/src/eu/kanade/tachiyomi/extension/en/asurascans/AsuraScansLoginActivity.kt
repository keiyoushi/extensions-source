package eu.kanade.tachiyomi.extension.en.asurascans

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast

class AsuraScansLoginActivity : Activity() {

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)

        val container = FrameLayout(this)
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url != null && url.startsWith("https://$ASURA_MAIN_HOST")) {
                        // Users can close the activity once logged in.
                    }
                }
            }
        }

        container.addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(container)

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            val loginUrl = intent.getStringExtra(EXTRA_URL) ?: DEFAULT_LOGIN_URL
            webView.loadUrl(loginUrl)
        }

        this.webView = webView

        Toast.makeText(this, "Log in, then press back to finish.", Toast.LENGTH_LONG).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView?.saveState(outState)
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        private const val ASURA_MAIN_HOST = "asuracomic.net"
        private const val DEFAULT_LOGIN_URL = "https://$ASURA_MAIN_HOST/login"
    }
}

package eu.kanade.tachiyomi.extension.en.niadd

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class NiaddWebViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.loadUrl(intent.getStringExtra("url") ?: "")
    }
}

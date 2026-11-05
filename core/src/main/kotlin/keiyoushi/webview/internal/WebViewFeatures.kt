package keiyoushi.webview.internal

import androidx.webkit.WebViewFeature

/** Whether WebView supports [feature]; false where androidx.webkit's check throws (no glue, e.g. Suwayomi) */
internal fun isFeatureSupported(feature: String): Boolean = try {
    WebViewFeature.isFeatureSupported(feature)
} catch (_: Throwable) {
    false
}

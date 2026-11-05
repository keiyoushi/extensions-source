package keiyoushi.webview.internal

import androidx.webkit.WebViewFeature
import org.junit.Assert.assertFalse
import org.junit.Test

class WebViewFeaturesTest {
    // The JVM's android.jar has no WebView.getFactory(), like Suwayomi: webkit throws on the first check and every later one
    @Test
    fun noGlueMeansUnsupported() {
        repeat(2) { assertFalse(isFeatureSupported(WebViewFeature.MUTE_AUDIO)) }
    }
}

package keiyoushi.utils

import keiyoushi.network.Fetch
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ProxyFetchTest {
    private val navigation = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"

    @Test
    fun kinds() {
        assertSame(Fetch.DOCUMENT, proxyFetch("GET", "/comics", navigation, isForMainFrame = true))
        assertSame(Fetch.SCRIPT, proxyFetch("GET", "/_next/static/chunks/main.js", "*/*", isForMainFrame = false))
        assertSame(Fetch.SCRIPT, proxyFetch("GET", "/assets/APP.MJS", "*/*", isForMainFrame = false))
        assertSame(Fetch.IMAGE, proxyFetch("GET", "/icon.webp", "image/avif,image/webp,*/*;q=0.8", isForMainFrame = false))
        assertSame(Fetch.XHR, proxyFetch("GET", "/api/boot", "*/*", isForMainFrame = false))
        // A script's text/html fetch, not a frame
        assertSame(Fetch.XHR, proxyFetch("GET", "/fragment", "text/html, */*; q=0.01", isForMainFrame = false))
    }

    @Test
    fun leftToTheWebView() {
        assertNull(proxyFetch("POST", "/api/verify", "*/*", isForMainFrame = false))
        assertNull(proxyFetch("GET", "/embed", navigation, isForMainFrame = false))
        assertNull(proxyFetch("GET", "/style.css", "text/css,*/*;q=0.1", isForMainFrame = false))
        assertNull(proxyFetch("GET", "/fonts/Bebas.TTF", "*/*", isForMainFrame = false))
    }
}

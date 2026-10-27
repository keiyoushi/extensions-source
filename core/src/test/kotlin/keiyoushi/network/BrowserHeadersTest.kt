package keiyoushi.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class BrowserHeadersTest {

    private val chromeUa = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Mobile Safari/537.36"
    private val firefoxUa = "Mozilla/5.0 (Android 14; Mobile; rv:158.0) Gecko/158.0 Firefox/158.0"

    @Test
    fun brandsMatchChrome() {
        assertEquals("\"Google Chrome\";v=\"155\", \"Chromium\";v=\"155\", \"Not(A:Brand\";v=\"24\"", chromeBrands(155))
        assertEquals("\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"", chromeBrands(120))
    }

    @Test
    fun document() {
        val sent = intercept(builder("https://a.test/series/1", referer = "https://a.test/").forDocument().build())
        assertEquals("navigate", sent.header("Sec-Fetch-Mode"))
        assertEquals("document", sent.header("Sec-Fetch-Dest"))
        assertEquals("same-origin", sent.header("Sec-Fetch-Site"))
        assertEquals("?1", sent.header("Sec-Fetch-User"))
        assertEquals("1", sent.header("Upgrade-Insecure-Requests"))
        assertEquals("u=0, i", sent.header("Priority"))
        assertNull(sent.header("Origin"))
    }

    @Test
    fun xhr() {
        val get = intercept(builder("https://a.test/api/list", referer = "https://a.test/").forXhr().build())
        assertEquals("cors", get.header("Sec-Fetch-Mode"))
        assertEquals("empty", get.header("Sec-Fetch-Dest"))
        assertEquals("*/*", get.header("Accept"))
        assertNull(get.header("Origin"))
        val post = intercept(builder("https://a.test/api/search", referer = "https://a.test/x").forXhr().post(json()).build())
        assertEquals("https://a.test", post.header("Origin"))
        val crossOrigin = intercept(builder("https://api.b.test/list", referer = "https://a.test/series/1").forXhr().build())
        assertEquals("https://a.test", crossOrigin.header("Origin"))
        assertEquals("cross-site", crossOrigin.header("Sec-Fetch-Site"))
    }

    @Test
    fun image() {
        val sent = intercept(builder("https://media.a.test/1", referer = "https://www.a.test/series/1").forImage().build())
        assertEquals("same-site", sent.header("Sec-Fetch-Site"))
        assertEquals("no-cors", sent.header("Sec-Fetch-Mode"))
        assertEquals("image", sent.header("Sec-Fetch-Dest"))
        assertEquals("i", sent.header("Priority"))
        // No-cors: no Origin even cross-origin
        assertNull(intercept(builder("https://cdn.b.test/1.jpg", referer = "https://a.test/").forImage().build()).header("Origin"))
    }

    @Test
    fun untaggedGetsOnlyWhatEveryRequestHas() {
        val sent = intercept(builder("https://a.test/series/1", referer = "https://a.test/").build())
        assertEquals("?1", sent.header("Sec-CH-UA-Mobile"))
        assertNotNull(sent.header("Accept-Language"))
        assertNull(sent.header("Sec-Fetch-Site"))
        assertNull(sent.header("Accept"))
        assertNull(sent.header("Priority"))
        assertNull(sent.header("Origin"))
        val post = intercept(builder("https://a.test/api/search", referer = "https://a.test/").post(json()).build())
        assertEquals("https://a.test", post.header("Origin"))
        assertNull(post.header("Sec-Fetch-Mode"))
    }

    @Test
    fun keepsWhatTheSourceSet() {
        val sent = intercept(
            builder("https://a.test/api/list").forXhr()
                .header("Origin", "https://other.test")
                .header("Sec-Fetch-Site", "none")
                .build(),
        )
        assertEquals("https://other.test", sent.header("Origin"))
        assertEquals("none", sent.header("Sec-Fetch-Site"))
    }

    @Test
    fun originTheSourceRemovedStaysRemoved() {
        val sent = intercept(builder("https://a.test/api/search", referer = "https://a.test/").removeHeader("Origin").post(json()).build())
        assertNull(sent.header("Origin"))
    }

    @Test
    fun defaultOriginWithoutReferer() {
        val sent = intercept(builder("https://a.test/api/search").forXhr().post(json()).build())
        assertEquals("https://a.test", sent.header("Origin"))
        assertEquals("same-origin", sent.header("Sec-Fetch-Site"))
    }

    @Test
    fun chromiumForksGetNoChromeHints() {
        val edge = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Mobile Safari/537.36 EdgA/155.0.0.0"
        val sent = intercept(builder("https://a.test/series/1", ua = edge).forDocument().build())
        assertNull(sent.header("Sec-CH-UA"))
        assertEquals("navigate", sent.header("Sec-Fetch-Mode"))
    }

    @Test
    fun firefoxGetsFirefoxValues() {
        val sent = intercept(builder("https://a.test/series/1", ua = firefoxUa).forDocument().build())
        assertNull(sent.header("Sec-CH-UA"))
        assertEquals("navigate", sent.header("Sec-Fetch-Mode"))
        assertEquals("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", sent.header("Accept"))
        assertEquals("u=5, i", intercept(builder("https://a.test/1", ua = firefoxUa).forImage().build()).header("Priority"))
    }

    @Test
    fun otherBrowsersGetTheCommonHeaders() {
        val safari = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"
        val sent = intercept(builder("https://a.test/series/1", ua = safari).forDocument().build())
        assertNull(sent.header("Accept"))
        assertNull(sent.header("Priority"))
        assertEquals("navigate", sent.header("Sec-Fetch-Mode"))
    }

    @Test
    fun nonBrowserClientsGetNoBrowserHeaders() {
        val sent = intercept(builder("https://api.a.test/list", ua = "Tachiyomi Dalvik/2.1.0").forXhr().build())
        assertNull(sent.header("Accept-Language"))
        assertNull(sent.header("Sec-Fetch-Mode"))
        assertNull(sent.header("Accept"))
    }

    @Test
    fun jsonWithoutCharsetOnTheWire() {
        // As OkHttp sets it from the body before the network interceptors
        val request = builder("https://a.test/api/x", referer = "https://a.test/").forXhr().post(json())
            .header("Content-Type", "application/json; charset=utf-8").build()
        val sent = intercept(request, wireOrder = true)
        assertEquals("application/json", sent.header("Content-Type"))
    }

    @Test
    fun wireOrderPerBrowser() {
        val chrome = intercept(builder("https://a.test/api/x", referer = "https://a.test/").forXhr().header("X-Site", "1").build(), wireOrder = true)
        assertEquals(
            listOf("Sec-CH-UA-Platform", "User-Agent", "Sec-CH-UA", "Sec-CH-UA-Mobile", "X-Site", "Accept", "Sec-Fetch-Site"),
            chrome.headers.map { it.first }.take(7),
        )
        val firefox = intercept(builder("https://a.test/api/x", referer = "https://a.test/", ua = firefoxUa).forXhr().header("X-Site", "1").build(), wireOrder = true)
        assertEquals(
            listOf("User-Agent", "Accept", "Accept-Language", "Referer", "X-Site", "Sec-Fetch-Dest", "Sec-Fetch-Mode", "Sec-Fetch-Site", "Priority"),
            firefox.headers.map { it.first },
        )
    }

    @Test
    fun noFetchMetadataOverHttp() {
        val sent = intercept(builder("http://a.test/series/1").forDocument().build())
        assertNull(sent.header("Sec-Fetch-Site"))
        assertNull(sent.header("Sec-CH-UA"))
    }

    @Test
    fun acceptLanguageLikeWebView() {
        fun chrome(vararg locales: Locale) = acceptLanguage(locales.toList())
        assertEquals("en-US,en;q=0.9", chrome(Locale.US))
        assertEquals("es-AR,es;q=0.9,en-US;q=0.8,en;q=0.7", chrome(Locale.forLanguageTag("es-AR"), Locale.US))
        assertEquals("en-US,en-GB;q=0.9,en;q=0.8", chrome(Locale.US, Locale.UK))
        assertEquals("id-ID,id;q=0.9", chrome(Locale.Builder().setLanguage("in").setRegion("ID").build()))
        assertEquals("ja", chrome(Locale.JAPANESE))
    }

    private fun json() = "{}".toRequestBody("application/json".toMediaType())

    // A source's request: KeiSource's default Origin, the given Referer
    private fun builder(url: String, referer: String? = null, ua: String = chromeUa) = Request.Builder().url(url)
        .header("User-Agent", ua)
        .header("Origin", "https://a.test")
        .apply { referer?.let { header("Referer", it) } }

    // The request as sent: with the wire order pass it's HTTP/1.1, as a unit test has no connection
    private fun intercept(request: Request, wireOrder: Boolean = false): Request {
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(BrowserHeadersInterceptor { "https://a.test" })
            .apply { if (wireOrder) addInterceptor(BrowserHeaderOrderInterceptor) }
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("".toResponseBody()).build()
            }
            .build()
        client.newCall(request).execute().close()
        return sent!!
    }
}

package keiyoushi.network

import okhttp3.CacheControl
import okhttp3.CompressionInterceptor
import okhttp3.FormBody
import okhttp3.Gzip
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.brotli.Brotli
import okhttp3.zstd.Zstd
import org.junit.Assert.assertEquals
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
        val sent = intercept(builder("https://a.test/series/1", referer = "https://a.test/").fetch(Fetch.DOCUMENT).build())
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
        val get = intercept(builder("https://a.test/api/list", referer = "https://a.test/").fetch(Fetch.XHR).build())
        assertEquals("cors", get.header("Sec-Fetch-Mode"))
        assertEquals("empty", get.header("Sec-Fetch-Dest"))
        assertEquals("*/*", get.header("Accept"))
        assertNull(get.header("Origin"))
        val post = intercept(builder("https://a.test/api/search", referer = "https://a.test/x").fetch(Fetch.XHR).post(json()).build())
        assertEquals("https://a.test", post.header("Origin"))
        val crossOrigin = intercept(builder("https://api.b.test/list", referer = "https://a.test/series/1").fetch(Fetch.XHR).build())
        assertEquals("https://a.test", crossOrigin.header("Origin"))
        assertEquals("cross-site", crossOrigin.header("Sec-Fetch-Site"))
    }

    @Test
    fun image() {
        val sent = intercept(builder("https://media.a.test/1", referer = "https://www.a.test/series/1").fetch(Fetch.IMAGE).build())
        assertEquals("same-site", sent.header("Sec-Fetch-Site"))
        assertEquals("no-cors", sent.header("Sec-Fetch-Mode"))
        assertEquals("image", sent.header("Sec-Fetch-Dest"))
        assertEquals("i", sent.header("Priority"))
        // No-cors: no Origin even cross-origin
        assertNull(intercept(builder("https://cdn.b.test/1.jpg", referer = "https://a.test/").fetch(Fetch.IMAGE).build()).header("Origin"))
    }

    @Test
    fun withoutKindGoesOutAsBuilt() {
        val get = builder("https://api.b.test/list", referer = "https://a.test/").header("X-Site", "1").build()
        assertEquals(get.headers, intercept(get, wireOrder = true).headers)
        // A source that drops the default Origin keeps it dropped
        val post = builder("https://a.test/api/search", referer = "https://a.test/").removeHeader("Origin").post(json()).build()
        assertEquals(post.headers, intercept(post, wireOrder = true).headers)
    }

    @Test
    fun noneGoesOutAsBuilt() {
        val image = builder("https://cdn.a.test/1.jpg", referer = "https://a.test/").fetch(Fetch.NONE).build()
        assertEquals(image.headers, intercept(image, wireOrder = true).headers)
        // Not taken for one of the apps' covers
        val cover = builder("https://cdn.a.test/c.jpg").cacheControl(CacheControl.Builder().noStore().build()).fetch(Fetch.NONE).build()
        assertEquals(cover.headers, intercept(cover).headers)
    }

    @Test
    fun keepsWhatTheSourceSet() {
        val sent = intercept(
            builder("https://a.test/api/list").fetch(Fetch.XHR)
                .header("Origin", "https://other.test")
                .header("Sec-Fetch-Site", "none")
                .build(),
        )
        assertEquals("https://other.test", sent.header("Origin"))
        assertEquals("none", sent.header("Sec-Fetch-Site"))
    }

    @Test
    fun defaultOriginWithoutReferer() {
        val sent = intercept(builder("https://a.test/api/search").fetch(Fetch.XHR).post(json()).build())
        assertEquals("https://a.test", sent.header("Origin"))
        assertEquals("same-origin", sent.header("Sec-Fetch-Site"))
    }

    @Test
    fun chromiumForksGetChromeHeaders() {
        val edge = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Mobile Safari/537.36 EdgA/154.0.0.0"
        val sent = intercept(builder("https://a.test/series/1", ua = edge).fetch(Fetch.DOCUMENT).build())
        assertEquals("\"Microsoft Edge\";v=\"154\", \"Chromium\";v=\"155\", \"Not(A:Brand\";v=\"24\"", sent.header("Sec-CH-UA"))
        assertEquals("u=0, i", sent.header("Priority"))
        val webView = "Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/155.0.0.0 Mobile Safari/537.36"
        assertEquals(chromeBrands(155, "Android WebView"), intercept(builder("https://a.test/1", ua = webView).fetch(Fetch.DOCUMENT).build()).header("Sec-CH-UA"))
        val samsung = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/29.0 Chrome/155.0.0.0 Mobile Safari/537.36"
        val unlisted = intercept(builder("https://a.test/series/1", ua = samsung).fetch(Fetch.DOCUMENT).build())
        assertNull(unlisted.header("Sec-CH-UA"))
        assertEquals("?1", unlisted.header("Sec-CH-UA-Mobile"))
        assertEquals("navigate", unlisted.header("Sec-Fetch-Mode"))
    }

    @Test
    fun firefoxGetsFirefoxValues() {
        val sent = intercept(builder("https://a.test/series/1", ua = firefoxUa).fetch(Fetch.DOCUMENT).build())
        assertNull(sent.header("Sec-CH-UA"))
        assertEquals("navigate", sent.header("Sec-Fetch-Mode"))
        assertEquals("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", sent.header("Accept"))
        assertEquals("u=5, i", intercept(builder("https://a.test/1", ua = firefoxUa).fetch(Fetch.IMAGE).build()).header("Priority"))
    }

    @Test
    fun otherClientsGoOutAsBuilt() {
        val safari = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"
        val request = builder("https://a.test/series/1", ua = safari).fetch(Fetch.DOCUMENT).build()
        assertEquals(request.headers, intercept(request).headers)
    }

    @Test
    fun nonBrowserClientsGetNoBrowserHeaders() {
        val sent = intercept(builder("https://api.a.test/list", ua = "Tachiyomi Dalvik/2.1.0").fetch(Fetch.XHR).build())
        assertEquals("https://a.test", sent.header("Origin"))
        assertNull(sent.header("Accept-Language"))
        assertNull(sent.header("Sec-Fetch-Mode"))
        assertNull(sent.header("Accept"))
    }

    @Test
    fun jsonWithoutCharsetOnTheWire() {
        // As OkHttp sets it from the body before the network interceptors
        val request = builder("https://a.test/api/x", referer = "https://a.test/").fetch(Fetch.XHR).post(json())
            .header("Content-Type", "application/json; charset=utf-8").build()
        val sent = intercept(request, wireOrder = true)
        assertEquals("application/json", sent.header("Content-Type"))
    }

    @Test
    fun wireOrderPerBrowser() {
        val chrome = intercept(builder("https://a.test/api/x", referer = "https://a.test/").fetch(Fetch.XHR).header("X-Site", "1").build(), wireOrder = true)
        assertEquals(
            listOf("Sec-CH-UA-Platform", "User-Agent", "Sec-CH-UA", "Sec-CH-UA-Mobile", "X-Site", "Accept", "Sec-Fetch-Site"),
            chrome.headers.map { it.first }.take(7),
        )
        val firefox = intercept(builder("https://a.test/api/x", referer = "https://a.test/", ua = firefoxUa).fetch(Fetch.XHR).header("X-Site", "1").build(), wireOrder = true, compression = true)
        assertEquals(
            listOf("User-Agent", "Accept", "Accept-Language", "Accept-Encoding", "Referer", "X-Site", "Sec-Fetch-Dest", "Sec-Fetch-Mode", "Sec-Fetch-Site", "Priority"),
            firefox.headers.map { it.first },
        )
    }

    @Test
    fun noFetchMetadataOverHttp() {
        val sent = intercept(builder("http://a.test/series/1").fetch(Fetch.DOCUMENT).build())
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

    @Test
    fun scripts() {
        val script = intercept(builder("https://cdn.b.test/app.js", referer = "https://a.test/").fetch(Fetch.SCRIPT).build())
        assertEquals("script", script.header("Sec-Fetch-Dest"))
        assertEquals("no-cors", script.header("Sec-Fetch-Mode"))
        assertEquals("*/*", script.header("Accept"))
        assertEquals("u=1", script.header("Priority"))
        assertNull(script.header("Origin"))
        assertEquals("u=2", intercept(builder("https://a.test/app.js", ua = firefoxUa).fetch(Fetch.SCRIPT).build()).header("Priority"))
    }

    @Test
    fun formSubmit() {
        val form = FormBody.Builder().add("q", "1").build()
        val sent = intercept(builder("https://a.test/search", referer = "https://a.test/").fetch(Fetch.DOCUMENT).post(form).build(), wireOrder = true)
        assertEquals("navigate", sent.header("Sec-Fetch-Mode"))
        assertEquals("https://a.test", sent.header("Origin"))
        assertEquals("max-age=0", sent.header("Cache-Control"))
        assertEquals(listOf("Cache-Control", "Sec-CH-UA"), sent.headers.map { it.first }.take(2))
    }

    @Test
    fun removedOriginStaysRemoved() {
        val sent = intercept(builder("https://a.test/api/search", referer = "https://a.test/").removeHeader("Origin").fetch(Fetch.XHR).post(json()).build())
        assertNull(sent.header("Origin"))
    }

    @Test
    fun fromPageIsSentAsBrowsersDo() {
        val sameOrigin = intercept(builder("https://a.test/api/pages").fetch(Fetch.XHR.from("https://a.test/series/1/2#p3")).build())
        assertEquals("https://a.test/series/1/2", sameOrigin.header("Referer"))
        assertEquals("same-origin", sameOrigin.header("Sec-Fetch-Site"))
        val crossSite = intercept(builder("https://cdn.b.test/1.jpg").fetch(Fetch.IMAGE.from("https://a.test/series/1/2")).build())
        assertEquals("https://a.test/", crossSite.header("Referer"))
        assertEquals("cross-site", crossSite.header("Sec-Fetch-Site"))
        assertNull(intercept(builder("http://cdn.b.test/1.jpg").fetch(Fetch.IMAGE.from("https://a.test/series/1/2")).build()).header("Referer"))
        // Paths are taken from the site, and non-ASCII pages are encoded
        assertEquals("https://a.test/series/1/2", intercept(builder("https://a.test/api").fetch(Fetch.XHR.from("/series/1/2")).build()).header("Referer"))
        assertEquals("https://a.test/", intercept(builder("https://cdn.b.test/1.jpg").fetch(Fetch.IMAGE.from("/series/1/2")).build()).header("Referer"))
        assertEquals("https://a.test/%E6%BC%AB%E7%94%BB/1", intercept(builder("https://a.test/api").fetch(Fetch.XHR.from("https://a.test/漫画/1")).build()).header("Referer"))
        // With NONE too
        assertEquals("https://a.test/", intercept(builder("https://cdn.b.test/1.jpg").fetch(Fetch.NONE.from("https://a.test/series/1/2")).build()).header("Referer"))
        // A Referer the source set itself is kept
        val own = intercept(builder("https://cdn.b.test/1.jpg", referer = "https://a.test/series/1/2").fetch(Fetch.IMAGE).build())
        assertEquals("https://a.test/series/1/2", own.header("Referer"))
    }

    @Test
    fun sourceCacheControlIsKept() {
        val sent = intercept(builder("https://a.test/", referer = "https://a.test/").fetch(Fetch.DOCUMENT).cacheControl(CacheControl.FORCE_NETWORK).build(), wireOrder = true)
        assertEquals("no-cache", sent.header("Cache-Control"))
    }

    @Test
    fun sourceMaxAgeIsKept() {
        val sent = intercept(builder("https://a.test/", referer = "https://a.test/").fetch(Fetch.DOCUMENT).cacheControl(CacheControl.Builder().maxAge(0, java.util.concurrent.TimeUnit.SECONDS).build()).build(), wireOrder = true)
        assertEquals("max-age=0", sent.header("Cache-Control"))
    }

    @Test
    fun absoluteFromWithoutSite() {
        val sent = intercept(builder("https://cdn.b.test/1.jpg").fetch(Fetch.IMAGE.from("https://a.test/series/1/2")).build(), site = "")
        assertEquals("https://a.test/", sent.header("Referer"))
    }

    @Test
    fun sourceFetchMetadataIsKept() {
        val sent = intercept(builder("https://cdn.b.test/1.jpg", referer = "https://a.test/").header("Sec-Fetch-Dest", "empty").fetch(Fetch.IMAGE).build())
        assertEquals("empty", sent.header("Sec-Fetch-Dest"))
        assertEquals("no-cors", sent.header("Sec-Fetch-Mode"))
        assertEquals("cross-site", sent.header("Sec-Fetch-Site"))
    }

    @Test
    fun jxlOnlyFromChrome155() {
        val chrome149 = chromeUa.replace("Chrome/155", "Chrome/149")
        val sent = intercept(builder("https://a.test/1.jpg", ua = chrome149).fetch(Fetch.IMAGE).build())
        assertEquals("image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8", sent.header("Accept"))
    }

    @Test
    fun ipv6Origin() {
        val sent = intercept(builder("http://[::1]:8080/api", referer = "http://[::1]:8080/").fetch(Fetch.XHR).post(json()).build())
        assertEquals("http://[::1]:8080", sent.header("Origin"))
    }

    @Test
    fun sameHostOtherPortIsSameSite() {
        val sent = intercept(builder("https://localhost:2/1.jpg", referer = "https://localhost:1/").fetch(Fetch.IMAGE).build())
        assertEquals("same-site", sent.header("Sec-Fetch-Site"))
    }

    @Test
    fun documentOrderOverHttp() {
        val sent = intercept(builder("http://a.test/series/1", referer = "http://a.test/").fetch(Fetch.DOCUMENT).build(), wireOrder = true)
        assertEquals(listOf("Upgrade-Insecure-Requests", "User-Agent", "Accept"), sent.headers.map { it.first }.take(3))
    }

    @Test
    fun retryFromResponseRequestStartsOver() {
        val stale = builder("https://a.test/api").tag(BrowserCall::class.java, BrowserCall(null, "https://a.test".toHttpUrl(), "https://a.test"))
        val none = stale.fetch(Fetch.NONE).build()
        assertEquals(none.headers, intercept(none, wireOrder = true).headers)
    }

    @Test
    fun coversAreImages() {
        // As the apps' cover loader builds it
        val cover = builder("https://cdn.a.test/cover.jpg", referer = "https://a.test/").cacheControl(CacheControl.Builder().noStore().build()).build()
        val sent = intercept(cover)
        assertEquals("image", sent.header("Sec-Fetch-Dest"))
        assertEquals("no-cors", sent.header("Sec-Fetch-Mode"))
        // As the source's imageFetch says
        assertEquals(cover.headers, intercept(cover, imageFetch = Fetch.NONE).headers)
        // Only a GET
        val post = builder("https://a.test/api", referer = "https://a.test/").cacheControl(CacheControl.Builder().noStore().build()).post(json()).build()
        assertEquals(post.headers, intercept(post).headers)
    }

    private fun Request.headersAsAdded() = tag(BrowserCall::class.java)?.hop(this) ?: this

    private fun json() = "{}".toRequestBody("application/json".toMediaType())

    // A source's request: KeiSource's default Origin, the given Referer
    private fun builder(url: String, referer: String? = null, ua: String = chromeUa) = Request.Builder().url(url)
        .header("User-Agent", ua)
        .header("Origin", "https://a.test")
        .apply { referer?.let { header("Referer", it) } }

    // The request as sent: with the wire order pass it's HTTP/1.1, as a unit test has no connection; without, the
    // headers as added. With compression, KeiSource's Accept-Encoding
    private fun intercept(request: Request, wireOrder: Boolean = false, compression: Boolean = false, site: String = "https://a.test", imageFetch: Fetch = Fetch.IMAGE): Request {
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(BrowserCallInterceptor({ site }, { imageFetch }))
            .apply { if (compression) addInterceptor(CompressionInterceptor(Gzip, Deflate, Brotli, Zstd)) }
            .addInterceptor(if (wireOrder) BrowserHeadersInterceptor else Interceptor { it.proceed(it.request().headersAsAdded()) })
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

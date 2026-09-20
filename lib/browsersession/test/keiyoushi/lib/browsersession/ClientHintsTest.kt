package keiyoushi.lib.browsersession

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClientHintsTest {

    @Before
    fun setup() {
        ClientHintsInterceptor.clearCache()
    }

    @Test
    fun parseChromeAndroidUserAgent() {
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.102 Mobile Safari/537.36"
        val hints = ClientHintsInterceptor.parse(ua)

        assertEquals("Android", hints.platform)
        assertTrue(hints.isMobile)
        assertTrue(hints.isChromiumBased)
        assertEquals("?1", hints.secChUaMobile)
        assertEquals("\"Android\"", hints.secChUaPlatform)

        assertNotNull(hints.secChUa)
        assertTrue(hints.secChUa!!.contains("\"Chromium\";v=\"130\""))
        assertTrue(hints.secChUa.contains("\"Google Chrome\";v=\"130\""))
        assertTrue(hints.secChUa.contains("\"Not?A_Brand\";v=\"99\""))
    }

    @Test
    fun parseChromeDesktopWindowsUserAgent() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        val hints = ClientHintsInterceptor.parse(ua)

        assertEquals("Windows", hints.platform)
        assertFalse(hints.isMobile)
        assertTrue(hints.isChromiumBased)
        assertEquals("?0", hints.secChUaMobile)
        assertEquals("\"Windows\"", hints.secChUaPlatform)

        assertNotNull(hints.secChUa)
        assertTrue(hints.secChUa!!.contains("\"Chromium\";v=\"130\""))
        assertTrue(hints.secChUa.contains("\"Google Chrome\";v=\"130\""))
    }

    @Test
    fun parseChromeMacUserAgent() {
        val ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
        val hints = ClientHintsInterceptor.parse(ua)

        assertEquals("macOS", hints.platform)
        assertFalse(hints.isMobile)
        assertEquals("\"macOS\"", hints.secChUaPlatform)
        assertEquals("?0", hints.secChUaMobile)
    }

    @Test
    fun parseChromeAndroidTabletUserAgent() {
        // Android tablets omit the "Mobile" token from Chrome UA
        val ua = "Mozilla/5.0 (Linux; Android 14; SM-X900) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.102 Safari/537.36"
        val hints = ClientHintsInterceptor.parse(ua)

        assertEquals("Android", hints.platform)
        assertFalse(hints.isMobile)
        assertTrue(hints.isChromiumBased)
        assertEquals("?0", hints.secChUaMobile)
        assertEquals("\"Android\"", hints.secChUaPlatform)
        assertNotNull(hints.secChUa)
    }

    @Test
    fun parseSafariMacUserAgent() {
        val ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"
        val hints = ClientHintsInterceptor.parse(ua)

        assertEquals("macOS", hints.platform)
        assertFalse(hints.isMobile)
        assertFalse(hints.isChromiumBased)
        assertNull(hints.secChUa)
    }

    @Test
    fun parseSamsungBrowserUserAgent() {
        val ua = "Mozilla/5.0 (Linux; Android 13; SAMSUNG SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/23.0 Chrome/115.0.5790.166 Mobile Safari/537.36"
        val hints = ClientHintsInterceptor.parse(ua)

        assertEquals("Android", hints.platform)
        assertTrue(hints.isMobile)
        assertTrue(hints.isChromiumBased)
        assertNotNull(hints.secChUa)
        assertTrue(hints.secChUa!!.contains("\"Samsung Internet\";v=\"23\""))
        assertTrue(hints.secChUa.contains("\"Chromium\";v=\"115\""))
    }

    @Test
    fun parseEdgeDesktopUserAgent() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.2736.56"
        val hints = ClientHintsInterceptor.parse(ua)

        assertEquals("Windows", hints.platform)
        assertFalse(hints.isMobile)
        assertTrue(hints.isChromiumBased)
        assertNotNull(hints.secChUa)
        assertTrue(hints.secChUa!!.contains("\"Microsoft Edge\";v=\"128\""))
        assertTrue(hints.secChUa.contains("\"Chromium\";v=\"128\""))
    }

    @Test
    fun parseFirefoxAndroidUserAgent() {
        val ua = "Mozilla/5.0 (Android 14; Mobile; rv:132.0) Gecko/132.0 Firefox/132.0"
        val hints = ClientHintsInterceptor.parse(ua)

        assertEquals("Android", hints.platform)
        assertTrue(hints.isMobile)
        assertFalse(hints.isChromiumBased)
        assertNull(hints.secChUa)
    }

    @Test
    fun parseFirefoxDesktopUserAgent() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0"
        val hints = ClientHintsInterceptor.parse(ua)

        assertEquals("Windows", hints.platform)
        assertFalse(hints.isMobile)
        assertFalse(hints.isChromiumBased)
        assertNull(hints.secChUa)
    }

    @Test
    fun cachingReturnsSameInstance() {
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        val first = ClientHintsInterceptor.parse(ua)
        val second = ClientHintsInterceptor.parse(ua)
        assertSame(first, second)
    }

    @Test
    fun interceptorInjectsHeadersForChrome() {
        val interceptor = ClientHintsInterceptor()
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        val request = Request.Builder()
            .url("https://example.com/")
            .header("User-Agent", ua)
            .build()

        var capturedRequest: Request? = null
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                capturedRequest = chain.request()
                createDummyResponse(chain.request())
            }
            .build()

        client.newCall(request).execute()

        val captured = requireNotNull(capturedRequest)
        assertEquals("?1", captured.header("Sec-CH-UA-Mobile"))
        assertEquals("\"Android\"", captured.header("Sec-CH-UA-Platform"))
        assertTrue(captured.header("Sec-CH-UA")!!.contains("\"Chromium\";v=\"130\""))
    }

    @Test
    fun interceptorPreservesExistingHeadersWhenNotForced() {
        val interceptor = ClientHintsInterceptor(force = false)
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        val request = Request.Builder()
            .url("https://example.com/")
            .header("User-Agent", ua)
            .header("Sec-CH-UA", "\"CustomBrand\";v=\"1\"")
            .build()

        var capturedRequest: Request? = null
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                capturedRequest = chain.request()
                createDummyResponse(chain.request())
            }
            .build()

        client.newCall(request).execute()

        val captured = requireNotNull(capturedRequest)
        assertEquals("\"CustomBrand\";v=\"1\"", captured.header("Sec-CH-UA"))
    }

    @Test
    fun interceptorOverwritesExistingHeadersWhenForced() {
        val interceptor = ClientHintsInterceptor(force = true)
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        val request = Request.Builder()
            .url("https://example.com/")
            .header("User-Agent", ua)
            .header("Sec-CH-UA", "\"CustomBrand\";v=\"1\"")
            .build()

        var capturedRequest: Request? = null
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                capturedRequest = chain.request()
                createDummyResponse(chain.request())
            }
            .build()

        client.newCall(request).execute()

        val captured = requireNotNull(capturedRequest)
        assertTrue(captured.header("Sec-CH-UA")!!.contains("\"Chromium\";v=\"130\""))
    }
}

private fun createDummyResponse(request: Request): Response =
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body("{}".toResponseBody("application/json".toMediaType()))
        .build()

package keiyoushi.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class WebKitBrowserHeadersTest {
    private val mobile = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"
    private val desktop = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko)"
    private val safari26 = mobile.replace("Mobile/", "Version/26.0 Mobile/") + " Safari/604.1"
    private val safari27 = safari26.replace("18_6", "18_7").replace("Version/26.0", "Version/27.0")
    private val agents = listOf(mobile, mobile.replace("18_6", "18_7"), desktop, safari26, safari27, "$desktop Version/27.0 Safari/605.1.15")

    @Test
    fun recognizesMobileAndDesktopWebKitWithoutChangingTheUserAgent() {
        for (ua in agents) {
            val sent = send(request(ua).build())
            assertEquals(ua, sent.header("User-Agent"))
            assertEquals("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", sent.header("Accept"))
            assertEquals("navigate", sent.header("Sec-Fetch-Mode"))
            assertEquals("document", sent.header("Sec-Fetch-Dest"))
            assertEquals("same-origin", sent.header("Sec-Fetch-Site"))
            assertEquals("u=0, i", sent.header("Priority"))
            assertNull(sent.header("Sec-Fetch-User"))
            assertNull(sent.header("Upgrade-Insecure-Requests"))
            assertNull(sent.header("Sec-CH-UA"))
            assertNull(sent.header("Sec-CH-UA-Mobile"))
            assertNull(sent.header("Sec-CH-UA-Platform"))
            assertNull(sent.header("TE"))
        }
    }

    @Test
    fun resourceValuesMatchIos26And27Captures() {
        val image = send(request(fetch = Fetch.IMAGE).build())
        assertEquals("image/webp,image/avif,image/jxl,image/heic,image/heic-sequence,video/*;q=0.8,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5", image.header("Accept"))
        assertEquals("u=5, i", image.header("Priority"))
        assertEquals("image", image.header("Sec-Fetch-Dest"))
        assertEquals("no-cors", image.header("Sec-Fetch-Mode"))
        for ((fetch, priority, destination) in listOf(Triple(Fetch.XHR, "u=3, i", "empty"), Triple(Fetch.SCRIPT, "u=1, i", "script"))) {
            val sent = send(request(fetch = fetch).build())
            assertEquals("*/*", sent.header("Accept"))
            assertEquals(priority, sent.header("Priority"))
            assertEquals(destination, sent.header("Sec-Fetch-Dest"))
        }
    }

    @Test
    fun webKitFillsLanguageWithoutConnection() {
        for (ua in agents) {
            val sent = send(request(ua).build())
            assertEquals(acceptLanguage(listOf(Locale.getDefault())), sent.header("Accept-Language"))
            assertNull(sent.header("Accept-Encoding"))
            assertEquals("ja", send(request(ua).header("Accept-Language", "ja").build()).header("Accept-Language"))
        }
    }

    @Test
    fun webKitOrdersHeadersWithoutSkippingOtherWireRules() {
        for (fetch in listOf(Fetch.DOCUMENT, Fetch.XHR)) {
            val req = Request.Builder().url("https://a.test/").fetch(fetch)
                .header("X-First", "first")
                .header("User-Agent", mobile)
                .header("Cache-Control", "no-store")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-Last", "last")
                .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val sent = req.inWireOrder(true)
            assertEquals(listOf("User-Agent", "X-First", "X-Last", "Content-Type"), sent.headers.map { it.first })
            assertEquals("application/json", sent.header("Content-Type"))
            assertNull(sent.header("Cache-Control"))
        }
    }

    @Test
    fun okHttpSendsLanguageAndSafariGetOrderOnTheWire() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5000
            val base = "http://localhost:${server.localPort}"
            val client = OkHttpClient.Builder()
                .protocols(listOf(Protocol.HTTP_1_1))
                .callTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(BrowserCallInterceptor({ base }, { Fetch.IMAGE }))
                .addNetworkInterceptor(BrowserHeadersInterceptor)
                .build()
            for (fetch in listOf(Fetch.DOCUMENT, Fetch.XHR, Fetch.IMAGE, Fetch.SCRIPT)) {
                val received = CompletableFuture.supplyAsync {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        reader.readLine()
                        val headers = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }
                            .map { it.substringBefore(":") to it.substringAfter(":").trim() }.toList()
                        socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        headers
                    }
                }
                val req = request(safari26, fetch).url("$base/").header("Referer", "$base/").build()
                client.newCall(req).execute().close()
                val headers = received.get(5, TimeUnit.SECONDS)
                val expected = buildList {
                    addAll(listOf("Host", "Sec-Fetch-Dest", "User-Agent"))
                    if (fetch == Fetch.DOCUMENT) add("Upgrade-Insecure-Requests")
                    addAll(
                        listOf(
                            "Accept",
                            "Referer",
                            "Sec-Fetch-Site",
                            "Sec-Fetch-Mode",
                            "Accept-Language",
                            "Priority",
                            "Accept-Encoding",
                            "Connection",
                        ),
                    )
                }
                assertEquals(expected, headers.map { it.first })
                assertEquals(acceptLanguage(listOf(Locale.getDefault())), headers.first { it.first == "Accept-Language" }.second)
            }
        }
    }

    @Test
    fun httpNavigationUpgradesButDoesNotSendSecureMetadata() {
        val sent = send(request().url("http://a.test/").build())
        assertEquals("1", sent.header("Upgrade-Insecure-Requests"))
        assertNull(sent.header("Sec-Fetch-Site"))
        assertEquals("u=0, i", sent.header("Priority"))
        assertEquals("navigate", send(request().url("http://localhost/").build()).header("Sec-Fetch-Mode"))
    }

    @Test
    fun sourceHeadersAndOptOutsAreKept() {
        val own = request().header("Accept", "application/json").header("Accept-Language", "ja")
            .header("Accept-Encoding", "identity").header("Priority", "u=2")
            .header("Origin", "https://custom.test").header("Sec-Fetch-User", "?1").build()
        val sent = send(own)
        for (name in listOf("Accept", "Accept-Language", "Accept-Encoding", "Priority", "Origin", "Sec-Fetch-User")) assertEquals(own.header(name), sent.header(name))
        val none = request(fetch = Fetch.NONE).build()
        assertNull(send(none).header("Accept"))
        val untagged = Request.Builder().url("https://a.test/").header("User-Agent", mobile).build()
        assertNull(send(untagged).header("Accept"))
    }

    @Test
    fun applePlatformFallbackDoesNotDependOnVersionsOrOverrideExistingBrowsers() {
        val platformAgents = listOf(
            mobile.replace("iPhone; CPU iPhone OS", "iPad; CPU OS"),
            mobile.replace("iPhone;", "iPod;"),
            mobile.replace("AppleWebKit/605.1.15", "AppleWebKit/999.1"),
            mobile.replace("Mobile/", "CriOS/155.0 Mobile/"),
        )
        for (ua in platformAgents) assertEquals(ua, Browser.WebKit, Browser.of(ua))
        assertEquals(Browser.Chrome, Browser.of("$desktop Chrome/155.0.0.0 Safari/537.36"))
        assertEquals(Browser.Firefox, Browser.of("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:158.0) Gecko/20100101 Firefox/158.0"))
        assertNull(Browser.of("Mozilla/5.0 (Linux; Android 15) AppleWebKit/605.1.15"))
    }

    @Test
    fun appleApiClientsWithoutWebKitGoOutAsBuilt() {
        val agents = listOf(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 27_0 like Mac OS X)",
            "ExampleApiClient/1.0 (iPad; CPU OS 27_0 like Mac OS X)",
            "ExampleApiClient/1.0 (Macintosh; Intel Mac OS X 10_15_7)",
        )
        for (ua in agents) {
            assertNull(Browser.of(ua))
            val req = request(ua).build()
            assertEquals(req.headers, send(req).headers)
        }
    }

    @Test
    fun webKitKeepsPriorityOnBothProtocolsAndWithoutNativeConnection() {
        val sent = send(request().build())
        assertEquals("u=0, i", sent.header("Priority"))
        assertEquals("u=0, i", sent.inWireOrder(true).header("Priority"))
        assertEquals("u=0, i", sent.inWireOrder(false).header("Priority"))
    }

    @Test
    fun chromeAndFirefoxKeepTheirExistingProtocolRules() {
        val chrome = request("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Mobile Safari/537.36").build()
        val firefox = request("Mozilla/5.0 (Android 14; Mobile; rv:158.0) Gecko/158.0 Firefox/158.0").build()
        val call = BrowserCall(null, "https://a.test/".toHttpUrl())
        for (http2 in listOf(false, true)) {
            val chromeSent = call.hop(chrome).inWireOrder(http2)
            assertEquals(if (http2) "u=0, i" else null, chromeSent.header("Priority"))
            assertNull(chromeSent.header("TE"))
            val firefoxSent = call.hop(firefox).inWireOrder(http2)
            assertEquals("u=0, i", firefoxSent.header("Priority"))
            assertEquals(if (http2) "trailers" else null, firefoxSent.header("TE"))
        }
        assertNull(send(chrome).header("Priority"))
        assertEquals("u=0, i", send(firefox).header("Priority"))
        assertNull(send(firefox).header("TE"))
    }

    @Test
    fun crossSiteRedirectsKeepFetchMetadata() {
        val req = request(fetch = Fetch.XHR.from("https://a.test/chapter/1?token=1")).build()
        val call = BrowserCall("https://a.test/chapter/1?token=1".toHttpUrl(), "https://a.test/".toHttpUrl())
        call.hop(req)
        val cross = call.hop(req.newBuilder().url("https://cdn.b.test/api").build())
        assertEquals("https://a.test", cross.header("Origin"))
        assertEquals("https://a.test/", cross.header("Referer"))
        assertEquals("cross-site", cross.header("Sec-Fetch-Site"))
        assertEquals("cors", cross.header("Sec-Fetch-Mode"))
        assertEquals("empty", cross.header("Sec-Fetch-Dest"))
    }

    private fun request(ua: String = mobile, fetch: Fetch = Fetch.DOCUMENT) = Request.Builder().url("https://a.test/")
        .header("User-Agent", ua).header("Referer", "https://a.test/").fetch(fetch)

    private fun send(request: Request): Request {
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(BrowserCallInterceptor({ "https://a.test" }, { Fetch.IMAGE }))
            // A chain without a Connection exercises the real interceptor as with a host-managed transport.
            .addInterceptor(BrowserHeadersInterceptor)
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("".toResponseBody()).build()
            }.build()
        client.newCall(request).execute().close()
        return sent!!
    }
}

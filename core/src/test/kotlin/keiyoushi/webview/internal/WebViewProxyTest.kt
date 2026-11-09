package keiyoushi.webview.internal

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** The proxy's HTTP side, with the route's interceptor answering in place of the site. */
class WebViewProxyTest {
    private companion object {
        const val PACKAGE = "app.test"
    }

    private val routes = mutableListOf<WebViewProxy.Route>()
    private val seen = CopyOnWriteArrayList<Request>()

    init {
        WebViewProxy.retireGraceMillis = 100
        WebViewOverride.watch = { Runnable {} }
    }

    @Before
    fun noProxyRunning() = awaitStopped()

    @After
    fun closeRoutes() {
        routes.forEach(WebViewProxy::close)
        awaitStopped()
        WebViewProxy.firstHeadMillis = 10_000
        WebViewProxy.maxConnections = 64
    }

    private fun open(host: String = "a.test", answer: (Request) -> Response = { ok(it, "hello") }): WebViewProxy.Route {
        val interceptor = Interceptor { chain -> chain.request().also(seen::add).let(answer) }
        return WebViewProxy.open(setOf(host), routeSend(OkHttpClient(), interceptor), {}).also(routes::add)
    }

    // Closed by the proxy: an end of stream, or a reset where it left bytes unread
    private fun ended(socket: Socket): Boolean = try {
        socket.getInputStream().read() == -1
    } catch (e: SocketException) {
        true
    }

    private fun connect() = Socket(InetAddress.getLoopbackAddress(), ProxyAccess.current().port).apply { soTimeout = 5_000 }

    @Test
    fun aSlowFirstHeadTimesOutInTotal() {
        WebViewProxy.firstHeadMillis = 400
        open()
        connect().use { socket ->
            val started = System.nanoTime()
            // A byte every 100 ms keeps each read well inside the time, but not the head as a whole
            val output = socket.getOutputStream()
            val drip = thread {
                runCatching {
                    "CONNECT a.test:443 HTTP/1.1\r\nHost: a.test:443\r\nX: ${"x".repeat(40)}".forEach {
                        output.write(it.code)
                        Thread.sleep(100)
                    }
                }
            }
            assertTrue(ended(socket))
            val took = (System.nanoTime() - started) / 1_000_000
            assertTrue("closed after $took ms", took in 300..2_000)
            drip.join()
        }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun anAuthorizedConnectionWaitsAsLongAsItLikes() {
        WebViewProxy.firstHeadMillis = 300
        val route = open()
        tunnel(route).use { tls ->
            Thread.sleep(600)
            tls.outputStream.write("GET / HTTP/1.1\r\nHost: a.test\r\n\r\n".toByteArray())
            assertEquals(Answer(200, "hello"), readResponse(tls.inputStream).answer)
        }
    }

    @Test
    fun atTheCapTheOldestUnauthenticatedConnectionMakesWay() {
        WebViewProxy.maxConnections = 3
        val route = open()
        val idle = List(3) { connect() }
        try {
            // Let the server take them all in, then a WebView connection comes
            Thread.sleep(200)
            assertEquals(Answer(200, "hello"), get(access(route), "a.test").answer)
            assertTrue(ended(idle[0]))
        } finally {
            idle.forEach { it.close() }
        }
    }

    @Test
    fun atTheCapOfAuthorizedConnectionsNewOnesAreRefused() {
        WebViewProxy.maxConnections = 2
        val route = open()
        val held = List(2) { tunnel(route) }
        try {
            connect().use { assertTrue(ended(it)) }
            held[0].close()
            awaitTrue("a connection to free up") {
                runCatching { get(access(route), "a.test").answer == Answer(200, "hello") }.getOrDefault(false)
            }
        } finally {
            held.forEach { it.close() }
        }
    }

    @Test
    fun aGetWithABodyIsABadRequest() {
        val route = open()
        tunnel(route).use { tls ->
            tls.outputStream.write("GET / HTTP/1.1\r\nHost: a.test\r\nContent-Length: 3\r\n\r\nabc".toByteArray())
            assertEquals(400, readResponse(tls.inputStream).code)
        }
        assertTrue(seen.isEmpty())
    }

    // Its end unknown, the body would be read as the next request
    @Test
    fun aLengthThatIsNotOneIsABadRequest() {
        val route = open()
        listOf("abc", "-1", "+3", "3, 3", "2147483648", "99999999999999999999", "${32 * 1024 * 1024 + 1}").forEach { length ->
            tunnel(route).use { tls ->
                tls.outputStream.write("POST / HTTP/1.1\r\nHost: a.test\r\nContent-Length: $length\r\n\r\nGET /smuggled HTTP/1.1\r\nHost: a.test\r\n\r\n".toByteArray())
                val response = readResponse(tls.inputStream)
                assertEquals(length, 400, response.code)
                assertEquals(length, "close", response.headers["connection"])
                assertEquals(length, -1, tls.inputStream.read())
            }
        }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun aChunkedCopySkipsEmptyReads() {
        val reads = ArrayDeque(listOf("ab", "", "cd", ""))
        val source = object : InputStream() {
            override fun read() = throw UnsupportedOperationException()
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                val next = reads.removeFirstOrNull() ?: return -1
                next.toByteArray().copyInto(bytes, offset)
                return next.length
            }
        }
        val output = ByteArrayOutputStream()
        with(WebViewProxy) { source.copyFlushing(output, chunked = true) }
        assertEquals("2\r\nab\r\n2\r\ncd\r\n0\r\n\r\n", output.toString(Charsets.ISO_8859_1))
    }

    @Test
    fun aLapsedRouteIsNotServedBeforeItIsDropped() {
        val route = open()
        open("b.test")
        tunnel(route).use { tls ->
            lapse(routes().single { "a.test" in (it[ProxyProtocol.HOSTS] as Set<*>) })
            tls.outputStream.write("GET / HTTP/1.1\r\nHost: a.test\r\n\r\n".toByteArray())
            assertEquals(403, readResponse(tls.inputStream).code)
        }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun theCertificateIsAcceptedOnlyForTheRoutesOwnHosts() {
        val a = open()
        val b = open("b.test")
        val own = a.certificate
        assertTrue(a.presents("https://a.test/x") { own })
        assertTrue(a.presents("https://cdn.a.test/") { own })
        assertFalse("another extension's host", a.presents("https://b.test/") { own })
        assertTrue(b.presents("https://b.test/") { own })
        assertFalse(a.presents("https://a.test/") { byteArrayOf(1, 2, 3) })
        assertFalse(a.presents("https://xa.test/") { own })
        assertFalse(a.presents(null) { own })
        assertFalse(a.presents("not a url") { own })
    }

    @Test
    fun asksForCredentials() {
        val route = open()
        Socket(InetAddress.getLoopbackAddress(), access(route).port).use { socket ->
            socket.getOutputStream().write("CONNECT a.test:443 HTTP/1.1\r\nHost: a.test:443\r\n\r\n".toByteArray())
            val response = readResponse(socket.getInputStream())
            assertEquals(407, response.code)
            assertEquals("Basic realm=\"${route.realm}\"", response.headers["proxy-authenticate"])
        }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun answersRequestsInsideTheTunnel() {
        val route = open { request ->
            when (request.method) {
                "POST" -> ok(request, request.body!!.let { Buffer().also(it::writeTo).readUtf8() })
                else -> ok(request, "hello")
            }
        }
        tunnel(route).use { tls ->
            val output = tls.outputStream
            val input = tls.inputStream
            output.write("GET /x?q=1 HTTP/1.1\r\nHost: a.test\r\nX-Requested-With: $PACKAGE\r\nSec-Fetch-Dest: document\r\n\r\n".toByteArray())
            assertEquals(Answer(200, "hello"), readResponse(input).answer)
            // Same connection: a HEAD without a body, then a chunked POST
            output.write("HEAD /x HTTP/1.1\r\nHost: a.test\r\n\r\n".toByteArray())
            val head = readResponse(input, noBody = true)
            assertEquals(Answer(200, ""), head.answer)
            output.write("POST /y HTTP/1.1\r\nHost: a.test\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n2\r\nde\r\n0\r\n\r\n".toByteArray())
            assertEquals(Answer(200, "abcde"), readResponse(input).answer)
        }
        assertEquals("https://a.test/x?q=1", seen[0].url.toString())
        assertNull(seen[0].header("X-Requested-With"))
        assertEquals(listOf("GET", "HEAD", "POST"), seen.map { it.method })
    }

    // The app's package, or the one the app has WebView say: Chrome's, Settings', YouTube for TV's or any installed app's
    @Test
    fun dropsOnlyWebViewsOwnRequestedWith() {
        val sent = CopyOnWriteArrayList<Request>()
        val send = routeSend(OkHttpClient(), { chain -> chain.request().also(sent::add).let { ok(it, "") } })
        val values = listOf(PACKAGE, "com.android.chrome", "com.android.settings", "com.google.android.youtube.tv", "org.example.some_app2", "XMLHttpRequest", "")
        values.forEach { value ->
            send.apply(Request.Builder().url("https://a.test/").header("X-Requested-With", value).build()).close()
        }
        assertEquals(listOf(null, null, null, null, null, "XMLHttpRequest", ""), sent.map { it.header("X-Requested-With") })
    }

    @Test
    fun streamsBodiesOfUnknownLength() {
        val route = open { request ->
            val body = Buffer().writeUtf8("streamed").asResponseBody("text/plain".toMediaType(), -1)
            ok(request, "").newBuilder().body(body).build()
        }
        tunnel(route).use { tls ->
            tls.outputStream.write("GET / HTTP/1.1\r\nHost: a.test\r\n\r\n".toByteArray())
            val response = readResponse(tls.inputStream)
            assertEquals("chunked", response.headers["transfer-encoding"])
            assertEquals(Answer(200, "streamed"), response.answer)
        }
    }

    @Test
    fun answersPlainHttpForItsHosts() {
        val route = open()
        Socket(InetAddress.getLoopbackAddress(), access(route).port).use { socket ->
            socket.getOutputStream().write("GET http://a.test/z HTTP/1.1\r\nHost: a.test\r\n${authorization(route)}\r\n".toByteArray())
            assertEquals(Answer(200, "hello"), readResponse(socket.getInputStream()).answer)
        }
        assertEquals("http://a.test/z", seen.single().url.toString())
    }

    @Test
    fun tunnelsOtherHostsAsTheyAre() {
        val route = open()
        ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { site ->
            thread { site.accept().use { it.getInputStream().copyTo(it.getOutputStream()) } }
            Socket(InetAddress.getLoopbackAddress(), access(route).port).use { socket ->
                val output = socket.getOutputStream()
                output.write("CONNECT 127.0.0.1:${site.localPort} HTTP/1.1\r\n${authorization(route)}\r\n".toByteArray())
                assertEquals(200, readResponse(socket.getInputStream(), noBody = true).code)
                output.write(byteArrayOf(1, 2, 3))
                assertArrayEquals(byteArrayOf(1, 2, 3), socket.getInputStream().readNBytes(3))
            }
        }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun tunnelsOtherHostsOnThroughTheDeviceProxy() {
        val route = open()
        ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { deviceProxy ->
            val heads = CopyOnWriteArrayList<String>()
            thread {
                deviceProxy.accept().use {
                    heads += String(it.getInputStream().readHeadBytes(), Charsets.ISO_8859_1).substringBefore("\r\n")
                    it.getOutputStream().write("HTTP/1.1 200 OK\r\n\r\n".toByteArray())
                    it.getInputStream().copyTo(it.getOutputStream())
                }
            }
            withProxySelector(Proxy(Proxy.Type.HTTP, InetSocketAddress(InetAddress.getLoopbackAddress(), deviceProxy.localPort))) {
                assertArrayEquals(byteArrayOf(1, 2, 3), echoThroughTunnel(route, "other.test:443"))
            }
            assertEquals(listOf("CONNECT other.test:443 HTTP/1.1"), heads)
        }
        assertTrue(seen.isEmpty())
    }

    // As a PAC script can answer
    @Test
    fun aDirectFirstChoiceTunnelsDirect() {
        val route = open()
        ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { site ->
            thread { site.accept().use { it.getInputStream().copyTo(it.getOutputStream()) } }
            val unused = Proxy(Proxy.Type.HTTP, InetSocketAddress(InetAddress.getLoopbackAddress(), 9))
            withProxySelector(Proxy.NO_PROXY, unused) {
                assertArrayEquals(byteArrayOf(1, 2, 3), echoThroughTunnel(route, "127.0.0.1:${site.localPort}"))
            }
        }
    }

    private fun withProxySelector(vararg proxies: Proxy, block: () -> Unit) {
        val default = ProxySelector.getDefault()
        ProxySelector.setDefault(
            object : ProxySelector() {
                override fun select(uri: URI) = proxies.toList()
                override fun connectFailed(uri: URI, address: SocketAddress, e: IOException) = Unit
            },
        )
        try {
            block()
        } finally {
            ProxySelector.setDefault(default)
        }
    }

    private fun echoThroughTunnel(route: WebViewProxy.Route, authority: String): ByteArray = Socket(InetAddress.getLoopbackAddress(), access(route).port).use { socket ->
        socket.soTimeout = 5_000
        val output = socket.getOutputStream()
        output.write("CONNECT $authority HTTP/1.1\r\n${authorization(route)}\r\n".toByteArray())
        assertEquals(200, readResponse(socket.getInputStream(), noBody = true).code)
        output.write(byteArrayOf(1, 2, 3))
        socket.getInputStream().readNBytes(3)
    }

    private fun InputStream.readHeadBytes(): ByteArray {
        val head = ByteArrayOutputStream()
        while (!head.toString(Charsets.ISO_8859_1).endsWith("\r\n\r\n")) head.write(read().takeIf { it >= 0 } ?: break)
        return head.toByteArray()
    }

    private fun access(route: WebViewProxy.Route) = ProxyAccess(ProxyAccess.current().port, route.realm, route.username, route.password, route.certificate)

    private fun authorization(route: WebViewProxy.Route) = access(route).authorization

    private fun tunnel(route: WebViewProxy.Route) = tunnel(access(route))
}

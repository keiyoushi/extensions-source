package keiyoushi.webview.internal

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * An older build's copy of core beside this build's, as when one extension is updated before another.
 *
 * The older copy is this build with its version lowered, and each frozen release in `core/src/test/resources/
 * webview-proxy/`. To freeze a release whose [ProxyProtocol.VERSION] went out, at that commit:
 * ```
 * ./gradlew :core:compileReleaseKotlin
 * jar cf core/src/test/resources/webview-proxy/v<VERSION>.jar \
 *     -C core/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes .
 * ```
 * and commit the jar. [ProxyCopy] drives a copy by the names [ProxyProtocol] lists as stable; should one change,
 * adapt [ProxyCopy] for the older jars rather than drop them.
 */
@RunWith(Parameterized::class)
class WebViewProxyCompatTest(@Suppress("unused") private val name: String, private val olderClasses: URL, private val olderVersion: Int?) {
    private val webView = FakeWebView()
    private val copies = mutableListOf<ProxyCopy>()

    private fun newer() = ProxyCopy(ProxyCopy.CURRENT, null, webView).also(copies::add)
    private fun older() = ProxyCopy(olderClasses, olderVersion, webView).also(copies::add)

    @Before
    fun noProxyRunning() = awaitStopped()

    @After
    fun closeRoutes() {
        copies.forEach(ProxyCopy::closeAll)
        awaitStopped()
    }

    @Test
    fun olderVersionIsLower() {
        assertTrue(older().version < newer().version)
    }

    @Test
    fun olderServerServesANewerCopysRecords() = servesRecordsANewerCopyWrote(older())

    @Test
    fun newerCopyTakesOverWithRoutesAndLoadsIntact() {
        val older = older()
        older.graceMillis = 3_000
        val release = CountDownLatch(1)
        older.open(setOf("a.test")) { request ->
            if (request.url.encodedPath == "/slow") release.await(10, TimeUnit.SECONDS)
            ok(request, Thread.currentThread().name)
        }
        val before = ProxyAccess.current()
        assertEquals(Answer(200, "WebViewProxy v${older.version}"), get(before, "a.test").answer)

        // A load in flight, and a kept-alive connection between loads
        var slow: Answer? = null
        val loading = thread { slow = get(before, "a.test", "/slow").answer }
        val idle = tunnel(before, "a.test")
        idle.outputStream.write("GET / HTTP/1.1\r\nHost: a.test\r\n\r\n".toByteArray())
        assertEquals(200, readResponse(idle.inputStream).code)

        val newer = newer()
        newer.open(setOf("b.test")) { ok(it, "b") }
        val after = ProxyAccess.current()
        assertEquals("same listener: WebView's override and remembered credentials stay", before.port, after.port)
        assertEquals(newer.version, (shared[ProxyProtocol.SERVER] as Map<*, *>)[ProxyProtocol.SERVER_VERSION])

        release.countDown()
        loading.join(10_000)
        assertEquals(Answer(200, "WebViewProxy v${older.version}"), slow)
        // Within a tick, new connections are the newer server's
        awaitTrue("the older server to stop accepting") { acceptThreads() == listOf("WebViewProxy v${newer.version} accept") }
        assertEquals(Answer(200, "WebViewProxy v${newer.version}"), get(after, "a.test").answer)
        assertEquals(Answer(200, "b"), get(after, "b.test").answer)

        // The older server lets go of what's left after its grace
        idle.soTimeout = 10_000
        assertEquals(-1, idle.inputStream.read())
        awaitTrue("the older server to end") { serverThreads() == listOf("WebViewProxy v${newer.version} accept") }
        webView.idle()
        assertEquals(listOf("set ${before.port}", "set ${before.port}"), webView.events)
    }

    @Test
    fun olderCopyUsesANewerServer() {
        val newer = newer()
        val older = older()
        newer.open(setOf("a.test")) { ok(it, "a") }
        older.open(setOf("b.test")) { ok(it, Thread.currentThread().name) }
        assertEquals(newer.version, (shared[ProxyProtocol.SERVER] as Map<*, *>)[ProxyProtocol.SERVER_VERSION])
        assertEquals(Answer(200, "WebViewProxy v${newer.version}"), get(ProxyAccess.current(), "b.test").answer)
        assertEquals(listOf("WebViewProxy v${newer.version} accept"), acceptThreads())
    }

    @Test
    fun theLastRouteClosingStopsTheProxyWhicheverCopyCloses() {
        val newer = newer()
        val older = older()
        val a = older.open(setOf("a.test")) { ok(it, "") }
        val b = newer.open(setOf("b.test")) { ok(it, "") }
        newer.close(b)
        older.close(a)
        awaitStopped()
        webView.idle()
        assertEquals("clear", webView.events.last())
    }

    @Test
    fun concurrentOpensAcrossVersionsLeaveTheNewestServing() {
        val threads = 12
        val copies = listOf(older(), newer(), older())
        val barrier = CyclicBarrier(threads)
        val opened = Collections.synchronizedList(mutableListOf<Any>())
        List(threads) { i ->
            thread {
                barrier.await()
                opened += copies[i % copies.size].open(setOf("h$i.test")) { ok(it, "$i") }
            }
        }.forEach { it.join() }
        assertEquals(copies[1].version, (shared[ProxyProtocol.SERVER] as Map<*, *>)[ProxyProtocol.SERVER_VERSION])
        awaitTrue("one server") { acceptThreads() == listOf("WebViewProxy v${copies[1].version} accept") }
        val proxy = ProxyAccess.current()
        (0 until threads).forEach { assertEquals(Answer(200, "$it"), get(proxy, "h$it.test").answer) }
        webView.idle()
        assertEquals(setOf("set ${proxy.port}"), webView.events.toSet())
        // No unauthenticated answers on the shared port
        Socket(InetAddress.getLoopbackAddress(), proxy.port).use {
            it.getOutputStream().write("CONNECT h0.test:443 HTTP/1.1\r\n\r\n".toByteArray())
            assertEquals(407, readResponse(it.getInputStream(), noBody = true).code)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun olderCopies(): List<Array<Any?>> = listOf(arrayOf<Any?>("this build at version ${ProxyProtocol.VERSION - 1}", ProxyCopy.CURRENT, ProxyProtocol.VERSION - 1)) +
            ProxyCopy.FROZEN.map { jar: File -> arrayOf(jar.name, jar.toURI().toURL(), null) }
    }
}

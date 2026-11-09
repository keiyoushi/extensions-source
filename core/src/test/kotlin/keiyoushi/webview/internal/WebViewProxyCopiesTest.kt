package keiyoushi.webview.internal

import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** Copies of this build's core sharing one proxy, as extensions on the same core version do. */
class WebViewProxyCopiesTest {
    private val webView = FakeWebView()
    private val copies = mutableListOf<ProxyCopy>()

    private fun copy(version: Int? = null, build: Long? = null) = ProxyCopy(ProxyCopy.CURRENT, version, webView, build = build).also(copies::add)

    private fun server() = shared[ProxyProtocol.SERVER] as Map<*, *>

    @Before
    fun noProxyRunning() = awaitStopped()

    @After
    fun closeRoutes() {
        copies.forEach(ProxyCopy::closeAll)
        awaitStopped()
    }

    @Test
    fun aCopyServesAnothersRoute() {
        val first = copy()
        val second = copy()
        first.open(setOf("a.test")) { ok(it, "first") }
        second.open(setOf("b.test")) { ok(it, "second") }
        val proxy = ProxyAccess.current()
        assertEquals(Answer(200, "first"), get(proxy, "a.test").answer)
        assertEquals(Answer(200, "second"), get(proxy, "x.b.test").answer)
        webView.idle()
        assertEquals(listOf("set ${proxy.port}", "set ${proxy.port}"), webView.events)
    }

    @Test
    fun theOverrideCoversTheRoutesHostsAsTheyChange() {
        val first = copy()
        val second = copy()
        val a = first.open(setOf("a.test")) { ok(it, "") }
        val b = second.open(setOf("B.test", "c.test")) { ok(it, "") }
        first.close(a)
        val d = first.open(setOf("d.test")) { ok(it, "") }
        lapse(routes().single { "d.test" in (it[ProxyProtocol.HOSTS] as Set<*>) })
        awaitTrue("the lapsed route to go") { routes().size == 1 }
        second.close(b)
        first.close(d)
        webView.idle()
        val bc = listOf("b.test", "*.b.test", "c.test", "*.c.test")
        assertEquals(
            listOf(listOf("a.test", "*.a.test"), listOf("a.test", "*.a.test") + bc, bc, bc + listOf("d.test", "*.d.test"), bc),
            webView.hosts,
        )
        assertEquals("clear", webView.events.last())
    }

    @Test
    fun aNewerBuildOfTheSameVersionTakesOver() {
        val older = copy(build = 100)
        older.open(setOf("a.test")) { ok(it, "a") }
        val newer = copy(build = 200)
        newer.open(setOf("b.test")) { ok(it, Thread.currentThread().name) }
        assertEquals(200L, server()[ProxyProtocol.SERVER_BUILD])
        awaitTrue("one accept loop") { acceptThreads().size == 1 }

        // The older build, opening and renewing, leaves it serving: no taking turns
        val again = older.open(setOf("c.test")) { ok(it, "c") }
        older.renew(again)
        assertEquals(200L, server()[ProxyProtocol.SERVER_BUILD])
        assertEquals(1, acceptThreads().size)
        assertEquals(Answer(200, "a"), get(ProxyAccess.current(), "a.test").answer)
    }

    @Test
    fun theSameBuildDoesNotTakeOver() {
        val first = copy(build = 100)
        first.open(setOf("a.test")) { ok(it, "a") }
        val record = server()
        val second = copy(build = 100)
        val route = second.open(setOf("b.test")) { ok(it, "b") }
        second.renew(route)
        assertTrue(server() === record)
        assertEquals(1, serverThreads().size)
    }

    @Test
    fun aFailedTakeoverLeavesTheOldServerServing() {
        val older = copy(version = ProxyProtocol.VERSION - 1)
        older.open(setOf("a.test")) { ok(it, "a") }
        val record = server()
        val proxy = ProxyAccess.current()
        // The newer copy can't make its server: the identity it would read is broken
        val certificate = shared.put(ProxyProtocol.CERTIFICATE, "unreadable")!!
        val newer = copy()
        try {
            newer.open(setOf("b.test")) { ok(it, "b") }
            fail("opened without a server")
        } catch (_: InvocationTargetException) {
        } finally {
            shared[ProxyProtocol.CERTIFICATE] = certificate
        }
        assertTrue(server() === record)
        assertEquals(listOf("WebViewProxy v${older.version} accept"), serverThreads())
        assertEquals(Answer(200, "a"), get(proxy, "a.test").answer)
        assertEquals(1, routes().size)
    }

    @Test
    fun lastRouteClosingClearsTheOverride() {
        val first = copy()
        val second = copy()
        val a = first.open(setOf("a.test")) { ok(it, "") }
        val b = second.open(setOf("b.test")) { ok(it, "") }
        val listener = shared[ProxyProtocol.LISTENER] as ServerSocket
        first.close(a)
        webView.idle()
        assertTrue(webView.events.none { it == "clear" })
        second.close(b)
        webView.idle()
        assertEquals("clear", webView.events.last())
        assertTrue(listener.isClosed)
        assertNull(shared[ProxyProtocol.SERVER])
    }

    @Test
    fun aLapsedRouteIsDroppedAndTheProxyStops() {
        copy().open(setOf("a.test")) { ok(it, "") }
        lapse(routes().single())
        awaitStopped()
        webView.idle()
        assertEquals("clear", webView.events.last())
        assertTrue(routes().isEmpty())
    }

    @Test
    fun renewingKeepsARouteAndBringsBackALapsedOne() {
        val copy = copy()
        val route = copy.open(setOf("a.test")) { ok(it, "back") }
        val lease = routes().single()[ProxyProtocol.EXPIRES_AT] as AtomicLong
        val before = lease.get()
        copy.renew(route)
        assertTrue(lease.get() > before)

        lapse(routes().single())
        awaitStopped()
        copy.renew(route)
        assertEquals(1, routes().size)
        assertEquals(Answer(200, "back"), get(ProxyAccess.current(), "a.test").answer)
        webView.idle()
        assertEquals(listOf("set", "clear", "set"), webView.events.map { it.substringBefore(' ') })
    }

    @Test
    fun ignoresUnknownRouteKeysAndSkipsUnreadableRecords() = servesRecordsANewerCopyWrote(copy())

    @Test
    fun concurrentOpensStartOneServer() {
        val threads = 16
        val copies = List(4) { copy() }
        val barrier = CyclicBarrier(threads)
        val opened = Collections.synchronizedList(mutableListOf<Pair<ProxyCopy, Any>>())
        List(threads) { i ->
            thread {
                val copy = copies[i % copies.size]
                barrier.await()
                opened += copy to copy.open(setOf("h$i.test")) { ok(it, "$i") }
            }
        }.forEach { it.join() }
        assertEquals(1, acceptThreads().size)
        webView.idle()
        assertEquals(setOf("set ${ProxyAccess.current().port}"), webView.events.toSet())
        assertEquals(threads, webView.events.size)

        val port = ProxyAccess.current().port
        val barrier2 = CyclicBarrier(threads)
        opened.map { (copy, route) ->
            thread {
                barrier2.await()
                copy.close(route)
            }
        }.forEach { it.join() }
        webView.idle()
        // Each but the last narrows the override to the routes left
        assertEquals(List(threads - 1) { "set $port" } + "clear", webView.events.drop(threads))
        awaitStopped()
    }
}

@Suppress("UNCHECKED_CAST")
internal val shared: MutableMap<String, Any> get() = ProxyAccess.shared

@Suppress("UNCHECKED_CAST")
internal fun routes(): MutableList<Map<String, Any>> = shared[ProxyProtocol.ROUTES] as MutableList<Map<String, Any>>

internal fun lapse(record: Map<String, Any>) = (record[ProxyProtocol.EXPIRES_AT] as AtomicLong).set(System.nanoTime() - 1)

/** The proxy's accept loops, by thread name: `WebViewProxy v<version> accept`, then `retired` for its grace. */
internal fun serverThreads(state: String = "accept|retired"): List<String> = Thread.getAllStackTraces().keys.filter { it.isAlive && it.name.matches(Regex("WebViewProxy v-?\\d+ ($state)")) }.map { it.name }

/** The accept loops still taking connections. */
internal fun acceptThreads() = serverThreads("accept")

/** Waits until no copy runs a server, its grace included. */
internal fun awaitStopped() = awaitTrue("the proxy to stop") {
    (ProxyAccess.shared[ProxyProtocol.LISTENER] == null) && serverThreads().isEmpty()
}

internal fun awaitTrue(what: String, seconds: Long = 10, condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
    while (!condition()) {
        check(System.nanoTime() < deadline) { "Timed out waiting for $what" }
        Thread.sleep(20)
    }
}

/**
 * Route records with fields [copy]'s server doesn't know are served all the same, and one it can't read is skipped,
 * as a newer copy's records are by an older server during a takeover's grace.
 */
internal fun servesRecordsANewerCopyWrote(copy: ProxyCopy) {
    copy.open(setOf("a.test")) { ok(it, "a") }
    val newer: Map<String, Any> = Collections.unmodifiableMap(
        mapOf(
            ProxyProtocol.HOSTS to setOf("b.test"),
            ProxyProtocol.SEND to java.util.function.Function<Request, Response> { ok(it, "newer") },
            ProxyProtocol.EXPIRES_AT to AtomicLong(System.nanoTime() + TimeUnit.MINUTES.toNanos(2)),
            "addedLater" to listOf("anything"),
        ),
    )
    val unreadable: Map<String, Any> = mapOf(ProxyProtocol.HOSTS to "b.test", ProxyProtocol.SEND to "not a function")
    synchronized(shared) { routes() += listOf(newer, unreadable) }
    try {
        val proxy = ProxyAccess.current()
        assertEquals(Answer(200, "newer"), get(proxy, "b.test").answer)
        assertEquals(Answer(200, "a"), get(proxy, "a.test").answer)
    } finally {
        synchronized(shared) { routes().removeAll { it === newer || it === unreadable } }
    }
}

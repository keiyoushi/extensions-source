package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class CatalogueRequestsTest {
    @Test
    fun `a confirmed host survives restart only within its original freshness window`() {
        val allowed = listOf("first.example", "second.example")
        val initial = ApiServer(allowed.last(), allowed, 100L)
        val encoded = initial.cacheValue(allowed)
        val restored = requireNotNull(restoreCatalogueServer(encoded, allowed, 110L))
        assertEquals(initial.host, restored.host)
        assertEquals(encoded, restored.cacheValue(allowed))
        assertEquals(null, restoreCatalogueServer(encoded, allowed, 99L))
        assertEquals(null, restoreCatalogueServer(encoded, allowed, 100L + 15 * 60 * 1000))
        assertEquals(null, restoreCatalogueServer(encoded, listOf("different.example"), 110L))
        assertEquals(null, restoreCatalogueServer("broken", allowed, 110L))
        assertEquals(null, initial.cacheValue(emptyList()))
    }

    @Test
    fun `an ordinary 500 can recover but a 500 with a retry instruction cannot`() = runBlocking<Unit> {
        withServer(Status(500), Status(200)) { first, second, requests ->
            assertEquals("catalogue", load(first) { CatalogueAlternate(second) {} })
            assertEquals(listOf(first.host, second.host), requests)
        }
        withServer(Status(500, headers = mapOf("Retry-After" to "60")), Status(200)) { first, _, requests ->
            assertEquals(500, errorCode { load(first) { error("Must respect retry instruction") } })
            assertEquals(listOf(first.host), requests)
        }
        withServer(Status(500), Status(200)) { first, _, _ ->
            assertEquals(500, errorCode { load(first) { null } })
        }
    }

    @Test
    fun `plain 503 retries a different node exactly once`() = runBlocking<Unit> {
        val unavailable = Status(503, "<html><title>Service Unavailable</title></html>", mapOf("Server" to "cloudflare"))
        withServer(unavailable, Status(200)) { first, second, requests ->
            val initial = ApiServer(first.host, listOf(first.host, second.host))
            val nodes = AtomicReference<ApiServer?>(initial)
            val decoded =
                load(first, decode = { response ->
                    assertSame(initial, nodes.get())
                    response.body.string()
                }) { nodes.catalogueAlternate(it, initial) }
            assertEquals("catalogue", decoded)
            val next = first.newBuilder().host(checkNotNull(nodes.get()).host).build()
            assertEquals(second, next)
            assertEquals("catalogue", load(next) { error("A healthy node must not be changed") })
            assertEquals(listOf(first.host, second.host, second.host), requests)
        }
    }

    @Test
    fun `another 503 ends the request and never looks up a third node`() = runBlocking<Unit> {
        withServer(Status(503), Status(503)) { first, second, requests ->
            val initial = ApiServer(first.host, listOf(first.host, second.host))
            val nodes = AtomicReference<ApiServer?>(initial)
            var selections = 0
            assertEquals(
                503,
                errorCode {
                    load(first) {
                        selections++
                        nodes.catalogueAlternate(it, initial)
                    }
                },
            )
            assertEquals(1, selections)
            assertSame(initial, nodes.get())
            assertEquals(listOf(first.host, second.host), requests)
        }
    }

    @Test
    fun `a stale request reuses a confirmed node and cannot replace it`() {
        val first = "https://first.example.invalid/categories/filter?page=2".toHttpUrl()
        val initial = ApiServer(first.host, listOf(first.host, "second.example.invalid", "third.example.invalid"))
        val nodes = AtomicReference<ApiServer?>(initial)
        val pending = checkNotNull(nodes.catalogueAlternate(first, initial))
        val confirmed = ApiServer("third.example.invalid", initial.candidates)
        nodes.set(confirmed)

        pending.recordSuccess()
        val reuse = checkNotNull(nodes.catalogueAlternate(first, initial))
        assertSame(confirmed, nodes.get())
        assertEquals(confirmed.host, reuse.url.host)
        assertEquals(first.encodedPath, reuse.url.encodedPath)
        assertEquals(first.query, reuse.url.query)
    }

    @Test
    fun `limits and verification or denial responses never select another node`() = runBlocking<Unit> {
        val blocked =
            listOf(
                Status(403),
                Status(429),
                Status(503, headers = mapOf("Retry-After" to "120")),
                Status(503, headers = mapOf("Cf-Mitigated" to "challenge")),
                Status(503, "<html><div id=challenge-error-title>Just a moment...</div></html>"),
                Status(503, "<html><title>Access denied</title></html>"),
            )
        blocked.forEach { status ->
            withServer(status, Status(200)) { first, _, requests ->
                assertEquals(status.code, errorCode { load(first) { error("Unexpected alternate lookup") } })
                assertEquals(listOf(first.host), requests)
            }
        }
    }

    @Test
    fun `missing alternate or business error does not repeat the request`() = runBlocking<Unit> {
        withServer(Status(503), Status(200)) { first, _, requests ->
            assertEquals(503, errorCode { load(first) { null } })
            assertEquals(listOf(first.host), requests)
        }
        withServer(Status(200), Status(200)) { first, _, requests ->
            val failure =
                errorCode {
                    load(first, decode = { throw HttpException(503) }) { error("Unexpected alternate lookup") }
                }
            assertEquals(503, failure)
            assertEquals(listOf(first.host), requests)
        }
        withServer(Status(503), Status(200)) { first, second, requests ->
            val initial = ApiServer(first.host, listOf(first.host, second.host))
            val nodes = AtomicReference<ApiServer?>(initial)
            val failure =
                errorCode {
                    load(first, decode = { throw HttpException(503) }) { nodes.catalogueAlternate(it, initial) }
                }
            assertEquals(503, failure)
            assertSame(initial, nodes.get())
            assertEquals(listOf(first.host, second.host), requests)
        }
    }

    private suspend fun load(
        first: HttpUrl,
        decode: (okhttp3.Response) -> String = { it.body.string() },
        alternate: (HttpUrl) -> CatalogueAlternate?,
    ): String = requestCatalogue(first, alternate) { target ->
        val request = Request.Builder().url(target).build()
        CatalogueReply(client.newCall(request).execute(), decode)
    }

    private suspend fun errorCode(block: suspend () -> Unit): Int = try {
        block()
        error("Expected an HTTP error")
    } catch (error: HttpException) {
        error.code
    }

    private suspend fun withServer(
        first: Status,
        second: Status,
        block: suspend (HttpUrl, HttpUrl, List<String>) -> Unit,
    ) {
        val requests = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/categories/filter") { exchange ->
            val host = exchange.requestHeaders.getFirst("Host").substringBefore(':')
            val status = if (host == "127.0.0.1") first else second
            requests += host
            status.headers.forEach { (name, value) -> exchange.responseHeaders.set(name, value) }
            exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            val body = status.body.encodeToByteArray()
            exchange.sendResponseHeaders(status.code, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            block(
                "http://127.0.0.1:${server.address.port}/categories/filter?page=2".toHttpUrl(),
                "http://localhost:${server.address.port}/categories/filter?page=2".toHttpUrl(),
                requests,
            )
        } finally {
            server.stop(0)
        }
    }

    private class Status(
        val code: Int,
        val body: String = "catalogue",
        val headers: Map<String, String> = emptyMap(),
    )

    private val client = OkHttpClient()
}

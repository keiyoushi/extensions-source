package keiyoushi.network

import com.sun.net.httpserver.HttpServer
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.CompressionInterceptor
import okhttp3.FormBody
import okhttp3.Gzip
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.brotli.Brotli
import okhttp3.zstd.Zstd
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BrowserHeadersCacheTest {

    private val ua = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Mobile Safari/537.36"
    private val hits = ConcurrentHashMap<String, AtomicInteger>()
    private val conditional = ConcurrentHashMap<String, String>()
    private val seen = ConcurrentHashMap<String, com.sun.net.httpserver.Headers>()

    // Another origin: the same host on another port
    private val thirdSeen = ConcurrentHashMap<String, com.sun.net.httpserver.Headers>()
    private val third = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            thirdSeen[exchange.requestURI.path] = exchange.requestHeaders
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        start()
    }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val path = exchange.requestURI.path
            hits.getOrPut(path) { AtomicInteger() }.incrementAndGet()
            exchange.requestHeaders.getFirst("If-None-Match")?.let { conditional[path] = it }
            seen[path] = exchange.requestHeaders
            val out = exchange.responseHeaders
            val status = when (path) {
                "/api" -> 200.also {
                    out.add("Cache-Control", "max-age=600")
                    out.add("Vary", "Origin, Accept-Encoding")
                }
                "/doc" -> 200.also {
                    out.add("Cache-Control", "max-age=600")
                    out.add("Vary", "Accept, Sec-CH-UA, Accept-Language, User-Agent")
                }
                "/img" -> 200.also {
                    out.add("Cache-Control", "max-age=600")
                    out.add("Vary", "Accept")
                }
                "/plain" -> 200.also { out.add("Cache-Control", "public") }
                // To the same server under another host name: another site
                "/to-other-site" -> 302.also { out.add("Location", "http://127.0.0.1:${address.port}/landed") }
                "/form" -> 303.also { out.add("Location", "/landed") }
                "/landed" -> 200
                "/to-other-then-back" -> 302.also { out.add("Location", "http://127.0.0.1:${address.port}/back") }
                "/back" -> 302.also { out.add("Location", "http://localhost:${address.port}/landed") }
                "/cached-hop" -> 301.also {
                    out.add("Cache-Control", "max-age=600")
                    out.add("Location", "http://localhost:${address.port}/landed")
                }
                "/to-cached-hop" -> 302.also { out.add("Location", "http://127.0.0.1:${address.port}/cached-hop") }
                "/to-third" -> 302.also { out.add("Location", "http://127.0.0.1:${third.address.port}/landed") }
                "/etag" -> if (exchange.requestHeaders.getFirst("If-None-Match") == "\"1\"") {
                    304
                } else {
                    200.also {
                        out.add("Cache-Control", "no-cache")
                        out.add("ETag", "\"1\"")
                    }
                }
                else -> 404
            }
            if (status == 304) {
                exchange.sendResponseHeaders(304, -1)
            } else {
                val body = "ok".toByteArray()
                exchange.sendResponseHeaders(status, body.size.toLong())
                exchange.responseBody.write(body)
            }
            exchange.close()
        }
        start()
    }
    private val base = "http://localhost:${server.address.port}"
    private val cacheDir = Files.createTempDirectory("kei-cache").toFile()

    // KeiSource's client, minus what needs the app
    private val client = OkHttpClient.Builder()
        .cache(Cache(cacheDir, 1024 * 1024))
        .addInterceptor(BrowserCallInterceptor({ base }, { Fetch.NONE }))
        .addInterceptor(CompressionInterceptor(Gzip, Deflate, Brotli, Zstd))
        .addNetworkInterceptor(CacheControlInterceptor())
        .addNetworkInterceptor(BrowserHeadersInterceptor)
        .build()

    @After
    fun tearDown() {
        server.stop(0)
        third.stop(0)
        cacheDir.deleteRecursively()
    }

    private fun request(path: String) = Request.Builder().url("$base$path")
        .header("User-Agent", ua)
        .header("Referer", "$base/")
        .header("Origin", base)

    private fun call(request: Request): Response = client.newCall(request).execute().also { it.body.string() }

    private fun assertCached(path: String, build: (Request.Builder) -> Request.Builder) {
        val first = call(build(request(path)).build())
        // The request from before the wire order pass, the cache's own directives still on it
        assertEquals(first.request.cacheControl.maxAgeSeconds, first.networkResponse!!.request.cacheControl.maxAgeSeconds)
        val second = call(build(request(path)).build())
        assertEquals(1, hits[path]!!.get())
        assertNotNull(second.cacheResponse)
        assertNull(second.networkResponse)
    }

    @Test
    fun xhrVaryOriginAndEncoding() = assertCached("/api") { it.fetch(Fetch.XHR) }

    @Test
    fun documentVaryOnBrowserHeaders() = assertCached("/doc") { it.fetch(Fetch.DOCUMENT) }

    @Test
    fun imageVaryAccept() = assertCached("/img") { it.fetch(Fetch.IMAGE) }

    // The request's max-age widened by CacheControlInterceptor, before the wire order pass drops it
    @Test
    fun untaggedWithRequestMaxAge() = assertCached("/plain") { it.cacheControl(CacheControl.Builder().maxAge(10, TimeUnit.MINUTES).build()) }

    @Test
    fun forceCacheAfterNetwork() {
        call(request("/img").fetch(Fetch.IMAGE).build())
        val cached = call(request("/img").fetch(Fetch.IMAGE).cacheControl(CacheControl.FORCE_CACHE).build())
        assertEquals(200, cached.code)
        assertEquals(1, hits["/img"]!!.get())
    }

    @Test
    fun redirectsUpdateSiteAndOrigin() {
        call(request("/to-other-site").fetch(Fetch.IMAGE).build())
        assertEquals("cross-site", seen["/landed"]!!.getFirst("Sec-Fetch-Site"))
        val form = FormBody.Builder().add("q", "1").build()
        call(request("/form").fetch(Fetch.DOCUMENT).post(form).build())
        assertNull(seen["/landed"]!!.getFirst("Origin"))
        assertNotNull(seen["/form"]!!.getFirst("Origin"))
        // A cross-origin hop of a page's fetch carries Origin, though the first request didn't
        call(request("/to-other-site").fetch(Fetch.XHR).build())
        assertEquals(base, seen["/landed"]!!.getFirst("Origin"))
    }

    @Test
    fun browserAcceptEncodingOnEveryRequest() {
        call(request("/landed").build())
        assertEquals("gzip,deflate,br,zstd", seen["/landed"]!!.getFirst("Accept-Encoding").replace(" ", ""))
        call(request("/landed").fetch(Fetch.XHR).build())
        assertEquals("gzip,deflate,br,zstd", seen["/landed"]!!.getFirst("Accept-Encoding").replace(" ", ""))
    }

    @Test
    fun fromPageAcrossRedirects() {
        // localhost -> 127.0.0.1 -> back to localhost: trimmed to the origin once, it stays trimmed
        call(request("/to-other-then-back").fetch(Fetch.XHR.from("$base/series/1?x=1")).build())
        assertEquals("$base/", seen["/back"]!!.getFirst("Referer"))
        assertEquals("$base/", seen["/landed"]!!.getFirst("Referer"))
        // With NONE too
        call(request("/to-other-site").fetch(Fetch.NONE.from("$base/series/1?x=1")).build())
        assertEquals("$base/", seen["/landed"]!!.getFirst("Referer"))
    }

    @Test
    fun fromPageStatePerCall() {
        // One builder sent twice: each call trims its own Referer
        val builder = request("/to-other-then-back").fetch(Fetch.XHR.from("$base/series/1?x=1"))
        call(builder.build())
        call(builder.url("$base/landed").build())
        assertEquals("$base/series/1?x=1", seen["/landed"]!!.getFirst("Referer"))
        call(builder.url("$base/to-other-then-back").build())
        assertEquals("$base/", seen["/landed"]!!.getFirst("Referer"))
    }

    @Test
    fun redirectHopsGoOverTheNetwork() {
        // The cacheable 301 in the middle is fetched again, so the last hop's Referer stays trimmed
        repeat(2) { call(request("/to-cached-hop").fetch(Fetch.XHR.from("$base/series/1?x=1")).build()) }
        assertEquals(2, hits["/cached-hop"]!!.get())
        assertEquals("$base/", seen["/landed"]!!.getFirst("Referer"))
    }

    @Test
    fun originNullAfterTwoCrossOriginHops() {
        call(Request.Builder().url("http://127.0.0.1:${server.address.port}/to-third").header("User-Agent", ua).header("Referer", "$base/").header("Origin", base).fetch(Fetch.XHR).build())
        assertEquals("null", thirdSeen["/landed"]!!.getFirst("Origin"))
    }

    @Test
    fun revalidatesWithEtag() {
        call(request("/etag").fetch(Fetch.XHR).build())
        val second = call(request("/etag").fetch(Fetch.XHR).build())
        assertEquals(2, hits["/etag"]!!.get())
        assertEquals("\"1\"", conditional["/etag"])
        assertEquals(200, second.code)
        assertNotNull(second.cacheResponse)
        assertEquals(304, second.networkResponse!!.code)
    }
}

package keiyoushi.webview.internal

import android.net.http.SslCertificate
import android.util.Log
import keiyoushi.webview.internal.ProxyProtocol.CERTIFICATE
import keiyoushi.webview.internal.ProxyProtocol.EXPIRES_AT
import keiyoushi.webview.internal.ProxyProtocol.HOSTS
import keiyoushi.webview.internal.ProxyProtocol.KEY_PAIR
import keiyoushi.webview.internal.ProxyProtocol.LISTENER
import keiyoushi.webview.internal.ProxyProtocol.PASSWORD
import keiyoushi.webview.internal.ProxyProtocol.REALM
import keiyoushi.webview.internal.ProxyProtocol.RETIRE
import keiyoushi.webview.internal.ProxyProtocol.ROUTES
import keiyoushi.webview.internal.ProxyProtocol.SEND
import keiyoushi.webview.internal.ProxyProtocol.SERVER
import keiyoushi.webview.internal.ProxyProtocol.SERVER_BUILD
import keiyoushi.webview.internal.ProxyProtocol.SERVER_VERSION
import keiyoushi.webview.internal.ProxyProtocol.USERNAME
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.security.KeyPair
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A local HTTP proxy that hands WebView's requests to a route's hosts to the route's `send` (see [routeSend]) and
 * tunnels every other host as it is. It is transport only: CONNECT, TLS to a route's hosts with the shared
 * certificate, HTTP/1.1 framing, tunnels and WebSockets; what a request becomes is up to `send`, which runs in its
 * route holder's copy of core. It only answers clients that send its random credentials, and the app's WebViews send
 * it their requests to the open routes' hosts, or all of their requests (see [WebViewOverride.scope]).
 *
 * The copies of core in a process share one proxy through [ProxyProtocol]. The highest [version], then the newest
 * [build], serves: a copy opening or renewing a route takes over an older one's server on the same port, so WebView
 * and the open routes carry on. Routes are leases their holders [renew]; the serving copy drops lapsed ones, and the
 * proxy stops with the last.
 */
internal object WebViewProxy {
    private const val TAG = "WebViewProxy"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val MAX_HEAD = 64 * 1024
    private const val MAX_BODY = 32 * 1024 * 1024

    // How often an accept loop looks up: to see it was retired, and to drop lapsed routes
    private const val ACCEPT_TICK_MS = 1_000

    // Times in a row this copy's classes may fail to load before its server leaves serving to another copy
    private const val MAX_LINKAGE_FAILURES = 3

    /** How long a route lasts without [renew]. */
    val LEASE = 2.minutes

    /** How often a run renews its route. */
    val RENEW_INTERVAL = 30.seconds

    /** This copy's protocol version; tests lower it to stand in for an older build. */
    @JvmField
    @Volatile
    var version: Int = ProxyProtocol.VERSION

    /** This copy's build; tests change it to stand in for another build of the same version. */
    @JvmField
    @Volatile
    var build: Long = CoreBuild.STAMP

    /** How long a retired or stopped server lets its connections finish; tests shorten it. */
    @JvmField
    @Volatile
    var retireGraceMillis: Long = 10_000

    /** How long a new connection has, from accept, to send its first head in full; tests shorten it. */
    @JvmField
    @Volatile
    var firstHeadMillis: Long = 10_000

    /** How many connections a server holds at once; tests lower it. */
    @JvmField
    @Volatile
    var maxConnections: Int = 64

    private val BODY_METHODS = setOf("POST", "PUT", "PATCH")
    private val HOP_BY_HOP = setOf(
        "connection", "proxy-connection", "keep-alive", "transfer-encoding", "te", "upgrade", "trailer",
        "proxy-authenticate", "proxy-authorization",
    )

    // OkHttp sets Host and Content-Length itself
    private val DROPPED_REQUEST_HEADERS = HOP_BY_HOP + setOf("host", "content-length")
    private val DROPPED_WEBSOCKET_HEADERS = setOf("proxy-connection", "proxy-authorization")

    @Suppress("UNCHECKED_CAST")
    private val shared: MutableMap<String, Any> = System.getProperties().let { properties ->
        val fresh = ConcurrentHashMap<String, Any>()
        (properties.putIfAbsent(ProxyProtocol.PROPERTY, fresh) ?: fresh) as MutableMap<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private val routes: MutableList<Map<String, Any>>
        get() = shared.computeIfAbsent(ROUTES) { CopyOnWriteArrayList<Map<String, Any>>() } as MutableList<Map<String, Any>>

    // Plain http requests to hosts without a route, sent as they come
    private val plainSend: Function<Request, Response> by lazy {
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).proxySelector(DeviceProxySelector).build()
        Function { client.newCall(it).execute() }
    }

    // The device's proxy as it is at each request: Android swaps the default selector as a PAC proxy comes and goes
    private object DeviceProxySelector : ProxySelector() {
        override fun select(uri: URI): List<Proxy> = getDefault()?.select(uri) ?: listOf(Proxy.NO_PROXY)

        override fun connectFailed(uri: URI, address: SocketAddress, e: IOException) {
            getDefault()?.connectFailed(uri, address, e)
        }
    }

    /**
     * An open route, and what a WebView needs to use the proxy: the shared identity, written once per process, so it
     * stays right whichever copy serves.
     */
    class Route internal constructor(
        internal val record: Map<String, Any>,
        private val patterns: Set<String>,
        val realm: String,
        val username: String,
        val password: String,
        /** The DER encoding of the proxy's certificate. */
        val certificate: ByteArray,
    ) {
        // Under the lock on the shared state
        internal var closed = false

        /** Whether a proxy auth request from [host] for [realm] is this proxy's. */
        fun asks(host: String?, realm: String?): Boolean = host == "127.0.0.1" && realm == this.realm

        /**
         * Whether [certificate], from an SSL error for [url], is this proxy's and [url] one of this route's hosts, so a
         * run never accepts the proxy for a host another extension routes.
         */
        fun presents(certificate: SslCertificate, url: String?): Boolean = presents(url) { ProxyTls.encoded(certificate) }

        internal fun presents(url: String?, encoded: () -> ByteArray?): Boolean {
            val host = url?.toHttpUrlOrNull()?.host ?: return false
            if (!patterns.matchesHost(host)) return false
            return MessageDigest.isEqual(encoded() ?: return false, certificate)
        }
    }

    /**
     * Hands requests to [hosts] and their subdomains to [send] until [close], or until the route lapses for want of a
     * [renew], and points all of the app's WebViews at the proxy meanwhile. [onReady] runs on the main thread once they
     * use it, or with false if WebView refused. Throws an [IOException] if the proxy can't start, and an
     * [IllegalArgumentException] for a host that isn't a host name.
     */
    fun open(hosts: Set<String>, send: Function<Request, Response>, onReady: (applied: Boolean) -> Unit): Route {
        val names = hosts.map { it.lowercase(Locale.ROOT) }
        names.forEach { require("https://$it/".toHttpUrlOrNull()?.host == it) { "Not a host name: $it" } }
        val patterns = names.flatMap { listOf(it, "*.$it") }.toSet()
        val record: Map<String, Any> = Collections.unmodifiableMap(
            mapOf(HOSTS to patterns, SEND to send, EXPIRES_AT to AtomicLong(System.nanoTime() + LEASE.inWholeNanoseconds)),
        )
        synchronized(shared) {
            val port = serveLocked().localPort
            val identity = identityLocked()
            routes += record
            applyLocked(port, record, onReady)
            return Route(record, patterns, identity.realm, identity.username, identity.password, identity.certificate.encoded)
        }
    }

    /** Extends [route]'s lease, and lists it again if it lapsed. Throws an [IOException] if the proxy can't listen. */
    fun renew(route: Route) {
        (route.record[EXPIRES_AT] as AtomicLong).set(System.nanoTime() + LEASE.inWholeNanoseconds)
        synchronized(shared) {
            if (route.closed) return
            val port = serveLocked().localPort
            if (routes.none { it === route.record }) {
                routes += route.record
                applyLocked(port)
            }
        }
    }

    /** Ends [route]; the proxy and WebView's override end with the last route. */
    fun close(route: Route) {
        synchronized(shared) {
            if (route.closed) return
            route.closed = true
            val removed = routes.removeIf { it === route.record }
            val listener = shared[LISTENER] as? ServerSocket ?: return
            if (routes.isEmpty()) {
                stopLocked()
            } else if (removed) {
                applyLocked(listener.localPort)
            }
        }
    }

    /**
     * Points WebView at the proxy for the routes' hosts as they are now, in the mode [WebViewOverride.scope] picks then,
     * and with [opened] calls [onReady] once it does. Posted under the lock, so the main thread sets and clears the
     * override in the order the routes and the device's proxy changed.
     */
    private fun applyLocked(port: Int, opened: Map<String, Any>? = null, onReady: (applied: Boolean) -> Unit = {}) {
        val hosts = routes.flatMap { (it[HOSTS] as? Set<*>).orEmpty().filterIsInstance<String>() }.distinct()
        WebViewOverride.post.execute {
            val applying = (opened == null || routes.any { it === opened }) &&
                WebViewOverride.set(port, WebViewOverride.scope(hosts), Runnable { onReady(true) })
            if (!applying) onReady(false)
        }
    }

    /**
     * The proxy's listener, started if it isn't, and served by this copy if it's newer than the server's. A takeover
     * that fails leaves the old server serving; a proxy that can't start at all throws an [IOException].
     */
    private fun serveLocked(): ServerSocket {
        var listener = (shared[LISTENER] as? ServerSocket)?.takeUnless { it.isClosed }
        val fresh = listener == null
        if (listener == null) {
            (shared.remove(SERVER) as? Map<*, *>)?.retire()
            // Any app can reach the port, but only WebViews told the random credentials get answers
            listener = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).apply { soTimeout = ACCEPT_TICK_MS }
            shared[LISTENER] = listener
        }
        val serving = shared[SERVER] as? Map<*, *>
        if (serving != null && !newerThan(serving)) return listener
        var server: Server? = null
        try {
            // Accepting before it's published or the old one retires, so a failure on the way leaves the old one serving
            server = Server(listener, identityLocked())
            server.start()
            shared[SERVER] = server.record
        } catch (e: Throwable) {
            server?.record?.retire()
            if (serving != null) {
                Log.e(TAG, "takeover", e)
                return listener
            }
            if (fresh) {
                shared.remove(LISTENER)
                listener.closeQuietly()
            }
            throw IOException("The WebView proxy didn't start", e)
        }
        serving?.retire()
        return listener
    }

    // Strictly, so two copies never take turns
    private fun newerThan(server: Map<*, *>): Boolean {
        val serverVersion = server[SERVER_VERSION] as? Int ?: Int.MIN_VALUE
        return version > serverVersion || (version == serverVersion && build > (server[SERVER_BUILD] as? Long ?: Long.MIN_VALUE))
    }

    private fun stopLocked() {
        (shared.remove(SERVER) as? Map<*, *>)?.retire()
        (shared.remove(LISTENER) as? ServerSocket)?.closeQuietly()
        WebViewOverride.post.execute { WebViewOverride.clear.run() }
    }

    private fun Map<*, *>.retire() = (this[RETIRE] as? Runnable)?.run()

    private class Identity(val keyPair: KeyPair, val certificate: X509Certificate, val realm: String, val username: String, val password: String) {
        val authorization = ("Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())).toByteArray()
    }

    // Made once per process and kept, so WebView's remembered decision for the certificate holds across servers
    private fun identityLocked(): Identity {
        if (shared[KEY_PAIR] == null) {
            val keyPair = ProxyTls.newKeyPair()
            shared[CERTIFICATE] = ProxyTls.certificate(keyPair)
            shared[REALM] = "keiyoushi-${randomHex()}"
            shared[USERNAME] = randomHex()
            shared[PASSWORD] = randomHex()
            // Last, as its presence says the rest is there
            shared[KEY_PAIR] = keyPair
        }
        return Identity(
            shared[KEY_PAIR] as KeyPair,
            shared[CERTIFICATE] as X509Certificate,
            shared[REALM] as String,
            shared[USERNAME] as String,
            shared[PASSWORD] as String,
        )
    }

    private fun randomHex() = ByteArray(16).also(SecureRandom()::nextBytes).toHexString()

    // A lapsed route the serving copy hasn't dropped yet is already out
    @Suppress("UNCHECKED_CAST")
    private fun sendFor(host: String): Function<Request, Response>? {
        val now = System.nanoTime()
        return routes.lastOrNull { record ->
            record[SEND] is Function<*, *> && !record.lapsed(now) && (record[HOSTS] as? Set<*>)?.matchesHost(host) == true
        }?.get(SEND) as Function<Request, Response>?
    }

    private fun Set<*>.matchesHost(host: String) = any { it is String && (host == it || (it.startsWith("*.") && host.endsWith(it.substring(1)))) }

    private fun Map<String, Any>.lapsed(now: Long) = (this[EXPIRES_AT] as? AtomicLong)?.let { now - it.get() > 0 } == true

    /** One copy's server on the shared listener, from its start until it's retired or the proxy stops. */
    private class Server(private val listener: ServerSocket, identity: Identity) {
        private val version = WebViewProxy.version
        private val build = WebViewProxy.build
        private val firstHeadNanos = TimeUnit.MILLISECONDS.toNanos(firstHeadMillis)
        private val maxConnections = WebViewProxy.maxConnections
        private val tls: SSLContext = ProxyTls.context(identity.keyPair, identity.certificate)
        private val authorization = identity.authorization
        private val realm = identity.realm

        // All guarded by connections: every socket the server holds, closed at the end of its grace; the connections it
        // accepted, counted against maxConnections; and those still waiting for their first authorized head, oldest first
        private val connections = mutableSetOf<Socket>()
        private val accepted = mutableSetOf<Socket>()
        private val unauthenticated = LinkedHashSet<Socket>()
        private var ended = false

        // A connection's thread, one more while it pipes a tunnel or WebSocket, and room for evicted ones still ending
        private val workers = ThreadPoolExecutor(0, 3 * maxConnections, 60, TimeUnit.SECONDS, SynchronousQueue()) {
            Thread(it, "$TAG v$version").apply { isDaemon = true }
        }

        @Volatile
        private var retired = false

        // Ends the server's watch on the device's proxy, once
        private val unwatch = AtomicReference<Runnable?>()

        val record: Map<String, Any> = Collections.unmodifiableMap(
            mapOf(
                SERVER_VERSION to version,
                SERVER_BUILD to build,
                RETIRE to Runnable(::retire),
            ),
        )

        /**
         * Starts accepting, and while it serves re-applies the override when the device's proxy changes, as the
         * override's mode depends on it. Under the lock on the shared state.
         */
        fun start() {
            // Before the accept loop, which may retire the server as soon as it runs
            try {
                unwatch.set(WebViewOverride.watch(Runnable(::deviceProxyChanged)))
            } catch (e: Throwable) {
                // Each apply still reads the device's proxy
                Log.e(TAG, "watch", e)
            }
            Thread(::run, "$TAG v$version accept").apply { isDaemon = true }.start()
        }

        // Called by any copy, under the lock or from this server's own loop: returns at once and never throws
        private fun retire() {
            retired = true
            try {
                unwatch.getAndSet(null)?.run()
            } catch (e: Throwable) {
                Log.e(TAG, "unwatch", e)
            }
        }

        private fun deviceProxyChanged() {
            synchronized(shared) {
                if (shared[SERVER] === record && routes.isNotEmpty()) applyLocked(listener.localPort)
            }
        }

        // Uncaught on this thread, a throw would crash the app
        private fun run() {
            try {
                acceptLoop()
            } catch (e: Throwable) {
                Log.e(TAG, "server", e)
            }
        }

        private fun acceptLoop() {
            var linkageFailures = 0
            while (!retired) {
                try {
                    if (!acceptNext()) break
                    dropLapsedRoutes()
                    linkageFailures = 0
                } catch (e: LinkageError) {
                    // This copy's classes no longer load, as once its extension is updated or uninstalled: log it once,
                    // back off, and if it lasts, leave serving to the next copy that opens or renews a route
                    if (linkageFailures++ == 0) Log.e(TAG, "accept", e)
                    if (linkageFailures >= MAX_LINKAGE_FAILURES) {
                        stepDown()
                        break
                    }
                    Thread.sleep(ACCEPT_TICK_MS.toLong() shl linkageFailures)
                } catch (e: Throwable) {
                    // Closed once the proxy stops. Anything else, such as running out of descriptors, passes: this loop
                    // ends only when the proxy no longer needs it
                    if (listener.isClosed) break
                    if (e !is IOException) Log.e(TAG, "accept", e)
                    Thread.sleep(ACCEPT_TICK_MS.toLong())
                }
            }
            // Retired or stopped: the loads in flight get a grace to finish, then tunnels WebView still holds open end
            Thread.currentThread().name = "$TAG v$version retired"
            Thread.sleep(retireGraceMillis)
            synchronized(connections) {
                ended = true
                connections.toList()
            }.forEach { it.closeQuietly() }
            workers.shutdown()
        }

        /** Takes the next connection, if one comes within a tick; false once the server is retired. */
        private fun acceptNext(): Boolean {
            val socket = try {
                // In turn on the listener's monitor, so a retired loop is out of accept before the next one is in
                synchronized(listener) { if (retired) return false else listener.accept() }
            } catch (_: SocketTimeoutException) {
                return true
            }
            val deadline = System.nanoTime() + firstHeadNanos
            if (!admit(socket)) {
                socket.closeQuietly()
                return true
            }
            try {
                workers.execute { handle(socket, deadline) }
            } catch (_: RejectedExecutionException) {
                release(socket)
                socket.closeQuietly()
            }
            return true
        }

        private fun dropLapsedRoutes() {
            val now = System.nanoTime()
            if (routes.isNotEmpty() && routes.none { it.lapsed(now) }) return
            synchronized(shared) {
                if (shared[SERVER] !== record) return
                val dropped = routes.removeIf { it.lapsed(now) }
                if (routes.isEmpty()) {
                    stopLocked()
                } else if (dropped) {
                    applyLocked(listener.localPort)
                }
            }
        }

        private fun stepDown() {
            retire()
            synchronized(shared) { if (shared[SERVER] === record) shared.remove(SERVER) }
        }

        /**
         * Counts [socket] in, or false when the server is ending or full of authorized connections. At the cap it closes
         * the oldest connection still waiting for its first authorized head rather than refuse, so a local app holding
         * idle sockets can't lock WebView out; each of those also ends at its first head's deadline.
         */
        private fun admit(socket: Socket): Boolean = synchronized(connections) {
            if (ended) return false
            if (accepted.size >= maxConnections) {
                val oldest = unauthenticated.firstOrNull() ?: return false
                release(oldest)
                oldest.closeQuietly()
            }
            accepted += socket
            unauthenticated += socket
            connections += socket
            true
        }

        private fun release(socket: Socket) = synchronized(connections) {
            accepted -= socket
            unauthenticated -= socket
            connections -= socket
        }

        // A socket opened after the grace would otherwise outlive the server
        private fun track(socket: Socket) {
            val late = synchronized(connections) { ended.also { if (!it) connections += socket } }
            if (late) socket.closeQuietly()
        }

        private fun untrack(socket: Socket) {
            synchronized(connections) { connections -= socket }
        }

        private fun handle(socket: Socket, deadline: Long) {
            try {
                serve(socket, deadline)
            } catch (_: IOException) {
                // Either side ended the connection
            } catch (_: RejectedExecutionException) {
                // No worker left to pipe a tunnel: the connection ends
            } catch (e: Throwable) {
                // Uncaught on this thread, it would crash the app
                Log.e(TAG, "connection", e)
            } finally {
                socket.closeQuietly()
                release(socket)
            }
        }

        private fun serve(socket: Socket, deadline: Long) {
            val output = socket.getOutputStream()
            // Unbuffered, so the TLS hello after a CONNECT stays in the socket for the handshake
            val head = readHead(DeadlineInput(socket, deadline)) ?: return
            if (!head.isAuthorized()) return output.askForCredentials()
            synchronized(connections) { unauthenticated -= socket }
            // Only the first head waits against the clock: WebView keeps its connections open, and a tunnel may stay
            // quiet on its side while the site sends
            socket.soTimeout = 0
            val input = BufferedInputStream(socket.getInputStream())
            if (head.method != "CONNECT") return exchangePlain(input, output, head)
            val origin = "https://${head.target}".toHttpUrlOrNull() ?: return output.respond(400)
            // Other hosts come here where WebView can't limit the proxy to the routes' hosts, and while it catches up
            val upstream = if (sendFor(origin.host) == null) connect(origin) ?: return output.respond(502) else null
            output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            output.flush()
            if (upstream != null) return pipe(input, output, upstream)
            // WebView offers only HTTP/1.1 for its WebSockets, which carry no X-Requested-With: they go to the site as
            // they are, in WebView's own TLS
            if (ProxyTls.offeredProtocols(socket) == listOf("http/1.1")) return pipe(input, output, connect(origin) ?: return)
            val tls = ProxyTls.accept(tls, socket)
            exchangeTls(BufferedInputStream(tls.inputStream), tls.outputStream, origin)
        }

        /** The requests of a TLS tunnel to [origin], in turn, until the server retires. */
        private fun exchangeTls(input: InputStream, output: OutputStream, origin: HttpUrl) {
            while (true) {
                val head = readHead(input) ?: return
                val send = sendFor(origin.host) ?: return output.respond(403)
                val url = origin.resolve(head.target) ?: return output.respond(400)
                if (head.isWebSocket) return webSocket(head, url, input, output)
                if (!forward(send, head, url, input, output) || head.closes || retired) return
            }
        }

        /** Plain http requests, each naming its URL in full; one connection can carry requests to several hosts. */
        private fun exchangePlain(input: InputStream, output: OutputStream, first: Head) {
            var head = first
            while (true) {
                val url = head.target.toHttpUrlOrNull()?.takeUnless { it.isHttps } ?: return output.respond(400)
                val send = sendFor(url.host) ?: plainSend
                if (head.isWebSocket) return webSocket(head, url, input, output)
                if (!forward(send, head, url, input, output) || head.closes || retired) return
                head = readHead(input) ?: return
                if (!head.isAuthorized()) return output.askForCredentials()
            }
        }

        /** Sends one request with [send] and writes its response; false when the connection has to end. */
        private fun forward(send: Function<Request, Response>, head: Head, url: HttpUrl, input: InputStream, output: OutputStream): Boolean {
            val body = try {
                readBody(input, head.headers) ?: ByteArray(0)
            } catch (_: BadRequest) {
                // The body's end is unknown: the next request can't be found
                output.respond(400)
                return false
            }
            val request = try {
                Request.Builder()
                    .url(url)
                    .headers(head.headers(DROPPED_REQUEST_HEADERS))
                    // OkHttp requires a body on POST, PUT and PATCH
                    .method(head.method, body.takeIf { head.method in BODY_METHODS || it.isNotEmpty() }?.toRequestBody())
                    .build()
            } catch (_: IllegalArgumentException) {
                // Such as a GET or HEAD with a body, which OkHttp refuses
                output.respond(400)
                return false
            }
            val response = try {
                send.apply(request)
            } catch (e: Throwable) {
                // send is another copy's code, which may have been uninstalled since
                if (e !is IOException) Log.e(TAG, "send", e)
                output.respond(502)
                return false
            }
            response.use { writeResponse(it, head.method, output) }
            return true
        }

        /** A WebSocket goes to the site as WebView sent it, over a connection of its own: OkHttp can't pass its frames on. */
        private fun webSocket(head: Head, url: HttpUrl, input: InputStream, output: OutputStream) {
            // As sent, unless it's a plain http request's full URL
            val target = head.target.takeIf { it.startsWith("/") } ?: (url.encodedPath + url.encodedQuery?.let { "?$it" }.orEmpty())
            val request = buildString {
                append("${head.method} $target HTTP/1.1\r\n")
                head.headers.forEach { (name, value) ->
                    if (name.lowercase(Locale.ROOT) !in DROPPED_WEBSOCKET_HEADERS) append("$name: $value\r\n")
                }
                append("\r\n")
            }
            val socket = connect(url) ?: return output.respond(502)
            try {
                val upstream = if (url.isHttps) tlsTo(socket, url) ?: return output.respond(502) else socket
                pipe(input, output, upstream, request.toByteArray(Charsets.ISO_8859_1))
            } finally {
                socket.closeQuietly()
                untrack(socket)
            }
        }

        /**
         * A connection to [url]'s host, through the device's proxy like WebView's own: the first `ProxySelector` gives,
         * which on Android follows the device's proxy, a PAC one included; null when it fails.
         */
        private fun connect(url: HttpUrl): Socket? {
            val selected = ProxySelector.getDefault()?.select(URI("https", null, url.host, url.port, null, null, null))
                ?.firstOrNull() ?: Proxy.NO_PROXY
            val proxy = (selected.address() as? InetSocketAddress)?.takeIf { selected.type() == Proxy.Type.HTTP }
            val socket = if (selected.type() == Proxy.Type.SOCKS) Socket(selected) else Socket()
            track(socket)
            return try {
                if (selected.type() == Proxy.Type.SOCKS) {
                    // The proxy resolves the name
                    socket.connect(InetSocketAddress.createUnresolved(url.host, url.port), CONNECT_TIMEOUT_MS)
                } else if (proxy == null) {
                    socket.connect(InetSocketAddress(url.host, url.port), CONNECT_TIMEOUT_MS)
                } else {
                    socket.connect(InetSocketAddress(proxy.hostString, proxy.port), CONNECT_TIMEOUT_MS)
                    val authority = if (':' in url.host) "[${url.host}]:${url.port}" else "${url.host}:${url.port}"
                    socket.soTimeout = CONNECT_TIMEOUT_MS
                    socket.getOutputStream().apply {
                        write("CONNECT $authority HTTP/1.1\r\nHost: $authority\r\n\r\n".toByteArray())
                        flush()
                    }
                    // Unbuffered, so nothing of the tunnel's own data is read with the reply; its status code is the target
                    if (readHead(socket.getInputStream())?.target != "200") throw IOException("CONNECT refused")
                }
                socket
            } catch (_: IOException) {
                socket.closeQuietly()
                untrack(socket)
                null
            }
        }

        /** Sends [first] to [upstream], then copies both ways until either side ends, and closes [upstream]. */
        private fun pipe(input: InputStream, output: OutputStream, upstream: Socket, first: ByteArray = ByteArray(0)) {
            track(upstream)
            try {
                upstream.soTimeout = 0
                val upstreamOutput = upstream.getOutputStream()
                upstreamOutput.write(first)
                upstreamOutput.flush()
                val back = workers.submit {
                    try {
                        upstream.getInputStream().copyFlushing(output)
                    } catch (_: IOException) {
                        // Either side ended the connection
                    } finally {
                        output.closeQuietly()
                    }
                }
                input.copyFlushing(upstreamOutput)
                upstream.shutdownOutput()
                back.get()
            } finally {
                upstream.closeQuietly()
                untrack(upstream)
            }
        }

        private fun Head.isAuthorized(): Boolean {
            val sent = headers["Proxy-Authorization"]?.toByteArray() ?: return false
            return MessageDigest.isEqual(sent, authorization)
        }

        private fun OutputStream.askForCredentials() = respond(407, "Proxy-Authenticate: Basic realm=\"$realm\"\r\n")
    }

    /** TLS to [url]'s host over [socket], its certificate checked for that host; null when the handshake fails. */
    private fun tlsTo(socket: Socket, url: HttpUrl): SSLSocket? {
        val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, url.host, url.port, true) as SSLSocket
        return try {
            tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            tls.startHandshake()
            tls
        } catch (_: IOException) {
            tls.closeQuietly()
            null
        }
    }

    private fun writeResponse(response: Response, method: String, output: OutputStream) {
        val noBody = method == "HEAD" || response.code in 100..199 || response.code == 204 || response.code == 304
        val length = response.body.contentLength()
        val chunked = !noBody && length < 0
        val head = buildString {
            append("HTTP/1.1 ${response.code} ${response.message}\r\n")
            response.headers.forEach { (name, value) ->
                val lower = name.lowercase(Locale.ROOT)
                if (lower !in HOP_BY_HOP && (lower != "content-length" || noBody)) append("$name: $value\r\n")
            }
            if (chunked) {
                append("Transfer-Encoding: chunked\r\n")
            } else if (!noBody) {
                append("Content-Length: $length\r\n")
            }
            append("\r\n")
        }
        output.write(head.toByteArray(Charsets.ISO_8859_1))
        if (!noBody) response.body.byteStream().copyFlushing(output, chunked)
        output.flush()
    }

    // Flushed as it comes, for streams such as server-sent events
    internal fun InputStream.copyFlushing(output: OutputStream, chunked: Boolean = false) {
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            // An empty chunk would end the body
            if (read == 0) continue
            if (chunked) output.write("${Integer.toHexString(read)}\r\n".toByteArray())
            output.write(buffer, 0, read)
            if (chunked) output.write("\r\n".toByteArray())
            output.flush()
        }
        if (chunked) output.write("0\r\n\r\n".toByteArray())
    }

    private fun OutputStream.respond(code: Int, headers: String = "") {
        write("HTTP/1.1 $code \r\n${headers}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        flush()
    }

    /** [socket]'s input, unbuffered, until [deadline] ([System.nanoTime]): a read that would end past it times out. */
    private class DeadlineInput(private val socket: Socket, private val deadline: Long) : InputStream() {
        private val input = socket.getInputStream()

        override fun read(): Int {
            limit()
            return input.read()
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            limit()
            return input.read(bytes, offset, length)
        }

        // A timeout per read alone would let a slow drip of bytes hold the connection for good
        private fun limit() {
            val left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            if (left <= 0) throw SocketTimeoutException("First head timed out")
            socket.soTimeout = left.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    /** A request line, or for a status line its code as [target], and the headers. */
    private class Head(val method: String, val target: String, val headers: List<Pair<String, String>>) {
        val isWebSocket get() = headers["Upgrade"].equals("websocket", ignoreCase = true)
        val closes get() = headers["Connection"].equals("close", ignoreCase = true)

        fun headers(dropped: Set<String>): Headers = Headers.Builder().apply {
            headers.forEach { (name, value) -> if (name.lowercase(Locale.ROOT) !in dropped) addUnsafeNonAscii(name, value) }
        }.build()
    }

    private operator fun List<Pair<String, String>>.get(name: String) = find { (key) -> key.equals(name, ignoreCase = true) }?.second

    /** The next head, or null at the end of the connection. */
    private fun readHead(input: InputStream): Head? {
        var size = 0
        fun line() = readLine(input)?.also {
            size += it.length + 2
            if (size > MAX_HEAD) throw IOException("Head too large")
        }
        var line = line() ?: return null
        // A stray CRLF between requests
        if (line.isEmpty()) line = line() ?: return null
        val parts = line.split(' ', limit = 3)
        if (parts.size < 2) throw IOException("Not HTTP")
        val headers = mutableListOf<Pair<String, String>>()
        while (true) {
            val header = line() ?: throw IOException("Truncated")
            if (header.isEmpty()) return Head(parts[0], parts[1], headers)
            val colon = header.indexOf(':')
            if (colon <= 0) throw IOException("Bad header")
            headers += header.substring(0, colon).trim() to header.substring(colon + 1).trim()
        }
    }

    /** A request the proxy answers with 400 and the connection's end. */
    private class BadRequest(message: String) : IOException(message)

    /** The body [headers] announce, or null if there's none. Throws [BadRequest] for a length that isn't one. */
    private fun readBody(input: InputStream, headers: List<Pair<String, String>>): ByteArray? {
        if (headers["Transfer-Encoding"]?.contains("chunked", ignoreCase = true) == true) {
            val body = ByteArrayOutputStream()
            while (true) {
                val size = readLine(input)?.substringBefore(';')?.trim()?.toIntOrNull(16)?.takeIf { it >= 0 }
                    ?: throw IOException("Bad chunk")
                if (size == 0) break
                if (body.size() + size > MAX_BODY) throw IOException("Body too large")
                body.write(input.readExactly(size))
                readLine(input)
            }
            // Trailers, up to the empty line
            do {
                val trailer = readLine(input) ?: throw IOException("Truncated")
            } while (trailer.isNotEmpty())
            return body.toByteArray()
        }
        val value = headers["Content-Length"] ?: return null
        val length = value.takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }?.toIntOrNull()
        if (length == null || length > MAX_BODY) throw BadRequest("Bad length")
        return input.readExactly(length)
    }

    /** One line without its CRLF, read byte by byte, or null at the end of the stream. */
    private fun readLine(input: InputStream): String? {
        val line = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (line.size() == 0) null else throw IOException("Truncated")
            if (byte == '\n'.code) return line.toString("ISO-8859-1").removeSuffix("\r")
            if (line.size() == MAX_HEAD) throw IOException("Line too long")
            line.write(byte)
        }
    }

    private fun InputStream.readExactly(count: Int): ByteArray {
        val bytes = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = read(bytes, offset, count - offset)
            if (read < 0) throw IOException("Truncated")
            offset += read
        }
        return bytes
    }
}

internal fun Closeable.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
    }
}

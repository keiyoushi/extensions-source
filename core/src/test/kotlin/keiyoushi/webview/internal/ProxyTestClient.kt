package keiyoushi.webview.internal

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** The proxy as a WebView is told of it, read from the shared state the way any copy of core reads it. */
internal class ProxyAccess(val port: Int, val realm: String, private val username: String, private val password: String, val certificate: ByteArray) {
    val authorization: String
        get() = "Proxy-Authorization: Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}\r\n"

    companion object {
        /** The shared state, made as a copy of core makes it if none has yet. */
        @Suppress("UNCHECKED_CAST")
        val shared: MutableMap<String, Any>
            get() = System.getProperties().let { properties ->
                properties.putIfAbsent(ProxyProtocol.PROPERTY, ConcurrentHashMap<String, Any>())
                properties[ProxyProtocol.PROPERTY] as MutableMap<String, Any>
            }

        /** The running proxy; throws if there's none. */
        fun current(): ProxyAccess = ProxyAccess(
            (shared[ProxyProtocol.LISTENER] as ServerSocket).localPort,
            shared[ProxyProtocol.REALM] as String,
            shared[ProxyProtocol.USERNAME] as String,
            shared[ProxyProtocol.PASSWORD] as String,
            (shared[ProxyProtocol.CERTIFICATE] as X509Certificate).encoded,
        )
    }
}

internal fun ok(request: Request, body: String): Response = Response.Builder()
    .request(request)
    .protocol(Protocol.HTTP_2)
    .code(200)
    .message("")
    .body(body.toResponseBody())
    .build()

/** A TLS connection to [host] through the proxy, trusting only the proxy's certificate. */
internal fun tunnel(proxy: ProxyAccess, host: String = "a.test"): SSLSocket {
    val socket = Socket(InetAddress.getLoopbackAddress(), proxy.port)
    socket.getOutputStream().write("CONNECT $host:443 HTTP/1.1\r\nHost: $host:443\r\n${proxy.authorization}\r\n".toByteArray())
    assertEquals(200, readResponse(socket.getInputStream(), noBody = true).code)
    val trust = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            check(chain[0].encoded.contentEquals(proxy.certificate))
        }
        override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
    }
    val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    return (context.socketFactory.createSocket(socket, host, 443, true) as SSLSocket).apply { startHandshake() }
}

/** One GET of [path] on [host] over a TLS tunnel of its own. */
internal fun get(proxy: ProxyAccess, host: String, path: String = "/"): Received = tunnel(proxy, host).use { tls ->
    tls.outputStream.write("GET $path HTTP/1.1\r\nHost: $host\r\n\r\n".toByteArray())
    readResponse(tls.inputStream)
}

internal data class Answer(val code: Int, val body: String)

internal class Received(val code: Int, val headers: Map<String, String>, val body: String) {
    val answer get() = Answer(code, body)
}

internal fun readResponse(input: InputStream, noBody: Boolean = false): Received {
    fun line(): String {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0 || byte == '\n'.code) return bytes.toString(Charsets.ISO_8859_1).removeSuffix("\r")
            bytes.write(byte)
        }
    }
    val code = line().split(' ')[1].toInt()
    val headers = generateSequence(::line).takeWhile { it.isNotEmpty() }
        .associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
    val body = when {
        noBody -> ""
        headers["transfer-encoding"] == "chunked" -> buildString {
            while (true) {
                val size = line().toInt(16)
                if (size == 0) break
                append(String(input.readNBytes(size)))
                line()
            }
            line()
        }
        else -> String(input.readNBytes(headers["content-length"]?.toInt() ?: 0))
    }
    return Received(code, headers, body)
}

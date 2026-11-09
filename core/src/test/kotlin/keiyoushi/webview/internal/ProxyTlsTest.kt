package keiyoushi.webview.internal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/**
 * TLS server side of the WebView proxy. The handshake uses SunJSSE on both ends, not Android's Conscrypt; the ALPN
 * contract exercised (a server that sets no application protocols stays silent, so the client falls back to HTTP/1.1)
 * is the same.
 */
class ProxyTlsTest {

    @Test
    fun readsTheAlpnOfAClientHello() {
        fun hello(vararg protocols: String): ByteBuffer {
            val engine = SSLContext.getInstance("TLS").apply { init(null, null, null) }.createSSLEngine("a.test", 443)
            engine.useClientMode = true
            engine.sslParameters = engine.sslParameters.apply { applicationProtocols = arrayOf(*protocols) }
            val out = ByteBuffer.allocate(engine.session.packetBufferSize)
            engine.wrap(ByteBuffer.allocate(0), out)
            out.flip()
            return out
        }
        assertEquals(listOf("h2", "http/1.1"), ProxyTls.alpn(hello("h2", "http/1.1")))
        assertEquals(listOf("http/1.1"), ProxyTls.alpn(hello("http/1.1")))
        assertEquals(emptyList<String>(), ProxyTls.alpn(hello()))
        assertNull(ProxyTls.alpn(ByteBuffer.wrap("GET / HTTP/1.1\r\n".toByteArray())))
    }

    private val keyPair = ProxyTls.newKeyPair()
    private val certificate = ProxyTls.certificate(keyPair)

    @Test
    fun handshakeServesOurCertificateOverHttp11() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val failure = AtomicReference<Throwable>()
        val ready = CountDownLatch(1)

        val serverThread = thread {
            try {
                server.accept().use { raw ->
                    ProxyTls.accept(ProxyTls.context(keyPair, certificate), raw).use { ssl ->
                        // echo one byte, proving the handshake finished and data flows both ways
                        ssl.outputStream.write(ssl.inputStream.read())
                        ssl.outputStream.flush()
                    }
                }
            } catch (t: Throwable) {
                failure.set(t)
            } finally {
                ready.countDown()
            }
        }

        val client = clientContextTrusting(certificate)
            .socketFactory.createSocket(InetAddress.getLoopbackAddress(), server.localPort) as SSLSocket
        client.use {
            // offer h2 so a server that negotiated it would be caught; ProxyTls must not
            it.sslParameters = it.sslParameters.apply { applicationProtocols = arrayOf("h2", "http/1.1") }
            it.startHandshake()

            val peer = it.session.peerCertificates.single() as X509Certificate
            assertArrayEquals("WebView must receive the proxy's certificate", certificate.encoded, peer.encoded)
            assertTrue("server must not negotiate h2 inside the tunnel", it.applicationProtocol.isNullOrEmpty())
            assertTrue("negotiated protocol", it.session.protocol in setOf("TLSv1.3", "TLSv1.2"))

            it.outputStream.write(0x42)
            it.outputStream.flush()
            assertEquals(0x42, it.inputStream.read())
        }

        assertTrue("server thread finished", ready.await(20, TimeUnit.SECONDS))
        serverThread.join(TimeUnit.SECONDS.toMillis(5))
        failure.get()?.let { throw AssertionError("server side failed", it) }
    }

    @Test
    fun certificateIsSelfSignedEcP256() {
        val cert = certificate

        assertEquals("v3", 3, cert.version)
        assertEquals("ecdsa-with-SHA256", "1.2.840.10045.4.3.2", cert.sigAlgOID)
        assertTrue("subject CN", cert.subjectX500Principal.name.contains("CN=Keiyoushi WebView proxy"))
        assertEquals("self-signed: issuer equals subject", cert.subjectX500Principal, cert.issuerX500Principal)

        val now = Instant.now()
        assertTrue("notBefore in the past", cert.notBefore.toInstant().isBefore(now))
        assertTrue("notAfter in the future", cert.notAfter.toInstant().isAfter(now))
        val lifetimeDays = Duration.between(cert.notBefore.toInstant(), cert.notAfter.toInstant()).toDays()
        assertEquals("now-1d..now+365d", 366L, lifetimeDays)

        // getBasicConstraints() returns -1 for a non-CA cert; a critical extension is reported by its OID
        assertEquals("not a CA", -1, cert.basicConstraints)
        assertTrue("basicConstraints critical", cert.criticalExtensionOIDs.contains("2.5.29.19"))

        val publicKey = cert.publicKey as ECPublicKey
        assertEquals("EC key", "EC", publicKey.algorithm)
        assertEquals("P-256 field size", 256, publicKey.params.curve.field.fieldSize)

        cert.verify(cert.publicKey) // self-signature verifies, throws otherwise
    }

    private fun clientContextTrusting(cert: X509Certificate): SSLContext {
        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (!MessageDigest.isEqual(chain.first().encoded, cert.encoded)) throw CertificateException("untrusted")
            }
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
    }
}

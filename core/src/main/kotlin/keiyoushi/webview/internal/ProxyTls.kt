package keiyoushi.webview.internal

import android.net.http.SslCertificate
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.Socket
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** TLS server side of the WebView proxy, for a self-signed EC P-256 certificate the proxy keeps in memory. */
internal object ProxyTls {
    /** A new EC P-256 key pair. */
    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    /** A TLS server context presenting [certificate], for [keyPair]'s private key. */
    fun context(keyPair: KeyPair, certificate: X509Certificate): SSLContext = SSLContext.getInstance("TLS").apply {
        init(arrayOf(KeyManager(keyPair.private, certificate)), null, null)
    }

    /**
     * Wraps a CONNECT tunnel's [socket] in TLS as the server of [context], after the handshake. Closing it, or a failed
     * handshake, closes [socket].
     */
    fun accept(context: SSLContext, socket: Socket): SSLSocket {
        val ssl = context.socketFactory.createSocket(socket, null, socket.port, true) as SSLSocket
        try {
            ssl.useClientMode = false
            val timeout = socket.soTimeout
            ssl.soTimeout = HANDSHAKE_TIMEOUT.inWholeMilliseconds.toInt()
            ssl.startHandshake()
            ssl.soTimeout = timeout
            return ssl
        } catch (e: Exception) {
            ssl.closeQuietly()
            throw e
        }
    }

    /**
     * The protocols the TLS ClientHello waiting on [socket] offers by ALPN, read without taking it off the socket; null
     * when none comes within the handshake timeout or it doesn't parse, and before Android 10, where
     * [ParcelFileDescriptor.fromSocket] takes the socket's own descriptor instead of a copy.
     */
    fun offeredProtocols(socket: Socket): List<String>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val hello = ByteArray(MAX_RECORD)
        val deadline = TimeSource.Monotonic.markNow() + HANDSHAKE_TIMEOUT
        ParcelFileDescriptor.fromSocket(socket).use { descriptor ->
            // Polled: a peek returns what has come so far, which may be only part of the hello, and with the tunnel's
            // socket timeout off it would wait for the first bytes past the deadline
            while (true) {
                val read = if (socket.getInputStream().available() > 0) {
                    Os.recvfrom(descriptor.fileDescriptor, hello, 0, hello.size, OsConstants.MSG_PEEK, null)
                } else {
                    0
                }
                if (read >= RECORD_HEADER) {
                    // The record header ends with the record's length
                    val length = RECORD_HEADER + ByteBuffer.wrap(hello, 3, 2).short.toUShort().toInt()
                    if (read >= length) return alpn(ByteBuffer.wrap(hello, 0, length))
                }
                if (read == hello.size || deadline.hasPassedNow()) return null
                Thread.sleep(1)
            }
        }
    }

    /** The ALPN protocols of a TLS record holding a ClientHello; null if it isn't one. */
    fun alpn(hello: ByteBuffer): List<String>? = try {
        fun ByteBuffer.skip(count: Int) = position(position() + count)
        fun ByteBuffer.u8() = get().toUByte().toInt()
        fun ByteBuffer.u16() = short.toUShort().toInt()
        if (hello.u8() != CONTENT_TYPE_HANDSHAKE) return null
        hello.skip(RECORD_HEADER - 1)
        if (hello.u8() != HANDSHAKE_CLIENT_HELLO) return null
        hello.skip(3 + 2 + 32) // length, version, random
        hello.skip(hello.u8()) // session id
        hello.skip(hello.u16()) // cipher suites
        hello.skip(hello.u8()) // compression methods
        val extensionsEnd = hello.u16() + hello.position()
        while (hello.position() < extensionsEnd) {
            val type = hello.u16()
            val length = hello.u16()
            if (type != EXTENSION_ALPN) {
                hello.skip(length)
                continue
            }
            val listEnd = hello.u16() + hello.position()
            return buildList {
                while (hello.position() < listEnd) {
                    val name = ByteArray(hello.u8())
                    hello.get(name)
                    add(name.toString(Charsets.US_ASCII))
                }
            }
        }
        emptyList()
    } catch (_: BufferUnderflowException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private const val RECORD_HEADER = 5
    private const val MAX_RECORD = RECORD_HEADER + 16 * 1024
    private const val CONTENT_TYPE_HANDSHAKE = 22
    private const val HANDSHAKE_CLIENT_HELLO = 1
    private const val EXTENSION_ALPN = 16
    private val HANDSHAKE_TIMEOUT = 15.seconds

    /** The DER encoding of an [SslError][android.net.http.SslError]'s [certificate]. */
    fun encoded(certificate: SslCertificate): ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        certificate.x509Certificate?.encoded
    } else {
        SslCertificate.saveState(certificate)?.getByteArray("x509-certificate")
    }

    /** A self-signed certificate for [keyPair], valid from a day ago for a year. */
    fun certificate(keyPair: KeyPair): X509Certificate {
        val now = Instant.now()
        val name = sequence(set(sequence(COMMON_NAME, utf8String("Keiyoushi WebView proxy"))))
        val tbsCertificate = sequence(
            explicit(0, integer(BigInteger.valueOf(2))), // version v3
            integer(BigInteger(64, SecureRandom()) + BigInteger.ONE), // serialNumber, random positive
            ECDSA_WITH_SHA256, // signature algorithm
            name, // issuer
            sequence(utcTime(now.minus(1, ChronoUnit.DAYS)), utcTime(now.plus(365, ChronoUnit.DAYS))), // validity
            name, // subject
            keyPair.public.encoded, // subjectPublicKeyInfo, already DER
            explicit(
                3, // extensions
                sequence(
                    sequence(
                        BASIC_CONSTRAINTS,
                        boolean(true), // critical
                        octetString(sequence()), // BasicConstraints {}, i.e. not a CA
                    ),
                ),
            ),
        )
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(tbsCertificate)
            sign()
        }
        val der = sequence(tbsCertificate, ECDSA_WITH_SHA256, bitString(signature))
        return CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
    }

    private val COMMON_NAME = oid("2.5.4.3")
    private val BASIC_CONSTRAINTS = oid("2.5.29.19")
    private val ECDSA_WITH_SHA256 = sequence(oid("1.2.840.10045.4.3.2"))

    private val UTC_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)

    private fun sequence(vararg parts: ByteArray) = tlv(0x30, *parts)
    private fun set(vararg parts: ByteArray) = tlv(0x31, *parts)
    private fun integer(value: BigInteger) = tlv(0x02, value.toByteArray())
    private fun boolean(value: Boolean) = tlv(0x01, byteArrayOf(if (value) 0xFF.toByte() else 0))
    private fun octetString(value: ByteArray) = tlv(0x04, value)
    private fun bitString(value: ByteArray) = tlv(0x03, byteArrayOf(0) + value) // 0 unused trailing bits
    private fun utf8String(value: String) = tlv(0x0c, value.toByteArray())
    private fun utcTime(time: Instant) = tlv(0x17, UTC_TIME.format(time).toByteArray())

    /** Context-specific tag [n], EXPLICIT (constructed). */
    private fun explicit(n: Int, vararg parts: ByteArray) = tlv(0xA0 or n, *parts)

    private fun oid(dotted: String): ByteArray {
        val arcs = dotted.split('.').map(String::toInt)
        val body = ByteArrayOutputStream().apply {
            write(40 * arcs[0] + arcs[1])
            for (arc in arcs.drop(2)) {
                val base128 = ArrayDeque<Int>()
                var value = arc
                base128.addFirst(value and 0x7f)
                value = value shr 7
                while (value > 0) {
                    base128.addFirst(value and 0x7f or 0x80)
                    value = value shr 7
                }
                base128.forEach(::write)
            }
        }
        return tlv(0x06, body.toByteArray())
    }

    /** One DER TLV: [tag], definite length, then [parts] concatenated. */
    private fun tlv(tag: Int, vararg parts: ByteArray): ByteArray {
        val length = parts.sumOf { it.size }
        return ByteArrayOutputStream(length + 6).apply {
            write(tag)
            when {
                length < 0x80 -> write(length)
                length < 0x100 -> {
                    write(0x81)
                    write(length)
                }
                else -> {
                    write(0x82)
                    write(length shr 8)
                    write(length and 0xff)
                }
            }
            parts.forEach(::write)
        }.toByteArray()
    }

    private class KeyManager(private val key: PrivateKey, private val certificate: X509Certificate) : X509ExtendedKeyManager() {
        private fun alias(keyType: String?) = if (keyType == "EC" || keyType == "EC_EC") ALIAS else null

        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = alias(keyType)
        override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?) = alias(keyType)
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = alias(keyType)?.let { arrayOf(it) }
        override fun getCertificateChain(alias: String?) = if (alias == ALIAS) arrayOf(certificate) else null
        override fun getPrivateKey(alias: String?) = if (alias == ALIAS) key else null
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? = null

        private companion object {
            const val ALIAS = "proxy"
        }
    }
}

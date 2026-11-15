package eu.kanade.tachiyomi.extension.all.lunaranime

import android.util.Base64
import keiyoushi.utils.getPreferences
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonString
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import kotlin.time.Clock

/**
 * The site's "Serenity" proof is a plain DPoP JWT. The browser generates its own P-256 key
 * pair, keeps it in IndexedDB and signs `<header>.<payload>` with ES256, sending the result in
 * the `cant-catch-this-monkey` header. The key pair is never issued or acknowledged by the
 * server, so one generated here is just as valid. Like the site's reader, only the page-list
 * request carries it: a key that signed other requests gets decoy pages.
 */
class LunarSerenity(
    private val apiHost: String,
    private val sourceId: Long,
) {

    @Volatile
    private var keyPair: KeyPair = loadOrGenerateKeyPair()

    private fun prefs() = getPreferences(sourceId)

    private fun loadOrGenerateKeyPair(): KeyPair {
        val prefs = prefs()
        val storedPrivate = prefs.getString(KEY_PRIVATE, null)
        val storedPublic = prefs.getString(KEY_PUBLIC, null)
        if (storedPrivate != null && storedPublic != null) {
            // An unreadable stored key is replaced by a new one below
            try {
                val keyFactory = KeyFactory.getInstance("EC")
                val privateKey = keyFactory.generatePrivate(
                    PKCS8EncodedKeySpec(Base64.decode(storedPrivate, Base64.NO_WRAP)),
                )
                val publicKey = keyFactory.generatePublic(
                    X509EncodedKeySpec(Base64.decode(storedPublic, Base64.NO_WRAP)),
                )
                return KeyPair(publicKey, privateKey)
            } catch (_: Exception) {
            }
        }
        return generateKeyPair().also { saveKeyPair(it) }
    }

    private fun saveKeyPair(pair: KeyPair) {
        prefs().edit()
            .putString(KEY_PRIVATE, Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP))
            .putString(KEY_PUBLIC, Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP))
            .apply()
    }

    private fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    @Synchronized
    fun reset() {
        keyPair = generateKeyPair().also { saveKeyPair(it) }
    }

    companion object {
        private const val KEY_PRIVATE = "ec_private_key"
        private const val KEY_PUBLIC = "ec_public_key"
        private const val PROOF_HEADER = "cant-catch-this-monkey"
        private const val COORDINATE_SIZE = 32
        private val PAGE_LIST_PATH = listOf("api", "manga", "r")
    }

    private val publicPoint get() = (keyPair.public as ECPublicKey).w

    private val coordinateX get() = publicPoint.affineX.toFixedWidth()

    private val coordinateY get() = publicPoint.affineY.toFixedWidth()

    /**
     * RFC 7638 thumbprint of the public JWK, used by the site as extra key material when
     * decrypting a chapter's session data.
     */
    val thumbprint: String
        get() {
            return jwk().toJsonString().sha256().base64Url()
        }

    /** Members in RFC 7638 order, as the thumbprint hashes them. */
    private fun jwk() = SerenityJwk(crv = "P-256", kty = "EC", x = coordinateX, y = coordinateY)

    fun proof(method: String, url: HttpUrl): String {
        val header = SerenityProofHeader(alg = "ES256", typ = "dpop+jwt", jwk = jwk())
        val payload = SerenityProofPayload(
            htu = "${url.scheme}://${url.host}${url.encodedPath}",
            htm = method.uppercase(),
            iat = Clock.System.now().epochSeconds,
            jti = UUID.randomUUID().toString(),
        )

        val signingInput = "${header.toJsonString().toByteArray().base64Url()}.${payload.toJsonString().toByteArray().base64Url()}"
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(signingInput.toByteArray())
            sign()
        }

        return "$signingInput.${signature.derToJose().base64Url()}"
    }

    fun interceptor() = Interceptor { chain ->
        val request = chain.request()
        if (request.url.host != apiHost || request.url.pathSegments.take(3) != PAGE_LIST_PATH) {
            return@Interceptor chain.proceed(request)
        }

        val response = chain.proceed(request.withProof())

        // The site mints a fresh key pair whenever the server rejects the current one.
        if (response.code == 403 && response.peekBody(1024).string().contains("validate", ignoreCase = true)) {
            response.close()
            reset()
            return@Interceptor chain.proceed(request.withProof())
        }

        response
    }

    private fun Request.withProof(): Request = newBuilder().header(PROOF_HEADER, proof(method, url)).build()

    private fun ByteArray.base64Url(): String = Base64.encodeToString(this, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)

    /** P-256 coordinates are always sent as 32 bytes, big endian. */
    private fun BigInteger.toFixedWidth(): String {
        val bytes = toByteArray()
        val fixed = when {
            bytes.size == COORDINATE_SIZE -> bytes
            bytes.size > COORDINATE_SIZE -> bytes.copyOfRange(bytes.size - COORDINATE_SIZE, bytes.size)
            else -> ByteArray(COORDINATE_SIZE).also { bytes.copyInto(it, COORDINATE_SIZE - bytes.size) }
        }
        return fixed.base64Url()
    }

    /** JOSE wants the raw `r || s` pair, while the JCA signs into a DER sequence. */
    private fun ByteArray.derToJose(): ByteArray {
        var index = 1
        val length = this[index++].toInt() and 0xFF
        if (length and 0x80 != 0) index += length and 0x7F

        fun readInteger(): BigInteger {
            require(this[index].toInt() == 0x02) { "Malformed ECDSA signature" }
            index++
            val size = this[index++].toInt() and 0xFF
            return BigInteger(1, copyOfRange(index, index + size)).also { index += size }
        }

        val r = readInteger().toByteArray32()
        val s = readInteger().toByteArray32()

        return r + s
    }

    private fun BigInteger.toByteArray32(): ByteArray {
        val bytes = toByteArray()
        return when {
            bytes.size == COORDINATE_SIZE -> bytes
            bytes.size > COORDINATE_SIZE -> bytes.copyOfRange(bytes.size - COORDINATE_SIZE, bytes.size)
            else -> ByteArray(COORDINATE_SIZE).also { bytes.copyInto(it, COORDINATE_SIZE - bytes.size) }
        }
    }
}

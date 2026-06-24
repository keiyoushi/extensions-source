package eu.kanade.tachiyomi.extension.all.lunaranime

import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.utils.applicationContext
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECPrivateKeySpec
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets.UTF_8

class LunarSigner(
    private val baseUrl: String,
    private val apiUrl: String,
) {
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var keyPair: KeyPairDto? = null
    private var privateKey: PrivateKey? = null

    fun dpopInterceptor() = Interceptor { chain ->
        var req = chain.request()
        val url = req.url.toString()

        if (!url.contains(apiUrl)) return@Interceptor chain.proceed(req)

        val dpop = signUrl(req.method, url.substringBefore('?'))
        if (dpop.isNotEmpty()) req = req.newBuilder().addHeader("dpop", dpop).build()

        val resp = chain.proceed(req)

        if (resp.code == 403 && resp.body.string().contains("validate", ignoreCase = true)) {
            keyPair = null
            throw IOException("Solve captcha in webview and retry")
        }
        resp
    }

    fun signUrl(method: String, url: String): String {
        if (keyPair == null) {
            val raw = exportKeysWv() ?: return ""
            keyPair = raw.parseAs<KeyPairDto>()
            privateKey = buildPrivateKey(keyPair!!.privateJwk)
        }
        return buildDpop(method, url, privateKey!!, keyPair!!.publicJwk)
    }

    private fun exportKeysWv(): String? {
        val latch = CountDownLatch(1)
        var result: String? = null

        handler.post {
            WebView(applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(
                            EXPORT_JS,
                            ValueCallback { value ->
                                result = when (value) {
                                    null, "null", "{}", "" -> null
                                    else -> value
                                }
                                latch.countDown()
                                view.destroy()
                            },
                        )
                    }
                }
                loadDataWithBaseURL(baseUrl, " ", "text/html", "utf-8", null)
            }
        }

        return if (latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) result else null
    }

    // ECDSA with P-256 curve and SHA-256 hash

    private val curveSpec: java.security.spec.ECParameterSpec = java.security.AlgorithmParameters.getInstance("EC").apply {
        init(java.security.spec.ECGenParameterSpec("secp256r1"))
    }.getParameterSpec(java.security.spec.ECParameterSpec::class.java)

    private fun buildDpop(method: String, url: String, privateKey: PrivateKey, publicJwk: JwkDto): String {
        val headerEncoded = base64UrlEncode(DpopHeader("dpop+jwt", "ES256", publicJwk).toJsonString())
        val payloadEncoded = base64UrlEncode(
            DpopPayload(
                htm = method.uppercase(),
                htu = url,
                iat = System.currentTimeMillis() / 1000,
                jti = base64UrlEncode(ByteArray(16).apply { java.security.SecureRandom().nextBytes(this) }),
            ).toJsonString(),
        )

        val signingInput = "$headerEncoded.$payloadEncoded"

        val derSignature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(signingInput.toByteArray(UTF_8))
        }.sign()

        val rawSignature = derToP1363(derSignature)

        return "$signingInput.${base64UrlEncode(rawSignature)}"
    }

    private fun buildPrivateKey(jwk: JwkDto): PrivateKey {
        val dBytes = Base64.getUrlDecoder().decode(jwk.d!!)
        val d = BigInteger(1, dBytes)
        return KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(d, curveSpec))
    }

    private fun derToP1363(der: ByteArray): ByteArray {
        val out = ByteArray(64)

        val rLen = der[3].toInt() and 0xFF
        val rOffset = 4
        val rOctets = der.copyOfRange(rOffset, rOffset + rLen)

        val sLenOffset = rOffset + rLen + 1
        val sLen = der[sLenOffset].toInt() and 0xFF
        val sOffset = sLenOffset + 1
        val sOctets = der.copyOfRange(sOffset, sOffset + sLen)

        val rBig = BigInteger(1, rOctets).toByteArray().takeLast(32).toByteArray()
        val sBig = BigInteger(1, sOctets).toByteArray().takeLast(32).toByteArray()

        System.arraycopy(rBig, 0, out, 32 - rBig.size, rBig.size)
        System.arraycopy(sBig, 0, out, 64 - sBig.size, sBig.size)
        return out
    }

    private fun base64UrlEncode(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    private fun base64UrlEncode(data: String): String = base64UrlEncode(data.toByteArray(UTF_8))

    companion object {
        private const val TIMEOUT_MS = 5000L
        private const val EXPORT_JS = """
            (function() {
                try {
                    var stored = localStorage.getItem("lunar-device-key-jwk");
                    if (stored) return JSON.parse(stored);
                } catch(e) {}
                return null;
            })();
        """
    }
}

@Serializable class JwkDto(val crv: String, val ext: Boolean, val key_ops: List<String>, val kty: String, val x: String, val y: String, val d: String? = null)

@Serializable class KeyPairDto(val publicJwk: JwkDto, val privateJwk: JwkDto)

@Serializable class DpopHeader(val typ: String, val alg: String, val jwk: JwkDto)

@Serializable class DpopPayload(val htm: String, val htu: String, val iat: Long, val jti: String)

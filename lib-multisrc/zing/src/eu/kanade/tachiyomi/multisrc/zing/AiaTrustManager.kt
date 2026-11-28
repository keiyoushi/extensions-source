package eu.kanade.tachiyomi.multisrc.zing

import android.annotation.SuppressLint
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.X509TrustManager

/**
 * The client's trust manager, completing chains that leave out an intermediate
 * with the issuer named in the last certificate's Authority Information Access, as browsers do.
 */
@SuppressLint("CustomX509TrustManager")
internal class AiaTrustManager(private val client: OkHttpClient) : X509TrustManager {
    private val platform = client.x509TrustManager!!

    private val issuers = ConcurrentHashMap<String, X509Certificate>()

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        try {
            platform.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            val url = chain.last().caIssuersUrl() ?: throw e
            val issuer = issuers.getOrPut(url) { fetchCertificate(url) }
            platform.checkServerTrusted(chain + issuer, authType)
        }
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = platform.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers

    private fun fetchCertificate(url: String): X509Certificate = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        CertificateFactory.getInstance("X.509").generateCertificate(response.body.byteStream()) as X509Certificate
    }

    // id-ad-caIssuers accessLocation: a uniformResourceIdentifier ([6] IA5String) after the OID
    private fun X509Certificate.caIssuersUrl(): String? {
        val extension = getExtensionValue(AIA_OID) ?: return null
        val start = (0..extension.size - CA_ISSUERS.size).firstOrNull { start ->
            CA_ISSUERS.indices.all { extension[start + it] == CA_ISSUERS[it] }
        } ?: return null
        val tag = start + CA_ISSUERS.size
        if (extension[tag] != URI_TAG) return null
        return String(extension, tag + 2, extension[tag + 1].toInt() and 0xFF, Charsets.US_ASCII)
    }

    companion object {
        private const val AIA_OID = "1.3.6.1.5.5.7.1.1"
        private val CA_ISSUERS = byteArrayOf(0x06, 0x08, 0x2B, 0x06, 0x01, 0x05, 0x05, 0x07, 0x30, 0x02)
        private const val URI_TAG = 0x86.toByte()
    }
}

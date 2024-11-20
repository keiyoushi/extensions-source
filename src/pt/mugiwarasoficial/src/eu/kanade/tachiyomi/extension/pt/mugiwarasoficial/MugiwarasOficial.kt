package eu.kanade.tachiyomi.extension.pt.mugiwarasoficial

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class MugiwarasOficial : Madara(
    "Mugiwaras Oficial",
    "https://mugiwarasoficial.com",
    "pt-BR",
    SimpleDateFormat("d 'de' MMM 'de' yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .ignoreAllSSLErrors()
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Estado) + .summary-content"

    private fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
        val naiveTrustManager = @SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        }

        val insecureSocketFactory = SSLContext.getInstance("TLSv1.2").apply {
            val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
            init(null, trustAllCerts, SecureRandom())
        }.socketFactory

        sslSocketFactory(insecureSocketFactory, naiveTrustManager)
        hostnameVerifier { _, _ -> true }
        return this
    }
}

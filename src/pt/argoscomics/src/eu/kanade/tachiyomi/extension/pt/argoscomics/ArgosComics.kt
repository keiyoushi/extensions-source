package eu.kanade.tachiyomi.extension.pt.argoscomics

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class ArgosComics : Madara(
    "Argos Comics",
    "https://argoscomic.com",
    "pt-BR",
    SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR")),
) {

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

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .ignoreAllSSLErrors()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val mime = response.headers["Content-Type"]
            if (response.isSuccessful) {
                if (mime == "application/octet-stream" || mime == null) {
                    // Fix image content type
                    val type = "image/jpeg".toMediaType()
                    val body = response.body.bytes().toResponseBody(type)
                    return@addInterceptor response.newBuilder().body(body)
                        .header("Content-Type", "image/jpeg").build()
                }
            }
            response
        }
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val useNewChapterEndpoint = true
}

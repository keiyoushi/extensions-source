package eu.kanade.tachiyomi.extension.es.catharsisfantasy

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class CatharsisFantasy : MangaThemesia(
    "Catharsis Fantasy",
    "https://catharsisfantasy.com",
    "es",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .ignoreAllSSLErrors()
        .build()

    private val iframeSelector: String = "#mangaIframe"

    override fun pageListParse(document: Document): List<Page> {
        return super.pageListParse(
            document.takeIf { it.select(iframeSelector).isEmpty() }
                ?: fetchIframeDocumentPageList(document),
        )
    }

    private fun fetchIframeDocumentPageList(document: Document): Document {
        val pagesUrl = document.selectFirst(iframeSelector)!!
            .absUrl("src")

        return client.newCall(GET(pagesUrl, headers))
            .execute().asJsoup()
    }

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

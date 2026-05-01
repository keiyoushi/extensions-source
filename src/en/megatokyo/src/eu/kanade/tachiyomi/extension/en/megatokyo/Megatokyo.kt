package eu.kanade.tachiyomi.extension.en.megatokyo

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class Megatokyo : HttpSource() {

    override val name = "Megatokyo"

    override val baseUrl = "https://megatokyo.com"

    override val lang = "en"

    override val supportsLatest = false

    private val dateParser = SimpleDateFormat("MMMMM dd, yyyy", Locale.US)

    override val client: OkHttpClient = network.client.newBuilder()
        .ignoreAllSSLErrors()
        .build()

    @SuppressLint("CustomX509TrustManager")
    private fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
        val naiveTrustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        }

        val insecureSocketFactory = SSLContext.getInstance("TLSv1.2").apply {
            init(null, arrayOf<TrustManager>(naiveTrustManager), SecureRandom())
        }.socketFactory

        sslSocketFactory(insecureSocketFactory, naiveTrustManager)
        hostnameVerifier { _, _ -> true }
        return this
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/archive.php?list_by=date")
            title = "Megatokyo"
            artist = "Fred Gallagher"
            author = "Fred Gallagher"
            status = SManga.ONGOING
            description = "Relax, we understand j00"
            thumbnail_url = "https://i.ibb.co/yWQM1gY/megatokyo.png"
        }

        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = fetchPopularManga(1)
        .map { it.mangas.first().apply { initialized = true } }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.content h2:contains(Comics by Date) + div ul li a[name]")
            .map { element ->
                SChapter.create().apply {
                    url = element.attr("href")
                    chapter_number = url.substringAfterLast("/").toFloatOrNull() ?: -1f
                    name = element.text()
                    date_upload = dateParser.tryParse(element.attr("title").replace(ordinalRegex, "$1"))
                }
            }.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#strip img").mapIndexed { i, element ->
            Page(i, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    companion object {
        private val ordinalRegex = "(\\d+)(st|nd|rd|th)".toRegex()
    }
}

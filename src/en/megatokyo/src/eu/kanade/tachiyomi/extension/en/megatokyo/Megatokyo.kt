package eu.kanade.tachiyomi.extension.en.megatokyo

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class Megatokyo : ParsedHttpSource() {

    override val name = "Megatokyo"

    override val baseUrl = "https://megatokyo.com"

    override val lang = "en"

    override val supportsLatest = false

    private val dateParser: SimpleDateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale.US)

    override val client = getUnsafeOkHttpClient()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create()
        manga.setUrlWithoutDomain("/archive.php?list_by=date")
        manga.title = "Megatokyo"
        manga.artist = "Fred Gallagher"
        manga.author = "Fred Gallagher"
        manga.status = SManga.ONGOING
        manga.description = "Relax, we understand j00"
        manga.thumbnail_url = "https://i.ibb.co/yWQM1gY/megatokyo.png"

        return Observable.just(MangasPage(arrayListOf(manga), false))
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = fetchPopularManga(1)
        .map { it.mangas.first().apply { initialized = true } }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterListSelector() =
        "div.content h2:contains(Comics by Date) + div ul li a[name]"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.url = element.attr("href")
        chapter.chapter_number = chapter.url.substringAfterLast("/").toFloat()
        chapter.name = element.text()
        chapter.date_upload = element.attr("title").toDate()
        return chapter
    }

    override fun pageListParse(document: Document) =
        document.select("#strip img")
            .mapIndexed { i, element ->
                Page(i, "", "https://megatokyo.com/" + element.attr("src"))
            }

    // certificate wasn't trusted for some reason so trusted all certificates
    private fun getUnsafeOkHttpClient(): OkHttpClient {
        // Create a trust manager that does not validate certificate chains
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                }
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                }

                override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
            },
        )

        // Install the all-trusting trust manager
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        // Create an ssl socket factory with our all-trusting manager
        val sslSocketFactory = sslContext.socketFactory

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }.build()
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw Exception("Not used")

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    override fun popularMangaSelector(): String = throw Exception("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun searchMangaNextPageSelector(): String = throw Exception("Not used")

    override fun searchMangaSelector(): String = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw Exception("Not used")

    override fun popularMangaNextPageSelector(): String = throw Exception("Not used")

    override fun popularMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun mangaDetailsParse(document: Document): SManga = throw Exception("Not used")

    override fun latestUpdatesNextPageSelector(): String = throw Exception("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesSelector(): String = throw Exception("Not used")

    private fun String.toDate(): Long {
        return runCatching { dateParser.parse(this.replace("(\\d+)(st|nd|rd|th)".toRegex(), "$1"))?.time }
            .onFailure { print("Something wrong happened: ${it.message}") }.getOrNull() ?: 0L
    }
}

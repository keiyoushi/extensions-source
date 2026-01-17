package eu.kanade.tachiyomi.extension.ar.oduto

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Suppress("unused")
class Oduto : HttpSource() {

    override val baseUrl = "https://nb19u.blogspot.com"
    override val lang = "ar"
    override val name = "Oduto"
    override val supportsLatest = false

    override val client: OkHttpClient =
        network.cloudflareClient
            .newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .rateLimit(10, 1, TimeUnit.SECONDS)
            .build()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(
        MangasPage(
            listOf(
                SManga.create().apply {
                    title = "BORUTO: Two Blue Vortex"
                    artist = "Mikio Ikemoto"
                    author = "Masashi Kishimoto"
                    genre = "شونين, دراما, خيال, أكشن, نينجا"
                    status = SManga.ONGOING
                    thumbnail_url = "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEggWB9vWPMqjEvIoDsJSO29OmW-srULDQD3cS9HJ8cDk0vq2jLwDerUX-i61CqmZf62eBVmWZwU5CgXi0p2lxhKrh2_nZum3p-k3q9QJ2uozove0QAbOKtbd1QPjytjrJc9UsL65X4BbFdgcicLDYubD9LgY1Kco8wyhDGm4YEOim8u1TL42gOFe16NaaEP/s3464/4D55C3C5-9168-4103-B45C-99B52B58B6A5.jpeg"
                    url = "/search/label/مانجا بوروتو/"
                    initialized = true
                },
            ),
            false,
        ),
    )!!

    // This can be called when the user refreshes the comic even if initialized is true
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    // Chapter

    private fun chapterNextPageSelector() = "#Blog1 > div.iPostsNavigation > button[data-load]"
    private fun chapterListSelector() = "#Blog1 article.blog-post.index-post"

    override fun chapterListParse(response: Response): List<SChapter> {
        val allElements = mutableListOf<Element>()
        var document = response.asJsoup()

        while (true) {
            val pageChapters = document.select(chapterListSelector())
            if (pageChapters.isEmpty()) break

            allElements += pageChapters

            val nextButton = document.selectFirst(chapterNextPageSelector()) ?: break
            val nextUrl = nextButton.absUrl("data-load")

            document = client.newCall(GET(nextUrl, headers))
                .execute()
                .asJsoup()
        }

        return allElements.map(::chapterFromElement)
    }

    private val chapterFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

    private fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            val anchor = element.select("div.iPostInfoWrap > h3 > a")
            val timeElement = element.select("div.iPostInfoWrap time")

            name = anchor.text().trim()
            setUrlWithoutDomain(anchor.attr("href"))

            timeElement.attr("datetime").let { rawDate -> date_upload = parseChapterDate(rawDate) }
        }
    }

    private fun parseChapterDate(date: String): Long =
        chapterFormat.tryParse(date)

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pageUrl = response.request.url.toString()

        return document.select("div#post-body img").mapIndexed { index, element ->
            val imageUrl = when {
                element.hasAttr("data-src") -> element.absUrl("data-src")
                else -> element.absUrl("src")
            }

            Page(
                index = index,
                url = pageUrl,
                imageUrl = imageUrl,
            )
        }
    }

    override fun popularMangaRequest(page: Int): Request =
        throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw Exception("No search")
    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: Response): SManga =
        throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException()
}

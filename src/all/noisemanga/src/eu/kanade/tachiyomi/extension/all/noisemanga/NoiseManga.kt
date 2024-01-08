package eu.kanade.tachiyomi.extension.all.noisemanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.concurrent.TimeUnit

abstract class NoiseManga(override val lang: String) : ParsedHttpSource() {

    override val name = "NOISE"

    override val baseUrl = "https://noisemanga.com"

    override val supportsLatest = false

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "ul#menu-home li a[title=\"Séries\"] + ul li a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.text()
        setUrlWithoutDomain(element.attr("href"))
        thumbnail_url = baseUrl + SLUG_TO_DETAILS_MAP[url]?.thumbnail_url
    }

    override fun popularMangaNextPageSelector(): String? = null

    /**
     * Since there are only three series, it's worth to do a client-side search.
     */
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return super.fetchSearchManga(page, query, filters)
            .map {
                val mangas = it.mangas.filter { m -> m.title.contains(query, true) }
                MangasPage(mangas, it.hasNextPage)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(page)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(document: Document): SManga {
        val mainContent = document.select("div.main-content-page").first()!!
        val entryContent = mainContent.select("div.entry-content").first()!!
        val descriptionSelector = if (lang == "en") "h4 + h4, h4 + div h4" else "h1 + h4"
        val mangaSlug = document.location().replace(baseUrl, "")

        return SManga.create().apply {
            title = mainContent.select("header h1.single-title").first()!!.text()
            author = SLUG_TO_DETAILS_MAP[mangaSlug]?.author
            artist = SLUG_TO_DETAILS_MAP[mangaSlug]?.artist
            status = SManga.ONGOING
            description = entryContent.select(descriptionSelector).last()!!.text()
            thumbnail_url = baseUrl + SLUG_TO_DETAILS_MAP[mangaSlug]?.thumbnail_url
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterListSelector(): String {
        val columnSelector = if (lang == "pt-BR") 1 else 2

        return "div.entry-content div table tr td:nth-child($columnSelector) a"
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.text()
        scanlator = "NOISE Manga"
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.single-content div.single-entry-summary img.aligncenter")
            .mapIndexed { i, element ->
                val imgUrl = element.attr("srcset")
                    .substringAfterLast(", ")
                    .substringBeforeLast(" ")
                Page(i, "", imgUrl)
            }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36"

        /**
         * There isn't a good title list available with all the information.
         * Since the service does only have three series, it's worth to manually
         * add the missing information, such as artist, author, and thumbnail.
         */
        private val SLUG_TO_DETAILS_MAP = mapOf(
            "/quack/" to SManga.create().apply {
                artist = "Kaji Pato"
                author = "Kaji Pato"
                thumbnail_url = "/wp-content/uploads/2019/11/quack1.jpg"
            },
            "/japow/" to SManga.create().apply {
                artist = "Eduardo Capelo"
                author = "Jun Sugiyama"
                thumbnail_url = "/wp-content/uploads/2019/11/JAPOW_000_NOISE_0000.jpg"
            },
            "/tools-challenge/" to SManga.create().apply {
                artist = "Max Andrade"
                author = "Max Andrade"
                thumbnail_url = "/wp-content/uploads/2019/11/TC_001_NOISE_0000-1.jpg"
            },
        )
    }
}

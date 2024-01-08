package eu.kanade.tachiyomi.extension.all.hennojin

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Hennojin(override val lang: String, suffix: String) : ParsedHttpSource() {
    override val baseUrl = "https://hennojin.com/home/$suffix"

    override val name = "Hennojin"

    // Popular is latest
    override val supportsLatest = false

    private val httpUrl by lazy { baseUrl.toHttpUrl() }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int) =
        popularMangaRequest(page)

    override fun latestUpdatesFromElement(element: Element) =
        popularMangaFromElement(element)

    override fun popularMangaSelector() = ".grid-items .layer-content"

    override fun popularMangaNextPageSelector() = ".paginate .next"

    override fun popularMangaRequest(page: Int) =
        httpUrl.request { addEncodedPathSegments("page/$page") }

    override fun popularMangaFromElement(element: Element) =
        SManga.create().apply {
            element.selectFirst(".title_link > a").let {
                title = it!!.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.selectFirst("img")!!.attr("src")
        }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        httpUrl.request {
            addEncodedPathSegments("page/$page")
            addQueryParameter("keyword", query)
            addQueryParameter("_wpnonce", WP_NONCE)
        }

    override fun searchMangaFromElement(element: Element) =
        popularMangaFromElement(element)

    override fun mangaDetailsRequest(manga: SManga) =
        GET("https://hennojin.com" + manga.url, headers)

    override fun mangaDetailsParse(document: Document) =
        SManga.create().apply {
            description = document.selectFirst(
                ".manga-subtitle + p + p",
            )?.html()?.replace("<br> ", "\n")
            genre = document.select(
                ".tags-list a[href*=/parody/]," +
                    ".tags-list a[href*=/tags/]," +
                    ".tags-list a[href*=/character/]",
            )?.joinToString { it.text() }
            artist = document.select(
                ".tags-list a[href*=/artist/]",
            )?.joinToString { it.text() }
            author = document.select(
                ".tags-list a[href*=/group/]",
            )?.joinToString { it.text() } ?: artist
            status = SManga.COMPLETED
        }

    override fun fetchChapterList(manga: SManga) =
        Request.Builder().url(manga.thumbnail_url!!)
            .head().build().run(client::newCall)
            .asObservableSuccess().map { res ->
                SChapter.create().apply {
                    name = "Chapter"
                    url = manga.reader
                    date_upload = res.date
                    chapter_number = -1f
                }.let(::listOf)
            }!!

    override fun pageListRequest(chapter: SChapter) =
        GET("https://hennojin.com" + chapter.url, headers)

    override fun pageListParse(document: Document) =
        document.select(".slideshow-container > img")
            .mapIndexed { idx, img -> Page(idx, "", img.absUrl("src")) }

    private inline fun HttpUrl.request(
        block: HttpUrl.Builder.() -> HttpUrl.Builder,
    ) = GET(newBuilder().block().toString(), headers)

    private inline val Response.date: Long
        get() = headers["Last-Modified"]?.run(httpDate::parse)?.time ?: 0L

    private inline val SManga.reader: String
        get() = "/home/manga-reader/?manga=$title&view=multi"

    override fun chapterListSelector() =
        throw UnsupportedOperationException("Not used")

    override fun chapterFromElement(element: Element) =
        throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException("Not used")

    companion object {
        // Let's hope this doesn't change
        private const val WP_NONCE = "40229f97a5"

        private val httpDate by lazy {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
        }
    }
}

package eu.kanade.tachiyomi.extension.all.hennojin

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import java.text.SimpleDateFormat
import java.util.Locale

class Hennojin(override val lang: String) : ParsedHttpSource() {
    override val baseUrl = "https://hennojin.com"

    override val name = "Hennojin"

    // Popular is latest
    override val supportsLatest = false

    private val httpUrl by lazy { "$baseUrl/home".toHttpUrl() }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int) =
        popularMangaRequest(page)

    override fun latestUpdatesFromElement(element: Element) =
        popularMangaFromElement(element)

    override fun popularMangaSelector() = ".grid-items .layer-content"

    override fun popularMangaNextPageSelector() = ".paginate .next"

    override fun popularMangaRequest(page: Int) =
        httpUrl.request {
            when (lang) {
                "ja" -> {
                    addEncodedPathSegments("page/$page/")
                    addQueryParameter("archive", "raw")
                }
                else -> addEncodedPathSegments("page/$page")
            }
        }

    override fun popularMangaFromElement(element: Element) =
        SManga.create().apply {
            element.selectFirst(".title_link > a").let {
                title = it!!.text()
                setUrlWithoutDomain(it.absUrl("href"))
            }
            thumbnail_url = element.selectFirst("img")?.absUrl("src")
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

    override fun mangaDetailsParse(document: Document) =
        SManga.create().apply {
            description = document.select(
                ".manga-subtitle + p + p",
            ).joinToString("\n") {
                it
                    .apply { select(Evaluator.Tag("br")).prepend("\\n") }
                    .text()
                    .replace("\\n", "\n")
                    .replace("\n ", "\n")
            }.trim()
            genre = document.select(
                ".tags-list a[href*=/parody/]," +
                    ".tags-list a[href*=/tags/]," +
                    ".tags-list a[href*=/character/]",
            ).joinToString { it.text() }
            artist = document.selectFirst(
                ".tags-list a[href*=/artist/]",
            )?.text()
            author = document.selectFirst(
                ".tags-list a[href*=/group/]",
            )?.text() ?: artist
            status = SManga.COMPLETED
        }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup(response.body.string())
        val date = document
            .selectFirst(".manga-thumbnail > img")
            ?.absUrl("src")
            ?.let { url ->
                Request.Builder()
                    .url(url)
                    .head()
                    .build()
                    .run(client::newCall)
                    .execute()
                    .date
            }
        return document.select("a:contains(Read Online)").map {
            SChapter.create().apply {
                setUrlWithoutDomain(
                    it
                        ?.absUrl("href")
                        ?.toHttpUrlOrNull()
                        ?.newBuilder()
                        ?.removeAllQueryParameters("view")
                        ?.addQueryParameter("view", "multi")
                        ?.build()
                        ?.toString()
                        ?: it.absUrl("href"),
                )
                name = "Chapter"
                date?.run { date_upload = this }
                chapter_number = -1f
            }
        }
    }

    override fun pageListParse(document: Document) =
        document.select(".slideshow-container > img")
            .mapIndexed { idx, img -> Page(idx, imageUrl = img.absUrl("src")) }

    private inline fun HttpUrl.request(
        block: HttpUrl.Builder.() -> HttpUrl.Builder,
    ) = GET(newBuilder().block().build(), headers)

    private inline val Response.date: Long
        get() = headers["Last-Modified"]?.run(httpDate::parse)?.time ?: 0L

    override fun chapterListSelector() =
        throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) =
        throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException()

    companion object {
        // Let's hope this doesn't change
        private const val WP_NONCE = "40229f97a5"

        private val httpDate by lazy {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
        }
    }
}

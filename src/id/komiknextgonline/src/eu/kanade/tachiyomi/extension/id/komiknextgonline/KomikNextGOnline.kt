package eu.kanade.tachiyomi.extension.id.komiknextgonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class KomikNextGOnline : ParsedHttpSource() {
    override val name = "Komik Next G Online"

    override val baseUrl = "https://komiknextgonline.com"

    override val lang = "id"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)

    // ======================== Popular ========================
    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addQueryParameter("comics_paged", page.toString())
            }
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "#left-content ul#comic-list li.comic"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleElement = element.selectFirst(".comic-title, .entry-title")!!
        title = titleElement.text().replace(TITLE_PREFIX_REGEX, "").trim()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun popularMangaNextPageSelector() = "a.next.page-numbers"

    // ======================== Latest ========================
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException()

    // ======================== Search ========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            baseUrl.toHttpUrl().newBuilder().apply {
                if (page > 1) {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                }
                addQueryParameter("s", query)
            }.build()
        } else {
            val filter = filters.filterIsInstance<UriPartFilter>().firstOrNull()
            val filterUrl = if (filter != null && filter.state != 0) {
                "$baseUrl/${filter.toUriPart()}".toHttpUrl().newBuilder()
            } else {
                baseUrl.toHttpUrl().newBuilder()
            }

            filterUrl.apply {
                if (page > 1) {
                    addQueryParameter("comics_paged", page.toString())
                }
            }.build()
        }

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "${popularMangaSelector()}, #left-content article.comic"

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // ======================== Details ========================
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.entry-title")!!.text()
        author = document.selectFirst("span.byline")?.text()?.replace("by ", "")?.trim()
        description = document.selectFirst("article.post")?.text()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        thumbnail_url = document.selectFirst("meta[property=\"og:image\"]")?.attr("abs:content")
    }

    // ======================== Chapters ========================
    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val chapter = SChapter.create().apply {
            setUrlWithoutDomain(document.selectFirst("meta[property=\"og:url\"]")!!.attr("abs:content"))
            name = "Chapter 1"
            date_upload = document.selectFirst("span.posted-on a")?.text()?.let {
                dateFormat.tryParse(it.replace("Posted on ", "").trim())
            } ?: 0L
        }

        return listOf(chapter)
    }

    // ======================== Pages ========================
    override fun pageListParse(document: Document): List<Page> = document.select("div#spliced-comic img").mapIndexed { i, element ->
        Page(i, "", element.attr("abs:src"))
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList() = FilterList(
        Filter.Header("Filter akan diabaikan jika ada pencarian teks"),
        CategoryFilter(),
    )

    private class CategoryFilter :
        UriPartFilter(
            "Kategori",
            arrayOf(
                Pair("Semua", ""),
                Pair("Pendidikan", "pendidikan"),
                Pair("Persahabatan", "persahabatan"),
                Pair("Anak Islami", "anak-islami"),
                Pair("Horor dan Misteri", "horor-dan-misteri"),
            ),
        )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    companion object {
        private val TITLE_PREFIX_REGEX = Regex("""^#\d+\.\s*""")
    }
}

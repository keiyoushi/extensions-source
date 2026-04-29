package eu.kanade.tachiyomi.extension.id.komiknextgonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class KomikNextGOnline : HttpSource() {
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

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("#left-content ul#comic-list li.comic").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("a.next.page-numbers") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleElement = element.selectFirst(".comic-title, .entry-title")!!
        title = titleElement.text().replace(TITLE_PREFIX_REGEX, "")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    // ======================== Latest ========================
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

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
            val filter = filters.firstInstanceOrNull<UriPartFilter>()
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

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("#left-content ul#comic-list li.comic, #left-content article.comic").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("a.next.page-numbers") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ======================== Details ========================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.entry-title")!!.text()
            author = document.selectFirst("span.byline")?.text()?.replace("by ", "")
            description = document.selectFirst("article.post")?.text()
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            thumbnail_url = document.selectFirst("meta[property=\"og:image\"]")?.attr("abs:content")
        }
    }

    // ======================== Chapters ========================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val chapter = SChapter.create().apply {
            setUrlWithoutDomain(document.selectFirst("meta[property=\"og:url\"]")!!.attr("abs:content"))
            name = "Chapter 1"
            date_upload = document.selectFirst("span.posted-on a")?.text()?.let {
                dateFormat.tryParse(it.replace("Posted on ", ""))
            } ?: 0L
        }

        return listOf(chapter)
    }

    // ======================== Pages ========================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("div#spliced-comic img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList() = FilterList(
        Filter.Header("Filter akan diabaikan jika ada pencarian teks"),
        CategoryFilter(),
    )

    companion object {
        private val TITLE_PREFIX_REGEX = Regex("""^#\d+\.\s*""")
    }
}

package eu.kanade.tachiyomi.extension.en.mangabat

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Mangabat : MangaBox(
    "Mangabat",
    "https://www.mangabats.com",
    "en",
    SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH),
) {

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // =================== Popular =========================

    override val popularUrlPath = "manga-list/hot-manga?page="

    // =================== Latest =========================

    override val latestUrlPath = "manga-list/latest-manga?page="

    // =================== Search =========================

    override val simpleQueryPath = "search/story/"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return super.searchMangaRequest(page, query, filters)
        }

        val url = "$baseUrl/genre".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> filter.toUriPart()?.let(url::addPathSegment)
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // =================== Chapters =========================

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            element.selectFirst("span[title]")?.attr("title")?.let {
                date_upload = parseChapterDate(it, scanlator!!) ?: 0
            }
        }
    }

    // =================== Pages =========================

    override fun imageRequest(page: Page) =
        super.imageRequest(page)
            .newBuilder().headers(headersBuilder().build())
            .build()

    // =================== Filters =========================

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        GenreFilter(getGenreFilters()),
    )

    override fun getGenreFilters(): Array<Pair<String?, String>> =
        super.getGenreFilters().map { it.second.toPathSegment() to it.second }.toTypedArray()

    // =================== Utilities =========================

    private fun String.toPathSegment() = this.lowercase()
        .replace(SPACE_REGEX, "-")

    companion object {
        private val SPACE_REGEX = """\s+""".toRegex()
    }
}

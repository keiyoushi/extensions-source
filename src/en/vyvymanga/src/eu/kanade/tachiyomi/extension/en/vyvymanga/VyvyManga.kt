package eu.kanade.tachiyomi.extension.en.vyvymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class VyvyManga : HttpSource() {
    override val name = "VyvyManga"

    override val baseUrl = "https://vymanga.net"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MMM dd, yyy", Locale.US)
    private val relativeDateRegex = Regex("""(\d+)""")

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/search" + if (page != 1) "?page=$page" else "", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".comic-item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                title = element.selectFirst(".comic-title")!!.text()
                thumbnail_url = element.selectFirst(".comic-image img.image.lozad")?.absUrl("data-src")
            }
        }
        val hasNextPage = document.selectFirst("[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/search?sort=updated_at" + if (page != 1) "&page=$page" else "", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is SearchType -> url.addQueryParameter("search_po", filter.selected)
                is SearchDescription -> if (filter.state) url.addQueryParameter("check_search_desc", "1")
                is AuthorSearchType -> url.addQueryParameter("author_po", filter.selected)
                is AuthorFilter -> url.addQueryParameter("author", filter.state)
                is StatusFilter -> url.addQueryParameter("completed", filter.selected)
                is SortFilter -> url.addQueryParameter("sort", filter.selected)
                is SortType -> url.addQueryParameter("sort_type", filter.selected)
                is GenreFilter -> {
                    filter.state.forEach {
                        if (!it.isIgnored()) url.addQueryParameter(if (it.isIncluded()) "genre[]" else "exclude_genre[]", it.id)
                    }
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            artist = document.selectFirst(".pre-title:contains(Artist) ~ a")?.text()
            author = document.selectFirst(".pre-title:contains(Author) ~ a")?.text()
            description = document.selectFirst(".summary > .content")?.text()
            genre = document.select(".pre-title:contains(Genres) ~ a").joinToString { it.text() }
            status = when (document.selectFirst(".pre-title:contains(Status) ~ span:not(.space)")?.text()) {
                "Ongoing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = document.selectFirst(".img-manga img")?.absUrl("src")
        }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".list-group > a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst("span")!!.text()
                date_upload = parseChapterDate(element.selectFirst("> p")?.text())
            }
        }
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img.d-block").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("data-src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Date parsing
    private fun parseChapterDate(date: String?): Long {
        date ?: return 0L

        return when {
            date.endsWith("ago") -> parseRelativeDate(date)
            else -> dateFormat.tryParse(date)
        }
    }

    private fun parseRelativeDate(date: String): Long {
        val number = relativeDateRegex.find(date)?.value?.toIntOrNull() ?: return 0L
        val cal = Calendar.getInstance()

        return when {
            date.contains("day") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("hour") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("minute") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            date.contains("second") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            else -> 0L
        }
    }

    // Filters
    override fun getFilterList(): FilterList {
        launchIO { fetchGenres(baseUrl, headers, client) }
        return FilterList(
            SearchType(),
            SearchDescription(),
            AuthorSearchType(),
            AuthorFilter(),
            StatusFilter(),
            SortFilter(),
            SortType(),
            GenreFilter(),
        )
    }
}

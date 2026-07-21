package eu.kanade.tachiyomi.extension.en.vyvymanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@Source
abstract class VyvyManga : KeiSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("MMM dd, yyy", Locale.US)
    private val relativeDateRegex = Regex("""(\d+)""")

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/search" + if (page != 1) "?page=$page" else ""
        return parseMangasPage(client.get(url))
    }

    private fun parseMangasPage(response: Response): MangasPage {
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
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/search?sort=updated_at" + if (page != 1) "&page=$page" else ""
        return parseMangasPage(client.get(url))
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        return parseMangasPage(client.get(url.build()))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size < 2) {
            return null
        }

        val mangaUrl = "/manga/${url.pathSegments[1]}"
        val manga = SManga.create().apply {
            this.url = mangaUrl
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
                this.url = mangaUrl
            }
    }

    // Updates
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()

        val manga = SManga.create().apply {
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

        val chapters = document.select(".list-group > a").map { element ->
            SChapter.create().apply {
                url = element.absUrl("href")
                name = element.selectFirst("span")!!.text()
                date_upload = parseChapterDate(element.selectFirst("> p")?.text())
            }
        }

        return SMangaUpdate(manga, chapters)
    }

    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.startsWith("http")) {
        chapter.url
    } else {
        baseUrl + chapter.url
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (!chapter.url.startsWith("http")) error("Refresh to reload chapters")

        val document = client.get(chapter.url).asJsoup()
        return document.select("img.d-block").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("data-src"))
        }
    }

    // Date parsing
    private fun parseChapterDate(date: String?): Long {
        date ?: return 0L

        return when {
            date.endsWith("ago") -> parseRelativeDate(date)
            else -> runCatching {
                LocalDate.parse(date, dateFormat).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrDefault(0L)
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
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/search").asJsoup()

        return document.select(".check-genre div div:has(.checkbox-genre)").map {
            GenreData(
                it.select("label").text(),
                it.select(".checkbox-genre").attr("data-value"),
            )
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreData>>()?.map { it.toGenre() } ?: emptyList()

        return FilterList(
            SearchType(),
            SearchDescription(),
            AuthorSearchType(),
            AuthorFilter(),
            StatusFilter(),
            SortFilter(),
            SortType(),
            GenreFilter(genres),
        )
    }
}

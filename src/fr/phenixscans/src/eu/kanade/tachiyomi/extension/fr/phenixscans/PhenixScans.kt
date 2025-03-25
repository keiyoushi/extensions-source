package eu.kanade.tachiyomi.extension.fr.phenixscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.float
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread

class PhenixScans : HttpSource() {
    override val baseUrl = "https://phenix-scans.com"
    private val apiBaseUrl = "https://api.phenix-scans.com"
    override val lang = "fr"
    override val name = "Phenix Scans"
    override val supportsLatest = true
    override val versionId = 2

    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.FRENCH)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val apiUrl = "$apiBaseUrl/front/homepage?section=top"

        return GET(apiUrl, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<TopMangaDto>()

        val mangas = data.top.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = "$apiBaseUrl/${it.coverImage}" // Possibility of using ?width=75 and cdn.[...]/?url=
                url = it.slug
            }
        }

        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val apiUrl = "$apiBaseUrl/front/homepage?page=$page&section=latest&limit=12"

        return GET(apiUrl, headers)
    }

    private fun parseMangaList(mangaList: List<LatestMangaItemDto>): List<SManga> {
        return mangaList.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = "$apiBaseUrl/${it.coverImage}" // Possibility of using ?width=75
                url = it.slug
            }
        }
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<LatestMangaDto>()

        val mangas = parseMangaList(data.latest)

        val hasNextPage = data.pagination.currentPage < data.pagination.totalPages

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            // No limits here
            val apiUrl = "$apiBaseUrl/front/manga/search".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .build()
            return GET(apiUrl, headers)
        }

        val url = "$apiBaseUrl/front/manga".toHttpUrl().newBuilder()
        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    url.addQueryParameter("sort", filter.toUriPart())
                }
                is GenreFilter -> {
                    val genres = filter.state
                        .filter { it.state }
                        .map { it.id }

                    url.addQueryParameter("genre", genres.joinToString(","))
                }
                is TypeFilter -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is StatusFilter -> {
                    url.addQueryParameter("status", filter.toUriPart())
                }
                else -> {}
            }
        }
        url.addQueryParameter("limit", "18") // Be cool on the API
        url.addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SearchResultsDto>()

        val hasNextPage = (data.pagination?.page ?: 0) < (data.pagination?.totalPages ?: 0)

        val mangas = parseMangaList(data.mangas)

        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Sorting & Filtering ==========================

    private class SortFilter : UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Alphabetic", "title"),
            Pair("Rating", "rating"),
            Pair("Last updated", "updatedAt"),
            Pair("Chapter number", "chapters"),
        ),
    )

    class Tag(name: String, val id: String) : Filter.CheckBox(name)

    private class GenreFilter(genres: List<Tag>) : Filter.Group<Tag>(
        "Genres",
        genres,
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All status", ""),
            Pair("Ongoing", "Ongoing"),
            Pair("On Hiatus", "Hiatus"),
            Pair("Completed", "Completed"),
        ),
    )

    private class TypeFilter : UriPartFilter(
        "Type",
        arrayOf(
            Pair("Any type", ""),
            Pair("Manga", "Manga"),
            Pair("Manhwa", "Manhwa"),
            Pair("Manhua", "Manhua"),
        ),
    )

    override fun getFilterList(): FilterList {
        fetchFilters()
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("Filters are not compatible with text-based search"),
            Filter.Separator(),

            Filter.Header("Type"),
            TypeFilter(),
            Filter.Separator(),

            Filter.Header("Sort by"),
            SortFilter(),
            Filter.Separator(),

            Filter.Header("Status"),
            StatusFilter(),
            Filter.Separator(),
        )

        if (filtersState == FiltersState.FETCHED) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Filter by genres"),
                GenreFilter(genresList),
            )
        } else {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Click on 'Reset' to load missing filters"),
            )
        }

        return FilterList(filters)
    }

    private var genresList: List<Tag> = emptyList()
    private var fetchFiltersAttempts = 0
    private var filtersState = FiltersState.NOT_FETCHED

    private fun fetchFilters() {
        if (filtersState != FiltersState.NOT_FETCHED || fetchFiltersAttempts >= 3) return
        filtersState = FiltersState.FETCHING
        fetchFiltersAttempts++
        thread {
            try {
                val response = client.newCall(GET("$apiBaseUrl/genres", headers)).execute()
                val filters = response.parseAs<GenreListDto>()

                genresList = filters.data.map { Tag(it.name, it.id) }

                filtersState = FiltersState.FETCHED
            } catch (e: Throwable) {
                filtersState = FiltersState.NOT_FETCHED
            }
        }
    }

    open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }

    // =============================== Manga ==================================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val apiUrl = "$apiBaseUrl/front/manga/${manga.url}"

        return GET(apiUrl, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<MangaDetailDto>()

        return SManga.create().apply {
            title = data.manga.title
            thumbnail_url = "$apiBaseUrl/${data.manga.coverImage}"
            url = data.manga.slug
            description = data.manga.synopsis
            status = when (data.manga.status) {
                "Ongoing" -> SManga.ONGOING
                "Hiatus" -> SManga.ON_HIATUS
                "Completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/manga/${manga.url}"
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<MangaDetailDto>()

        return data.chapters.map {
            SChapter.create().apply {
                chapter_number = it.number.float
                date_upload = simpleDateFormat.tryParse(it.createdAt)
                name = "Chapter ${it.number}"
                url = "${data.manga.slug}/${it.number}"
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.url.substringBeforeLast("/")
        val chapterNumber = chapter.url.substringAfterLast("/")
        return "$baseUrl/manga/$slug/chapitre/$chapterNumber"
    }

    // =============================== Pages ================================

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val slug = chapter.url.substringBeforeLast("/")
        val chapterNumber = chapter.url.substringAfterLast("/")

        val apiUrl = "$apiBaseUrl/front/manga/$slug/chapter/$chapterNumber"

        return GET(apiUrl, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ChapterContentDto>()

        return data.chapter.images.mapIndexed { index, url ->
            Page(index, imageUrl = "$apiBaseUrl/$url")
        }
    }
}

package eu.kanade.tachiyomi.extension.fr.phenixscans

import android.util.Log
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
import java.util.*
import kotlin.concurrent.thread

class PhenixScans : HttpSource() {
    override val baseUrl = "https://phenix-scans.com"
    val apiBaseUrl = baseUrl.replace("https://", "https://api.")
    override val lang = "fr"
    override val name = "Phenix Scans"
    override val supportsLatest = true

    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.FRENCH)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiBaseUrl/front/homepage?section=top"

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<TopResponse>()

        val mangas = data.top.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = "$apiBaseUrl/${it.coverImage}" // Possibility of using ?width=75
                url = "$baseUrl/manga/${it.slug}"
            }
        }

        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("front").addPathSegment("homepage")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("section", "latest")
            .addQueryParameter("limit", "12")
            .build()

        Log.e("PhenixScans", url.toString())

        return GET(url.toString(), headers)
    }

    private fun parseMangaList(mangaList: List<LatestManga>): List<SManga> {
        return mangaList.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = "$apiBaseUrl/${it.coverImage}" // Possibility of using ?width=75
                url = "$baseUrl/manga/${it.slug}"
            }
        }
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<LatestMangaResponse>()

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

        Log.e("PhenixScans", "Search url: ${url.build()}")

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SearchResultDto>()

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
                val filters = response.parseAs<GenresDto>()

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
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("front").addPathSegment("manga")
            .addPathSegment(manga.url.substringAfterLast("manga/"))
            .build()

        Log.e("PhenixScans", url.toString())

        return GET(url.toString(), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<MangaDetailResponse>()

        return SManga.create().apply {
            title = data.manga.title
            thumbnail_url = "$apiBaseUrl/${data.manga.coverImage}"
            url = "/manga/${data.manga.slug}"
            description = data.manga.synopsis
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<MangaDetailResponse>()

        return data.chapters.map {
            SChapter.create().apply {
                chapter_number = it.number.float
                date_upload = simpleDateFormat.tryParse(it.createdAt)
                name = "Chapter ${it.number}"
                url = "/manga/${data.manga.slug}/chapitre/${it.number}"
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    // =============================== Pages ================================

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val endpoint = chapter.url.substringAfterLast("manga/")
            .replace("/chapitre/", "/chapter/")
        val url = "$apiBaseUrl/front/manga/$endpoint"

        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ChapterReadingResponse>()

        return data.chapter.images.mapIndexed { index, url ->
            Page(index, imageUrl = "$apiBaseUrl/$url")
        }
    }
}

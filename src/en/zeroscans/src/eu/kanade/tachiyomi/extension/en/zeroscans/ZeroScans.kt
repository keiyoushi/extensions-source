package eu.kanade.tachiyomi.extension.en.zeroscans

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.Single
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy

class ZeroScans : HttpSource() {

    override val name: String = "Zero Scans"

    override val lang: String = "en"

    override val baseUrl: String = "https://zscans.com"

    override val supportsLatest: Boolean = true

    private val json: Json by injectLazy()

    private var comicList: List<ZeroScansComicDto> = emptyList()

    private lateinit var rankings: ZeroScansRankingsDto

    private val zsHelper = ZeroScansHelper()

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        if (page == 1) runCatching { updateComicsData() }
        return super.fetchLatestUpdates(page)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/$API_PATH/new-chapters")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val newChapters = response.parseAs<NewChaptersResponseDto>()

        val titlesList = newChapters.all.mapNotNull {
            comicList.firstOrNull { comic -> comic.slug == it.slug }
        }.map { comic -> zsHelper.zsComicEntryToSManga(comic) }

        return MangasPage(titlesList, false)
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return fetchSearchManga(page, query = "", filters = getFilterList())
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (page == 1) runCatching { updateComicsData() }

        filters.filterIsInstance<RankingsFilter>().firstOrNull()?.let { rankingFilter ->
            val type = rankingList[rankingFilter.state!!.index].type
            val ascending = rankingFilter.state!!.ascending
            getRankingsIfNeeded(type, ascending)?.let {
                return Observable.just(MangasPage(it, false))
            }
        }

        var filteredComics = comicList

        if (query.isNotBlank()) {
            filteredComics = filteredComics.filter { it.name.contains(query, ignoreCase = true) }
        }

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> {
                    filteredComics = filteredComics.filter { zsHelper.checkStatusFilter(filter, it) }
                }
                is GenreFilter -> {
                    filteredComics = filteredComics.filter { zsHelper.checkGenreFilter(filter, it) }
                }
                is SortFilter -> {
                    filter.state?.let {
                        val type = sortList[it.index].type
                        val ascending = it.ascending
                        filteredComics = zsHelper.applySortFilter(type, ascending, filteredComics)
                    }
                }
                else -> { /* Do Nothing */ }
            }
        }

        // Get 20 comics at a time
        val chunkedFilteredComics = filteredComics.chunked(20)
        if (chunkedFilteredComics.isEmpty()) {
            return Observable.just(MangasPage(emptyList(), false))
        }

        val comics = chunkedFilteredComics[page - 1].map { comic -> zsHelper.zsComicEntryToSManga(comic) }
        val hasNextPage = page < chunkedFilteredComics.size

        return Observable.just(MangasPage(comics, hasNextPage))
    }

    private fun getRankingsIfNeeded(type: String?, ascending: Boolean): List<SManga>? {
        if (type.isNullOrBlank()) return null

        val rankingEntries = when (type) {
            "weekly" -> {
                if (!ascending) {
                    rankings.weekly.reversed()
                } else {
                    rankings.weekly
                }
            }
            "monthly" -> {
                if (!ascending) {
                    rankings.monthly.reversed()
                } else {
                    rankings.monthly
                }
            }
            else -> {
                if (!ascending) {
                    rankings.allTime.reversed()
                } else {
                    rankings.allTime
                }
            }
        }

        val titlesList = rankingEntries.mapNotNull { rankingEntry ->
            comicList.firstOrNull { rankingEntry.slug == it.slug }
        }.map { comic -> zsHelper.zsComicEntryToSManga(comic) }

        return titlesList
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        runCatching { updateComicsData() }
        val mangaSlug = "$baseUrl${manga.url}".toHttpUrl().pathSegments[1]

        try {
            val comic = comicList.first { comic -> comic.slug == mangaSlug }
                .let { zsHelper.zsComicEntryToSManga(it) }

            return Observable.just(comic)
        } catch (e: NoSuchElementException) {
            throw Exception("Migrate from Zero Scans to Zero Scans")
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        // Paginated fragile endpoint so explicit rateLimit
        val chapterListClient = client.newBuilder().rateLimit(1, 2).build()
        val zsChapters = mutableListOf<ZeroScansChapterDto>()
        var page = 0
        var hasMoreResult = true
        try {
            while (hasMoreResult) {
                page++
                val newResponse = chapterListClient.newCall(zsChapterListRequest(page, manga)).execute()
                val newZSChapterPage = zsChapterListParse(newResponse)
                zsChapters.addAll(newZSChapterPage.chapters)
                hasMoreResult = newZSChapterPage.hasNextPage
            }

            zsChapters.map { it.toSChapter(manga) }.let {
                return Observable.just(it)
            }
        } catch (e: Exception) {
            Log.e("Zero Scans", "Error parsing chapter list", e)
            throw(e)
        }
    }

    private fun zsChapterListRequest(page: Int, manga: SManga): Request {
        val mangaId = "$baseUrl${manga.url}".toHttpUrl().queryParameter("id")
        return GET("$baseUrl/$API_PATH/comic/$mangaId/chapters?sort=desc&page=$page")
    }

    private fun zsChapterListParse(response: Response): ZeroScansChapterPage {
        return response.parseAs<ZeroScansResponseDto<ZeroScansChaptersResponseDto>>()
            .data.let {
                ZeroScansChapterPage(
                    it.data,
                    it.currentPage < it.lastPage,
                )
            }
    }

    class ZeroScansChapterPage(
        val chapters: List<ZeroScansChapterDto>,
        val hasNextPage: Boolean,
    )

    private fun ZeroScansChapterDto.toSChapter(manga: SManga): SChapter {
        val comicSlug = "$baseUrl${manga.url}".toHttpUrl().pathSegments[1]
        val zsChapter = this
        return SChapter.create().apply {
            name = "Chapter ${zsChapter.name}"
            scanlator = zsChapter.group
            chapter_number = zsChapter.name.toFloat()
            date_upload = zsHelper.parseChapterUploadDate(zsChapter.createdAt)
            url = "/comics/$comicSlug/${zsChapter.id}"
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrlPaths = "$baseUrl${chapter.url}".toHttpUrl().pathSegments
        val mangaSlug = chapterUrlPaths[1]
        val chapterId = chapterUrlPaths[2]
        return GET("$baseUrl/$API_PATH/comic/$mangaSlug/chapters/$chapterId")
    }

    override fun pageListParse(response: Response): List<Page> {
        val allQualityZSPages = response.parseAs<ZeroScansResponseDto<ZeroScansPageResponseDto>>().data.chapter

        val highResZSPages = allQualityZSPages.highQuality.takeIf { it.isNotEmpty() } ?: allQualityZSPages.goodQuality
        val pages = highResZSPages.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }

        return pages
    }

    // Fetch Comics, Genres, Statuses and Rankings on creating source
    init {
        Single.fromCallable {
            runCatching { updateComicsData() }
        }.subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe({}, {})
    }

    // Filters
    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        if (genreList.isNotEmpty()) {
            filters.add(GenreFilter(genreList))
        }
        if (statusList.isNotEmpty()) {
            filters.add(StatusFilter(statusList))
        }
        filters += listOf(
            SortFilter(sortList),
            RankingsHeader(),
            RankingsHeader2(),
            RankingsFilter(rankingList),
        )

        return FilterList(filters)
    }

    class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)
    class Genre(name: String, val id: Int) : Filter.TriState(name)

    private var genreList: List<Genre> = emptyList()

    class StatusFilter(statuses: List<Status>) : Filter.Group<Status>("Status", statuses)
    class Status(name: String, val id: Int) : Filter.TriState(name)

    private var statusList: List<Status> = emptyList()

    class SortFilter(sorts: List<ZeroScans.Sort>) :
        Filter.Sort("Sort by", sorts.map { it.name }.toTypedArray(), Selection(3, false))

    class Sort(val name: String, val type: String)

    private val sortList = listOf(
        Sort("Alphabetic", "alphabetic"),
        Sort("Rating", "rating"),
        Sort("Chapter Count", "chapter_count"),
        Sort("Bookmark Count", "bookmark_count"),
        Sort("View Count", "view_count"),
    )

    class RankingsHeader :
        Filter.Header("Note: Genre, Sort, Status filter and Search query")
    class RankingsHeader2 :
        Filter.Header("are not applied to rankings")

    class RankingsFilter(rankings: List<Ranking>) :
        Filter.Sort("Rankings", rankings.map { it.name }.toTypedArray(), Selection(0, false))

    class Ranking(val name: String, val type: String? = null)

    private val rankingList = listOf(
        Ranking("None"),
        Ranking("All Time", "all-time"),
        Ranking("Weekly", "weekly"),
        Ranking("Monthly", "monthly"),
    )

    // Helpers
    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(it.body.string())
    }

    private fun comicsDataRequest(): Request {
        return GET("$baseUrl/$API_PATH/comics")
    }

    private fun comicsDataParse(response: Response): ZeroScansComicsDataDto {
        return response.parseAs<ZeroScansResponseDto<ZeroScansComicsDataDto>>().data
    }

    private fun updateComicsData() {
        val response = client.newCall(comicsDataRequest()).execute()
        comicsDataParse(response).let {
            genreList = it.genres.map { genreDto ->
                Genre(genreDto.name, genreDto.id)
            }
            statusList = it.statuses.map { statusDto ->
                Status(statusDto.name, statusDto.id)
            }
            comicList = it.comics
            rankings = it.rankings
        }
    }

    // Unused Stuff
    override fun imageUrlParse(response: Response): String = ""

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    companion object {
        const val API_PATH = "swordflake"
    }
}

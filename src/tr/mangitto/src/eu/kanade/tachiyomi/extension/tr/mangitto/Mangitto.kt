package eu.kanade.tachiyomi.extension.tr.mangitto

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class Mangitto : HttpSource() {

    override val name = "Mangitto"

    override val baseUrl = "https://mangtto.com"

    override val lang = "tr"

    override val supportsLatest = true

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/manga/trends?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<MangttoResponse<MangttoTrendsData>>().data
        val mangas = data.mangas.map { it.toSManga() }

        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/manga/last-added?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<MangttoResponse<MangttoLatestData>>().data

        // The API returns a list of chapters, so we distinct by slug to avoid duplicates.
        val mangas = data.chapters.distinctBy { it.slug }.map { it.toSManga() }
        val hasNextPage = response.request.url.queryParameter("page")?.toIntOrNull()?.let {
            it < data.pages
        } ?: false

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/manga/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("q", query)

        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        if (genreFilter != null) {
            url.addQueryParameter("genre", genreFilter.getQuery())
        }

        val adultFilter = filters.firstInstanceOrNull<AdultFilter>()
        url.addQueryParameter("isAdult", adultFilter?.state?.toString() ?: "false")

        val completedFilter = filters.firstInstanceOrNull<CompletedFilter>()
        if (completedFilter?.state == true) {
            url.addQueryParameter("isFinished", "true")
        }

        val scoreFilter = filters.firstInstanceOrNull<ScoreFilter>()
        url.addQueryParameter("meanScore", scoreFilter?.state?.takeIf { it.isNotEmpty() } ?: "0")

        val dateFilter = filters.firstInstanceOrNull<DateFilter>()
        url.addQueryParameter("releaseDate", dateFilter?.state?.takeIf { it.isNotEmpty() } ?: "0")

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<MangttoResponse<MangttoSearchData>>().data
        val mangas = data.hits.map { it.toSManga() }

        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage = (currentPage * data.limit) < data.estimatedTotalHits

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/api/manga/${manga.url}", headers)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangttoResponse<MangttoDetailData>>().data.toSManga()

    // ============================= Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val allChapters = mutableListOf<SChapter>()
        var page = 1
        var hasNextPage = true

        while (hasNextPage) {
            val request = GET("$baseUrl/api/manga/${manga.url}/chapters?page=$page", headers)
            val response = client.newCall(request).execute()
            val data = response.parseAs<MangttoResponse<MangttoChapterPageData>>().data

            allChapters.addAll(data.chapters.map { it.toSChapter(manga.url) })

            hasNextPage = page < data.pages
            page++
        }

        allChapters
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.url}"

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/api/manga/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<MangttoResponse<MangttoPageData>>().data
        val staticInfo = data.chapter.static.firstOrNull() ?: return emptyList()
        val pathSegments = response.request.url.pathSegments
        val mangaSlug = pathSegments[pathSegments.size - 2]
        val chapterStr = data.chapter.chapter.toString().removeSuffix(".0")

        return (1..staticInfo.fileSize).mapIndexed { index, pageNum ->
            Page(index, imageUrl = "https://cdn.zukrein.com/$mangaSlug/$chapterStr/$pageNum-${staticInfo.fansubId}.jpeg")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        GenreFilter(),
        AdultFilter(),
        CompletedFilter(),
        ScoreFilter(),
        DateFilter(),
    )
}

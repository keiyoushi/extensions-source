package eu.kanade.tachiyomi.extension.tr.mangitto

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

@Source
abstract class Mangitto : HttpSource() {

    override val supportsLatest = true

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/manga/populer?skip=${(page - 1) * PAGE_SIZE}&take=$PAGE_SIZE", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<MangttoPopularData>()
        val mangas = data.mangas.map { it.toSManga() }
        val skip = response.request.url.queryParameter("skip")?.toIntOrNull() ?: 0

        return MangasPage(mangas, skip + data.mangas.size < data.total)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/manga/latest?skip=${(page - 1) * PAGE_SIZE}&take=$PAGE_SIZE", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<MangttoLatestData>()

        // The API returns a list of chapters, so we distinct by slug to avoid duplicates.
        val mangas = data.chapters.map { it.manga }.distinctBy { it.slug }.map { it.toSManga() }
        val skip = response.request.url.queryParameter("skip")?.toIntOrNull() ?: 0

        return MangasPage(mangas, skip + data.chapters.size < data.total)
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/manga/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("q", query)

        filters.firstInstanceOrNull<GenreFilter>()?.getQuery()
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("genres", it) }

        if (filters.firstInstanceOrNull<AdultFilter>()?.state == true) {
            url.addQueryParameter("isAdult", "true")
        }

        if (filters.firstInstanceOrNull<CompletedFilter>()?.state == true) {
            url.addQueryParameter("isFinished", "true")
        }

        filters.firstInstanceOrNull<ScoreFilter>()?.state
            ?.takeIf { it.isNotBlank() }
            ?.let { url.addQueryParameter("meanScore", it.trim()) }

        filters.firstInstanceOrNull<DateFilter>()?.state
            ?.takeIf { it.isNotBlank() }
            ?.let { url.addQueryParameter("releaseDate", it.trim()) }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<MangttoSearchData>()
        val mangas = data.hits.map { it.document.toSManga() }

        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage = (currentPage * SEARCH_PAGE_SIZE) < data.estimatedTotalHits

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/api/manga/${manga.url}", headers)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangttoDetailData>().toSManga()

    // ============================= Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val allChapters = mutableListOf<SChapter>()
        var skip = 0

        while (true) {
            val request = GET("$baseUrl/api/manga/${manga.url}/chapters?skip=$skip&take=$PAGE_SIZE", headers)
            val data = client.newCall(request).execute().parseAs<MangttoChapterPageData>()

            allChapters.addAll(data.chapters.map { it.toSChapter(manga.url) })
            skip += data.chapters.size

            if (data.chapters.isEmpty() || skip >= data.total) break
        }

        // The API returns chapters in ascending order.
        allChapters.reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.url}"

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/api/manga/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<MangttoPageData>()
        val upload = data.uploads.firstOrNull() ?: return emptyList()
        val pathSegments = response.request.url.pathSegments
        val mangaSlug = pathSegments[pathSegments.size - 2]
        val chapterStr = pathSegments.last()

        return (1..upload.fileLength).map { pageNum ->
            Page(pageNum - 1, imageUrl = "${data.cdn}/manga/$mangaSlug/$chapterStr/$pageNum-${upload.fansubId}.webp")
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

    companion object {
        private const val PAGE_SIZE = 50
        private const val SEARCH_PAGE_SIZE = 42
    }
}

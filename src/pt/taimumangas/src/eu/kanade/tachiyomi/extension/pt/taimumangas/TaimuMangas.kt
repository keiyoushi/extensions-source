package eu.kanade.tachiyomi.extension.pt.taimumangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class TaimuMangas : HttpSource() {

    override val name = "TaimuMangas"

    override val baseUrl = "https://taimumangas.rzword.xyz"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = seriesListRequest(
        page = page,
        sortBy = "total_views",
    )

    override fun popularMangaParse(response: Response): MangasPage = seriesListParse(response)

    override fun latestUpdatesRequest(page: Int): Request = seriesListRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = seriesListParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = seriesListRequest(page, query.takeIf(String::isNotBlank), filters)

    override fun searchMangaParse(response: Response): MangasPage = seriesListParse(response)

    override fun getFilterList(): FilterList = getFilters()

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$API_BASE_URL/library/series/${extractCode(manga.url)}/", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<SeriesDetailResponse>().series.toSManga()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga)).asObservableSuccess()
        .map { response ->
            val chapters = mutableListOf<ChapterSummary>()
            var chapterPage = response.parseAs<ChapterListResponse>().data

            chapters += chapterPage.chapters

            while (chapterPage.hasNext) {
                chapterPage = client.newCall(chapterListRequest(manga, chapterPage.currentPage + 1))
                    .execute()
                    .use { it.parseAs<ChapterListResponse>().data }
                chapters += chapterPage.chapters
            }

            chapters.map { it.toSChapter() }
        }

    override fun chapterListRequest(manga: SManga): Request = chapterListRequest(manga, 1)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<ChapterListResponse>().data.chapters.map { it.toSChapter() }

    override fun pageListRequest(chapter: SChapter): Request = GET("$API_BASE_URL/chapters/${extractCode(chapter.url)}/", headers)

    override fun pageListParse(response: Response): List<Page> = response.parseAs<ChapterDetailResponse>()
        .chapter
        .pages
        .sortedBy { it.number }
        .mapIndexed { index, page -> page.toPage(index) }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${extractCode(manga.url)}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${extractCode(chapter.url)}"

    private fun seriesListRequest(
        page: Int,
        query: String? = null,
        filters: FilterList = FilterList(),
        sortBy: String? = null,
    ): Request {
        val url = "$API_BASE_URL/library/series/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PAGE_SIZE.toString())

        if (!query.isNullOrBlank()) {
            url.addQueryParameter("name", query)
        }

        if (!sortBy.isNullOrBlank()) {
            url.addQueryParameter("sort_by", sortBy)
            url.addQueryParameter("sort_order", "desc")
        }

        filters.forEach { filter ->
            when (filter) {
                is SelectFilter -> filter.selectedValue().takeIf(String::isNotBlank)?.let {
                    url.addQueryParameter(filter.queryName, it)
                }
                is GenreFilter -> {
                    val includedGenres = filter.includedGenreIds()
                    val excludedGenres = filter.excludedGenreIds()

                    if (includedGenres.isNotEmpty()) {
                        url.addQueryParameter("genres_include", includedGenres.joinToString(","))
                    }
                    if (excludedGenres.isNotEmpty()) {
                        url.addQueryParameter("genres_exclude", excludedGenres.joinToString(","))
                    }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    private fun chapterListRequest(manga: SManga, page: Int): Request {
        val url = "$API_BASE_URL/library/series/${extractCode(manga.url)}/chapters/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", CHAPTER_PAGE_SIZE.toString())
            .build()

        return GET(url, headers)
    }

    private fun seriesListParse(response: Response): MangasPage {
        val result = response.parseAs<SeriesListResponse>()
        return MangasPage(
            result.series.map { it.toSManga() },
            result.pagination.hasNext,
        )
    }

    private fun extractCode(url: String): String = url.trimEnd('/').substringAfterLast('/')

    companion object {
        private const val PAGE_SIZE = 24
        private const val CHAPTER_PAGE_SIZE = 100
    }
}

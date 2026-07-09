package eu.kanade.tachiyomi.extension.pt.taimumangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

@Source
abstract class TaimuMangas : HttpSource() {

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "application/json")
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = libraryRequest(
        page = page,
        sort = "rating",
    )

    override fun popularMangaParse(response: Response): MangasPage = libraryParse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$API_BASE_URL/updates".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PAGE_SIZE.toString())
            .addQueryParameter("adult_mode", "true")
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<UpdatesResponse>()
        return MangasPage(
            result.items.map { it.toSManga() },
            result.hasMore,
        )
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = libraryRequest(
        page = page,
        query = query.takeIf(String::isNotBlank),
        filters = filters,
    )

    override fun searchMangaParse(response: Response): MangasPage = libraryParse(response)

    override fun getFilterList(): FilterList = getFilters()

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$API_BASE_URL/series/${extractIdentifier(manga.url)}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<SeriesDetail>().toSManga()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga)).asObservableSuccess()
        .map { response ->
            val chapters = mutableListOf<ChapterSummary>()
            var chapterPage = response.parseAs<ChapterListResponse>()

            chapters += chapterPage.items

            while (chapterPage.hasMore) {
                chapterPage = client.newCall(chapterListRequest(manga, chapterPage.page + 1))
                    .execute()
                    .use { it.parseAs<ChapterListResponse>() }
                chapters += chapterPage.items
            }

            chapters.map { it.toSChapter() }
        }

    override fun chapterListRequest(manga: SManga): Request = chapterListRequest(manga, 1)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<ChapterListResponse>().items.map { it.toSChapter() }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$API_BASE_URL/chapters/${extractIdentifier(chapter.url)}".toHttpUrl().newBuilder()
            .addQueryParameter("adult", "true")
            .build()

        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<ChapterDetailResponse>()
        .pages
        .sortedBy { it.number }
        .mapIndexed { index, page -> page.toPage(index) }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${extractIdentifier(manga.url)}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${extractIdentifier(chapter.url)}"

    private fun libraryRequest(
        page: Int,
        query: String? = null,
        filters: FilterList = FilterList(),
        sort: String? = null,
    ): Request {
        val url = "$API_BASE_URL/library".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PAGE_SIZE.toString())
            .addQueryParameter("adult", "true")

        if (!query.isNullOrBlank()) {
            url.addQueryParameter("q", query)
        }

        if (!sort.isNullOrBlank()) {
            url.addQueryParameter("sort", sort)
            url.addQueryParameter("order", "desc")
        }

        filters.forEach { filter ->
            when (filter) {
                is SelectFilter -> filter.selectedValue().takeIf(String::isNotBlank)?.let {
                    url.addQueryParameter(filter.queryName, it)
                }
                is GenreFilter -> {
                    val includedGenres = filter.includedGenreSlugs()

                    if (includedGenres.isNotEmpty()) {
                        url.addQueryParameter("genres", includedGenres.joinToString(","))
                    }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    private fun chapterListRequest(manga: SManga, page: Int): Request {
        val url = "$API_BASE_URL/series/${extractIdentifier(manga.url)}/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", CHAPTER_PAGE_SIZE.toString())
            .addQueryParameter("order", "desc")
            .build()

        return GET(url, headers)
    }

    private fun libraryParse(response: Response): MangasPage {
        val result = response.parseAs<LibraryResponse>()
        return MangasPage(
            result.items.map { it.toSManga() },
            result.hasNextPage,
        )
    }

    private fun extractIdentifier(url: String): String = url.trimEnd('/').substringAfterLast('/')

    companion object {
        private const val API_BASE_URL = "https://apiv2.taimumangas.com/api/v1/reader"
        private const val PAGE_SIZE = 24
        private const val CHAPTER_PAGE_SIZE = 100
    }
}

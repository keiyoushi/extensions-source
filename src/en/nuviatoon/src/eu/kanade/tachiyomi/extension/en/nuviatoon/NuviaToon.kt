package eu.kanade.tachiyomi.extension.en.nuviatoon

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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable

@Source
abstract class NuviaToon : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Accept", "application/json")
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/nuvia-api/series?per_page=18&page=$page&sort=views&dir=desc", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<PaginatedResponse<SeriesDto>>()
        return MangasPage(dto.data.map { it.toSManga() }, dto.hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/nuvia-api/series?per_page=18&page=$page&sort=created_at&dir=desc", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/nuvia-api/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("per_page", "18")
            addQueryParameter("page", page.toString())

            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }

            val statusFilter = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()
            val genreFilter = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
            val sortFilter = filters.firstInstanceOrNull<SortFilter>()

            if (statusFilter != null) {
                addQueryParameter("status", statusFilter)
            }

            if (genreFilter != null) {
                addQueryParameter("genre", genreFilter)
            }

            if (sortFilter != null) {
                addQueryParameter("sort", sortFilter.toUriPart())
                addQueryParameter("dir", sortFilter.toDirPart())
            } else {
                addQueryParameter("sort", "views")
                addQueryParameter("dir", "desc")
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(baseUrl)) {
            val url = query.toHttpUrlOrNull()
            val slug = url?.pathSegments?.getOrNull(1)
            if (slug != null) {
                val manga = SManga.create().apply {
                    this.url = slug
                }
                return fetchMangaDetails(manga).map {
                    it.initialized = true
                    MangasPage(listOf(it), false)
                }
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/nuvia-api/series/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<SeriesDto>().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/nuvia-api/series/${manga.url}/chapters", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.pathSegments.let { it[it.size - 2] }
        return response.parseAs<List<ChapterDto>>()
            .map { it.toSChapter(slug) }
            .reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/series/${chapter.url.substringBefore("?")}"

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfter("id=")
        return GET("$baseUrl/nuvia-api/chapters/$id/pages", headers)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<List<PageDto>>().mapIndexed { index, dto ->
        Page(index, imageUrl = dto.imageUrl)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        StatusFilter(),
        GenreFilter(),
        SortFilter(),
    )
}

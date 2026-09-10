package eu.kanade.tachiyomi.extension.tr.mangadenizi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class MangaDenizi : HttpSource() {
    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .addInterceptor(UnscramblerInterceptor())
        .build()

    private val apiHeaders: Headers by lazy {
        headersBuilder()
            .add("Accept", "application/json")
            .add("Referer", "$baseUrl/manga")
            .build()
    }

    // ===============================
    // Popular
    // ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/v1/web/manga?sort=popular&page=$page", apiHeaders)

    override fun popularMangaParse(response: Response): MangasPage {
        val json = response.parseAs<MangaApiResponse<MangaIndexData>>().data.manga
        val mangas = json.data.map { it.toSManga() }
        val hasNextPage = json.currentPage < json.lastPage
        return MangasPage(mangas, hasNextPage)
    }

    // ===============================
    // Latest
    // ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/v1/web/manga?sort=latest&page=$page", apiHeaders)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ===============================
    // Search
    // ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        val url = "$baseUrl/api/v1/web/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) {
                addQueryParameter("q", query.trim())
            }
            filterList.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        if (filter.toUriPart().isNotEmpty()) {
                            addQueryParameter("sort", filter.toUriPart())
                        }
                    }
                    is StatusFilter -> {
                        if (filter.toUriPart().isNotEmpty()) {
                            addQueryParameter("status[]", filter.toUriPart())
                        }
                    }
                    is CategoryFilter -> {
                        if (filter.toUriPart().isNotEmpty()) {
                            addQueryParameter("categories[]", filter.toUriPart())
                        }
                    }
                    is DemographicFilter -> {
                        if (filter.toUriPart().isNotEmpty()) {
                            addQueryParameter("demographics[]", filter.toUriPart())
                        }
                    }
                    else -> {}
                }
            }
        }.build()
        return GET(url, apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ===============================
    // Filters
    // ===============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        CategoryFilter(),
        DemographicFilter(),
    )

    // ===============================
    // Details
    // ===============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.trim().removePrefix("/").removePrefix("manga/")
        return GET("$baseUrl/api/v1/web/manga/$slug", apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaApiResponse<MangaDetailsData>>().data.manga.toSManga()

    // ===============================
    // Chapters
    // ===============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val manga = response.parseAs<MangaApiResponse<MangaDetailsData>>().data.manga
        return manga.chapters.map { it.toSChapter(manga.slug) }
    }

    // ===============================
    // Pages
    // ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val trimmed = chapter.url.trim().removePrefix("/").removePrefix("read/").removePrefix("manga/")
        val mangaSlug = trimmed.substringBefore("/")
        val chapterSlug = trimmed.substringAfter("/")
        return GET("$baseUrl/api/v1/reader/$mangaSlug/$chapterSlug", apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<ReaderDto>()
        return dto.pages.mapIndexed { index, page -> page.toPage(index) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

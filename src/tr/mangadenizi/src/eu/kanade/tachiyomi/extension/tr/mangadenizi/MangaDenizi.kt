package eu.kanade.tachiyomi.extension.tr.mangadenizi

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaDenizi : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(UnscramblerInterceptor())

    private val apiHeaders: Headers
        get() = headersBuilder()
            .set("Accept", "application/json")
            .set("Referer", "$baseUrl/manga")
            .build()

    // =============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val json = client.get("$baseUrl/api/v1/web/manga?sort=popular&page=$page", apiHeaders)
            .parseAs<MangaApiResponse<MangaIndexData>>().data.manga
        val mangas = json.data.map { it.toSManga() }
        val hasNextPage = json.currentPage < json.lastPage
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val json = client.get("$baseUrl/api/v1/web/manga?sort=latest&page=$page", apiHeaders)
            .parseAs<MangaApiResponse<MangaIndexData>>().data.manga
        val mangas = json.data.map { it.toSManga() }
        val hasNextPage = json.currentPage < json.lastPage
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/v1/web/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) {
                addQueryParameter("q", query.trim())
            }
            filters.forEach { filter ->
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

        val json = client.get(url, apiHeaders)
            .parseAs<MangaApiResponse<MangaIndexData>>().data.manga
        val mangas = json.data.map { it.toSManga() }
        val hasNextPage = json.currentPage < json.lastPage
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Deeplink ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.equals(baseUrl.toHttpUrl().host, ignoreCase = true)) return null
        val segments = url.pathSegments
        if (segments.firstOrNull() != "manga" || segments.size < 2) return null
        val slug = segments[1]
        val manga = client.get("$baseUrl/api/v1/web/manga/$slug", apiHeaders)
            .parseAs<MangaApiResponse<MangaDetailsData>>().data.manga
        return manga.toSManga()
    }

    // =============================== Details & Chapters ====================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.trim().removePrefix("/").removePrefix("manga/")
        val mangaData = client.get("$baseUrl/api/v1/web/manga/$slug", apiHeaders)
            .parseAs<MangaApiResponse<MangaDetailsData>>().data.manga
        val sManga = mangaData.toSManga()
        val sChapters = mangaData.chapters.map { it.toSChapter(mangaData.slug) }
        return SMangaUpdate(sManga, sChapters)
    }

    // =============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val trimmed = chapter.url.trim().removePrefix("/").removePrefix("read/").removePrefix("manga/")
        val mangaSlug = trimmed.substringBefore("/")
        val chapterSlug = trimmed.substringAfter("/")
        val dto = client.get("$baseUrl/api/v1/reader/$mangaSlug/$chapterSlug", apiHeaders)
            .parseAs<ReaderDto>()
        return dto.pages.mapIndexed { index, page -> page.toPage(index) }
    }

    // =============================== Filters ===============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        CategoryFilter(),
        DemographicFilter(),
    )
}

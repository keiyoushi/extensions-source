package eu.kanade.tachiyomi.extension.es.dynasty

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Source
abstract class Dynasty : KeiSource() {

    override fun Headers.Builder.configureHeaders() = add("Accept", "application/json, text/plain, */*")

    override suspend fun getPopularManga(page: Int): MangasPage = mangaPageParse("$baseUrl/api/mangas?page=$page&limit=20&sort=popular".toHttpUrl(), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaPageParse("$baseUrl/api/mangas?page=$page&limit=20&sort=newest".toHttpUrl(), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "20")

            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            } else {
                val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
                if (genreFilter != null && genreFilter.state != 0) {
                    addQueryParameter("genre", genreFilter.toUriPart())
                }
            }

            val sortFilter = filters.firstInstanceOrNull<SortFilter>()
            if (sortFilter != null) {
                addQueryParameter("sort", sortFilter.toUriPart())
            }
        }.build()

        return mangaPageParse(url, page)
    }

    private suspend fun mangaPageParse(url: HttpUrl, page: Int): MangasPage {
        val result = client.get(url).parseAs<MangaPaginatedResponse>()
        val sortType = url.queryParameter("sort")

        var mangasData = result.getMangas().filter {
            it.type?.contains("novel", ignoreCase = true) != true
        }

        mangasData = when (sortType) {
            "popular" -> mangasData.sortedByDescending { it.views ?: 0 }
            "newest" -> mangasData.sortedByDescending { Instant.tryParse(it.updatedAt) }
            "rating" -> mangasData.sortedByDescending { it.rating ?: 0f }
            "az" -> mangasData.sortedBy { it.title }
            else -> mangasData
        }

        val mangas = mangasData.map { it.toSManga() }
        return MangasPage(mangas, page < result.getTotalPages())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaId = manga.url.substringBefore("|")
        val details = if (fetchDetails) async { fetchDetails(mangaId) } else null
        val chapterList = if (fetchChapters) async { fetchChapterList(mangaId) } else null

        SMangaUpdate(
            details?.await() ?: manga,
            chapterList?.await() ?: chapters,
        )
    }

    private suspend fun fetchDetails(mangaId: String): SManga {
        val json = client.get("$baseUrl/api/mangas/$mangaId").parseAs<JsonElement>()
        val data = if (json is JsonObject && json.containsKey("data")) {
            json["data"]!!
        } else {
            json
        }
        return data.parseAs<MangaDto>().toSManga()
    }

    private suspend fun fetchChapterList(mangaId: String): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        var page = 1
        var totalPages: Int

        do {
            val res = client.get("$baseUrl/api/chapters/paginated?manga_id=$mangaId&page=$page&limit=100&sort=desc")
                .parseAs<ChapterPaginatedResponse>()
            totalPages = res.getTotalPages()
            allChapters.addAll(res.getChapters().map { it.toSChapter() })
            page++
        } while (page <= totalPages)

        return allChapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = client.get(getChapterUrl(chapter)).parseAs<List<PageDto>>()
        return pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.getUrl())
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/api/chapter-pages?chapter_id=${chapter.url}"

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.substringAfter("|", "")
        return if (slug.isNotEmpty()) "$baseUrl/manga/$slug" else baseUrl
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        GenreFilter(),
    )
}

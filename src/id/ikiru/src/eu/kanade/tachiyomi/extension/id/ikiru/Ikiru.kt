package eu.kanade.tachiyomi.extension.id.ikiru

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class Ikiru : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/api/public/library/search".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "popular")
            .addQueryParameter("sort", "desc")
            .addQueryParameter("page", page.toString())
            .build()
        val response = client.get(url)
        return parseSearchResponse(response)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/public/library/search".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "updated")
            .addQueryParameter("sort", "desc")
            .addQueryParameter("page", page.toString())
            .build()
        val response = client.get(url)
        return parseSearchResponse(response)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/public/library/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("query", query.trim())
        }

        filters.filterIsInstance<UriFilter>().forEach {
            it.addToUri(url)
        }

        val response = client.get(url.build())
        return parseSearchResponse(response)
    }

    private fun parseSearchResponse(response: Response): MangasPage {
        val res = response.parseAs<IkiruResponseDto<IkiruSearchDataDto>>()
        val mangaList = res.data?.mangas?.map { it.toSManga() } ?: emptyList()
        val hasNextPage = mangaList.size >= 20
        return MangasPage(mangaList, hasNextPage)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.removePrefix("/manga/").trim('/')
        return "$baseUrl/manga/$slug"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url.removePrefix("/manga/").trim('/')

        val detailsDeferred = if (fetchDetails) {
            async { fetchMangaDetails(slug) }
        } else {
            null
        }

        val chaptersDeferred = if (fetchChapters) {
            async { fetchChapterList(slug) }
        } else {
            null
        }

        val updatedManga = detailsDeferred?.await() ?: manga
        val updatedChapters = chaptersDeferred?.await() ?: chapters

        SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun fetchMangaDetails(slug: String): SManga {
        val response = client.get("$baseUrl/api/public/manga/$slug")
        val res = response.parseAs<IkiruResponseDto<IkiruMangaDetailDto>>()
        return res.data?.toSManga() ?: SManga.create().apply { url = slug }
    }

    private suspend fun fetchChapterList(slug: String): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        var page = 1
        var hasMore = true

        while (hasMore) {
            val url = "$baseUrl/api/public/manga/$slug/chapter".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .build()

            val response = client.get(url)
            val res = response.parseAs<IkiruResponseDto<IkiruChapterListDto>>()
            val data = res.data ?: break
            val chapters = data.chapters.map { it.toSChapter(slug) }

            if (chapters.isEmpty()) break
            allChapters.addAll(chapters)

            hasMore = data.hasMore
            page++
        }

        return allChapters
    }

    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.startsWith("http")) chapter.url else "$baseUrl${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        val document = response.asJsoup()
        val images = document.select("img[src*='cdn.ikiru.id'], img.is-loading")

        return images.mapIndexedNotNull { idx, img ->
            val src = img.attr("src").takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            Page(idx, imageUrl = src)
        }
    }
}

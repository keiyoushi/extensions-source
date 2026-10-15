package eu.kanade.tachiyomi.extension.pt.taimumangas

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class TaimuMangas : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override fun Headers.Builder.configureHeaders() = set("Accept", "application/json")

    override suspend fun getPopularManga(page: Int): MangasPage = client.get(libraryUrl(page, sort = "rating"))
        .parseAs<LibraryResponse>()
        .toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$API_BASE_URL/updates".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PAGE_SIZE.toString())
            .addQueryParameter("adult_mode", "true")
            .build()

        return client.get(url).parseAs<UpdatesResponse>().toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = client
        .get(libraryUrl(page, query = query.takeIf(String::isNotBlank), filters = filters))
        .parseAs<LibraryResponse>()
        .toMangasPage()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null

        val identifier = url.pathSegments.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
        return client.get("$API_BASE_URL/series/$identifier").parseAs<SeriesDetail>().toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { getMangaDetails(manga) } else null
        val chapterList = if (fetchChapters) async { getChapterList(manga) } else null

        SMangaUpdate(
            manga = details?.await() ?: manga,
            chapters = chapterList?.await() ?: chapters,
        )
    }

    private suspend fun getMangaDetails(manga: SManga): SManga = client.get("$API_BASE_URL/series/${extractIdentifier(manga.url)}")
        .parseAs<SeriesDetail>()
        .toSManga()

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val identifier = extractIdentifier(manga.url)
        val chapters = mutableListOf<ChapterSummary>()
        var chapterPage = client.get(chapterListUrl(identifier, 1)).parseAs<ChapterListResponse>()

        chapters += chapterPage.items

        while (chapterPage.hasMore) {
            chapterPage = client.get(chapterListUrl(identifier, chapterPage.page + 1)).parseAs<ChapterListResponse>()
            chapters += chapterPage.items
        }

        return chapters.map { it.toSChapter() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$API_BASE_URL/chapters/${extractIdentifier(chapter.url)}".toHttpUrl().newBuilder()
            .addQueryParameter("adult", "true")
            .build()

        return client.get(url)
            .parseAs<ChapterDetailResponse>()
            .pages
            .sortedBy { it.number }
            .mapIndexed { index, page -> page.toPage(index) }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${extractIdentifier(manga.url)}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${extractIdentifier(chapter.url)}"

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    private fun libraryUrl(
        page: Int,
        query: String? = null,
        filters: FilterList = FilterList(),
        sort: String? = null,
    ): HttpUrl {
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

        return url.build()
    }

    private fun chapterListUrl(identifier: String, page: Int): HttpUrl = "$API_BASE_URL/series/$identifier/chapters".toHttpUrl()
        .newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("per_page", CHAPTER_PAGE_SIZE.toString())
        .addQueryParameter("order", "desc")
        .build()

    private fun extractIdentifier(url: String): String = url.trimEnd('/').substringAfterLast('/')

    companion object {
        private const val API_BASE_URL = "https://api.taimumangas.com/api/v1/reader"
        private const val PAGE_SIZE = 24
        private const val CHAPTER_PAGE_SIZE = 100
    }
}

package eu.kanade.tachiyomi.extension.pt.risentoons

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class Risentoons : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    // The API rejects requests without this client signature
    override fun Headers.Builder.configureHeaders() = set("X-Rip-Client", "V6")

    private val apiUrl get() = "$baseUrl/api"

    override suspend fun getPopularManga(page: Int) = getMangaList(page) { addQueryParameter("sort", "views") }

    override suspend fun getLatestUpdates(page: Int) = getMangaList(page) {}

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getMangaList(page) {
        if (query.isNotBlank()) addQueryParameter("search", query.trim())
        filters.firstInstance<SortFilter>().selected.ifEmpty { null }?.let { addQueryParameter("sort", it) }
        filters.firstInstance<TypeFilter>().selected.ifEmpty { null }?.let { addQueryParameter("type_filter", it) }
        filters.firstInstance<StatusFilter>().selected.ifEmpty { null }?.let { addQueryParameter("status", it) }
        filters.firstInstanceOrNull<GenreFilter>()?.state
            ?.filter { it.state }
            ?.joinToString(",") { it.name }
            ?.ifEmpty { null }
            ?.let { addQueryParameter("genres", it) }
    }

    private suspend fun getMangaList(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "24")
            .addQueryParameter("page", page.toString())
            .apply(params)
            .build()
        return client.get(url).parseAs<MangaListDto>().toMangasPage(baseUrl)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() !in setOf("manga", "biblioteca")) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return fetchMangaDetails(slug)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) fetchMangaDetails(manga.url) else manga }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga.url) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchMangaDetails(slug: String): SManga = client.get("$apiUrl/mangas/$slug")
        .parseAs<DataDto<MangaDto>>().data.toSManga(baseUrl)

    private suspend fun fetchChapterList(slug: String): List<SChapter> = client.get("$apiUrl/mangas/$slug/chapters?order=desc")
        .parseAs<ChapterListDto>().chapters.map { it.toSChapter(slug) }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = (baseUrl + chapter.url).toHttpUrl().queryParameter("chapter")!!
        return client.get("$apiUrl/mangas/chapters/$chapterId/pages").parseAs<PageListDto>().toPageList(baseUrl)
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres")
        .parseAs<DataDto<List<GenreDto>>>().data.map { it.name }.toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<String>>()
        return FilterList(
            listOfNotNull(
                SortFilter(),
                TypeFilter(),
                StatusFilter(),
                genres?.let(::GenreFilter),
            ),
        )
    }
}

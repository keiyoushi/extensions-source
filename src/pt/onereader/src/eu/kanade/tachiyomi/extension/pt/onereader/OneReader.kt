package eu.kanade.tachiyomi.extension.pt.onereader

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class OneReader : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    private val apiBaseUrl = "https://api.onereader.net".toHttpUrl()

    override suspend fun getPopularManga(page: Int): MangasPage = search(
        page = page,
        query = "",
        order = "az",
        genre = "",
        type = "",
        status = "",
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = search(
        page = page,
        query = "",
        order = "recent",
        genre = "",
        type = "",
        status = "",
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val order = filters.firstInstanceOrNull<OrderFilter>()?.selected() ?: "az"
        val genre = filters.firstInstanceOrNull<TagFilter>()?.selected().orEmpty()
        val type = filters.firstInstanceOrNull<TypeFilter>()?.selected().orEmpty()
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected().orEmpty()

        return search(page, query, order, genre, type, status)
    }

    private suspend fun search(
        page: Int,
        query: String,
        order: String,
        genre: String,
        type: String,
        status: String,
    ): MangasPage {
        val url = apiUrl("api", "mangas", "search").newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("order", order)
            .addQueryParameter("role", "standard")
            .addQueryParameter("search", query.trim())
            .addQueryParameter("genre", genre)
            .addQueryParameter("type", type)
            .addQueryParameter("status", status)
            .build()

        return client.get(url).parseAs<SearchDto>().toMangasPage()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val mangaKey = url.queryParameter("id") ?: return null

        val manga = SManga.create().apply { this.url = mangaKey }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga-details".toHttpUrl().newBuilder()
        .addQueryParameter("id", manga.url)
        .build()
        .toString()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga = if (fetchDetails) {
            client.get(apiUrl("api", "mangas", manga.url)).parseAs<MangaDto>().toSManga(details = true)
        } else {
            manga
        },
        chapters = if (fetchChapters) {
            client.get(apiUrl("api", "chapters", manga.url))
                .parseAs<Map<String, ChapterDto>>()
                .entries
                .sortedByDescending { it.key.toDoubleOrNull() ?: Double.NEGATIVE_INFINITY }
                .map { it.value.toSChapter(it.key, baseUrl) }
        } else {
            chapters
        },
    )

    override val supportsRelatedMangas = false

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = throw UnsupportedOperationException()

    override fun getChapterUrl(chapter: SChapter): String = requireNotNull(
        baseUrl.toHttpUrl().resolve(chapter.url),
    ).toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val webUrl = getChapterUrl(chapter).toHttpUrl()
        val mangaKey = requireNotNull(webUrl.queryParameter("id"))
        val chapterNumber = requireNotNull(webUrl.queryParameter("capitulo")).replace('.', '_')

        return client.get(apiUrl("api", "chapters", mangaKey, chapterNumber))
            .parseAs<PagesDto>()
            .toPages(apiBaseUrl)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        OrderFilter(),
        Filter.Separator(),
        TypeFilter(),
        StatusFilter(),
        TagFilter(),
    )

    private fun apiUrl(vararg pathSegments: String): HttpUrl = apiBaseUrl.newBuilder()
        .apply { pathSegments.forEach { addPathSegment(it) } }
        .build()

    companion object {
        private const val PAGE_SIZE = 24
    }
}

package eu.kanade.tachiyomi.multisrc.rawspa

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

abstract class RawSpa : KeiSource() {
    protected abstract val mangaPath: String

    protected abstract val readerPath: String

    protected open val idPrefix = ""

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", getFilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get("$baseUrl/spa/latest-manga?page=$page", Fetch.XHR)
        .parseAs<MangaListDto>()
        .toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/spa".toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addPathSegment("search")
                addQueryParameter("query", query)
            } else {
                addPathSegments("genre/all")
                filters.filterIsInstance<UriPartFilter>().forEach { it.addToUri(this) }
            }
            addQueryParameter("page", page.toString())
        }.build()

        return client.get(url, Fetch.XHR).parseAs<MangaListDto>().toMangasPage()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/$mangaPath/$idPrefix${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() !in setOf(mangaPath, readerPath)) return null

        val mangaId = url.pathSegments.getOrNull(1)?.removePrefix(idPrefix) ?: return null
        return fetchMangaDetail(mangaId).toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val result = fetchMangaDetail(manga.url)
        return SMangaUpdate(result.toSManga(), result.chapters.map { it.toSChapter(chapterUrl(result.mangaId, it.number)) })
    }

    private suspend fun fetchMangaDetail(mangaId: String) = client.get("$baseUrl/spa/manga/$mangaId", Fetch.XHR).parseAs<MangaDetailResponseDto>()

    protected open fun chapterUrl(mangaId: Int, number: String) = "/$readerPath/$idPrefix$mangaId/chapter-$number"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (mangaId, chapterNumber) = chapter.url.split('/').takeLast(2)

        return client.get("$baseUrl/spa/manga/${mangaId.removePrefix(idPrefix)}/${chapterNumber.removePrefix("chapter-")}", Fetch.XHR)
            .parseAs<ChapterPageResponseDto>()
            .toPageList()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Filters are ignored when using text search."),
        StatusFilter(),
        SortFilter(),
    )
}

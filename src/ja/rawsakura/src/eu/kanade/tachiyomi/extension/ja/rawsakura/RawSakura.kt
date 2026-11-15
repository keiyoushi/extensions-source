package eu.kanade.tachiyomi.extension.ja.rawsakura

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class RawSakura : KeiSource() {
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

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/sa${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() !in setOf("title", "reader")) return null

        val mangaId = url.pathSegments.getOrNull(1)?.removePrefix("sa") ?: return null
        return fetchMangaDetail(mangaId).toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val result = fetchMangaDetail(manga.url)
        return SMangaUpdate(result.toSManga(), result.toSChapterList())
    }

    private suspend fun fetchMangaDetail(mangaId: String) = client.get("$baseUrl/spa/manga/$mangaId", Fetch.XHR).parseAs<MangaDetailResponseDto>()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = (baseUrl + chapter.url).toHttpUrl().pathSegments
        val mangaId = segments[1].removePrefix("sa")
        val chapterNumber = segments[2].removePrefix("chapter-")

        return client.get("$baseUrl/spa/manga/$mangaId/$chapterNumber", Fetch.XHR)
            .parseAs<ChapterPageResponseDto>()
            .toPageList()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Filters are ignored when using text search."),
        StatusFilter(),
        SortFilter(),
    )
}

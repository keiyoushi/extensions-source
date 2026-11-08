package eu.kanade.tachiyomi.extension.en.desirescans

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class DesireScans : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getSeries(page) { addQueryParameter("sort", "popular") }

    override suspend fun getLatestUpdates(page: Int) = getSeries(page) { addQueryParameter("sort", "updated") }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getSeries(page) {
        if (query.isNotBlank()) addQueryParameter("q", query.trim())
        filters.filterIsInstance<UrlFilter>().forEach { it.addTo(this) }
    }

    // The site's own calls add a `_v` build id, which only keeps caches apart
    private suspend fun getSeries(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder()
            .apply(params)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")
            .addQueryParameter("contentMode", "comics")
            .build()
        val result = client.get(url, Fetch.XHR.from("$baseUrl/series")).parseAs<SeriesListDto>()
        return MangasPage(result.data.map { it.toSManga(baseUrl) }, result.meta.hasMore)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 3 || segments[0] != "series" || segments[1] != "comic") return null
        val slug = segments[2]
        return getSeriesPage(slug, 1).series.toSManga(baseUrl).apply { this.url = slug }
    }

    // The series page lists 100 chapters at a time
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val first = getSeriesPage(manga.url, 1)
        val details = first.series.toSManga(baseUrl).apply { url = manga.url }
        if (!fetchChapters) return@coroutineScope SMangaUpdate(details, chapters)
        val others = (2..first.totalPages).map { page -> async { getSeriesPage(manga.url, page).chapters } }.awaitAll()
        SMangaUpdate(details, (first.chapters + others.flatten()).map { it.toSChapter(manga.url) }.reversed())
    }

    private suspend fun getSeriesPage(slug: String, page: Int): SeriesPageDto {
        val url = "$baseUrl/series/comic/$slug".toHttpUrl().newBuilder()
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()
        return client.get(url, Fetch.DOCUMENT).asJsoup()
            .extractNextJs<SeriesPageDto> { it is JsonObject && "series" in it && "chapters" in it && "totalPages" in it }!!
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/comic/${manga.url}"

    // A locked chapter's pages come redacted, without their image, apart from a free preview
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterPages = client.get("$baseUrl${chapter.url}", Fetch.DOCUMENT).asJsoup()
            .extractNextJs<ChapterPagesDto> { it is JsonObject && "pages" in it && "number" in it }
            ?: return emptyList()
        return chapterPages.pages
            .filter { !it.isRedacted && it.imageUrl.isNotEmpty() }
            .sortedBy { it.pageNumber }
            .mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()
}

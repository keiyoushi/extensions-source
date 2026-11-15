package eu.kanade.tachiyomi.extension.tr.aerotoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.minutes

@Source
abstract class AeroToon : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    // The API answers 429 past 30 requests a minute; images are on another host
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 25, period = 1.minutes) { it.host == baseUrl.toHttpUrl().host }

    override suspend fun getPopularManga(page: Int) = getSeries(seriesUrl(page, "popular").build())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/series/latest-updates".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        val data = client.get(url, Fetch.XHR).parseAs<LatestUpdatesDto>()
        return MangasPage(data.updates.filterNot { it.isNovel }.map { it.toSManga() }, data.hasMore)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = seriesUrl(page, filters.firstInstanceOrNull<SortFilter>()?.value ?: "popular").apply {
            if (query.isNotBlank()) addQueryParameter("search", query.trim())
            filters.firstInstanceOrNull<StatusFilter>()?.value?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<TypeFilter>()?.value?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<GenreFilter>()?.selected?.takeIf { it.isNotEmpty() }?.let {
                addQueryParameter("genre", it.joinToString(","))
            }
        }.build()
        return getSeries(url)
    }

    // Series links also come as /series/<slug> or /seri/<id>, chapter links as /seri/<slug>/bolum/<number>
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() !in SERIES_PATHS) return null
        val series = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return client.get("$apiUrl/series/$series", Fetch.XHR).parseAs<SeriesDetailsDto>().series
            .takeUnless { it.isNovel }
            ?.toSManga()
            ?.apply { initialized = true }
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "tur") return super.getMangasByUrl(url, page)
        val genre = GENRES.find { it.slug == url.pathSegments.getOrNull(1) } ?: return MangasPage(emptyList(), false)
        return getSeries(seriesUrl(page, "latest").addQueryParameter("genre", genre.name).build())
    }

    private fun seriesUrl(page: Int, sort: String) = "$apiUrl/series".toHttpUrl().newBuilder()
        .addQueryParameter("sort", sort)
        .addQueryParameter("limit", "24")
        .addQueryParameter("page", page.toString())

    // Novels (text chapters) are left out
    private suspend fun getSeries(url: HttpUrl): MangasPage {
        val data = client.get(url, Fetch.XHR).parseAs<SeriesListDto>()
        return MangasPage(data.series.filterNot { it.isNovel }.map { it.toSManga() }, data.hasMore)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val data = client.get("$apiUrl/series/${manga.url}", Fetch.XHR).parseAs<SeriesDetailsDto>()
        return SMangaUpdate(data.series.toSManga(), data.toSChapterList(manga.url))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$apiUrl/chapters/${chapter.url}", Fetch.XHR).parseAs<PageListDto>().toPageList()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    override fun getMangaUrl(manga: SManga) = "$baseUrl/seri/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/seri/${chapter.memo["series"]!!.string}/bolum/${chapter.memo["number"]!!.string}"

    companion object {
        private val SERIES_PATHS = setOf("seri", "series")
    }
}

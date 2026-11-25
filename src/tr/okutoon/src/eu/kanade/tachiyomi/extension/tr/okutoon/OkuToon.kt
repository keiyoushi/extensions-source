package eu.kanade.tachiyomi.extension.tr.okutoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class OkuToon : KeiSource() {

    private val apiUrl get() = "$baseUrl/api/v1".toHttpUrl()

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(), PeriodFilter()))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter("updated")))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = apiUrl.newBuilder()
            .addPathSegment("series")
            .addQueryParameter("page", page.toString())
        if (query.isNotBlank()) {
            url.addQueryParameter("q", query.trim())
        }
        filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(url) }
        val response = client.get(url.build(), Fetch.XHR).parseAs<SeriesListResponse>()
        return MangasPage(response.data.map { it.toSManga() }, response.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val host = url.host.removePrefix("www.")
        if (host != baseUrl.toHttpUrl().host && host != "okutoon.com") return null
        val slug = url.pathSegments.first().takeIf { it.isNotEmpty() && it != "tur" } ?: return null
        return getSeries(slug).toSManga(baseUrl).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = getSeries(getMangaUrl(manga).toHttpUrl().pathSegments.first())
        return SMangaUpdate(series.toSManga(baseUrl), series.toSChapterList())
    }

    private suspend fun getSeries(slug: String): SeriesDetailsDto {
        val url = apiUrl.newBuilder()
            .addPathSegment("series")
            .addPathSegment(slug)
            .build()
        return client.get(url, Fetch.XHR).parseAs<SeriesResponse>().data
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (series, number) = getChapterUrl(chapter).toHttpUrl().pathSegments
        val url = apiUrl.newBuilder()
            .addPathSegment("series")
            .addPathSegment(series)
            .addPathSegment("chapters")
            .addPathSegment(number)
            .build()
        return client.get(url, Fetch.XHR).parseAs<ChapterResponse>().data.pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.imageUrl)
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = apiUrl.newBuilder().addPathSegment("categories").build()
        return client.get(url, Fetch.XHR).parseAs<CategoriesResponse>().data.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            SortFilter(),
            PeriodFilter(),
            StatusFilter(),
            TypeFilter(),
            FlagFilter("One-shot", "one_shot"),
            FlagFilter("Doujinshi", "doujinshi"),
            FlagFilter("AI çevirilerini gizle", "hide_ai"),
            Filter.Separator(),
            ChapterCountFilter("En az bölüm", "min_chapters"),
            ChapterCountFilter("En fazla bölüm", "max_chapters"),
            data?.parseAs<List<CategoryDto>>()?.let { categories -> GenreFilter(categories.map { Genre(it.name, it.slug) }) },
        ),
    )
}

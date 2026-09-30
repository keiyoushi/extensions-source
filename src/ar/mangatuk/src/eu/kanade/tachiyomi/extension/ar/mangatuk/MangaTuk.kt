package eu.kanade.tachiyomi.extension.ar.mangatuk

import eu.kanade.tachiyomi.source.model.Filter
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class MangaTuk : KeiSource() {

    private val apiUrl = "https://api.mangatuk.com/api"

    override suspend fun getPopularManga(page: Int) = search(page, "", "popular", null)

    override suspend fun getLatestUpdates(page: Int) = search(page, "", "latest_updates", null)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value ?: "popular"
        val status = filters.firstInstanceOrNull<StatusFilter>()?.value
        return search(page, query, sort, status)
    }

    private suspend fun search(page: Int, query: String, sort: String, status: String?): MangasPage {
        val url = "$apiUrl/catalog/search".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query)
            addQueryParameter("sort", sort)
            status?.let { addQueryParameter("status", it) }
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
        }.build()
        val result = client.get(url).parseAs<SeriesListDto>()
        return MangasPage(result.data.map { it.toSManga() }, page * PAGE_SIZE < result.total)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter(String::isNotBlank)
        if (segments.size < 2 || segments[0] !in setOf("series", "manga")) return null
        return client.get("$apiUrl/catalog/series/by-slug/${segments[1]}").parseAs<SeriesDto>().toSMangaDetails()
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.memo["slug"]!!.jsonPrimitive.content}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/series/${chapter.memo["series"]!!.jsonPrimitive.content}/${chapter.memo["slug"]!!.jsonPrimitive.content}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = client.get("$apiUrl/catalog/series/${manga.url}").parseAs<SeriesDto>()
        return SMangaUpdate(
            series.toSMangaDetails(),
            series.chapters.filterNot { it.locked }.map { it.toSChapter(series.slug) }.reversed(),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$apiUrl/catalog/chapters/${chapter.url}/pages")
        .parseAs<List<PageDto>>()
        .mapIndexed { i, page -> Page(i, imageUrl = page.imageUrl) }

    private class SortFilter : Filter.Select<String>("ترتيب حسب", SORTS.map { it.first }.toTypedArray()) {
        val value get() = SORTS[state].second
    }

    private class StatusFilter : Filter.Select<String>("الحالة", STATUSES.map { it.first }.toTypedArray()) {
        val value get() = STATUSES[state].second
    }

    companion object {
        private const val PAGE_SIZE = 30

        private val SORTS = listOf(
            "الأكثر شعبية" to "popular",
            "الرائج" to "trending",
            "آخر التحديثات" to "latest_updates",
            "الأحدث" to "latest",
            "أبجدي" to "alphabetical",
            "التقييم" to "rating",
        )

        private val STATUSES = listOf(
            "الكل" to null,
            "مستمر" to "ongoing",
            "مكتمل" to "completed",
            "متوقف" to "hiatus",
            "ملغي" to "cancelled",
        )
    }
}

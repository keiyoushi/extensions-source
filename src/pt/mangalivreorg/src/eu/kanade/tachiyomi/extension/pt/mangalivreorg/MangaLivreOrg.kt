package eu.kanade.tachiyomi.extension.pt.mangalivreorg

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.get
import keiyoushi.utils.long
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaLivreOrg : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .add("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
        // The chapter endpoint answers 404 unless this header is present, with any value.
        .add("Sec-Fetch-Site", "same-origin")

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, "views", period = "ever")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, "updates")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val body = FormBody.Builder()
                .add("search", query)
                .build()
            val results = client.post("$baseUrl/lib/search/series.json", body)
                .parseAs<Map<String, List<SearchItemDto>>>()

            return MangasPage(results.values.flatten().map(SearchItemDto::toSManga), hasNextPage = false)
        }

        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selectedValue
        if (!category.isNullOrEmpty()) {
            val url = "$baseUrl/categories/series_list.json".toHttpUrl().newBuilder()
                .addQueryParameter("id_category", category)
                .build()
            val results = client.get(url).parseAs<CategoryListDto>()

            return MangasPage(results.series.map(ListItemDto::toSManga), hasNextPage = false)
        }

        val order = filters.firstInstanceOrNull<SortFilter>()?.selectedValue ?: "updates"
        val period = filters.firstInstanceOrNull<PeriodFilter>()?.selectedValue
            .takeIf { order == "views" }
        return getMangaList(page, order, period)
    }

    private suspend fun getMangaList(page: Int, order: String, period: String? = null): MangasPage {
        val url = "$baseUrl/api/v1/mangas/list".toHttpUrl().newBuilder()
            .addQueryParameter("order", order)
            .apply { period?.let { addQueryParameter("period", it) } }
            .addQueryParameter("page", page.toString())
            .build()

        val result = client.get(url).parseAs<MangaListDto>()
        return MangasPage(result.series.map(ListItemDto::toSManga), result.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() !in MANGA_PATH_SEGMENTS) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
        val manga = SManga.create().apply { this.url = slug }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get("$baseUrl/api/v1/mangas/${manga.url}").parseAs<MangaDetailsDto>()

        return SMangaUpdate(
            manga = details.manga.toSManga(),
            chapters = details.chapters.map { it.toSChapter(details.manga.slug) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$baseUrl/api/v1/chapters/${chapter.url}")
        .parseAs<ChapterPagesDto>()
        .toPageList()

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val genres = client.get("$baseUrl/api/v1/genres").parseAs<List<GenreDto>>()
        return FilterData(genres).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList(SortFilter())

        return FilterList(
            SortFilter(),
            PeriodFilter(),
            CategoryFilter(filterData.genres),
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["slug"]!!.string
        val legacyId = chapter.memo["legacyId"]!!.long
        val number = chapter.memo["number"]!!.string
        return "$baseUrl/ler/$slug/online/$legacyId/$number"
    }

    companion object {
        private val MANGA_PATH_SEGMENTS = listOf("manga", "ler")
    }
}

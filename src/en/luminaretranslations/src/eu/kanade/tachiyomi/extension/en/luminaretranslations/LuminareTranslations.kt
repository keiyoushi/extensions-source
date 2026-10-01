package eu.kanade.tachiyomi.extension.en.luminaretranslations

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class LuminareTranslations : KeiSource() {

    private val apiUrl get() = "$baseUrl/wp-json/yarnovel/v1"
    private val pageSize = 24

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(listOf(Filters("Popular", "popular")))))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(listOf(Filters("Latest", "latest")))))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", pageSize.toString())
            .addQueryParameter("type", "manga")

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query)
        }

        filters.firstInstanceOrNull<SortFilter>()?.selected
            ?.let { url.addQueryParameter("sort", it) }

        filters.firstInstanceOrNull<GenreFilter>()?.state
            ?.filter { it.state }
            ?.joinToString(",") { it.slug }
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("genres", it) }

        filters.firstInstanceOrNull<TagFilter>()?.state
            ?.filter { it.state }
            ?.joinToString(",") { it.slug }
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("tags", it) }

        filters.firstInstanceOrNull<AuthorFilter>()?.selected
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("author", it) }

        filters.firstInstanceOrNull<ArtistFilter>()?.selected
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("artist", it) }

        filters.firstInstanceOrNull<StatusFilter>()?.selected
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("status", it) }

        val result = client.get(url.build()).parseAs<EntryResponse>()
        val mangas = result.data.filter { it.type !in EXCLUDED_TYPES }.map { it.toSManga() }
        val hasNextPage = (page * pageSize) < result.meta.total
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // The series and chapters API endpoints fail with a server-side PHP memory error, so parse the series page
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val xData = document.selectFirst("section[x-data*=chapters:]")!!.attr("x-data").lines().map(String::trim)
        fun xDataArray(key: String) = xData.first { it.startsWith("$key:") }.removePrefix("$key:").trim().removeSuffix(",")

        val info = xDataArray("infoRows").parseAs<List<InfoRow>>().associate { it.label to it.value }

        val updatedManga = manga.apply {
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            description = document.selectFirst("#series-description")?.wholeText()?.trim()
            author = info["Author"]
            artist = info["Artist"]
            genre = info["Genre"]
            status = when (info["Status"]?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "dropped" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }

        val chapterList = xDataArray("chapters").parseAs<List<ChapterData>>()
            .map { it.toSChapter(manga.url) }
            .sortedByDescending { it.chapter_number }

        return SMangaUpdate(updatedManga, chapterList)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/series/${chapter.memo["seriesSlug"]!!.string}/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = client.get(getChapterUrl(chapter)).asJsoup().select("img.reader-page[data-src]")
        val server = images.firstOrNull()?.attr("data-server-id")
        return images.filter { it.attr("data-server-id") == server }.mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("data-src"))
        }
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/explore/filters").parseAs<FilterResponse>().toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterResponse>() ?: return FilterList()
        return FilterList(
            Filter.Header("Note: Search and active filters are applied together"),
            SortFilter(filterData.sorts),
            StatusFilter(filterData.statuses),
            Filter.Separator(),
            GenreFilter(filterData.genres),
            TagFilter(filterData.tags),
            AuthorFilter(filterData.authors),
            ArtistFilter(filterData.artists),
        )
    }

    companion object {
        private val EXCLUDED_TYPES = setOf("novel", "light_novel", "web_novel")
    }
}

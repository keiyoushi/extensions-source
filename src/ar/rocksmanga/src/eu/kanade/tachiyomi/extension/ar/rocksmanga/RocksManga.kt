package eu.kanade.tachiyomi.extension.ar.rocksmanga

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element

@Source
abstract class RocksManga : KeiSource() {
    private val apiUrl get() = baseUrl.replace("://", "://api.")

    override val supportsFilterFetching = true

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    // ============================== Listings ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, sort = "most_viewed")

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
        return browse(
            page,
            sort = filters.firstInstanceOrNull<SortFilter>()?.selected,
            query = query,
            type = filters.firstInstanceOrNull<TypeFilter>()?.selected,
            status = filters.firstInstanceOrNull<StatusFilter>()?.selected,
            include = genres.filter { it.state == Filter.TriState.STATE_INCLUDE }.map { it.value },
            exclude = genres.filter { it.state == Filter.TriState.STATE_EXCLUDE }.map { it.value },
        )
    }

    private suspend fun browse(
        page: Int,
        sort: String? = null,
        query: String = "",
        type: String? = null,
        status: String? = null,
        include: List<String> = emptyList(),
        exclude: List<String> = emptyList(),
    ): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder().apply {
            if (page > 1) addQueryParameter("page", page.toString())
            if (!sort.isNullOrEmpty()) {
                addQueryParameter("sort", sort)
                if (sort == "alphabetical") addQueryParameter("direction", "asc")
            }
            if (query.isNotBlank()) addQueryParameter("search", query.trim())
            if (!type.isNullOrEmpty()) addQueryParameter("type", type)
            if (!status.isNullOrEmpty()) addQueryParameter("status", status)
            if (include.isNotEmpty()) addQueryParameter("genre", include.joinToString(","))
            if (exclude.isNotEmpty()) addQueryParameter("genre_exclude", exclude.joinToString(","))
        }.build()

        val document = client.get(url).asJsoup()
        val mangas = document.select(".catalog-grid-compact .unit, main .unit").distinct().map { it.toSManga() }
        return MangasPage(mangas, document.selectFirst("li.page-item > a.page-link[rel=next]") != null)
    }

    private fun Element.toSManga() = SManga.create().apply {
        val link = selectFirst(".info > a[href]")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = selectFirst("img")?.absUrl("src")
    }

    // ============================ Details & chapters ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = document.extractNextJs<DetailsDto> { it is JsonObject && "readHref" in it }!!.manga
        val chapterList = document.extractNextJs<ChapterListDto> { it is JsonObject && "mangaSlug" in it && "chapters" in it }!!

        return SMangaUpdate(
            details.toSManga(apiUrl),
            chapterList.chapters.sortedByDescending { it.id }.map { it.toSChapter(chapterList.mangaSlug) },
        )
    }

    // ================================= Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.extractNextJs<ReaderDto> { it is JsonObject && "chapter" in it && "manga" in it }!!
            .chapter.pages.mapIndexed { i, path -> Page(i, imageUrl = "$apiUrl$path") }
    }

    // ================================= Filters =================================

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/browse").asJsoup()
        .extractNextJs<GenresDto> { it is JsonObject && "genres" in it && "filters" in it }!!
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            TypeFilter(),
            StatusFilter(),
        )
        data?.parseAs<GenresDto>()?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
            filters += Filter.Separator()
            filters += GenreFilter(genres.map { Genre(it.name, it.slug) })
        }
        return FilterList(filters)
    }
}

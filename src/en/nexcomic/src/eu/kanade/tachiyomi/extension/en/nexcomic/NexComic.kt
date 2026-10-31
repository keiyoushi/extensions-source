package eu.kanade.tachiyomi.extension.en.nexcomic

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class NexComic : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int) = MangasPage(getAllSeries().map { it.toSManga(baseUrl) }, false)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = coroutineScope {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "viewed"
        val popular = if (sort == "viewed") {
            async { client.get("$baseUrl/api/local/popular?limit=100&period=all").parseAs<PopularDto>() }
        } else {
            null
        }
        val search = query.trim()
        val types = filters.firstInstanceOrNull<TypeFilter>()?.checked.orEmpty()
        val statuses = filters.firstInstanceOrNull<StatusFilter>()?.checked.orEmpty()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val includedGenres = genreFilter?.included.orEmpty()
        val excludedGenres = genreFilter?.excluded.orEmpty()

        val series = getAllSeries().filter { series ->
            (listOf(series.title) + series.alternativeTitles).any { it.contains(search, ignoreCase = true) } &&
                (types.isEmpty() || series.type in types) &&
                (statuses.isEmpty() || series.status in statuses) &&
                series.genres.containsAll(includedGenres) &&
                series.genres.none { it in excludedGenres }
        }
        val sorted = popular?.await()?.data?.withIndex()?.associate { it.value.slug to it.index }
            ?.let { rank -> series.sortedBy { rank[it.slug] ?: Int.MAX_VALUE } }
            ?: series
        MangasPage(sorted.map { it.toSManga(baseUrl) }, false)
    }

    private suspend fun getAllSeries() = client.get("$baseUrl/api/local/series").parseAs<SeriesListDto>().data

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return getCatalog(slug).series.firstOrNull { it.slug == slug }?.toSManga(baseUrl)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.slug()}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.slug()
        val catalog = getCatalog(slug)
        return SMangaUpdate(
            manga = catalog.series.first { it.slug == slug }.toSManga(baseUrl),
            chapters = catalog.chapters
                .filter { it.seriesSlug == slug && !it.locked }
                .map { it.toSChapter() }
                .sortedByDescending { it.chapter_number },
        )
    }

    // the series page's rsc payload carries the whole site catalog, chapters included
    private suspend fun getCatalog(slug: String) = client.get("$baseUrl/series/$slug.rsc").extractNextJs<CatalogDto> {
        it is JsonObject && "series" in it && "chapters" in it
    }!!

    override fun getChapterUrl(chapter: SChapter): String {
        val (slug, chapterSlug) = chapter.url.split("/")
        return "$baseUrl/series/$slug/chapter/$chapterSlug"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = client.get("${getChapterUrl(chapter)}.rsc").extractNextJs<ChapterPagesDto> {
            it is JsonObject && "chapterNumber" in it && (it["pages"] as? JsonArray)?.isNotEmpty() == true
        }!!.pages
        return pages.sortedBy { it.pageNumber }.mapIndexed { index, page ->
            Page(index, imageUrl = baseUrl + page.url)
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = getAllSeries().flatMap { it.genres }.distinct().sorted().toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(SortFilter(), TypeFilter(), StatusFilter())
        data?.parseAs<List<String>>()?.let { filters.add(GenreFilter(it)) }
        return FilterList(filters)
    }

    // old mangathemesia urls were /manga/<slug>/
    private fun SManga.slug() = url.trim('/').substringAfterLast('/')
}

package eu.kanade.tachiyomi.extension.en.tailspace

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Tailspace :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 5, period = 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage = getBrowsePage(page) {
        addQueryParameter("sort", "trending")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getBrowsePage(page) {}

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getBrowsePage(page) {
        if (query.isNotBlank()) addQueryParameter("search", query.trim())
        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> filter.value.takeIf(String::isNotEmpty)?.let { addQueryParameter("sort", it) }
                is CategoryGroup -> filter.state.filter { it.state }.forEach { addQueryParameter("c", it.name) }
                is FinishedFilter -> if (filter.state) addQueryParameter("finishedOnly", "true")
                is SfwFilter -> if (filter.state) addQueryParameter("sfwOnly", "true")
                is TagGroup -> filter.state.forEach {
                    when {
                        it.isIncluded() -> addQueryParameter("tag", it.id.toString())
                        it.isExcluded() -> addQueryParameter("excludeTag", it.id.toString())
                    }
                }
                else -> {}
            }
        }
    }

    private suspend fun getBrowsePage(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = "$baseUrl/browse.data".toHttpUrl().newBuilder()
            .addQueryParameter("_routes", BROWSE_ROUTE)
            .addQueryParameter("page", page.toString())
            .apply(params)
            .build()
        val data = client.get(url, Fetch.XHR).parseRouteData<BrowseData>(BROWSE_ROUTE)
        val coalesce = coalesceSeries()
        val mangas = data.comicsAndAds
            .filter { "slug" in it }
            .map { it.parseAs<BrowseComic>().toSManga(coalesce) }
        return MangasPage(mangas, page < data.numberOfPages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.first() != "c") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val comic = getComic(slug) ?: return null
        return SManga.create().apply {
            this.url = comic.slug
            title = if (coalesceSeries()) comic.name.seriesTitle() else comic.name
            thumbnail_url = thumbnailUrl(comic.id, comic.thumbnailVersion)
        }
    }

    override fun getMangaUrl(manga: SManga): String = comicUrl(manga.url).toString()

    // Details and chapters come from the same comic pages, so both are always returned
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = getComic(manga.url) ?: throw Exception("Comic not found")
        val coalesce = coalesceSeries()
        val series = if (coalesce) getSeries(comic) else listOf(comic)
        val first = series.first()

        manga.apply {
            title = if (coalesce) first.name.seriesTitle() else first.name
            thumbnail_url = thumbnailUrl(first.id, first.thumbnailVersion)
            author = first.artists
            artist = first.artists
            description = series.firstNotNullOfOrNull { it.description?.takeIf(String::isNotBlank) }?.let(::parseDescription)
            genre = genres(series.map { it.category }, series.map { it.isSfw }, series.flatMap { it.tags.orEmpty() })
            status = parseStatus(series.map { it.state })
        }

        val chapterList = series.mapIndexed { i, c ->
            SChapter.create().apply {
                url = c.slug
                name = if (series.size == 1) "Chapter" else c.name
                chapter_number = (i + 1).toFloat()
                date_upload = c.published ?: 0L
            }
        }.reversed()

        return SMangaUpdate(manga, chapterList)
    }

    // A series is a chain of comics linked to their previous and next parts
    private suspend fun getSeries(comic: Comic): List<Comic> {
        val series = ArrayDeque(listOf(comic))
        val seen = mutableSetOf(comic.id)

        var previous = comic.previousComic
        while (previous != null) {
            val c = getComic(previous.slug)?.takeIf { seen.add(it.id) } ?: break
            series.addFirst(c)
            previous = c.previousComic
        }

        var next = comic.nextComic
        while (next != null) {
            val c = getComic(next.slug)?.takeIf { seen.add(it.id) } ?: break
            series.addLast(c)
            next = c.nextComic
        }

        return series
    }

    override fun getChapterUrl(chapter: SChapter): String = comicUrl(chapter.url).toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val comic = getComic(chapter.url) ?: throw Exception("Comic not found")
        return comic.pages.orEmpty().sortedBy { it.pageNumber }.mapIndexed { i, page ->
            Page(i, imageUrl = "$PAGES_PATH/comics/${comic.id}/${page.token}.${page.fileType ?: "jpg"}")
        }
    }

    private suspend fun getComic(slug: String): Comic? {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("c")
            .addPathSegment("$slug.data")
            .addQueryParameter("_routes", COMIC_ROUTE)
            .build()
        return client.get(url, Fetch.XHR).parseRouteData<ComicPageData>(COMIC_ROUTE).comic
    }

    private fun comicUrl(slug: String): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("c")
        .addPathSegment(slug)
        .build()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/tags", Fetch.XHR).parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            CategoryGroup(),
            FinishedFilter(),
            SfwFilter(),
        )

        val tags = data?.parseAs<TagsResponse>()?.data
        if (tags == null) {
            filters += Filter.Header("Tap 'Reset' to load tags")
        } else {
            val groups = tags.groupBy { it.category }
            groups[null]?.let { filters += TagGroup("General tags", it) }
            groups["species"]?.let { filters += TagGroup("Species tags", it) }
            groups["character"]?.let { filters += TagGroup("Character tags", it) }
            groups["franchise"]?.let { filters += TagGroup("Franchise tags", it) }
        }

        return FilterList(filters)
    }

    private fun coalesceSeries() = preferences.getBoolean(PREF_COALESCE_SERIES, true)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_COALESCE_SERIES
            title = "Coalesce series"
            summary = "Combine the linked parts of a series into a single entry"
            setDefaultValue(true)
        }.let(screen::addPreference)
    }
}

private const val BROWSE_ROUTE = "routes/pages/browse/BrowsePage"
private const val COMIC_ROUTE = "routes/pages/comic/ComicPage"
private const val PREF_COALESCE_SERIES = "pref_coalesce_series"

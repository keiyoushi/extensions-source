package eu.kanade.tachiyomi.multisrc.gigaviewer

import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.GraphQLErrorInterceptor
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

// GigaViewer Sources: https://hatena.co.jp/solutions/gigaviewer
abstract class GigaViewer :
    KeiSource(),
    ConfigurableSource {
    protected open val apiUrl get() = "$baseUrl/graphql"
    protected open val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addInterceptor(GraphQLErrorInterceptor())
    }

    /**
     * Series lists shown in popular ordered by their likes, and in latest ordered by their newest episode.
     *
     * GigaViewer sites sort their series into lists called serial groups, and each entry here is the database id of one of them.
     *
     * To find the id of a group:
     * 1. Open an episode of a series from that list and take the `data-giga_series` value from the document.
     * 2. Send this query, then pick the group by its name from the response:
     * ```
     * curl https://<site>/graphql -H "Content-Type: application/json" -d '{"query": "{ series(databaseId: \"<data-giga_series>\") { serialGroups { databaseId name } } }"}'
     * ```
     *
     * Genres can also be used:
     * list them with `-d '{"query": "{ genres { id name } }"}'` and decode the `id` from base64, which gives `Genre:<databaseId>`.
     */
    protected open val seriesListIds: List<String> = emptyList()

    // Sites without likes shows 0 for every series, so they keep the order of their lists
    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = fetchSeriesList(seriesListIds)
            .sortedByDescending { it.likeCount }
            .mapNotNull { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = fetchSeriesList(seriesListIds)
            .sortedByDescending { it.latestPublishedAt }
            .mapNotNull { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val mangas = client.post(apiUrl, body = graphQLBody(SEARCH_QUERY, variables = SearchVariables(query)))
                .parseGraphQLAs<SearchResponse>().searchSeries.edges
                .map { it.node }
                .sortedBy { it.isVolumeOnly }
                .mapNotNull { it.toSManga() }
            return MangasPage(mangas, false)
        }

        val ids = filters.firstInstance<CollectionFilter>().value
        return MangasPage(fetchCollection(ids), false)
    }

    protected open suspend fun fetchCollection(ids: List<String>): List<SManga> = fetchSeriesList(ids).mapNotNull { it.toSManga() }

    protected open suspend fun fetchSeriesList(ids: List<String>): List<SeriesListItem> {
        val variables = ids.withIndex().associate { (i, id) ->
            val nodeId = if (':' in id) id else "SerialGroup:$id"
            "id$i" to Base64.encodeToString(nodeId.toByteArray(), Base64.NO_WRAP)
        }

        return client.post(apiUrl, body = graphQLBody(seriesListQuery(variables.keys), variables = variables))
            .parseGraphQLAs<Map<String, SeriesListNode>>()
            .values
            .flatMap { node -> node.series.edges.map { it.node } }
            .distinctBy { it.databaseId }
    }

    /**
     * Sets the url of an entry from a website list. These lists only link to an episode,
     * but a series thumbnail has the series id at the start of its file name.
     * Thumbnails from `cdn-scissors.gigaviewer.com` contain that url as their last path segment.
     * Without a series thumbnail, the episode path is used instead and [fetchMangaUpdate] looks up its series.
     */
    protected fun SManga.setSeriesUrl(episodeUrl: String, thumbnailUrl: String?) {
        val path = episodeUrl.toHttpUrl().encodedPath
        val thumbnail = thumbnailUrl?.toHttpUrlOrNull()?.let { it.pathSegments.last().toHttpUrlOrNull() ?: it }
        val seriesId = thumbnail?.pathSegments?.takeLast(2)
            ?.takeIf { it.first().startsWith("series-") }
            ?.last()?.substringBefore("-")
        url = seriesId ?: path
        memo = buildJsonObject {
            put("path", path)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.first() !in listOf("episode", "volume")) return null
        return fetchSeries(fetchSeriesId(url.toString())).toSManga()
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.memo["path"]!!.string

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val seriesId = if (manga.url.startsWith("/")) fetchSeriesId(baseUrl + manga.url) else manga.url // for old url compatibility
        val series = fetchSeries(seriesId)
        val details = series.toSManga()

        if (!fetchChapters) return@coroutineScope SMangaUpdate(details, chapters)

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val hideUnavailable = preferences.getBoolean(HIDE_UNAVAILABLE_PREF_KEY, false)
        val episodes = async { fetchReadableProducts(seriesId, "episode", series.episodes.totalCount) }
        val volumes = async { fetchReadableProducts(seriesId, "volume", series.volumes.totalCount) }
        val chapterList = (episodes.await() + volumes.await())
            .filterNot { (hideLocked && it.isLocked) || (hideUnavailable && it.isUnavailable()) }
            .map { it.toSChapter(it.isUnavailable()) }

        SMangaUpdate(
            details,
            chapterList,
        )
    }

    private suspend fun fetchSeriesId(url: String): String = client.get(url).asJsoup().selectFirst("script.js-valve")!!.attr("data-giga_series")

    private suspend fun fetchSeries(id: String): SeriesDetails = client.post(apiUrl, body = graphQLBody(SERIES_QUERY, variables = SeriesVariables(id))).parseGraphQLAs<SeriesResponse>().series

    private suspend fun fetchReadableProducts(seriesId: String, type: String, count: Int): List<ReadableProduct> {
        val products = mutableListOf<ReadableProduct>()
        while (products.size < count) {
            val url = "$baseUrl/api/viewer/pagination_readable_products".toHttpUrl().newBuilder()
                .addQueryParameter("type", type)
                .addQueryParameter("aggregate_id", seriesId)
                .addQueryParameter("sort_order", "desc")
                .addQueryParameter("offset", products.size.toString())
                .build()
            val result = client.get(url).parseAs<List<ReadableProduct>>()
            if (result.isEmpty()) break
            products += result
        }
        return products
    }

    /** Whether a chapter can't be read at all. It then gets a 🔒 and "Hide Unavailable Chapters" removes it. */
    protected open fun ReadableProduct.isUnavailable(): Boolean = purchaseInfo.unavailable

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.memo["type"]!!.string}/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pageStructure = client.get(getChapterUrl(chapter)).asJsoup()
            .selectFirst("script#episode-json")!!.attr("data-value")
            .parseAs<ViewerDto>().readableProduct.pageStructure

        if (pageStructure == null || pageStructure.pages.isEmpty()) {
            throw Exception("This chapter is either unavailable or must be purchased.")
        }

        val isScrambled = pageStructure.choJuGiga == "baku"
        return pageStructure.pages
            .filter { it.type == "main" }
            .mapIndexed { i, page ->
                val src = page.src!!
                Page(i, imageUrl = if (isScrambled) "$src#scramble" else src)
            }
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val options = getFilterOptions()
        return if (options.isNotEmpty()) {
            FilterList(CollectionFilter(options))
        } else {
            FilterList()
        }
    }

    /**
     * Filter options, each a label and the series lists it shows.
     * The ids are found as described in [seriesListIds].
     */
    protected open fun getFilterOptions(): List<Pair<String, List<String>>> = emptyList()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Paid Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_UNAVAILABLE_PREF_KEY
            title = "Hide Unavailable Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val HIDE_UNAVAILABLE_PREF_KEY = "hide_unavailable"
    }
}

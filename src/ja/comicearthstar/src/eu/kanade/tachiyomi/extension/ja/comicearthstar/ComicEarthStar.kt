package eu.kanade.tachiyomi.extension.ja.comicearthstar

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ComicEarthStar :
    GigaViewer(
        "Comic Earth Star",
        "https://comic-earthstar.com",
        "ja",
        "https://cdn-img.comic-earthstar.com",
        true,
    ) {
    private val apiUrl = "$baseUrl/graphql"
    private val jst = TimeZone.getTimeZone("Asia/Tokyo")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }

    override val supportsLatest = false

    override val publisher: String = "アース・スター エンターテイメント"

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    private inline fun <reified T> apiRequest(operationName: String, variables: T, query: String): Request {
        val payload = Payload(operationName, variables, query).toJsonString().toRequestBody()
        return POST(apiUrl, headers, payload)
    }

    override fun popularMangaRequest(page: Int): Request {
        val cal = Calendar.getInstance(jst)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY)
        cal.set(Calendar.HOUR_OF_DAY, 18)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        if (Calendar.getInstance(jst).time.before(cal.time)) {
            cal.add(Calendar.DATE, -7)
        }

        val since = cal.time
        cal.add(Calendar.DATE, 7)
        val until = cal.time

        val variables = mapOf(
            "latestUpdatedSince" to dateFormat.format(since),
            "latestUpdatedUntil" to dateFormat.format(until),
        )

        return apiRequest("Earthstar_LatestUpdates", variables, LATEST_QUERY)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val results = response.parseAs<LatestResponse>().data.serialGroup.latestUpdatedSeriesEpisodes.map { it.toSManga(baseUrl) }
        return MangasPage(results, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val variables = mapOf(
                "keyword" to query,
            )
            return apiRequest("Common_Search", variables, SEARCH_QUERY).newBuilder().tag("search").build()
        }

        return when (val filter = filters.firstInstance<CollectionFilter>().selected.path) {
            "Earthstar_SeriesOngoing" -> apiRequest(filter, EmptyVariables, ONGOING_QUERY).newBuilder().tag("ongoing").build()
            "Earthstar_SeriesFinished" -> apiRequest(filter, EmptyVariables, FINISHED_QUERY).newBuilder().tag("finished").build()
            "Earthstar_Oneshot" -> apiRequest(filter, EmptyVariables, ONESHOT_QUERY).newBuilder().tag("oneshot").build()
            else -> popularMangaRequest(page)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val tag = response.request.tag()
        val mangas = when (tag) {
            "search" -> {
                val results = response.parseAs<SearchResponse>()
                results.data.searchSeries.edges.map { it.node.toSManga(baseUrl) }
            }

            "oneshot" -> {
                val results = response.parseAs<OneshotResponse>()
                results.data.seriesOneshot.seriesSlice.seriesList.map { it.toSManga(baseUrl) }
            }

            "finished", "ongoing" -> {
                val results = response.parseAs<SeriesResponse>()
                results.data.serialGroup.seriesSlice.seriesList.map { it.toSManga(baseUrl) }
            }

            else -> popularMangaParse(response).mangas
        }
        return MangasPage(mangas, false)
    }

    override fun getFilterList(): FilterList = FilterList(CollectionFilter(getCollections()))

    private class CollectionFilter(val collections: List<Collection>) : Filter.Select<Collection>("コレクション", collections.toTypedArray()) {
        val selected: Collection
            get() = collections[state]
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("最新の更新", ""),
        Collection("連載中", "Earthstar_SeriesOngoing"),
        Collection("連載終了", "Earthstar_SeriesFinished"),
        Collection("読切作品", "Earthstar_Oneshot"),
    )

    // https://comic-earthstar.com/_next/static/chunks/4037-39196498009057a7.js
    companion object {
        private const val LATEST_QUERY = $$"query Earthstar_LatestUpdates($latestUpdatedSince: DateTime!, $latestUpdatedUntil: DateTime!) { serialGroup(groupName: \"トップ：更新作品\") { latestUpdatedSeriesEpisodes: updatedFreeEpisodes(since: $latestUpdatedSince until: $latestUpdatedUntil) { permalink series { title thumbnailUri } } } }"
        private const val SEARCH_QUERY = $$"query Common_Search($keyword: String!) { searchSeries(keyword: $keyword) { edges { node { title thumbnailUri firstEpisode { permalink } } } } }"
        private const val ONESHOT_QUERY = "query Earthstar_Oneshot { seriesOneshot: serialGroup(groupName: \"連載・読切：読切作品\") { seriesSlice { seriesList { ...Earthstar_SeriesListItem_Series } } } } fragment Earthstar_SeriesListItem_Series on Series { thumbnailUri title firstEpisode { permalink } }"
        private const val ONGOING_QUERY = "query Earthstar_SeriesOngoing { serialGroup(groupName: \"連載・読切：連載作品：連載中\") { seriesSlice { seriesList { ...Earthstar_SeriesListItem_Series } } } } fragment Earthstar_SeriesListItem_Series on Series { thumbnailUri title firstEpisode { permalink } }"
        private const val FINISHED_QUERY = "query Earthstar_SeriesFinished { serialGroup(groupName: \"連載・読切：連載作品：連載終了\") { seriesSlice { seriesList { ...Earthstar_SeriesListItem_Series } } } } fragment Earthstar_SeriesListItem_Series on Series { thumbnailUri title firstEpisode { permalink } }"
    }
}

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
        val results = response.parseAs<LatestResponse>().data.serialGroup.latestUpdatedSeriesEpisodes.map { it.toSManga() }
        return MangasPage(results, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val variables = mapOf(
                "keyword" to query,
            )
            return apiRequest("Common_Search", variables, SEARCH_QUERY).newBuilder().tag("search").build()
        }

        val filter = filters.firstInstance<CategoryFilter>()
        return apiRequest(filter.type, EmptyVariables, filter.value)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val tag = response.request.tag()
        val mangas = if (tag == "search") {
            response.parseAs<SearchResponse>().data.searchSeries.edges.map { it.node.toSManga() }
        } else {
            response.parseAs<SeriesResponse>().data.serialGroup.seriesSlice.seriesList.map { it.toSManga() }
        }

        return MangasPage(mangas, false)
    }

    override fun getFilterList() = FilterList(
        CategoryFilter(),
    )

    private open class SelectFilter(displayName: String, private val vals: Array<Triple<String, String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val type: String
            get() = vals[state].second

        val value: String
            get() = vals[state].third
    }

    private class CategoryFilter :
        SelectFilter(
            "コレクション",
            arrayOf(
                Triple("連載中", "Earthstar_SeriesOngoing", ONGOING_QUERY),
                Triple("連載終了", "Earthstar_SeriesFinished", FINISHED_QUERY),
                Triple("読切作品", "Earthstar_Oneshot", ONESHOT_QUERY),
            ),
        )

    // https://comic-earthstar.com/_next/static/chunks/4037-39196498009057a7.js
    companion object {
        private const val LATEST_QUERY = $$"query Earthstar_LatestUpdates($latestUpdatedSince: DateTime!, $latestUpdatedUntil: DateTime!) { serialGroup(groupName: \"トップ：更新作品\") { latestUpdatedSeriesEpisodes: updatedFreeEpisodes(since: $latestUpdatedSince until: $latestUpdatedUntil) { permalink series { title thumbnailUri } } } }"
        private const val SEARCH_QUERY = $$"query Common_Search($keyword: String!) { searchSeries(keyword: $keyword) { edges { node { title thumbnailUri firstEpisode { permalink } } } } }"
        private const val ONESHOT_QUERY = "query Earthstar_Oneshot { serialGroup(groupName: \"連載・読切：読切作品\") { seriesSlice { seriesList { ...Earthstar_SeriesListItem_Series } } } } fragment Earthstar_SeriesListItem_Series on Series { thumbnailUri title firstEpisode { permalink } }"
        private const val ONGOING_QUERY = "query Earthstar_SeriesOngoing { serialGroup(groupName: \"連載・読切：連載作品：連載中\") { seriesSlice { seriesList { ...Earthstar_SeriesListItem_Series } } } } fragment Earthstar_SeriesListItem_Series on Series { thumbnailUri title firstEpisode { permalink } }"
        private const val FINISHED_QUERY = "query Earthstar_SeriesFinished { serialGroup(groupName: \"連載・読切：連載作品：連載終了\") { seriesSlice { seriesList { ...Earthstar_SeriesListItem_Series } } } } fragment Earthstar_SeriesListItem_Series on Series { thumbnailUri title firstEpisode { permalink } }"
    }
}

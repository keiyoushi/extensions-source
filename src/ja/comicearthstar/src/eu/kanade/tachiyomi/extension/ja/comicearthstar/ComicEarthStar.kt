package eu.kanade.tachiyomi.extension.ja.comicearthstar

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.utils.GraphQLErrorInterceptor
import keiyoushi.utils.firstInstance
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseGraphQLAs
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Source
abstract class ComicEarthStar : GigaViewer() {
    private val apiUrl get() = "$baseUrl/graphql"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addGigaViewerInterceptors()
        .addInterceptor(GraphQLErrorInterceptor())

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        // updates are published weekly on Thursday 18:00 JST
        val now = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"))
        var since = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY))
            .truncatedTo(ChronoUnit.DAYS)
            .withHour(18)
        if (now.isBefore(since)) {
            since = since.minusWeeks(1)
        }
        val until = since.plusWeeks(1)

        val variables = mapOf(
            "latestUpdatedSince" to DateTimeFormatter.ISO_INSTANT.format(since.toInstant()),
            "latestUpdatedUntil" to DateTimeFormatter.ISO_INSTANT.format(until.toInstant()),
        )

        val response = client.post(apiUrl, body = graphQLBody(LATEST_QUERY, "Earthstar_LatestUpdates", variables))
        val results = response.parseGraphQLAs<LatestResponse>().serialGroup.latestUpdatedSeriesEpisodes.map { it.toSManga() }
        return MangasPage(results, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = if (query.isNotEmpty()) {
            val variables = mapOf(
                "keyword" to query,
            )
            client.post(apiUrl, body = graphQLBody(SEARCH_QUERY, "Common_Search", variables))
                .parseGraphQLAs<SearchResponse>().searchSeries.edges.map { it.node.toSManga() }
        } else {
            val filter = filters.firstInstance<CategoryFilter>()
            client.post(apiUrl, body = graphQLBody(filter.value, filter.type, EmptyVariables))
                .parseGraphQLAs<SeriesResponse>().serialGroup.seriesSlice.seriesList.map { it.toSManga() }
        }

        return MangasPage(mangas, false)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
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

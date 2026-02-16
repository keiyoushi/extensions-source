package eu.kanade.tachiyomi.extension.ja.comicearthstar

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("Unused")
@Serializable
class Payload<T>(
    val operationName: String,
    val variables: T,
    val query: String,
)

@Serializable
object EmptyVariables

@Serializable
class LatestResponse(
    val data: LatestData,
)

@Serializable
class LatestData(
    val serialGroup: LatestSerialGroup,
)

@Serializable
class LatestSerialGroup(
    val latestUpdatedSeriesEpisodes: List<LatestUpdatedSeriesEpisode>,
)

@Serializable
class LatestUpdatedSeriesEpisode(
    val permalink: String,
    val series: LatestSeries,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = series.title
        url = permalink.removePrefix(baseUrl)
        thumbnail_url = series.thumbnailUri
    }
}

@Serializable
class LatestSeries(
    val thumbnailUri: String,
    val title: String,
)

@Serializable
class SearchResponse(
    val data: SearchData,
)

@Serializable
class SearchData(
    val searchSeries: SearchSeries,
)

@Serializable
class SearchSeries(
    val edges: List<SearchEdge>,
)

@Serializable
class SearchEdge(
    val node: EntryNode,
)

@Serializable
class EntryNode(
    val firstEpisode: FirstEpisode,
    val thumbnailUri: String,
    val title: String,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = this@EntryNode.title
        url = firstEpisode.permalink.removePrefix(baseUrl)
        thumbnail_url = thumbnailUri
    }
}

@Serializable
class FirstEpisode(
    val permalink: String,
)

@Serializable
class OneshotResponse(
    val data: OneshotData,
)

@Serializable
class OneshotData(
    val seriesOneshot: SeriesEntries,
)

@Serializable
class SeriesResponse(
    val data: SeriesData,
)

@Serializable
class SeriesData(
    val serialGroup: SeriesEntries,
)

@Serializable
class SeriesEntries(
    val seriesSlice: SeriesSlices,
)

@Serializable
class SeriesSlices(
    val seriesList: List<EntryNode>,
)

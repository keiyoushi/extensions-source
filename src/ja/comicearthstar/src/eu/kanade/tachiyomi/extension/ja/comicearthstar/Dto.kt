package eu.kanade.tachiyomi.extension.ja.comicearthstar

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

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
    private val permalink: String,
    private val series: LatestSeries,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = series.title
        url = permalink.toHttpUrl().encodedPath
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
    private val firstEpisode: FirstEpisode,
    private val thumbnailUri: String,
    private val title: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@EntryNode.title
        url = firstEpisode.permalink.toHttpUrl().encodedPath
        thumbnail_url = thumbnailUri
    }
}

@Serializable
class FirstEpisode(
    val permalink: String,
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

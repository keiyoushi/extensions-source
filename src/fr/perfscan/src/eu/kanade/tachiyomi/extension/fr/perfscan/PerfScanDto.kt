package eu.kanade.tachiyomi.extension.fr.perfscan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PerfScanResponse<T>(
    val data: T,
)

@Serializable
class PerfScanSeries(
    val slug: String,
    val title: String,
    val thumbnail: String,
)

@Serializable
class PerfScanSeriesDetails(
    val title: String,
    val slug: String,
    val thumbnail: String,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    @SerialName("SeriesGenre") val seriesGenre: List<PerfScanGenreObject> = emptyList(),
    @SerialName("Status") val statusObject: PerfScanStatusObject? = null,
    @SerialName("Chapter") val chapters: List<PerfScanChapter> = emptyList(),
)

@Serializable
class PerfScanGenreObject(
    @SerialName("Genre") val genre: PerfScanGenre,
)

@Serializable
class PerfScanGenre(
    val name: String,
)

@Serializable
class PerfScanStatusObject(
    val name: String,
)

@Serializable
class PerfScanChapter(
    val id: String,
    val index: Float,
    val title: String?,
    val createdAt: String,
    @SerialName("Season") val season: PerfScanSeason,
)

@Serializable
class PerfScanSeason(
    val name: String,
)

@Serializable
class PerfScanPageList(
    val content: List<PerfScanImage>,
)

@Serializable
class PerfScanImage(
    val value: String,
)

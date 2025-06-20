package eu.kanade.tachiyomi.extension.all.webtoons

import kotlinx.serialization.Serializable

typealias EpisodeListResponse = ResultDto<EpisodeList>

@Serializable
class ResultDto<T>(
    val result: T,
)

@Serializable
class EpisodeList(
    val episodeList: List<Episode>,
)

@Serializable
class Episode(
    val episodeNo: Float,
    val episodeTitle: String,
    val viewerLink: String,
    val exposureDateMillis: Long,
    val hasBgm: Boolean = false,
)

@Serializable
class MotionToonResponse(
    val assets: MotionToonAssets,
)

@Serializable
class MotionToonAssets(
    val images: Map<String, String>,
)

package eu.kanade.tachiyomi.extension.ja.ichicomi

import kotlinx.serialization.Serializable

@Serializable
class LatestUpdateDto(
    val episode: LatestUpdateEpisodeDto,
)

@Serializable
class LatestUpdateEpisodeDto(
    val permalink: String,
    val series: LatestUpdateSeriesDto,
)

@Serializable
class LatestUpdateSeriesDto(
    val title: String,
    val subThumbnailUriTemplate: String,
)

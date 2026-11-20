package eu.kanade.tachiyomi.extension.all.mangadex.dto

import kotlinx.serialization.Serializable

@Serializable
class StatisticsDto(
    val statistics: Map<String, MangaStatisticsDto>,
)

@Serializable
class MangaStatisticsDto(
    val rating: RatingStatisticsDto? = null,
    val follows: Long? = null,
)

@Serializable
class RatingStatisticsDto(
    val bayesian: Double? = null,
    val distribution: Map<String, Long> = emptyMap(),
)

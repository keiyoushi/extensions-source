package eu.kanade.tachiyomi.extension.all.mangadex.dto

import kotlinx.serialization.Serializable

@Serializable
class StatisticsDto(
    val statistics: Map<String, MangaStatisticsDto>,
)

@Serializable
class MangaStatisticsDto(
    val rating: RatingStatisticsDto,
)

@Serializable
class RatingStatisticsDto(
    val bayesian: Double,
    val distribution: Map<String, Long>,
)

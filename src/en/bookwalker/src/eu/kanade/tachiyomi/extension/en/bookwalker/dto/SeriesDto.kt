package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("series")
class SeriesDto(
    val seriesId: Int,
    val seriesName: String,
    val imageUrl: String,
) : HoldBookEntityDto()

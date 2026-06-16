package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("series")
class SeriesDto(
    val seriesId: Int,
    val seriesName: String,
    val imageUrl: String,
) : HoldBookEntityDto()

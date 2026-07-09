package eu.kanade.tachiyomi.extension.es.akaya

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterDataDto(
    private val images: List<ChapterImageDto>? = null,
) {
    val sortedImages get() = images?.sortedBy { it.orderSort } ?: emptyList()
}

@Serializable
class ChapterImageDto(
    val image: String,
    @SerialName("order_sort") val orderSort: Int = 0,
)

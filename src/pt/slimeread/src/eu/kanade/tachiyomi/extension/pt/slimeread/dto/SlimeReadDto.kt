package eu.kanade.tachiyomi.extension.pt.slimeread.dto

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PopularMangaDto(
    @SerialName("book_image") val thumbnail_url: String?,
    @SerialName("book_id") val id: Int,
    @SerialName("book_name_original") val name: String,
)

@Serializable
data class LatestResponseDto(
    val pages: Int,
    val page: Int,
    val data: List<PopularMangaDto>,
)

fun List<PopularMangaDto>.toSMangaList(): List<SManga> = map { item ->
    SManga.create().apply {
        thumbnail_url = item.thumbnail_url
        title = item.name
        url = "/manga/${item.id}"
    }
}

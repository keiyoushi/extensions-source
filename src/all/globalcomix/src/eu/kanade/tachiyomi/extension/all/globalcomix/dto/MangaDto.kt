package eu.kanade.tachiyomi.extension.all.globalcomix.dto

import eu.kanade.tachiyomi.extension.all.globalcomix.comic
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias MangaDto = ResponseDto<MangaDataDto>
typealias MangasDto = PaginatedResponseDto<MangaDataDto>

@Suppress("PropertyName")
@Serializable
@SerialName(comic)
class MangaDataDto(
    val name: String,
    val description: String?,
    val status_name: String?,
    val category_name: String?,
    val image_url: String?,
    val artist: ArtistDto,
) : EntityDto() {
    companion object {
        /**
         * Create an [SManga] instance from the JSON DTO element.
         */
        fun MangaDataDto.createManga(): SManga =
            SManga.create().also {
                it.initialized = true
                it.url = id.toString()
                it.description = description
                it.author = artist.let { it.roman_name ?: it.name }
                it.status = status_name?.let(::convertStatus) ?: SManga.UNKNOWN
                it.genre = category_name
                it.title = name
                it.thumbnail_url = image_url
            }

        private fun convertStatus(status: String): Int {
            return when (status) {
                "Ongoing" -> SManga.ONGOING
                "Preview" -> SManga.ONGOING
                "Finished" -> SManga.COMPLETED
                "On hold" -> SManga.ON_HIATUS
                "Cancelled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }
}

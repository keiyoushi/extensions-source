package eu.kanade.tachiyomi.extension.pt.argosscan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaDto(
    @SerialName("attributes")
    val details: DetailsDto,
    @SerialName("cover_image_url")
    val thumbnailUrl: String,
    val id: String,
) {
    val type get() = details.type

    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = details.title.values.first()
        description = details.description.values.firstOrNull()
        genre = details.genres.get("genre")?.let(List<String>::joinToString)
        thumbnail_url = "$baseUrl/$thumbnailUrl"
        status = details.status.toStatus()
        url = this@MangaDto.id
        initialized = true
    }
}

fun String.toStatus(): Int =
    when (lowercase()) {
        "em-lancamento" -> SManga.ONGOING
        "completa" -> SManga.COMPLETED
        "em-pausa" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

@Serializable
class DetailsDto(
    val title: Map<String, String>,
    val description: Map<String, String> = emptyMap(),
    val status: String,
    @SerialName("tags")
    val genres: Map<String, List<String>> = emptyMap(),
    val type: String,
)

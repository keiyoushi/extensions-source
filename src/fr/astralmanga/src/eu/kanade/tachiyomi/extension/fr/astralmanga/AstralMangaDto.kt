package eu.kanade.tachiyomi.extension.fr.astralmanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class PresignResponseDto(
    val url: String,
)

@Serializable
class MangaResponseDto(
    val mangas: List<MangaDto>,
    val total: Int,
)

@Serializable
class MangaDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val urlId: String,
    val cover: CoverDto? = null,
    val status: String? = null,
    val type: String? = null,
    val publishDate: String? = null,
    val genres: List<NameDto>? = null,
    val authors: List<NameDto>? = null,
    val artists: List<NameDto>? = null,
    val teams: List<NameDto>? = null,
) {
    fun toSManga(presignS3Key: (String) -> String?): SManga = SManga.create().apply {
        url = "/manga/$urlId"
        title = this@MangaDto.title

        thumbnail_url = cover?.image?.link?.let { link ->
            if (link.startsWith("s3:")) presignS3Key(link.substringAfter("s3:")) else link
        }

        description = this@MangaDto.description

        status = when (this@MangaDto.status?.uppercase()) {
            "ON_GOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "CANCELLED" -> SManga.CANCELLED
            "HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }

        genre = (listOfNotNull(type) + (genres?.map { it.name } ?: emptyList())).joinToString()
        author = authors?.joinToString { it.name }?.ifBlank { null }
        artist = artists?.joinToString { it.name }?.ifBlank { this@apply.author }

        val extraInfo = StringBuilder()
        if (!teams.isNullOrEmpty()) {
            extraInfo.append("\n\nTeams: ").append(teams.joinToString { it.name })
        }
        val year = this@MangaDto.publishDate?.substringBefore("-")
        if (!year.isNullOrBlank()) {
            extraInfo.append("\nAnnée: ").append(year)
        }
        if (extraInfo.isNotEmpty()) {
            description = (description ?: "") + extraInfo.toString()
        }
    }
}

@Serializable
class CoverDto(
    val image: ImageDto? = null,
)

@Serializable
class ImageDto(
    val link: String,
)

@Serializable
class NameDto(val name: String)

@Serializable
class RscChapterDto(
    val id: String,
    val orderId: Float,
    val publishDate: String? = null,
    val mangaId: String,
) {
    val orderIdString: String
        get() = if (orderId % 1 == 0f) orderId.toInt().toString() else orderId.toString()
}

@Serializable
class RscImageDto(
    val link: String,
    val orderId: Int,
)

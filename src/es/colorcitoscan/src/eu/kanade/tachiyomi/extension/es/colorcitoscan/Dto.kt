package eu.kanade.tachiyomi.extension.es.colorcitoscan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.time.Instant

@Serializable
class ComicsResponseDto(
    val response: List<ComicDataDto> = emptyList(),
)

@Serializable
class ComicDataDto(
    val name: String,
    private val slug: String,
    private val urlImg: String? = null,
    private val alternativeName: String? = null,
    @SerialName("state_id") val stateId: Int? = null,
    val origins: List<OriginItemDto> = emptyList(),
    val genders: List<GenderItemDto> = emptyList(),
    val trending: TrendingDto? = null,
    val actualizacionCap: String? = null,
    val averageRating: Double? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = name
        url = "/ver/$slug"
        thumbnail_url = urlImg
    }

    fun containsQuery(query: String): Boolean = name.contains(query, ignoreCase = true) ||
        alternativeName?.contains(query, ignoreCase = true) == true
}

@Serializable
class OriginItemDto(
    val origin: NamedDto? = null,
)

@Serializable
class GenderItemDto(
    val gender: NamedDto? = null,
)

@Serializable
class NamedDto(
    val id: Int? = null,
    val name: String? = null,
)

@Serializable
class TrendingDto(
    val visitas: Long = 0L,
    val mensual: Long = 0L,
    val semanal: Long = 0L,
    val diario: Long = 0L,
)

@Serializable
class ProjectDetailsResponseDto(
    val response: ProjectDetailsDto,
)

@Serializable
class ProjectDetailsDto(
    val id: Int? = null,
    private val name: String,
    private val sinopsis: String? = null,
    private val slug: String,
    private val urlImg: String? = null,
    private val state: ProjectStateDto? = null,
    private val genders: List<GenderItemDto> = emptyList(),
    private val origins: List<OriginItemDto> = emptyList(),
    private val autors: List<NamedDto> = emptyList(),
    private val artists: List<NamedDto> = emptyList(),
    private val lastChapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = "/ver/$slug"
        title = name
        thumbnail_url = urlImg
        description = sinopsis
        status = when (state?.estado?.lowercase(Locale.ROOT)) {
            "en emision", "en emisión" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            "en pausa" -> SManga.ON_HIATUS
            "cancelado", "abandonado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        genre = (
            origins.mapNotNull { it.origin?.name } +
                genders.mapNotNull { it.gender?.name }
            )
            .distinct()
            .joinToString()
        author = autors.mapNotNull { it.name }.joinToString().takeIf { it.isNotBlank() }
        artist = artists.mapNotNull { it.name }.joinToString().takeIf { it.isNotBlank() }
    }

    fun toChapterList(): List<SChapter> = lastChapters.map { chapter ->
        chapter.toSChapter(slug)
    }
}

@Serializable
class ProjectStateDto(
    val estado: String? = null,
)

@Serializable
class ChapterDto(
    val id: Int? = null,
    private val num: String? = null,
    private val name: String? = null,
    private val slug: String,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(projectSlug: String): SChapter = SChapter.create().apply {
        url = "/ver/$projectSlug/$slug"
        name = if (!num.isNullOrBlank()) "Capítulo $num" else slug
        scanlator = this@ChapterDto.name?.takeIf { it.isNotBlank() }
        date_upload = Instant.tryParse(createdAt)
    }
}

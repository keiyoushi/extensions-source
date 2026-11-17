package eu.kanade.tachiyomi.extension.pt.akaitomangas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

@Serializable
class RankDto(
    val initialProjects: List<ProjectDto>,
)

@Serializable
class GenresDto(
    val availableGenders: List<NameDto>,
)

@Serializable
class NameDto(
    val name: String,
)

@Serializable
class ProjectDto(
    private val id: String,
    private val title: String,
    @SerialName("cover_image") private val coverImage: String? = null,
    private val link: String,
) {
    fun toSManga() = SManga.create().apply {
        url = "/$id/${link.removePrefix("/")}"
        title = this@ProjectDto.title
        thumbnail_url = coverImage
    }
}

@Serializable
class MangaPageDto(
    val project: ProjectDetailsDto,
    val groups: List<GroupDto>,
)

@Serializable
class ProjectDetailsDto(
    private val title: String,
    private val synopsis: String? = null,
    @SerialName("cover_image") private val coverImage: String? = null,
    private val status: String? = null,
    private val authors: List<NameDto> = emptyList(),
    private val artists: List<NameDto> = emptyList(),
    private val genders: List<NameDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@ProjectDetailsDto.title
        description = synopsis
        thumbnail_url = coverImage
        author = authors.joinToString { it.name }.ifBlank { null }
        artist = artists.joinToString { it.name }.ifBlank { null }
        genre = genders.joinToString { it.name }.ifBlank { null }
        status = when (this@ProjectDetailsDto.status) {
            "ACTIVE", "UP_TO_DATE" -> SManga.ONGOING
            "FINISHED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "DROPPED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class GroupDto(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    private val title: JsonPrimitive,
    @SerialName("created_at") private val createdAt: String? = null,
    private val accessType: String? = null,
) {
    val number get() = title.content.toFloatOrNull() ?: 0f

    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        url = "$mangaUrl/capitulo/${title.content}"
        name = buildString {
            if (accessType == "VIP_ONLY" || accessType == "COINS") append("🔒 ")
            append("Capítulo ${title.content}")
        }
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class PagesDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val pageNumber: Int,
    val photo: String,
)

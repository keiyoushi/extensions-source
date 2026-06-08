package eu.kanade.tachiyomi.extension.pt.rfdragonscan

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.SimpleDateFormat

@Serializable
class ProjectsPageDto(
    val projects: List<ProjectDto>,
    val pagination: PaginationDto? = null,
)

@Serializable
class ProjectDto(
    val id: String,
    val title: String,
    @SerialName("cover_image") private val coverImage: String? = null,
    val link: String,
) {
    fun toSManga() = SManga.create().apply {
        url = "/$id/$link"
        this.title = this@ProjectDto.title
        thumbnail_url = coverImage
    }
}

@Serializable
class PaginationDto(
    val hasNextPage: Boolean,
)

@Serializable
class MangaDetailsDto(
    private val title: String? = null,
    private val synopsis: String? = null,
    @SerialName("cover_image") private val coverImage: String? = null,
    private val status: String? = null,
    private val authors: List<NameDto>? = null,
    private val artists: List<NameDto>? = null,
    private val genders: List<NameDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDetailsDto.title ?: ""
        description = synopsis
        thumbnail_url = coverImage
        status = when (this@MangaDetailsDto.status) {
            "ACTIVE", "UP_TO_DATE" -> SManga.ONGOING
            "FINISHED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        author = authors?.joinToString { it.name ?: "" }?.takeIf { it.isNotBlank() }
        artist = artists?.joinToString { it.name ?: "" }?.takeIf { it.isNotBlank() }
        genre = genders?.joinToString { it.name ?: "" }?.takeIf { it.isNotBlank() }
    }
}

@Serializable
class NameDto(val name: String? = null)

@Serializable
class SeasonListDto(
    val groups: List<SeasonGroupDto>? = null,
)

@Serializable
class SeasonGroupDto(
    val chapters: List<ChapterDataDto>? = null,
)

@Serializable
class ChapterDataDto(
    val title: JsonPrimitive? = null,
    @SerialName("created_at") private val createdAt: String? = null,
    val isUpcoming: Boolean? = null,
    val hasRestriction: Boolean? = null,
) {
    fun toSChapter(mangaId: String, mangaSlug: String, dateFormat: SimpleDateFormat) = SChapter.create().apply {
        val formatTitle = this@ChapterDataDto.title?.contentOrNull?.removeSuffix(".0") ?: ""
        url = "/$mangaId/$mangaSlug/capitulo/$formatTitle"
        name = "Capítulo $formatTitle"
        date_upload = createdAt?.let { dateFormat.tryParse(it) } ?: 0L
    }
}

@Serializable
class PagesDto(
    val pages: List<PageDataDto>? = null,
) {
    fun toPages(): List<Page> = pages?.mapIndexed { index, page ->
        Page(index, imageUrl = page.photo ?: "")
    } ?: emptyList()
}

@Serializable
class PageDataDto(
    val photo: String? = null,
)

package eu.kanade.tachiyomi.extension.pt.argosscan

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

@Serializable
class ProjectResponseDto(
    private val items: List<ProjectDto> = emptyList(),
) {
    fun toSMangaList(query: String = ""): List<SManga> = items.filter { it.type?.equals("Novel", ignoreCase = true) != true }
        .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
        .map { it.toSManga() }
}

@Serializable
class ProjectDto(
    val id: String,
    val title: String,
    val slug: String,
    val type: String? = null,
    private val description: String? = null,
    private val status: String? = null,
    @SerialName("cover_latest_url") private val coverLatestUrl: String? = null,
    private val authors: List<AuthorDto> = emptyList(),
    private val tags: List<TagDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$slug"
        title = this@ProjectDto.title
        thumbnail_url = coverLatestUrl
        description = this@ProjectDto.description
        status = when (this@ProjectDto.status?.lowercase()) {
            "completo" -> SManga.COMPLETED
            "em lançamento" -> SManga.ONGOING
            "em pausa" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        author = authors.filter { it.role.equals("autor", true) }
            .joinToString { it.name }
            .takeIf { it.isNotBlank() }
        artist = authors.filter { it.role.equals("artista", true) }
            .joinToString { it.name }
            .takeIf { it.isNotBlank() }
        genre = tags.joinToString { it.name }.takeIf { it.isNotBlank() }
    }
}

@Serializable
class AuthorDto(
    val name: String,
    val role: String? = null,
)

@Serializable
class TagDto(
    val name: String,
)

@Serializable
class ChapterResponseDto(
    private val items: List<ChapterDto> = emptyList(),
) {
    fun toSChapterList(projectId: String, dateFormat: SimpleDateFormat): List<SChapter> = items.sortedWith(compareByDescending<ChapterDto> { it.volumeNumber }.thenByDescending { it.chapterNumber })
        .map { it.toSChapter(projectId, dateFormat) }

    fun getImagesForChapter(chapterId: String): List<Page> {
        val chapter = items.find { it.id == chapterId }
            ?: throw Exception("Capítulo não encontrado.")
        return chapter.images?.mapIndexed { i, img ->
            Page(i, imageUrl = img.fileUrl)
        } ?: emptyList()
    }
}

@Serializable
class ChapterDto(
    val id: String,
    private val title: String? = null,
    @SerialName("chapter_number") val chapterNumber: Float? = null,
    @SerialName("volume_number") val volumeNumber: Int? = null,
    @SerialName("created_at") private val createdAt: String? = null,
    val images: List<ImageDto>? = null,
) {
    fun toSChapter(projectId: String, dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = "$id|$projectId"
        name = buildString {
            if (volumeNumber != null) append("Vol. $volumeNumber ")
            append("Cap. ")
            append(chapterNumber?.toString()?.removeSuffix(".0") ?: "0")
            if (!this@ChapterDto.title.isNullOrBlank()) append(" - ${this@ChapterDto.title}")
        }.trim()
        date_upload = createdAt?.substringBefore(".")?.let {
            dateFormat.tryParse(it)
        } ?: 0L
    }
}

@Serializable
class ImageDto(
    @SerialName("file_url") val fileUrl: String,
)

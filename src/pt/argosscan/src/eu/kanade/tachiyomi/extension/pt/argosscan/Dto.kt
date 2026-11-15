package eu.kanade.tachiyomi.extension.pt.argosscan

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Instant

@Serializable
class Projects(
    private val items: List<Project> = emptyList(),
) {
    fun toMangasPage(query: String = "") = items.filter { it.type?.equals("Novel", ignoreCase = true) != true }
        .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
        .map { it.toSManga() }.let {
            MangasPage(it, false)
        }
}

@Serializable
class Project(
    val id: String,
    val title: String,
    val slug: String,
    val type: String? = null,
    private val description: String? = null,
    private val status: String? = null,
    @SerialName("cover_latest_url") private val coverLatestUrl: String? = null,
    private val authors: List<Author> = emptyList(),
    private val tags: List<Tag> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$slug"
        title = this@Project.title
        thumbnail_url = coverLatestUrl
        description = this@Project.description
        status = when (this@Project.status?.lowercase()) {
            "completo" -> SManga.COMPLETED
            "em lançamento" -> SManga.ONGOING
            "em pausa" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        // Fixed: The API returns the roles in English ("Author", "Artist"), not just Portuguese
        author = authors.filter { it.role.equals("Author", true) || it.role.equals("autor", true) }
            .joinToString { it.name }
            .takeIf { it.isNotBlank() }
        artist = authors.filter { it.role.equals("Artist", true) || it.role.equals("artista", true) }
            .joinToString { it.name }
            .takeIf { it.isNotBlank() }
        genre = tags.joinToString { it.name }.takeIf { it.isNotBlank() }
        memo = buildJsonObject { put("projectId", id) }
    }
}

@Serializable
class Author(
    val name: String,
    val role: String? = null,
)

@Serializable
class Tag(
    val name: String,
)

@Serializable
class Chapters(
    private val items: List<Chapter> = emptyList(),
) {
    fun toSChapterList(projectId: String): List<SChapter> = items.sortedWith(compareByDescending<Chapter> { it.volumeNumber }.thenByDescending { it.chapterNumber })
        .map { it.toSChapter(projectId) }
}

@Serializable
class Chapter(
    val id: String,
    private val title: String? = null,
    @SerialName("chapter_number") val chapterNumber: Float? = null,
    @SerialName("volume_number") val volumeNumber: Int? = null,
    @SerialName("created_at") private val createdAt: String? = null,
    val images: List<Image>? = null,
) {
    fun toSChapter(projectId: String) = SChapter.create().apply {
        url = id
        name = buildString {
            if (volumeNumber != null) append("Vol. $volumeNumber ")
            append("Cap. ")
            append(chapterNumber?.toString()?.removeSuffix(".0") ?: "0")
            if (!this@Chapter.title.isNullOrBlank()) append(" - ${this@Chapter.title}")
        }.trim()
        date_upload = Instant.tryParse(createdAt)
        memo = buildJsonObject {
            putJsonArray("images") {
                images.orEmpty().forEach {
                    add(it.fileUrl)
                }
            }
        }
    }
}

@Serializable
class Image(
    @SerialName("file_url") val fileUrl: String,
)

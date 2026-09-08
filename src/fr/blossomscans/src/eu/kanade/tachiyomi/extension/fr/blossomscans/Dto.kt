package eu.kanade.tachiyomi.extension.fr.blossomscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class SeriesListResponse(
    val data: List<SeriesDto>,
    val pagination: PaginationDto,
)

@Serializable
class PaginationDto(
    val page: Int,
    val totalPages: Int,
)

@Serializable
class SeriesDto(
    private val slug: String,
    private val title: String,
    private val cover: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@SeriesDto.title
        thumbnail_url = cover?.let { baseUrl.resolveImage(it) }
    }
}

@Serializable
class SeriesDetailsDto(
    val slug: String,
    private val title: String,
    private val cover: String? = null,
    private val synopsis: String? = null,
    private val status: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val genres: List<GenreDto> = emptyList(),
    val chapters: List<ChapterDto>,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@SeriesDetailsDto.title
        thumbnail_url = cover?.let { baseUrl.resolveImage(it) }
        description = synopsis
        author = this@SeriesDetailsDto.author
        artist = this@SeriesDetailsDto.artist
        genre = genres.map { it.name }.joinToString().ifBlank { null }
        status = when (this@SeriesDetailsDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val number: Float,
    val chapterId: String,
    private val title: String? = null,
    private val pages: String,
    private val isPremium: Boolean = false,
    private val freeAt: String? = null,
    private val publishedAt: String? = null,
) {
    val isLocked: Boolean
        get() {
            if (!isPremium) {
                return false
            }
            val until = freeAt?.let { Instant.tryParse(it) }?.takeIf { it > 0 } ?: return true
            return System.currentTimeMillis() <= until
        }

    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val chapterNumber = number.toString().removeSuffix(".0")

        url = chapterId
        memo = buildJsonObject {
            put("mangaSlug", mangaSlug)
            put("number", chapterNumber)
        }
        name = buildString {
            if (isLocked) {
                append("🔒 ")
            }
            append("Chapitre ")
            append(chapterNumber)
            if (title != null) {
                append(" - ")
                append(title)
            }
        }
        chapter_number = number
        date_upload = Instant.tryParse(publishedAt)
    }

    fun pageUrls(baseUrl: String): List<String> = runCatching {
        jsonInstance.parseToJsonElement(pages).jsonArray.map { baseUrl.resolveImage(it.jsonPrimitive.content) }
    }.getOrDefault(emptyList())
}

private fun String.resolveImage(src: String): String = when {
    src.startsWith("http") -> src
    src.startsWith("/") -> this + src
    else -> "$this/$src"
}

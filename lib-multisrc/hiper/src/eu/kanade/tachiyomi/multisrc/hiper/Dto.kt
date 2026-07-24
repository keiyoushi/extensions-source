package eu.kanade.tachiyomi.multisrc.hiper

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class WrapperContent(
    val hits: List<MangaDto>,
)

@Serializable
class MangaDto(
    val id: Long,
    val slug: String,
    val title: String,
    val synopsis: String?,
    val coverUrl: String?,
    val status: String?,
    val genres: List<String>? = null,
    val authors: List<String>? = null,
    val artists: List<String>? = null,
    val type: String?,
    val contentRating: String?,
) {
    fun toSManga(mangaPath: String) = SManga.create().apply {
        title = this@MangaDto.title
        description = synopsis
        thumbnail_url = coverUrl
        artist = artists?.joinToString()
        author = authors?.joinToString()
        genre = ((genres ?: emptyList()) + listOfNotNull(type, contentRating)).joinToString()
        status = when (this@MangaDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        url = "/$mangaPath/$slug"
        memo = buildJsonObject {
            put("mangaId", id)
        }
        initialized = true
    }
}

@Serializable
class ChapterDto(
    val number: Float,
    val title: String?,
    val createdAt: String,
) {
    fun toSChapter(mangaPath: String) = SChapter.create().apply {
        name = buildChapterName()
        chapter_number = number
        date_upload = Instant.parseOrNull(createdAt)?.toEpochMilliseconds() ?: 0L
        url = "$mangaPath/$number"
    }

    private fun buildChapterName(): String = title?.let {
        when {
            NUMBER_REGEX.containsMatchIn(it) -> it
            else -> "$labelNumber $it"
        }
    } ?: labelNumber

    private val labelNumber: String get() = "Chapter ${number.toString().replace(".0", "")}"

    companion object {
        private val NUMBER_REGEX = """\d+""".toRegex()
    }
}

@Serializable
class PageDto(
    val pageOrder: Int,
    val webpUrl: String,
    val avifUrl: String?,
) {
    fun toPage() = Page(pageOrder, imageUrl = avifUrl ?: webpUrl)
}

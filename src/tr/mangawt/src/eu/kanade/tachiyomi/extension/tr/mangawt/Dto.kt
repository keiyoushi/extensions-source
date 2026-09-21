package eu.kanade.tachiyomi.extension.tr.mangawt

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class MangaListResponse(
    val mangas: List<MangaDto>,
    val page: Int,
    val pages: Int,
)

@Serializable
class MangaDto(
    @SerialName("_id") val id: String,
    val slug: String,
    private val title: String,
    private val coverUrl: String,
    private val type: String? = null,
    private val status: String? = null,
    private val genres: List<String> = emptyList(),
    private val author: String? = null,
    private val artist: String? = null,
    private val description: String? = null,
    private val altTitles: List<String> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = coverUrl
        author = this@MangaDto.author?.ifBlank { null }
        artist = this@MangaDto.artist?.ifBlank { null }
        genre = buildList {
            type?.let(::add)
            addAll(genres)
        }.joinToString().ifBlank { null }
        description = buildString {
            this@MangaDto.description?.let { append(it.trim()) }
            if (altTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternatif isimler: ")
                append(altTitles.joinToString())
            }
        }.ifBlank { null }
        status = when (this@MangaDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    @SerialName("_id") private val id: String,
    val number: Float,
    private val title: String,
    private val locked: Boolean = false,
    private val lockExpiresAt: String? = null,
    private val publishAt: String? = null,
    private val createdAt: String? = null,
) {
    fun toSChapter(mangaId: String, slug: String) = SChapter.create().apply {
        url = id
        memo = buildJsonObject {
            put("mangaId", mangaId)
            put("slug", slug)
            put("number", number.toString().removeSuffix(".0"))
            if (locked) put("lockedUntil", Instant.tryParse(lockExpiresAt))
        }
        name = if (locked) "$title 🔒" else title
        chapter_number = number
        date_upload = Instant.tryParse(publishAt ?: createdAt)
    }
}

@Serializable
class PageListResponse(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val pageNumber: Int,
    val signedUrl: String,
)

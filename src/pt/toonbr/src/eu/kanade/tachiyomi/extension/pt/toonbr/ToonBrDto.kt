package eu.kanade.tachiyomi.extension.pt.toonbr

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
internal class MangaDto(
    private val title: String,
    private val slug: String,
    private val description: String? = null,
    private val status: String? = null,
    private val coverImage: String? = null,
    val chapters: List<ChapterDto>? = null,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = "/manga/$slug"
        title = this@MangaDto.title
        thumbnail_url = coverImage?.let { "$cdnUrl$it" }
        description = this@MangaDto.description
        status = when (this@MangaDto.status) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
internal class ChapterDto(
    private val id: String,
    private val title: String,
    private val chapterNumber: Float? = null,
    private val createdAt: String? = null,
    val pages: List<PageDto>? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/chapter/$id"
        name = chapterNumber?.let { "Capítulo ${it.formatNumber()}" } ?: this@ChapterDto.title
        chapter_number = this@ChapterDto.chapterNumber ?: 0f
        date_upload = Instant.tryParse(createdAt)
    }

    private fun Float.formatNumber(): String = if (this % 1 == 0f) this.toInt().toString() else this.toString()
}

@Serializable
internal class PageDto(
    val imageUrl: String? = null,
)

@Serializable
internal class LoginRequest(
    private val email: String,
    private val password: String,
)

@Serializable
internal class LoginResponse(
    val token: String,
)

@Serializable
internal class MangaListResponse(
    val data: List<MangaDto>,
)

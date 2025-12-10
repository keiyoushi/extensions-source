package eu.kanade.tachiyomi.extension.pt.toonbr

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

@Serializable
internal data class MangaDto(
    val id: String,
    val title: String,
    val slug: String,
    val description: String? = null,
    val status: String? = null,
    val coverImage: String? = null,
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
internal data class ChapterDto(
    val id: String,
    val title: String,
    val chapterNumber: Float? = null,
    val createdAt: String? = null,
    val pages: List<PageDto>? = null,
) {
    fun toSChapter(dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = "/chapter/$id"
        name = chapterNumber?.let { "Capítulo ${it.formatNumber()}" } ?: this@ChapterDto.title
        chapter_number = this@ChapterDto.chapterNumber ?: 0f
        date_upload = createdAt?.let { dateFormat.tryParse(it) } ?: 0L
    }

    private fun Float.formatNumber(): String {
        return if (this % 1 == 0f) this.toInt().toString() else this.toString()
    }
}

@Serializable
internal data class PageDto(
    val id: String? = null,
    val imageUrl: String? = null,
    val pageNumber: Int? = null,
)

@Serializable
internal data class LoginResponse(
    val token: String,
)

@Serializable
internal data class MangaListResponse(
    val data: List<MangaDto>,
)

package eu.kanade.tachiyomi.extension.pt.taosect

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

@Serializable
internal data class MangaDto(
    val title: String? = null,
    val url: String,
    val description: String? = null,
    val coverImage: String? = null,
    val chapters: List<ChapterDto>? = null,
    val artist: String? = null,
    val author: String? = null,
    val genre: String? = null,
    val status: String? = null,
) {
    fun toSManga(): SManga {
        val title = this.title ?: extractTitleFromSlug(url)

        return SManga.create().apply {
            this.url = this@MangaDto.url
            this.title = title
            thumbnail_url = coverImage
            description = this@MangaDto.description
            artist = this@MangaDto.artist
            author = this@MangaDto.author
            genre = this@MangaDto.genre
            status = when (this@MangaDto.status) {
                "Ativos" -> SManga.ONGOING
                "Finalizados" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun extractTitleFromSlug(url: String): String {
        return url.let { it.substring(0, it.length - 1) }
            .replace("projeto/", "")
            .split("-")
            .joinToString(" ") { word ->
                word.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                }
            }
    }
}

@Serializable
internal data class ChapterDto(
    val url: String,
    val title: String,
    val createdAt: String? = null,
    val pages: List<PageDto>? = null,
) {
    fun toSChapter(dateFormat: SimpleDateFormat): SChapter {
        val chapterNumber = CHAPTER_REGEX.find(title)
            ?.groupValues
            ?.get(1)
            ?.toFloatOrNull()

        return SChapter.create().apply {
            this.url = this@ChapterDto.url
            name = this@ChapterDto.title
            chapter_number = chapterNumber ?: 0f
            date_upload = createdAt?.let { dateFormat.tryParse(it) } ?: 0L
        }
    }

    companion object {
        val CHAPTER_REGEX = Regex("""(\d+(?:\.\d+)?)""")
    }
}

@Serializable
internal data class PageDto(
    val imageUrl: String,
    val pageNumber: Int,
) {
    fun toSPage(): Page = Page(
        index = pageNumber,
        imageUrl = imageUrl,
    )
}

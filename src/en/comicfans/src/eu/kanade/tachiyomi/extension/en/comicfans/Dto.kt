package eu.kanade.tachiyomi.extension.en.comicfans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

typealias ListDataDto<T> = DataDto<ListDto<T>>

@Serializable
class ListDto<T>(
    val totalPages: Int,
    val list: List<T>,
)

@Serializable
class DataDto<T>(
    val data: T,
)

@Serializable
class MangaDto(
    val id: Int,
    val title: String,
    val coverImgUrl: String,
    val status: Int,
    val authorPseudonym: String? = null,
    val synopsis: String? = null,
) {
    fun toSManga(cdnUrl: String): SManga = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = "$cdnUrl/$coverImgUrl"
        author = authorPseudonym

        url = buildString {
            append("/comic/")
            append(slugify(id, title))
        }
        description = synopsis
        status = when (this@MangaDto.status) {
            0 -> SManga.ONGOING
            1 -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
class ChapterDto(
    val id: Int,
    val title: String,
    val updateTime: Long? = null,
) {
    fun toSChapter(index: Int): SChapter = SChapter.create().apply {
        name = "Ch. $index - $title"
        chapter_number = index.toFloat()
        date_upload = updateTime ?: 0L
        url = buildString {
            append("/episode/")
            append(slugify(id, title))
        }
    }
}

@Serializable
class PageDataDto(
    val comicImageList: List<PageDto>,
) {
    @Serializable
    class PageDto(
        val imageUrl: String,
        val sortNum: Int,
    )
}

private val symbolsRegex = Regex("\\W")
private val hyphenRegex = Regex("-{2,}")

private fun slugify(id: Int, title: String): String = buildString {
    append(id)
    append("-")
    append(
        title.lowercase()
            .replace(symbolsRegex, "-")
            .replace(hyphenRegex, "-")
            .removeSuffix("-")
            .removePrefix("-"),
    )
}

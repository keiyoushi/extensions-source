package eu.kanade.tachiyomi.multisrc.fansubscat

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ResultDto<T>(val result: T)

@Serializable
class MangaDto(
    private val slug: String,
    private val name: String,
    @SerialName("thumbnail_url") private val thumbnailUrl: String,
    private val author: String?,
    private val synopsis: String?,
    private val status: String,
    private val genres: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = thumbnailUrl
        author = this@MangaDto.author
        description = synopsis
        status = when {
            this@MangaDto.status.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
            this@MangaDto.status.contains("finished", ignoreCase = true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = genres
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val title: String,
    private val number: Float,
    private val fansub: String,
    private val created: Long,
) {
    fun toSChapter() = SChapter.create().apply {
        url = id
        name = title
        chapter_number = number
        scanlator = fansub
        date_upload = created
    }
}

@Serializable
class PageDto(private val url: String) {
    fun toPage(index: Int) = Page(index, imageUrl = url)
}

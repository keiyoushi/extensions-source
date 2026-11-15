package eu.kanade.tachiyomi.extension.vi.metruyen18sbs

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class ChapterListDto(
    val items: List<ChapterDto>,
    val next: String? = null,
)

@Serializable
class ChapterDto(
    val id: Long,
    private val number: String,
    private val title: String,
    private val url: String,
) {
    fun toSChapter(date: Long) = SChapter.create().apply {
        this.url = this@ChapterDto.url.toHttpUrl().encodedPath
        name = title
        chapter_number = number.toFloat()
        date_upload = date
    }
}

@Serializable
class PageDto(val url: String)

@Serializable
class GenreDto(val name: String, val value: String)

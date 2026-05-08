package eu.kanade.tachiyomi.extension.en.ninekon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

@Serializable
class BooksResponse(
    val pages: Int = 0,
    val books: List<BookDto> = emptyList(),
)

@Serializable
class BookDto(
    private val gid: String,
    private val title: String,
    private val cover: String? = null,
    private val host: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = gid
        this.title = this@BookDto.title
        thumbnail_url = if (host != null && cover != null) host + cover else null
    }
}

@Serializable
class BookDetailsDto(
    private val gid: String,
    private val title: String,
    private val summary: String? = null,
    private val author: String? = null,
    private val tags: String? = null,
    private val status: String? = null,
    private val host: String? = null,
    private val cover: String? = null,
    @SerialName("dt_updated") private val dtUpdated: String? = null,
    private val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = gid
        this.title = this@BookDetailsDto.title
        this.author = this@BookDetailsDto.author
        description = summary
        genre = tags?.split("|")?.filter { it.isNotBlank() }?.joinToString()
        this.status = when (this@BookDetailsDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = if (host != null && cover != null) host + cover else null
        initialized = true
    }

    fun getChapters(): List<SChapter> {
        val sChapters = chapters.map { it.toSChapter(gid) }.reversed()
        if (sChapters.isNotEmpty() && !dtUpdated.isNullOrEmpty()) {
            sChapters[0].date_upload = dateFormat.tryParse(dtUpdated)
        }
        return sChapters
    }
}

@Serializable
class ChapterDto(
    private val gid: String,
    private val ordinal: Float? = null,
) {
    fun toSChapter(mangaId: String) = SChapter.create().apply {
        url = "/books/$mangaId/chapters/$gid/pages"
        name = "Chapter ${ordinal?.toString()?.removeSuffix(".0") ?: "Unknown"}"
        chapter_number = ordinal ?: -1f
    }
}

@Serializable
class PagesDto(
    private val host: String,
    private val pages: List<String> = emptyList(),
) {
    fun getImages() = pages.map { host + it }
}

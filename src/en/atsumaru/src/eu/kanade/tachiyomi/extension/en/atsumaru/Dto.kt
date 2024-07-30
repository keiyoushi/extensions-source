package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class BrowseMangaDto(
    val items: List<MangaObjectDto>,
)

@Serializable
class MangaObjectDto(
    val manga: MangaDto,
)

@Serializable
class SearchResultsDto(
    val hits: List<SearchMangaDto>,
) {
    @Serializable
    class SearchMangaDto(
        val info: MangaDto,
    )
}

@Serializable
class MangaDto(
    // Common
    private val title: String,
    private val cover: String,
    private val slug: String,

    // Details
    private val authors: List<String>? = null,
    private val description: String? = null,
    private val genres: List<String>? = null,
    private val statuses: List<String>? = null,

    // Chapters
    val chapters: List<ChapterDto>? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = cover
        url = "/manga/s1/$slug"

        authors?.let {
            author = it.joinToString()
        }
        description = this@MangaDto.description
        genres?.let {
            genre = it.joinToString()
        }
        statuses?.let {
            status = when (it.first().lowercase().substringBefore(" ")) {
                "ongoing" -> SManga.ONGOING
                "complete" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }
}

@Serializable
class ChapterDto(
    val pages: List<PageDto>,
    val name: String,
    private val type: String,
    private val title: String? = null,
    private val date: String? = null,
) {
    fun toSChapter(slug: String): SChapter = SChapter.create().apply {
        val chapterNumber = this@ChapterDto.name.replace("_", ".")
            .filter { it.isDigit() || it == '.' }

        name = buildString {
            append("Chapter ")
            append(chapterNumber)
            if (title != null) {
                append(" - ")
                append(title)
            }
        }
        url = "$slug/${this@ChapterDto.name}"
        chapter_number = chapterNumber.toFloat()
        scanlator = type.takeUnless { it == "Chapter" }
        date?.let {
            date_upload = parseDate(it)
        }
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            DATE_FORMAT.parse(dateStr)!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    companion object {
        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        }
    }
}

@Serializable
class PageDto(
    val pageURLs: List<String>,
    val name: String,
)
